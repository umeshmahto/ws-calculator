package org.egov.wscalculation.djbmonthlybilling.service.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConsumptionResult {
    private BigDecimal actualConsumption;
    private BigDecimal averageConsumption;
    private BigDecimal billingConsumption;
    private BigDecimal previousConsumption;
    private BigDecimal deviationFactor;
    private boolean onePointFiveX;
}
