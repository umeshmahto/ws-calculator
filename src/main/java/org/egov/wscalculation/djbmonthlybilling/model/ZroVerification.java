package org.egov.wscalculation.djbmonthlybilling.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ZroStatus;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZroVerification {
    private String id;
    private String tenantid;
    private String billingcycleid;
    private String connectionno;
    private BigDecimal consumption;
    private BigDecimal previousconsumption;
    private BigDecimal deviationfactor;
    private ZroStatus status;
    private String remarks;
    private String actionby;
    private Long actiondate;
    private String createdby;
    private Long createdtime;
    private String lastmodifiedby;
    private Long lastmodifiedtime;
}
