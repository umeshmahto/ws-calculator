package org.egov.wscalculation.djbmonthlybilling.service.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SewerageCalculationContext {
    private BigDecimal waterVolumetricCharge;
    private boolean waterConnectionAvailable;
    private boolean sewerConnectionAvailable;
    private boolean additionalWaterSource;
    private String consumerCategory;
    private String propertyUsage;
    private BigDecimal builtUpAreaSqm;
    private Integer numberOfRooms;
    private Integer numberOfBeds;
}
