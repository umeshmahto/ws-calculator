package org.egov.wscalculation.djbmonthlybilling.service.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TariffSlabCharge {

    private BigDecimal from;
    private BigDecimal to;
    private BigDecimal units;
    private BigDecimal ratePerKl;
    private BigDecimal charge;
}