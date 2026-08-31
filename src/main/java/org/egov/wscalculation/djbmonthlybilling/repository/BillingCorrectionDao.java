package org.egov.wscalculation.djbmonthlybilling.repository;

import java.util.List;

import org.egov.wscalculation.djbmonthlybilling.model.BillingCorrection;

public interface BillingCorrectionDao {

    int save(BillingCorrection correction);

    int update(BillingCorrection correction);

    BillingCorrection findById(String tenantId, String id);

    List<BillingCorrection> findByConnection(String tenantId, String connectionNo);
}
