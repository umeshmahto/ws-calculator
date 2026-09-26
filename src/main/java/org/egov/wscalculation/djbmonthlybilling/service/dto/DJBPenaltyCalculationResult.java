package org.egov.wscalculation.djbmonthlybilling.service.dto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DJBPenaltyCalculationResult {
    private BigDecimal totalPenalty;
    @Builder.Default
    private List<DJBPenaltyItem> items = Collections.emptyList();
    private String explanation;
}
