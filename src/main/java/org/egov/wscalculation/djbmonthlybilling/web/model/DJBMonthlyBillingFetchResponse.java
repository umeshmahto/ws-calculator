package org.egov.wscalculation.djbmonthlybilling.web.model;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DJBMonthlyBillingFetchResponse {

    private String tenantId;
    private String connectionNo;
    private BillingCycleSummary billingCycle;
    private BillingCalculationSummary calculation;
    private BillSummary bill;
    private BigDecimal finalBillAmount;

    @Data
    @Builder
    public static class BillingCycleSummary {
        private String id;
        private String billingPeriodFrom;
        private String billingPeriodTo;
        private String meterReadingId;
        private String readingQualityCode;
        private String billingBasis;
        private BigDecimal previousReading;
        private BigDecimal currentReading;
        private BigDecimal actualConsumption;
        private BigDecimal averageConsumption;
        private BigDecimal billingConsumption;
        private BigDecimal previousConsumption;
        private BigDecimal deviationFactor;
        private Boolean onePointFiveXFlag;
        private Integer averageCycleCount;
        private Integer provisionalCycleCount;
        private String zroStatus;
        private String zroRemarks;
        private String demandId;
        private String billId;
        private String correctionStatus;
        private String status;
    }

    @Data
    @Builder
    public static class BillingCalculationSummary {
        private String tariffCategory;
        private String unit;
        private BigDecimal billableConsumption;
        private BigDecimal waterVolumetricCharge;
        private BigDecimal serviceCharge;
        private BigDecimal totalWaterCharge;
        private List<SlabSummary> slabs;
        private BigDecimal regularSewerageCharge;
        private BigDecimal additionalSewerageCharge;
        private BigDecimal totalSewerageCharge;
        private BigDecimal totalBeforeRebate;
        private BigDecimal rebateAmount;
        private BigDecimal netAmount;
    }

    @Data
    @Builder
    public static class SlabSummary {
        private BigDecimal from;
        private BigDecimal to;
        private BigDecimal units;
        private BigDecimal ratePerKl;
        private BigDecimal charge;
    }

    @Data
    @Builder
    public static class BillSummary {
        private String billId;
        private String billNumber;
        private String billStatus;
        private String demandId;
        private String billingCycleId;
        private BigDecimal netAmount;
    }
}
