package org.egov.wscalculation.djbmonthlybilling.web.model;

import java.math.BigDecimal;

import org.egov.wscalculation.djbmonthlybilling.model.ZroVerification;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DJBZroVerificationInboxItem {

    private ZroVerification verification;
    private String billingCycleId;
    private String connectionNo;
    private Long billingPeriodFrom;
    private Long billingPeriodTo;
    private BigDecimal previousReading;
    private BigDecimal currentReading;
    private String readingQualityCode;
    private String billingBasis;
    private BigDecimal actualConsumption;
    private BigDecimal thresholdConsumption;
    private Boolean onePointFiveXFlag;
    private String billingCycleStatus;
    private String correctionStatus;
    private String calculationId;
    private String demandId;
    private String billId;
}
