package org.egov.wscalculation.djbmonthlybilling.service;

import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBReadingQualityCode;
import org.egov.wscalculation.djbmonthlybilling.service.dto.BillingBasisDecision;
import org.egov.wscalculation.djbmonthlybilling.service.dto.ConsumptionResult;
import org.springframework.stereotype.Service;

@Service
public class DJBMonthlyBillingService {
    private final BillingBasisService billingBasisService;
    private final ConsumptionService consumptionService;

    public DJBMonthlyBillingService(BillingBasisService billingBasisService,
            ConsumptionService consumptionService) {
        this.billingBasisService = billingBasisService;
        this.consumptionService = consumptionService;
    }

    public ConsumptionResult determineConsumption(String tenantId, String connectionNo,
            WaterBillingCycle currentCycle, DJBReadingQualityCode rqc, DJBMonthlyBillingRule rule) {
        BillingBasisDecision decision = billingBasisService.determineBillingBasis(
                tenantId, connectionNo, currentCycle, rqc, rule);
        return consumptionService.calculate(tenantId, connectionNo, currentCycle, decision, rule);
    }
}
