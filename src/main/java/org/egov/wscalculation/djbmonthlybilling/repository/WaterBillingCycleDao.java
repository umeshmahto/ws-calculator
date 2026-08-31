package org.egov.wscalculation.djbmonthlybilling.repository;

import java.util.List;

import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;

public interface WaterBillingCycleDao {

        int save(WaterBillingCycle billingCycle);

        int update(WaterBillingCycle billingCycle);

        WaterBillingCycle findById(String tenantId, String id);

        WaterBillingCycle findByConnectionAndPeriod(String tenantId, String connectionNo,
                        Long billingPeriodFrom, Long billingPeriodTo);

        WaterBillingCycle findLatestByConnection(String tenantId, String connectionNo);

        WaterBillingCycle findLatestOkByConnection(String tenantId, String connectionNo);

        List<WaterBillingCycle> findPreviousActualCycles(String tenantId, String connectionNo,
                        Long beforeBillingPeriodTo, int limit);

        List<WaterBillingCycle> findCyclesForConnection(String tenantId, String connectionNo,
                        Long beforeBillingPeriodTo, int limit);

        List<WaterBillingCycle> findCyclesForCorrection(String tenantId, String connectionNo,
                        Long fromBillingPeriodTo, Long toBillingPeriodTo);

        WaterBillingCycle findPreviousOkByConnectionBefore(String tenantId, String connectionNo,
                        Long beforeBillingPeriodTo);
}
