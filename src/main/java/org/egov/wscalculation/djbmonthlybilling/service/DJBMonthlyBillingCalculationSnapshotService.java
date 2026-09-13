package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.DJBMonthlyBillingCalculation;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.repository.DJBMonthlyBillingCalculationDao;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
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
    private final WaterBillingCycleDao billingCycleDao;
    private final ObjectMapper objectMapper;

    public DJBMonthlyBillingCalculationSnapshotService(
            DJBMonthlyBillingCalculationDao calculationDao,
            WaterBillingCycleDao billingCycleDao,
            ObjectMapper objectMapper) {
        this.calculationDao = calculationDao;
        this.billingCycleDao = billingCycleDao;
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
            DJBMonthlyBillingRule billingRule,
            BigDecimal appliedPaidAdjustment,
            BigDecimal residualPaidCredit,
            BigDecimal carriedForwardCreditApplied,
            List<String> carriedForwardCreditAllocationIds,
            BigDecimal netAmount) {

        String calculationId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        DJBMonthlyBillingStatement statement = buildStatement(
                cycle, connection, property, water, sewerage, rebate, billingRule,
                appliedPaidAdjustment, residualPaidCredit, carriedForwardCreditApplied,
                carriedForwardCreditAllocationIds, netAmount, calculationId, now, actor(requestInfo));

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

    public void updateStatus(String tenantId, String calculationId, String status) {
        if (!StringUtils.hasText(calculationId) || !StringUtils.hasText(tenantId) || !StringUtils.hasText(status)) {
            return;
        }
        if (calculationDao.updateStatus(tenantId, calculationId, status) != 1) {
            throw new IllegalStateException("Failed to update DJB billing calculation snapshot status for " + calculationId);
        }
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
            DJBMonthlyBillingStatement statement = objectMapper.readValue(
                    calculation.getSnapshotjson(), DJBMonthlyBillingStatement.class);
            if (statement.getAudit() != null) {
                statement.getAudit().setCalculationStatus(calculation.getStatus());
            }
            return statement;
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
            DJBMonthlyBillingRule billingRule,
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
                        .from(slab.getFrom()).to(slab.getTo()).units(slab.getUnits())
                        .ratePerKl(slab.getRatePerKl()).charge(slab.getCharge())
                        .explanation(buildSlabExplanation(slab)).build());
            }
        }

        List<DJBMonthlyBillingStatement.RebateLine> rebateLines = new ArrayList<DJBMonthlyBillingStatement.RebateLine>();
        if (rebate != null && rebate.getRebateItems() != null) {
            for (RebateItem item : rebate.getRebateItems()) {
                rebateLines.add(DJBMonthlyBillingStatement.RebateLine.builder()
                        .code(item.getCode()).name(item.getName()).rate(item.getRate())
                        .baseAmount(item.getBaseAmount()).rebateAmount(item.getRebateAmount())
                        .explanation(item.getExplanation()).build());
            }
        }

        BigDecimal grossAmount = water.getTotalWaterCharge()
                .add(sewerage.getTotalSewerageCharge())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal configuredMinimum = null;
        if (billingRule != null && (cycle.getBillingbasis() == BillingBasis.PROVISIONAL || cycle.getBillingbasis() == BillingBasis.AVERAGE)) {
            configuredMinimum = billingRule.getMinimumPostAverageConsumptionKl() == null
                    ? null : BigDecimal.valueOf(billingRule.getMinimumPostAverageConsumptionKl().longValue());
        }

        decisionReason = buildDecisionReason(cycle, billingRule, decisionReason, configuredMinimum);

        DJBMonthlyBillingStatement.CorrectionSummary correction = buildCorrectionSummary(cycle);

        List<String> sewerageRuleCodes = new ArrayList<String>();
        if (sewerage.getRegularRuleCode() != null) sewerageRuleCodes.add(sewerage.getRegularRuleCode());
        if (sewerage.getAdditionalRuleCode() != null) sewerageRuleCodes.add(sewerage.getAdditionalRuleCode());

        List<String> rebateCodes = new ArrayList<String>();
        for (DJBMonthlyBillingStatement.RebateLine line : rebateLines) {
            if (line.getCode() != null) rebateCodes.add(line.getCode());
        }

        return DJBMonthlyBillingStatement.builder()
                .consumer(DJBMonthlyBillingStatement.Consumer.builder()
                        .tenantId(cycle.getTenantid()).connectionNo(cycle.getConnectionno())
                        .tariffCategory(water.getCategory())
                        .propertyUsage(property == null ? null : property.getUsageCategory())
                        .propertyAreaSqm(property == null ? null : property.getSuperBuiltUpArea()).build())
                .billingCycle(DJBMonthlyBillingStatement.BillingCycle.builder()
                        .id(cycle.getId()).periodFrom(cycle.getBillingperiodfrom()).periodTo(cycle.getBillingperiodto())
                        .meterReadingId(cycle.getMeterreadingid()).readingQualityCode(cycle.getReadingqualitycode())
                        .billingBasis(billingBasis)
                        .correctionStatus(cycle.getCorrectionstatus() == null ? null : cycle.getCorrectionstatus().toString())
                        .status(cycle.getStatus() == null ? null : cycle.getStatus().toString())
                        .averageCycleCount(cycle.getAveragecyclecount()).provisionalCycleCount(cycle.getProvisionalcyclecount())
                        .zroStatus(cycle.getZrostatus() == null ? null : cycle.getZrostatus().toString())
                        .zroRemarks(cycle.getZroremarks()).build())
                .reading(DJBMonthlyBillingStatement.Reading.builder()
                        .previousReading(cycle.getPreviousokreading()).previousReadingDate(cycle.getPreviousokreadingdate())
                        .currentReading(cycle.getCurrentreading()).currentReadingDate(cycle.getCurrentreadingdate())
                        .previousConsumption(cycle.getPreviousconsumption()).actualConsumption(cycle.getActualconsumption())
                        .billingConsumption(cycle.getBillingconsumption()).averageConsumption(cycle.getAverageconsumption())
                        .deviationFactor(cycle.getDeviationfactor()).unit("KL").build())
                .billingDecision(DJBMonthlyBillingStatement.BillingDecision.builder()
                        .basis(billingBasis).reasonCode(reasonCode).reason(decisionReason)
                        .actualReadingAvailable(cycle.getActualconsumption() != null)
                        .historicalAverage(cycle.getAverageconsumption())
                        .minimumBillingConsumption(configuredMinimum)
                        .averageCycleCount(cycle.getAveragecyclecount())
                        .provisionalCycleCount(cycle.getProvisionalcyclecount())
                        .billingRuleCode(billingRule == null ? null : billingRule.getCode())
                        .configuredAverageMaximumCycles(billingRule == null ? null : billingRule.getAverageMaximumCycles())
                        .configuredProvisionalMaximumCycles(billingRule == null ? null : billingRule.getProvisionalMaximumCycles())
                        .build())
                .onePointFiveX(DJBMonthlyBillingStatement.OnePointFiveX.builder()
                        .evaluated(cycle.getPreviousconsumption() != null && cycle.getActualconsumption() != null)
                        .multiplier(new BigDecimal("1.5")).previousConsumption(cycle.getPreviousconsumption())
                        .thresholdConsumption(threshold).actualConsumption(cycle.getActualconsumption())
                        .exceeded(onePointFiveExceeded).minimumZroConsumption(new BigDecimal("20"))
                        .zroRequired(zroRequired).reason(buildOnePointFiveReason(cycle, threshold, zroRequired)).build())
                .charges(DJBMonthlyBillingStatement.Charges.builder()
                        .water(DJBMonthlyBillingStatement.WaterCharges.builder()
                                .consumption(water.getConsumption()).unit("KL").tariffId(water.getTariffId())
                                .category(water.getCategory()).volumetricCharge(water.getWaterVolumetricCharge())
                                .serviceCharge(water.getServiceCharge()).totalWaterCharge(water.getTotalWaterCharge())
                                .slabs(slabs).build())
                        .sewerage(DJBMonthlyBillingStatement.SewerageCharges.builder()
                                .regularCharge(sewerage.getRegularSewerageCharge()).additionalCharge(sewerage.getAdditionalSewerageCharge())
                                .totalCharge(sewerage.getTotalSewerageCharge()).regularRuleCode(sewerage.getRegularRuleCode())
                                .additionalRuleCode(sewerage.getAdditionalRuleCode()).explanation(sewerage.getExplanation()).build())
                        .rebates(DJBMonthlyBillingStatement.RebateCharges.builder()
                                .totalRebate(rebate == null ? BigDecimal.ZERO.setScale(MONEY_SCALE) : rebate.getTotalRebate())
                                .lines(rebateLines).explanation(rebate == null ? "No DJB monthly rebate applicable" : rebate.getExplanation()).build())
                        .grossAmount(grossAmount).netAmount(netAmount).build())
                .adjustments(DJBMonthlyBillingStatement.Adjustments.builder()
                        .paidCorrectionApplied(zero(appliedPaidAdjustment))
                        .residualPaidCreditCreated(zero(residualPaidCredit))
                        .carriedForwardCreditApplied(zero(carriedForwardCreditApplied))
                        .carriedForwardCreditAllocationIds(carriedForwardCreditAllocationIds == null
                                ? new ArrayList<String>() : carriedForwardCreditAllocationIds).build())
                .correction(correction)
                .reconciliation(DJBMonthlyBillingStatement.Reconciliation.builder()
                        .calculatedAmount(netAmount).status("PENDING_BILL").build())
                .demand(null).bill(null)
                .audit(DJBMonthlyBillingStatement.Audit.builder()
                        .calculationId(calculationId).engineVersion(ENGINE_VERSION).calculatedAt(calculatedAt)
                        .calculatedBy(calculatedBy).calculationStatus("CALCULATED")
                        .billingRuleCode(billingRule == null ? null : billingRule.getCode())
                        .tariffId(water.getTariffId()).sewerageRuleCodes(sewerageRuleCodes).rebateCodes(rebateCodes).build())
                .build();
    }

    private String buildDecisionReason(
            WaterBillingCycle cycle,
            DJBMonthlyBillingRule billingRule,
            String defaultReason,
            BigDecimal configuredMinimum) {
        if (BillingBasis.CORRECTED_ACTUAL.equals(cycle.getBillingbasis())) {
            return "Current OK reading corrected prior estimated billing; corrected consumption is calculated from the previous OK reading to the current OK reading.";
        }
        if (BillingBasis.AVERAGE.equals(cycle.getBillingbasis())) {
            Integer count = cycle.getAveragecyclecount() == null ? 0 : cycle.getAveragecyclecount();
            Integer max = billingRule == null ? null : billingRule.getAverageMaximumCycles();
            if (max != null && count > max && configuredMinimum != null) {
                return "Average billing cycle limit reached; billable consumption is the higher of historical average and the configured minimum of "
                        + configuredMinimum + " KL.";
            }
            return "Reading quality code " + cycle.getReadingqualitycode()
                    + " requires average billing; billable consumption uses the applicable historical average.";
        }
        if (BillingBasis.PROVISIONAL.equals(cycle.getBillingbasis())) {
            Integer count = cycle.getProvisionalcyclecount() == null ? 0 : cycle.getProvisionalcyclecount();
            Integer max = billingRule == null ? null : billingRule.getProvisionalMaximumCycles();
            if (max != null && count > max && configuredMinimum != null) {
                return "Provisional cycle limit reached; billable consumption is the higher of historical average and the configured minimum of "
                        + configuredMinimum + " KL.";
            }
            return "This cycle is provisional; billable consumption uses the historical average under the DJB provisional billing rule.";
        }
        return defaultReason;
    }

    private DJBMonthlyBillingStatement.CorrectionSummary buildCorrectionSummary(WaterBillingCycle cycle) {
        boolean required = cycle.getCorrectionstatus() != null
                && cycle.getPreviousokreadingdate() != null
                && (BillingBasis.CORRECTED_ACTUAL.equals(cycle.getBillingbasis()) || cycle.getCorrectionstatus().toString().contains("COMPLETED"));

        if (!required) {
            return DJBMonthlyBillingStatement.CorrectionSummary.builder().required(false)
                    .status(cycle.getCorrectionstatus() == null ? null : cycle.getCorrectionstatus().toString())
                    .build();
        }

        List<DJBMonthlyBillingStatement.CorrectionCycle> superseded = new ArrayList<DJBMonthlyBillingStatement.CorrectionCycle>();
        BigDecimal previouslyCalculatedAmount = BigDecimal.ZERO.setScale(MONEY_SCALE);
        List<WaterBillingCycle> cycles = billingCycleDao.findCyclesForCorrection(
                cycle.getTenantid(), cycle.getConnectionno(), cycle.getPreviousokreadingdate(), cycle.getBillingperiodto());
        if (cycles != null) {
            for (WaterBillingCycle previous : cycles) {
                BigDecimal amount = readCalculatedAmount(previous);
                DJBMonthlyBillingStatement.CorrectionCycle correctionCycle =
                        DJBMonthlyBillingStatement.CorrectionCycle.builder()
                                .billingCycleId(previous.getId())
                                .billingBasis(previous.getBillingbasis() == null ? null : previous.getBillingbasis().toString())
                                .billingConsumption(previous.getBillingconsumption())
                                .demandId(previous.getDemandid())
                                .billId(previous.getBillid())
                                .calculatedNetAmount(amount)
                                .build();
                if (amount != null) previouslyCalculatedAmount = previouslyCalculatedAmount.add(amount);
                superseded.add(correctionCycle);
            }
        }

        BigDecimal correctedConsumption = cycle.getCurrentreading() != null && cycle.getPreviousokreading() != null
                ? cycle.getCurrentreading().subtract(cycle.getPreviousokreading()) : cycle.getActualconsumption();

        return DJBMonthlyBillingStatement.CorrectionSummary.builder()
                .required(true)
                .status(cycle.getCorrectionstatus().toString())
                .reasonCode("OK_AFTER_ESTIMATED_CYCLES")
                .reason("Current valid OK reading corrected prior average/provisional billing cycles.")
                .previousOkBillingCycleId(findPreviousOkCycleId(cycle))
                .currentBillingCycleId(cycle.getId())
                .previousOkReading(cycle.getPreviousokreading())
                .currentReading(cycle.getCurrentreading())
                .correctedConsumption(correctedConsumption)
                .supersededCycles(superseded)
                .previouslyCalculatedAmount(previouslyCalculatedAmount)
                .build();
    }

    private String findPreviousOkCycleId(WaterBillingCycle cycle) {
        WaterBillingCycle previous = billingCycleDao.findPreviousOkByConnectionBefore(
                cycle.getTenantid(), cycle.getConnectionno(), cycle.getBillingperiodto());
        return previous == null ? null : previous.getId();
    }

    private BigDecimal readCalculatedAmount(WaterBillingCycle cycle) {
        if (!StringUtils.hasText(cycle.getCalculationid())) return null;
        DJBMonthlyBillingCalculation previous = calculationDao.findById(cycle.getTenantid(), cycle.getCalculationid());
        if (previous == null || !StringUtils.hasText(previous.getSnapshotjson())) return null;
        try {
            DJBMonthlyBillingStatement statement = objectMapper.readValue(previous.getSnapshotjson(), DJBMonthlyBillingStatement.class);
            return statement.getCharges() == null ? null : statement.getCharges().getNetAmount();
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(MONEY_SCALE) : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
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
