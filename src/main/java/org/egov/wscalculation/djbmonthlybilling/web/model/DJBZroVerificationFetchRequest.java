package org.egov.wscalculation.djbmonthlybilling.web.model;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.egov.common.contract.request.RequestInfo;

import lombok.Data;

@Data
public class DJBZroVerificationFetchRequest {

    @NotNull
    private RequestInfo requestInfo;

    @NotBlank
    private String billingCycleId;
}
