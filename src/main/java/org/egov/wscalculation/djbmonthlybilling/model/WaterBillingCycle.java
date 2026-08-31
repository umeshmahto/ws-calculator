package org.egov.wscalculation.djbmonthlybilling.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingCycleStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ZroStatus;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WaterBillingCycle {
    private String id;
    private String tenantid;
    private String connectionno;
    private Long billingperiodfrom;
    private Long billingperiodto;
    private String meterreadingid;
    private String readingqualitycode;
    private BillingBasis billingbasis;
    private BigDecimal previousokreading;
    private Long previousokreadingdate;
    private BigDecimal currentreading;
    private Long currentreadingdate;
    private BigDecimal actualconsumption;
    private BigDecimal averageconsumption;
    private BigDecimal billingconsumption;
    private BigDecimal previousconsumption;
    private BigDecimal deviationfactor;
    private Boolean onepointfivexflag = Boolean.FALSE;
    private Integer averagecyclecount = 0;
    private Integer provisionalcyclecount = 0;
    private ZroStatus zrostatus;
    private String zroremarks;
    private String zroactionby;
    private Long zroactiondate;
    private String calculationid;
    private String demandid;
    private String billid;
    private CorrectionStatus correctionstatus;
    private BillingCycleStatus status;
    private String createdby;
    private Long createdtime;
    private String lastmodifiedby;
    private Long lastmodifiedtime;
}
