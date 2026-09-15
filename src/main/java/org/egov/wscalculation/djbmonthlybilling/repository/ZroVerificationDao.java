package org.egov.wscalculation.djbmonthlybilling.repository;

import java.util.List;

import org.egov.wscalculation.djbmonthlybilling.model.ZroVerification;
import org.egov.wscalculation.djbmonthlybilling.model.ZroVerificationInboxRecord;

public interface ZroVerificationDao {
    int save(ZroVerification verification);
    int update(ZroVerification verification);
    ZroVerification findById(String tenantId, String id);
    ZroVerification findByBillingCycle(String tenantId, String billingCycleId);
    List<ZroVerificationInboxRecord> searchPending(String tenantId, String connectionNo, int limit, int offset);
    int countPending(String tenantId, String connectionNo);
}
