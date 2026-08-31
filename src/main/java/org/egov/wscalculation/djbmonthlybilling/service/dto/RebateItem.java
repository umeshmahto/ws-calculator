package org.egov.wscalculation.djbmonthlybilling.service.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RebateItem {

    private String code;
    private String name;
    private BigDecimal rate;
    private BigDecimal baseAmount;
    private BigDecimal rebateAmount;
    private String explanation;
}