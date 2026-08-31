package org.egov.wscalculation.djbmonthlybilling.service.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RebateCalculationResult {

    private BigDecimal totalRebate;
    private List<RebateItem> rebateItems;
    private String explanation;
}