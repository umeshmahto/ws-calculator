package org.egov.wscalculation.djbmonthlybilling.repository;

import java.math.BigDecimal;
import java.util.List;

import org.egov.wscalculation.djbmonthlybilling.model.DJBResidualCredit;
import org.egov.wscalculation.djbmonthlybilling.model.DJBResidualCreditAllocation;

public interface ResidualCreditDao {

    List<DJBResidualCredit> findOpenCreditsForUpdate(String tenantId, String connectionNo);

    DJBResidualCredit findByIdForUpdate(String tenantId, String id);

    DJBResidualCredit findBySourceCorrection(String tenantId, String sourceCorrectionId);

    List<DJBResidualCreditAllocation> findAllocationsByBillingCycle(String tenantId, String billingCycleId);

    int saveCredit(DJBResidualCredit credit);

    int updateCredit(DJBResidualCredit credit);

    int saveAllocation(DJBResidualCreditAllocation allocation);

    int updateAllocation(DJBResidualCreditAllocation allocation);

    int reserveCredit(String tenantId, String creditId, BigDecimal amount, long currentTime, String actor);

    int consumeReservedCredit(String tenantId, String creditId, BigDecimal amount, long currentTime, String actor);

    int releaseReservedCredit(String tenantId, String creditId, BigDecimal amount, long currentTime, String actor);
}
