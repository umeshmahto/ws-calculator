package org.egov.wscalculation.djbmonthlybilling.model;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic billing-service bill cancellation criteria used by the DJB
 * automatic-correction flow. The billing service endpoint itself is generic;
 * DJB only supplies the exact bill ids that have been superseded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillCancellationCriteria {

    private String tenantId;
    private Set<String> consumerCodes;
    private String businessService;
    private Object additionalDetails;
    private Set<String> billIds;
    private String statusToBeUpdated;
}
