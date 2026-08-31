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

    private BigDecimal consumption;

    private BillingBasis billingBasis;

    private String readingQualityCode;

    private String consumerType;

    private String propertyCategory;

    private String connectionType;

    private boolean bulkConnection;

    private Integer dwellingUnitCount;

    private BigDecimal propertyAreaSqm;

    private boolean functionalRwh;

    private boolean functionalWastewaterRecycling;

    /**
     * For RWH the DJB document explicitly describes the rebate as a
     * percentage of the total bill amount.
     */
    private BigDecimal totalBillBeforeRebate;

    /**
     * The 20 KL free-water rule is intentionally supplied a base amount
     * by the caller rather than guessing which tax-heads are free.
     */
    private BigDecimal freeWaterEligibleAmount;

    private List<String> configuredReadingQualityCodes = Collections.emptyList();
}