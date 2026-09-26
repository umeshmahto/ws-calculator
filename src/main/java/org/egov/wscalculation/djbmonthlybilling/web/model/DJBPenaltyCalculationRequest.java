package org.egov.wscalculation.djbmonthlybilling.web.model;

import java.math.BigDecimal;

import javax.validation.Valid;

import org.egov.common.contract.request.RequestInfo;

import lombok.Data;

@Data
public class DJBPenaltyCalculationRequest {

    @Valid
    private RequestInfo requestInfo;

    private String tenantId;
    private String connectionNo;

    /** Number of dishonoured cheques covered by this penalty event. */
    private Integer dishonouredChequeCount;

    /** True when an unauthorized connection is being regularized. */
    private Boolean regularizationRequired = false;

    /** DOMESTIC / NON_DOMESTIC / COMMERCIAL. */
    private String connectionType;

    /** Epoch milliseconds for the regularization assessment date. */
    private Long regularizationDate;

    /** True when a misuse/wastage offence is being assessed. */
    private Boolean misuseWastageOffence = false;

    /** True when this is a subsequent offence subject to a daily fine. */
    private Boolean subsequentMisuseWastageOffence = false;

    /** Number of days for a subsequent daily fine. */
    private Integer misuseWastageDays;

    /** Fine amount selected by the competent authority; enforced against the DJB cap. */
    private BigDecimal misuseWastageFineAmount;
}
