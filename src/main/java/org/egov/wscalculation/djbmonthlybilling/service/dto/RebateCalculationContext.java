package org.egov.wscalculation.djbmonthlybilling.service.dto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Data;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;

@Data
@Builder
public class RebateCalculationContext {

    /** Raw meter/billing-period consumption retained for audit and non-monthly consumers. */
    private BigDecimal consumption;

    /** Monthly-equivalent consumption used by DJB monthly-threshold rebate rules. */
    private BigDecimal monthlyConsumption;

    private BillingBasis billingBasis;

    private String readingQualityCode;

    private String consumerType;

    private String propertyCategory;

    private String connectionType;

    private boolean bulkConnection;

    private Integer dwellingUnitCount;

    /** True only when the source connection/property data confirms DJB employee eligibility. */
    private boolean djbEmployeeEligible;

    /** Number of eligible domestic connections for the employee rebate. */
    private Integer eligibleConnectionCount;

    private BigDecimal propertyAreaSqm;

    private boolean functionalRwh;

    private boolean functionalWastewaterRecycling;

    /**
     * For RWH the DJB document explicitly describes the rebate as a
     * percentage of the total bill amount.
     */
    private BigDecimal totalBillBeforeRebate;

    /**
     * The 20 KL free-water concession applies to the complete billable
     * water + sewerage amount for an eligible Meter-OK cycle.
     */
    private BigDecimal freeWaterEligibleAmount;

    private List<String> configuredReadingQualityCodes = Collections.emptyList();
}