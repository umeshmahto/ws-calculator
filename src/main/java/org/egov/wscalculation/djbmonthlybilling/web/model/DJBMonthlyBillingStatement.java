package org.egov.wscalculation.djbmonthlybilling.web.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DJBMonthlyBillingStatement {

    private Consumer consumer;
    private BillingCycle billingCycle;
    private Reading reading;
    private BillingDecision billingDecision;
    private OnePointFiveX onePointFiveX;
    private Charges charges;
    private Adjustments adjustments;
    private DemandSummary demand;
    private BillSummary bill;
    private Audit audit;

    @Data
    @Builder
    public static class Consumer {
        private String tenantId;
        private String connectionNo;
        private String tariffCategory;
        private String propertyUsage;
        private BigDecimal propertyAreaSqm;
    }

    @Data
    @Builder
    public static class BillingCycle {
        private String id;
        private Long periodFrom;
        private Long periodTo;
        private String meterReadingId;
        private String readingQualityCode;
        private String billingBasis;
        private String correctionStatus;
        private String status;
        private Integer averageCycleCount;
        private Integer provisionalCycleCount;
        private String zroStatus;
        private String zroRemarks;
    }

    @Data
    @Builder
    public static class Reading {
        private BigDecimal previousReading;
        private Long previousReadingDate;
        private BigDecimal currentReading;
        private Long currentReadingDate;
        private BigDecimal previousConsumption;
        private BigDecimal actualConsumption;
        private BigDecimal billingConsumption;
        private BigDecimal averageConsumption;
        private BigDecimal deviationFactor;
        private String unit;
    }

    @Data
    @Builder
    public static class BillingDecision {
        private String basis;
        private String reasonCode;
        private String reason;
        private boolean actualReadingAvailable;
        private BigDecimal historicalAverage;
        private BigDecimal minimumBillingConsumption;
        private Integer averageCycleCount;
        private Integer provisionalCycleCount;
    }

    @Data
    @Builder
    public static class OnePointFiveX {
        private boolean evaluated;
        private BigDecimal multiplier;
        private BigDecimal previousConsumption;
        private BigDecimal thresholdConsumption;
        private BigDecimal actualConsumption;
        private boolean exceeded;
        private BigDecimal minimumZroConsumption;
        private boolean zroRequired;
        private String reason;
    }

    @Data
    @Builder
    public static class Charges {
        private WaterCharges water;
        private SewerageCharges sewerage;
        private RebateCharges rebates;
        private BigDecimal grossAmount;
        private BigDecimal netAmount;
    }

    @Data
    @Builder
    public static class WaterCharges {
        private BigDecimal consumption;
        private String unit;
        private String tariffId;
        private String category;
        private BigDecimal volumetricCharge;
        private BigDecimal serviceCharge;
        private BigDecimal totalWaterCharge;
        @Builder.Default
        private List<Slab> slabs = Collections.emptyList();
    }

    @Data
    @Builder
    public static class Slab {
        private BigDecimal from;
        private BigDecimal to;
        private BigDecimal units;
        private BigDecimal ratePerKl;
        private BigDecimal charge;
        private String explanation;
    }

    @Data
    @Builder
    public static class SewerageCharges {
        private BigDecimal regularCharge;
        private BigDecimal additionalCharge;
        private BigDecimal totalCharge;
        private String regularRuleCode;
        private String additionalRuleCode;
        private String explanation;
    }

    @Data
    @Builder
    public static class RebateCharges {
        private BigDecimal totalRebate;
        @Builder.Default
        private List<RebateLine> lines = Collections.emptyList();
        private String explanation;
    }

    @Data
    @Builder
    public static class RebateLine {
        private String code;
        private String name;
        private BigDecimal rate;
        private BigDecimal baseAmount;
        private BigDecimal rebateAmount;
        private String explanation;
    }

    @Data
    @Builder
    public static class Adjustments {
        private BigDecimal paidCorrectionApplied;
        private BigDecimal residualPaidCreditCreated;
        private BigDecimal carriedForwardCreditApplied;
        @Builder.Default
        private List<String> carriedForwardCreditAllocationIds = Collections.emptyList();
    }

    @Data
    @Builder
    public static class DemandSummary {
        private boolean created;
        private String id;
        private String status;
        private BigDecimal amount;
    }

    @Data
    @Builder
    public static class BillSummary {
        private boolean generated;
        private String id;
        private String number;
        private String status;
        private String demandId;
        private BigDecimal amount;
        private BigDecimal calculatedAmount;
        private BigDecimal difference;
        private String reconciliationStatus;
    }

    @Data
    @Builder
    public static class Audit {
        private String calculationId;
        private String engineVersion;
        private Long calculatedAt;
        private String calculatedBy;
    }
}
