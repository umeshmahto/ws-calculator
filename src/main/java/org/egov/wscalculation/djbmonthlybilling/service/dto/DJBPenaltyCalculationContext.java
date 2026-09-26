package org.egov.wscalculation.djbmonthlybilling.service.dto;

import java.time.LocalDate;
import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

/**
 * Explicit input for DJB one-time penalty calculation.
 *
 * The trigger data is intentionally not inferred from a monthly meter cycle.
 * Dishonoured cheque and unauthorized-connection regularization are event-driven
 * charges and therefore require explicit event input.
 */
@Data
@Builder
public class DJBPenaltyCalculationContext {

    /** Number of dishonoured cheques for the penalty event. */
    private Integer dishonouredChequeCount;

    /** Whether an unauthorized water/sewer connection is being regularized. */
    private boolean regularizationRequired;

    /** DOMESTIC or NON_DOMESTIC / COMMERCIAL. */
    private String connectionType;

    /** Date on which the regularization penalty is assessed. */
    private LocalDate regularizationDate;

    /** Whether a water misuse/wastage offence is being assessed. */
    private boolean misuseWastageOffence;

    /** True for a subsequent offence, where the fine is assessed per day. */
    private boolean subsequentMisuseWastageOffence;

    /** Number of days for a subsequent-offence daily fine. */
    private Integer misuseWastageDays;

    /** Actual fine chosen by the competent authority, subject to the DJB statutory cap. */
    private BigDecimal misuseWastageFineAmount;
}
