package org.egov.wscalculation.djbmonthlybilling.service.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;

@Data
@Builder
public class BillingBasisDecision {
    private BillingBasis billingBasis;
    private String readingQualityCode;
    private int averageCycleCount;
    private int provisionalCycleCount;
    private boolean requiresZro;
    private boolean actualReadingAvailable;
    private BigDecimal previousConsumption;
}
