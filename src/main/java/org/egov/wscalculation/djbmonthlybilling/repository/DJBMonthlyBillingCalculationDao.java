package org.egov.wscalculation.djbmonthlybilling.repository;

import org.egov.wscalculation.djbmonthlybilling.model.DJBMonthlyBillingCalculation;

public interface DJBMonthlyBillingCalculationDao {
    int save(DJBMonthlyBillingCalculation calculation);
    int updateStatus(String tenantId, String id, String status);
    int updateSnapshot(String tenantId, String id, String status, long calculatedTime, String calculatedBy, String snapshotJson);
    DJBMonthlyBillingCalculation findById(String tenantId, String id);
    DJBMonthlyBillingCalculation findByBillingCycle(String tenantId, String billingCycleId);
}
