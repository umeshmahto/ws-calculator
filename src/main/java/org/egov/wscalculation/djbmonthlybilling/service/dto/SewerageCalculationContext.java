package org.egov.wscalculation.djbmonthlybilling.service.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SewerageCalculationContext {
    private BigDecimal waterVolumetricCharge;
    private boolean waterConnectionAvailable;
    private boolean sewerConnectionAvailable;
    private boolean additionalWaterSource;
    private String consumerCategory;
    private String propertyUsage;
    private BigDecimal builtUpAreaSqm;
    private Integer numberOfRooms;
    private Integer numberOfBeds;

    /** Legacy whole calendar-month count retained for compatibility/reporting. */
    @Builder.Default
    private Integer billingMonths = 1;

    /** Fractional 30-day billing factor used for monthly fixed charges (e.g. 81 days = 2.7). */
    private BigDecimal billingPeriodMonths;

    /** Number of functional banquet-hall sites; defaults to one site when not supplied. */
    private Integer functionalSiteCount;
}
