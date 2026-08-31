package org.egov.wscalculation.djbmonthlybilling.repository;

import org.egov.wscalculation.djbmonthlybilling.model.ZroVerification;

public interface ZroVerificationDao {

    int save(ZroVerification verification);

    int update(ZroVerification verification);

    ZroVerification findById(String tenantId, String id);

    ZroVerification findByBillingCycle(String tenantId, String billingCycleId);
}
