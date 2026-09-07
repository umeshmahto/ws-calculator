package org.egov.wscalculation.djbmonthlybilling.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ResidualCreditStatus;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DJBResidualCredit {
    private String id;
    private String tenantid;
    private String connectionno;
    private String sourcecorrectionid;
    private BigDecimal originalamount;
    private BigDecimal remainingamount;
    private BigDecimal reservedamount;
    private ResidualCreditStatus status;
    private String createdby;
    private Long createdtime;
    private String lastmodifiedby;
    private Long lastmodifiedtime;
}
