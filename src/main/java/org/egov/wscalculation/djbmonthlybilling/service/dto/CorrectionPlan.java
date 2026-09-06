package org.egov.wscalculation.djbmonthlybilling.service.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Data;

import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;

@Data
@Builder
public class CorrectionPlan {

    private String connectionNo;

    private String previousOkBillingCycleId;
    private String currentOkBillingCycleId;

    private BigDecimal previousOkReading;
    private BigDecimal currentOkReading;

    private BigDecimal correctedConsumption;

    /** Total amount already collected against superseded average/provisional demands. */
    @Builder.Default
    private BigDecimal paidAdjustmentAmount = BigDecimal.ZERO;

    /** Portion of paid adjustment applied to the corrected bill. */
    @Builder.Default
    private BigDecimal appliedPaidAdjustmentAmount = BigDecimal.ZERO;

    /** Portion of paid adjustment left as customer credit after the corrected bill is fully settled. */
    @Builder.Default
    private BigDecimal residualPaidCreditAmount = BigDecimal.ZERO;

    private List<WaterBillingCycle> cyclesToCorrect;

    private boolean correctionRequired;

    private String reason;
}