package org.egov.wscalculation.djbmonthlybilling.service.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SewerageCalculationResult {
    private BigDecimal regularSewerageCharge;
    private BigDecimal additionalSewerageCharge;
    private BigDecimal totalSewerageCharge;
    private String regularRuleCode;
    private String additionalRuleCode;
    private String explanation;
}
