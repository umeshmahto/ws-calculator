package org.egov.wscalculation.djbmonthlybilling.web.model;

import javax.validation.constraints.NotNull;

import org.egov.common.contract.request.RequestInfo;

import lombok.Data;

@Data
public class DJBZroVerificationSearchRequest {

    @NotNull
    private RequestInfo requestInfo;

    private String tenantId;
    private String connectionNo;
    private Integer offset;
    private Integer limit;
    private String status;
}
