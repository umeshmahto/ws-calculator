package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.wscalculation.constants.WSCalculationConstant;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingCycleStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ZroStatus;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBAdditionalSewerageCharge;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyRebate;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlySewerageRule;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariff;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.TariffCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.master.DJBMonthlyBillingMasterProvider;
import org.egov.wscalculation.repository.DemandRepository;
import org.egov.wscalculation.util.CalculatorUtil;
import org.egov.wscalculation.util.WSCalculationUtil;
import org.egov.wscalculation.config.WSCalculationConfiguration;
import org.egov.wscalculation.web.models.Demand;
import org.egov.wscalculation.web.models.DemandDetail;
import org.egov.wscalculation.web.models.DemandNotificationObj;
import org.egov.wscalculation.web.models.Property;
import org.egov.wscalculation.web.models.WaterConnection;
import org.egov.wscalculation.web.models.WaterConnectionRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class DJBMonthlyDemandService {

    private static final int MONEY_SCALE = 2;

    private final DJBMonthlyBillingMasterProvider masterProvider;
    private final TariffCalculationService tariffCalculationService;
    private final SewerageCalculationService sewerageCalculationService;
    private final RebateCalculationService rebateCalculationService;
    private final DemandRepository demandRepository;
    private final CalculatorUtil calculatorUtil;
    private final WSCalculationUtil wsCalculationUtil;
    private final WSCalculationConfiguration config;

    public DJBMonthlyDemandService(
            DJBMonthlyBillingMasterProvider masterProvider,
            TariffCalculationService tariffCalculationService,
            SewerageCalculationService sewerageCalculationService,
            RebateCalculationService rebateCalculationService,
            DemandRepository demandRepository,
            CalculatorUtil calculatorUtil,
            WSCalculationUtil wsCalculationUtil,
            WSCalculationConfiguration config) {

        this.masterProvider = masterProvider;
        this.tariffCalculationService = tariffCalculationService;
        this.sewerageCalculationService = sewerageCalculationService;
        this.rebateCalculationService = rebateCalculationService;
        this.demandRepository = demandRepository;
        this.calculatorUtil = calculatorUtil;
        this.wsCalculationUtil = wsCalculationUtil;
        this.config = config;
    }

    /**
     * Creates a generic UPYOG Demand from the already calculated DJB billing
     * cycle. This service owns only DJB rule calculation and mapping. The
     * generic billing-service remains untouched.
     *
     * No Bill API is called here.
     */
    public DemandResult createDemand(
            RequestInfo requestInfo,
            WaterBillingCycle cycle) {

        validateCycle(cycle);

        // Idempotency: the same billing cycle must never create a second demand.
        if (org.springframework.util.StringUtils.hasText(cycle.getDemandid())) {
            return DemandResult.builder()
                    .demandCreated(true)
                    .zroRequired(false)
                    .message("DJB demand already exists for billing cycle")
                    .build();
        }

        if (Boolean.TRUE.equals(cycle.getOnepointfivexflag())
                && isDomestic(cycle, requestInfo)) {
            cycle.setZrostatus(ZroStatus.PENDING);
            cycle.setZroremarks(
                    "Consumption exceeds DJB 1.5x threshold; ZRO verification required");
            cycle.setStatus(BillingCycleStatus.CALCULATED);

            return DemandResult.builder()
                    .demandCreated(false)
                    .zroRequired(true)
                    .message("Demand not generated because DJB 1.5x ZRO verification is required")
                    .build();
        }

        BigDecimal consumption = cycle.getBillingconsumption();
        if (consumption == null) {
            throw new IllegalStateException(
                    "Billing consumption is required before demand generation");
        }

        String tenantId = cycle.getTenantid();
        String connectionNo = cycle.getConnectionno();

        WaterConnection connection = loadWaterConnection(
                requestInfo, connectionNo, tenantId);

        Property property = wsCalculationUtil.getProperty(
                WaterConnectionRequest.builder()
                        .requestInfo(requestInfo)
                        .waterConnection(connection)
                        .build());

        String category = resolveTariffCategory(
                connection, property);

        List<DJBMonthlyWaterTariff> tariffs =
                masterProvider.getWaterTariffs(requestInfo, tenantId);

        TariffCalculationResult water =
                tariffCalculationService.calculate(
                        consumption, category, tariffs);

        SewerageCalculationContext sewerageContext =
                SewerageCalculationContext.builder()
                        .waterVolumetricCharge(
                                water.getWaterVolumetricCharge())
                        .waterConnectionAvailable(true)
                        .sewerConnectionAvailable(true)
                        .additionalWaterSource(
                                isAdditionalWaterSource(connection))
                        .consumerCategory(category)
                        .propertyUsage(property.getUsageCategory())
                        .builtUpAreaSqm(property.getSuperBuiltUpArea())
                        .build();

        List<DJBMonthlySewerageRule> sewerageRules =
                masterProvider.getSewerageRules(
                        requestInfo, tenantId);

        List<DJBAdditionalSewerageCharge> additionalSewerageRules =
                masterProvider.getAdditionalSewerageCharges(
                        requestInfo, tenantId);

        SewerageCalculationResult sewerage =
                sewerageCalculationService.calculate(
                        sewerageContext,
                        sewerageRules,
                        additionalSewerageRules);

        BigDecimal grossAmount =
                water.getTotalWaterCharge()
                        .add(sewerage.getTotalSewerageCharge())
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        List<DJBMonthlyRebate> rebates =
                masterProvider.getRebates(requestInfo, tenantId);

        RebateCalculationContext rebateContext =
                RebateCalculationContext.builder()
                        .consumption(consumption)
                        .billingBasis(cycle.getBillingbasis())
                        .readingQualityCode(cycle.getReadingqualitycode())
                        .consumerType(category)
                        .propertyCategory(category)
                        .connectionType(category)
                        .bulkConnection(false)
                        .propertyAreaSqm(property.getSuperBuiltUpArea())
                        .functionalRwh(false)
                        .functionalWastewaterRecycling(false)
                        .totalBillBeforeRebate(grossAmount)
                        .freeWaterEligibleAmount(
                                water.getTotalWaterCharge())
                        .build();

        RebateCalculationResult rebate =
                rebateCalculationService.calculate(
                        rebateContext, rebates);

        BigDecimal netAmount =
                grossAmount.subtract(rebate.getTotalRebate())
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (netAmount.signum() < 0) {
            throw new IllegalStateException(
                    "DJB net demand amount cannot be negative: " + netAmount);
        }

        List<DemandDetail> demandDetails = new java.util.ArrayList<>();

        /*
         * Current generic WS tax-head contract contains WS_CHARGE and
         * WS_TIME_REBATE. We keep the complete gross water + sewer amount in
         * WS_CHARGE, and represent DJB rebates as a negative WS_TIME_REBATE.
         *
         * We do not invent a DJB-specific tax head because billing-service
         * owns generic tax-head masters.
         */
        if (grossAmount.signum() > 0) {
            demandDetails.add(
                    DemandDetail.builder()
                            .taxHeadMasterCode(
                                    WSCalculationConstant.WS_CHARGE)
                            .taxAmount(grossAmount)
                            .collectionAmount(BigDecimal.ZERO)
                            .tenantId(tenantId)
                            .build());
        }

        if (rebate.getTotalRebate().signum() > 0) {
            demandDetails.add(
                    DemandDetail.builder()
                            .taxHeadMasterCode(
                                    WSCalculationConstant.WS_TIME_REBATE)
                            .taxAmount(
                                    rebate.getTotalRebate()
                                            .negate()
                                            .setScale(MONEY_SCALE,
                                                    RoundingMode.HALF_UP))
                            .collectionAmount(BigDecimal.ZERO)
                            .tenantId(tenantId)
                            .build());
        }

        if (demandDetails.isEmpty()) {
            throw new IllegalStateException(
                    "No positive DJB demand detail could be generated");
        }

        User payer = resolvePayer(connection, property);

        Long taxPeriodFrom = cycle.getBillingperiodfrom();
        if (CorrectionStatus.PENDING.equals(
                cycle.getCorrectionstatus())
                && cycle.getPreviousokreadingdate() != null) {
            /*
             * DJB automatic correction is a single actual demand from the
             * previous OK reading to the current OK reading.
             */
            taxPeriodFrom = cycle.getPreviousokreadingdate();
        }

        Demand demand = Demand.builder()
                .tenantId(tenantId)
                .consumerCode(connectionNo)
                .consumerType("waterConnection")
                .businessService(config.getBusinessService())
                .payer(payer)
                .taxPeriodFrom(taxPeriodFrom)
                .taxPeriodTo(cycle.getBillingperiodto())
                .demandDetails(demandDetails)
                .minimumAmountPayable(
                        config.getMinimumPayableAmount())
                .billExpiryTime(
                        config.getDemandBillExpiryTime() == null
                                ? null
                                : System.currentTimeMillis()
                                        + config.getDemandBillExpiryTime())
                .status(Demand.StatusEnum.ACTIVE)
                .additionalDetails(buildAdditionalDetails(
                        cycle, category, grossAmount,
                        rebate.getTotalRebate(), netAmount,
                        property.getPropertyId()))
                .build();

        DemandNotificationObj notification =
                DemandNotificationObj.builder()
                        .requestInfo(requestInfo)
                        .tenantId(tenantId)
                        .waterConnectionIds(
                                Collections.singleton(connectionNo))
                        .billingCycle(
                                WSCalculationConstant.Monthly_Billing_Period)
                        .build();

        List<Demand> response =
                demandRepository.saveDemand(
                        requestInfo,
                        Collections.singletonList(demand),
                        notification);

        if (CollectionUtils.isEmpty(response)
                || response.get(0) == null
                || !StringUtils.hasText(response.get(0).getId())) {
            throw new IllegalStateException(
                    "Billing-service returned no demand id for "
                            + connectionNo);
        }

        Demand created = response.get(0);

        return DemandResult.builder()
                .demandCreated(true)
                .zroRequired(false)
                .demand(created)
                .grossAmount(grossAmount)
                .rebateAmount(rebate.getTotalRebate())
                .netAmount(netAmount)
                .message("DJB demand created successfully")
                .build();
    }

    private WaterConnection loadWaterConnection(
            RequestInfo requestInfo,
            String connectionNo,
            String tenantId) {

        List<WaterConnection> connections =
                calculatorUtil.getWaterConnection(
                        requestInfo, connectionNo, tenantId);

        if (CollectionUtils.isEmpty(connections)) {
            throw new IllegalStateException(
                    "Water connection not found: " + connectionNo);
        }

        return calculatorUtil.getWaterConnectionObject(connections);
    }

    private User resolvePayer(
            WaterConnection connection,
            Property property) {

        if (!CollectionUtils.isEmpty(
                connection.getConnectionHolders())) {
            return connection.getConnectionHolders()
                    .get(0)
                    .toCommonUser();
        }

        if (!CollectionUtils.isEmpty(property.getOwners())) {
            return property.getOwners()
                    .get(0)
                    .toCommonUser();
        }

        throw new IllegalStateException(
                "No owner/payer found for water connection "
                        + connection.getConnectionNo());
    }

    private String resolveTariffCategory(
            WaterConnection connection,
            Property property) {

        String candidate = connection.getConnectionCategory();

        if (!StringUtils.hasText(candidate)) {
            candidate = property.getUsageCategory();
        }

        if (!StringUtils.hasText(candidate)) {
            throw new IllegalStateException(
                    "Cannot determine DJB tariff category for "
                            + connection.getConnectionNo());
        }

        String normalized = candidate.trim()
                .replace("-", "_")
                .replace(" ", "_")
                .toUpperCase();

        if (normalized.contains("DOMESTIC")
                || normalized.contains("RESIDENTIAL")
                || normalized.contains("CAT_I")) {
            return "DOMESTIC";
        }

        if (normalized.contains("COMMERCIAL")
                || normalized.contains("NON_DOMESTIC")
                || normalized.contains("CAT_II")
                || normalized.contains("BUSINESS")) {
            return "COMMERCIAL";
        }

        throw new IllegalStateException(
                "Unsupported DJB tariff category: " + candidate);
    }

    private boolean isDomestic(
            WaterBillingCycle cycle,
            RequestInfo requestInfo) {

        try {
            WaterConnection connection =
                    loadWaterConnection(
                            requestInfo,
                            cycle.getConnectionno(),
                            cycle.getTenantid());

            String category = connection.getConnectionCategory();

            return category != null
                    && (category.toUpperCase().contains("DOMESTIC")
                    || category.toUpperCase().contains("RESIDENTIAL")
                    || category.toUpperCase().contains("CAT_I"));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean isAdditionalWaterSource(
            WaterConnection connection) {

        String source = connection.getWaterSource();

        return source != null
                && (source.toUpperCase().contains("BORE")
                || source.toUpperCase().contains("BOREWELL"));
    }

    private Map<String, Object> buildAdditionalDetails(
            WaterBillingCycle cycle,
            String category,
            BigDecimal grossAmount,
            BigDecimal rebateAmount,
            BigDecimal netAmount,
            String propertyId) {

        Map<String, Object> details = new HashMap<>();
        details.put("djbBillingCycleId", cycle.getId());
        details.put("djbBillingBasis",
                cycle.getBillingbasis() == null
                        ? null
                        : cycle.getBillingbasis().toString());
        details.put("readingQualityCode",
                cycle.getReadingqualitycode());
        details.put("billingPeriodFrom",
                cycle.getBillingperiodfrom());
        details.put("billingPeriodTo",
                cycle.getBillingperiodto());
        details.put("tariffCategory", category);
        details.put("grossAmount", grossAmount);
        details.put("rebateAmount", rebateAmount);
        details.put("netAmount", netAmount);
        details.put("propertyId", propertyId);

        return details;
    }

    private void validateCycle(WaterBillingCycle cycle) {

        if (cycle == null) {
            throw new IllegalArgumentException(
                    "Billing cycle is required");
        }

        if (!StringUtils.hasText(cycle.getTenantid())
                || !StringUtils.hasText(cycle.getConnectionno())) {
            throw new IllegalArgumentException(
                    "Billing cycle tenant and connection are required");
        }

        if (cycle.getBillingperiodfrom() == null
                || cycle.getBillingperiodto() == null) {
            throw new IllegalArgumentException(
                    "Billing period is required");
        }
    }

    @lombok.Builder
    @lombok.Data
    public static class DemandResult {
        private boolean demandCreated;
        private boolean zroRequired;
        private Demand demand;
        private BigDecimal grossAmount;
        private BigDecimal rebateAmount;
        private BigDecimal netAmount;
        private String message;
    }
}
