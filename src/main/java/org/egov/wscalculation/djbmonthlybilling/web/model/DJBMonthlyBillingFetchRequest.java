package org.egov.wscalculation.djbmonthlybilling.web.model;

import javax.validation.constraints.NotBlank;

import org.egov.common.contract.request.RequestInfo;

import lombok.Data;

@Data
public class DJBMonthlyBillingFetchRequest {

    private RequestInfo requestInfo;

    @NotBlank
    private String tenantId;

    @NotBlank
    private String connectionNo;

    /** Optional. If omitted, the latest billing cycle for the connection is returned. */
    private String billingCycleId;
}
