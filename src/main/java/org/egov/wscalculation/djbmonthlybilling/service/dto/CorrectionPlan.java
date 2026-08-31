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

    private List<WaterBillingCycle> cyclesToCorrect;

    private boolean correctionRequired;

    private String reason;
}