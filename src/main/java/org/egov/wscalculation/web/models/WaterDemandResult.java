package org.egov.wscalculation.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterDemandResult {
    private BigDecimal calculatedOccupancy;
    private BigDecimal chosenLpcd;
    private BigDecimal baseDemand;
    private BigDecimal contingencyPercentage;
    private BigDecimal totalWaterDemand;

    private String matchedNormCode;
    private String matchedNormName;
    private String formulaUsed;
    private String calculationBasisApplied;
    private Map<String, BigDecimal> contextVariables;
	
    
    
    
}