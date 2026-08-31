package org.egov.wscalculation.djbmonthlybilling.service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MonthlyBillingCalculationResult {
    private BillingBasisDecision billingBasisDecision;
    private ConsumptionResult consumptionResult;
}
