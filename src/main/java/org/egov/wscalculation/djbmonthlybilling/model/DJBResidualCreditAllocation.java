package org.egov.wscalculation.djbmonthlybilling.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ResidualCreditAllocationStatus;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DJBResidualCreditAllocation {
    private String id;
    private String tenantid;
    private String creditid;
    private String billingcycleid;
    private String demandid;
    private String billid;
    private BigDecimal appliedamount;
    private ResidualCreditAllocationStatus status;
    private String createdby;
    private Long createdtime;
    private String lastmodifiedby;
    private Long lastmodifiedtime;
}
