package org.egov.wscalculation.djbmonthlybilling.web.model;

import org.egov.common.contract.response.ResponseInfo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DJBMonthlyBillingStatementResponse {
    private ResponseInfo responseInfo;
    private DJBMonthlyBillingStatement billingStatement;
}
