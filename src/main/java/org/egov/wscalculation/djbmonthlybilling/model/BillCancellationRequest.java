package org.egov.wscalculation.djbmonthlybilling.model;

import org.egov.common.contract.request.RequestInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request contract matching billing-service bill/v2/_cancelbill.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillCancellationRequest {

    private RequestInfo requestInfo;
    private BillCancellationCriteria updateBillCriteria;
}
