package org.egov.wscalculation.djbmonthlybilling.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillingCorrection {
    private String id;
    private String tenantid;
    private String connectionno;
    private String frombillingcycleid;
    private String tobillingcycleid;
    private CorrectionStatus status;
    private String reason;
    private BigDecimal paidadjustmentamount;
    private BigDecimal appliedpaidadjustmentamount;
    private BigDecimal residualpaidcreditamount;
    private String olddemandid;
    private String oldbillid;
    private String correcteddemandid;
    private String correctedbillid;
    private String createdby;
    private Long createdtime;
    private String lastmodifiedby;
    private Long lastmodifiedtime;
}
