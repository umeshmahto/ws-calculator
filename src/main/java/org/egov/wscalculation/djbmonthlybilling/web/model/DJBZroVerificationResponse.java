package org.egov.wscalculation.djbmonthlybilling.web.model;

import org.egov.common.contract.response.ResponseInfo;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.ZroVerification;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DJBZroVerificationResponse {

    private ResponseInfo responseInfo;
    private ZroVerification verification;
    private WaterBillingCycle billingCycle;
    private String demandId;
    private String billId;
    private String message;
}
