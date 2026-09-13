package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.DJBMonthlyBillingCalculation;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.repository.DJBMonthlyBillingCalculationDao;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateItem;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.TariffCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.TariffSlabCharge;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingStatement;
import org.egov.wscalculation.web.models.Property;
import org.egov.wscalculation.web.models.WaterConnection;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DJBMonthlyBillingCalculationSnapshotService {

    public static final String ENGINE_VERSION = "DJB-MONTHLY-BILLING-1.0";

    private static final int MONEY_SCALE = 2;

    private final DJBMonthlyBillingCalculationDao calculationDao;
    private final ObjectMapper objectMapper;

    public DJBMonthlyBillingCalculationSnapshotService(
            DJBMonthlyBillingCalculationDao calculationDao,
            ObjectMapper objectMapper) {
        this.calculationDao = calculationDao;
        this.objectMapper = objectMapper;
    }

    public String persist(
            RequestInfo requestInfo,
            WaterBillingCycle cycle,
            WaterConnection connection,
            Property property,
            TariffCalculationResult water,
            SewerageCalculationResult sewerage,
            RebateCalculationResult rebate,
            BigDecimal appliedPaidAdjustment,
            BigDecimal residualPaidCredit,
            BigDecimal carriedForwardCreditApplied,
            List<String> carriedForwardCreditAllocationIds,
            BigDecimal netAmount) {

        String calculationId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        DJBMonthlyBillingStatement statement = buildStatement(
                cycle, connection, property, water, sewerage, rebate,
                appliedPaidAdjustment, residualPaidCredit, carriedForwardCreditApplied,
                carriedForwardCreditAllocationIds, netAmount,
                calculationId, now, actor(requestInfo));

        final String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(statement);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize DJB billing calculation snapshot for " + cycle.getId(), ex);
        }

        DJBMonthlyBillingCalculation calculation = DJBMonthlyBillingCalculation.builder()
                .id(calculationId)
                .tenantid(cycle.getTenantid())
                .billingcycleid(cycle.getId())
                .connectionno(cycle.getConnectionno())
                .engineversion(ENGINE_VERSION)
                .status("CALCULATED")
                .calculatedtime(now)
                .calculatedby(actor(requestInfo))
                .snapshotjson(snapshotJson)
                .build();

        if (calculationDao.save(calculation) != 1) {
            throw new IllegalStateException("Failed to persist DJB billing calculation snapshot for " + cycle.getId());
        }

        return calculationId;
    }

    public DJBMonthlyBillingStatement read(String tenantId, String calculationId) {
        if (!StringUtils.hasText(calculationId)) {
            return null;
        }
        DJBMonthlyBillingCalculation calculation = calculationDao.findById(tenantId, calculationId);
        if (calculation == null || !StringUtils.hasText(calculation.getSnapshotjson())) {
            return null;
        }
        try {
            return objectMapper.readValue(calculation.getSnapshotjson(), DJBMonthlyBillingStatement.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read DJB billing calculation snapshot " + calculationId, ex);
        }
    }

    private DJBMonthlyBillingStatement buildStatement(
            WaterBillingCycle cycle,
            WaterConnection connection,
            Property property,
            TariffCalculationResult water,
            SewerageCalculationResult sewerage,
            RebateCalculationResult rebate,
            BigDecimal appliedPaidAdjustment,
            BigDecimal residualPaidCredit,
            BigDecimal carriedForwardCreditApplied,
            List<String> carriedForwardCreditAllocationIds,
            BigDecimal netAmount,
            String calculationId,
            long calculatedAt,
            String calculatedBy) {

        BigDecimal threshold = null;
        boolean onePointFiveExceeded = false;
        if (cycle.getPreviousconsumption() != null && cycle.getActualconsumption() != null) {
            threshold = cycle.getPreviousconsumption().multiply(new BigDecimal("1.5"));
            onePointFiveExceeded = cycle.getActualconsumption().compareTo(threshold) > 0;
        }

        boolean zroRequired = Boolean.TRUE.equals(cycle.getOnepointfivexflag())
                && "DOMESTIC".equalsIgnoreCase(water.getCategory())
                && org.egov.wscalculation.djbmonthlybilling.model.enums.ZroStatus.PENDING.equals(cycle.getZrostatus());

        String billingBasis = cycle.getBillingbasis() == null ? null : cycle.getBillingbasis().toString();
        String decisionReason;
        String reasonCode;
        if (BillingBasis.ACTUAL.equals(cycle.getBillingbasis()) || BillingBasis.CORRECTED_ACTUAL.equals(cycle.getBillingbasis())) {
            decisionReason = "Actual meter consumption is billed for the selected OK/corrected-actual cycle.";
            reasonCode = BillingBasis.CORRECTED_ACTUAL.equals(cycle.getBillingbasis()) ? "CORRECTION_COMPLETED" : "RQC_OK";
        } else if (BillingBasis.AVERAGE.equals(cycle.getBillingbasis())) {
            decisionReason = "Meter reading quality code " + cycle.getReadingqualitycode() + " is billed on the DJB average basis.";
            reasonCode = "RQC_AVERAGE";
        } else if (BillingBasis.PROVISIONAL.equals(cycle.getBillingbasis())) {
            decisionReason = "The cycle is billed on DJB provisional consumption after an estimated/rejected billing decision.";
            reasonCode = "PROVISIONAL_BILLING";
        } else {
            decisionReason = "Billing basis was determined by the DJB monthly billing engine.";
            reasonCode = "BILLING_BASIS_ENGINE";
        }

        List<DJBMonthlyBillingStatement.Slab> slabs = new ArrayList<DJBMonthlyBillingStatement.Slab>();
        if (water.getSlabCharges() != null) {
            for (TariffSlabCharge slab : water.getSlabCharges()) {
                slabs.add(DJBMonthlyBillingStatement.Slab.builder()
                        .from(slab.getFrom())
                        .to(slab.getTo())
                        .units(slab.getUnits())
                        .ratePerKl(slab.getRatePerKl())
                        .charge(slab.getCharge())
                        .explanation(buildSlabExplanation(slab))
                        .build());
            }
        }

        List<DJBMonthlyBillingStatement.RebateLine> rebateLines = new ArrayList<DJBMonthlyBillingStatement.RebateLine>();
        if (rebate != null && rebate.getRebateItems() != null) {
            for (RebateItem item : rebate.getRebateItems()) {
                rebateLines.add(DJBMonthlyBillingStatement.RebateLine.builder()
                        .code(item.getCode())
                        .name(item.getName())
                        .rate(item.getRate())
                        .baseAmount(item.getBaseAmount())
                        .rebateAmount(item.getRebateAmount())
                        .explanation(item.getExplanation())
                        .build());
            }
        }

        BigDecimal grossAmount = water.getTotalWaterCharge()
                .add(sewerage.getTotalSewerageCharge()).setScale(MONEY_SCALE, java.math.RoundingMode.HALF_UP);


        return DJBMonthlyBillingStatement.builder()
                .consumer(DJBMonthlyBillingStatement.Consumer.builder()
                        .tenantId(cycle.getTenantid())
                        .connectionNo(cycle.getConnectionno())
                        .tariffCategory(water.getCategory())
                        .propertyUsage(property == null ? null : property.getUsageCategory())
                        .propertyAreaSqm(property == null ? null : property.getSuperBuiltUpArea())
                        .build())
                .billingCycle(DJBMonthlyBillingStatement.BillingCycle.builder()
                        .id(cycle.getId())
                        .periodFrom(cycle.getBillingperiodfrom())
                        .periodTo(cycle.getBillingperiodto())
                        .meterReadingId(cycle.getMeterreadingid())
                        .readingQualityCode(cycle.getReadingqualitycode())
                        .billingBasis(billingBasis)
                        .correctionStatus(cycle.getCorrectionstatus() == null ? null : cycle.getCorrectionstatus().toString())
                        .status(cycle.getStatus() == null ? null : cycle.getStatus().toString())
                        .averageCycleCount(cycle.getAveragecyclecount())
                        .provisionalCycleCount(cycle.getProvisionalcyclecount())
                        .zroStatus(cycle.getZrostatus() == null ? null : cycle.getZrostatus().toString())
                        .zroRemarks(cycle.getZroremarks())
                        .build())
                .reading(DJBMonthlyBillingStatement.Reading.builder()
                        .previousReading(cycle.getPreviousokreading())
                        .previousReadingDate(cycle.getPreviousokreadingdate())
                        .currentReading(cycle.getCurrentreading())
                        .currentReadingDate(cycle.getCurrentreadingdate())
                        .previousConsumption(cycle.getPreviousconsumption())
                        .actualConsumption(cycle.getActualconsumption())
                        .billingConsumption(cycle.getBillingconsumption())
                        .averageConsumption(cycle.getAverageconsumption())
                        .deviationFactor(cycle.getDeviationfactor())
                        .unit("KL")
                        .build())
                .billingDecision(DJBMonthlyBillingStatement.BillingDecision.builder()
                        .basis(billingBasis)
                        .reasonCode(reasonCode)
                        .reason(decisionReason)
                        .actualReadingAvailable(cycle.getActualconsumption() != null)
                        .historicalAverage(cycle.getAverageconsumption())
                        .minimumBillingConsumption(BillingBasis.PROVISIONAL.equals(cycle.getBillingbasis()) ? new BigDecimal("25") : null)
                        .averageCycleCount(cycle.getAveragecyclecount())
                        .provisionalCycleCount(cycle.getProvisionalcyclecount())
                        .build())
                .onePointFiveX(DJBMonthlyBillingStatement.OnePointFiveX.builder()
                        .evaluated(cycle.getPreviousconsumption() != null && cycle.getActualconsumption() != null)
                        .multiplier(new BigDecimal("1.5"))
                        .previousConsumption(cycle.getPreviousconsumption())
                        .thresholdConsumption(threshold)
                        .actualConsumption(cycle.getActualconsumption())
                        .exceeded(onePointFiveExceeded)
                        .minimumZroConsumption(new BigDecimal("20"))
                        .zroRequired(zroRequired)
                        .reason(buildOnePointFiveReason(cycle, threshold, zroRequired))
                        .build())
                .charges(DJBMonthlyBillingStatement.Charges.builder()
                        .water(DJBMonthlyBillingStatement.WaterCharges.builder()
                                .consumption(water.getConsumption())
                                .unit("KL")
                                .tariffId(water.getTariffId())
                                .category(water.getCategory())
                                .volumetricCharge(water.getWaterVolumetricCharge())
                                .serviceCharge(water.getServiceCharge())
                                .totalWaterCharge(water.getTotalWaterCharge())
                                .slabs(slabs)
                                .build())
                        .sewerage(DJBMonthlyBillingStatement.SewerageCharges.builder()
                                .regularCharge(sewerage.getRegularSewerageCharge())
                                .additionalCharge(sewerage.getAdditionalSewerageCharge())
                                .totalCharge(sewerage.getTotalSewerageCharge())
                                .regularRuleCode(sewerage.getRegularRuleCode())
                                .additionalRuleCode(sewerage.getAdditionalRuleCode())
                                .explanation(sewerage.getExplanation())
                                .build())
                        .rebates(DJBMonthlyBillingStatement.RebateCharges.builder()
                                .totalRebate(rebate == null ? BigDecimal.ZERO.setScale(MONEY_SCALE) : rebate.getTotalRebate())
                                .lines(rebateLines)
                                .explanation(rebate == null ? "No DJB monthly rebate applicable" : rebate.getExplanation())
                                .build())
                        .grossAmount(grossAmount)
                        .netAmount(netAmount)
                        .build())
                .adjustments(DJBMonthlyBillingStatement.Adjustments.builder()
                        .paidCorrectionApplied(zero(appliedPaidAdjustment))
                        .residualPaidCreditCreated(zero(residualPaidCredit))
                        .carriedForwardCreditApplied(zero(carriedForwardCreditApplied))
                        .carriedForwardCreditAllocationIds(carriedForwardCreditAllocationIds == null
                                ? new ArrayList<String>() : carriedForwardCreditAllocationIds)
                        .build())
                .demand(null)
                .bill(null)
                .audit(DJBMonthlyBillingStatement.Audit.builder()
                        .calculationId(calculationId)
                        .engineVersion(ENGINE_VERSION)
                        .calculatedAt(calculatedAt)
                        .calculatedBy(calculatedBy)
                        .build())
                .build();
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(MONEY_SCALE) : value.setScale(MONEY_SCALE, java.math.RoundingMode.HALF_UP);
    }

    private String buildSlabExplanation(TariffSlabCharge slab) {
        return slab.getUnits() + " KL x Rs." + slab.getRatePerKl() + " = Rs." + slab.getCharge();
    }

    private String buildOnePointFiveReason(WaterBillingCycle cycle, BigDecimal threshold, boolean zroRequired) {
        if (cycle.getActualconsumption() == null || cycle.getPreviousconsumption() == null) {
            return "1.5x comparison could not be evaluated because previous or actual consumption is unavailable.";
        }
        if (!Boolean.TRUE.equals(cycle.getOnepointfivexflag())) {
            return "Actual consumption did not trigger the DJB 1.5x rule.";
        }
        if (zroRequired) {
            return cycle.getActualconsumption() + " KL exceeds 1.5 x previous consumption (" + threshold + " KL) and requires ZRO verification.";
        }
        return cycle.getActualconsumption() + " KL exceeded the 1.5 x comparison, but the current cycle does not require ZRO blocking.";
    }

    private String actor(RequestInfo requestInfo) {
        if (requestInfo != null && requestInfo.getUserInfo() != null
                && StringUtils.hasText(requestInfo.getUserInfo().getUuid())) {
            return requestInfo.getUserInfo().getUuid();
        }
        return "SYSTEM";
    }
}
