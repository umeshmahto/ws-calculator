package org.egov.wscalculation.djbmonthlybilling.service.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TariffCalculationResult {

    private String tariffId;
    private String category;

    private BigDecimal consumption;
    private BigDecimal waterVolumetricCharge;
    private BigDecimal serviceCharge;
    private BigDecimal totalWaterCharge;

    private List<TariffSlabCharge> slabCharges;
}