package org.egov.wscalculation.djbmonthlybilling.service.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DJBPenaltyItem {
    private String code;
    private String name;
    private BigDecimal rateOrUnitAmount;
    private Integer quantity;
    private BigDecimal amount;
    private String basis;
}
