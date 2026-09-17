package org.egov.wscalculation.djbmonthlybilling.model;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class ZroVerificationInboxRecord {
    private ZroVerification verification;
    private String billingcycleid;
    private Long billingperiodfrom;
    private Long billingperiodto;
    private BigDecimal previousreading;
    private Long previousokreadingdate;
    private BigDecimal currentreading;
    private String readingqualitycode;
    private String billingbasis;
    private BigDecimal actualconsumption;
    private Boolean onepointfivexflag;
    private String billingcyclestatus;
    private String correctionstatus;
    private String calculationid;
    private String demandid;
    private String billid;
}
