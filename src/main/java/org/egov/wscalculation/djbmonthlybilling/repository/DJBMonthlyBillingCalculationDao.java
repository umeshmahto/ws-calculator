package org.egov.wscalculation.djbmonthlybilling.repository;

import org.egov.wscalculation.djbmonthlybilling.model.DJBMonthlyBillingCalculation;

public interface DJBMonthlyBillingCalculationDao {
    int save(DJBMonthlyBillingCalculation calculation);
    DJBMonthlyBillingCalculation findById(String tenantId, String id);
    DJBMonthlyBillingCalculation findByBillingCycle(String tenantId, String billingCycleId);
}
