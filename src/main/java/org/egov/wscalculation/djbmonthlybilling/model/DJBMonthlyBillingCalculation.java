package org.egov.wscalculation.djbmonthlybilling.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DJBMonthlyBillingCalculation {
    private String id;
    private String tenantid;
    private String billingcycleid;
    private String connectionno;
    private String engineversion;
    private String status;
    private Long calculatedtime;
    private String calculatedby;
    private String snapshotjson;
}
