package org.egov.wscalculation.web.models;

import java.math.BigDecimal;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WaterDemandDetail {

    @JsonProperty("matchedNormCode")
    private String matchedNormCode;
    
    @JsonProperty("matchedNormName")
    private String matchedNormName;

    @JsonProperty("formulaUsed")
    private String formulaUsed;

    @JsonProperty("rawOccupancy")
    private BigDecimal rawOccupancy;

    @JsonProperty("calculatedOccupancy")
    private BigDecimal calculatedOccupancy;

    @JsonProperty("chosenLpcd")
    private BigDecimal chosenLpcd;

    @JsonProperty("baseDemand")
    private BigDecimal baseDemand;

    @JsonProperty("contingencyPercentage")
    private BigDecimal contingencyPercentage;

    @JsonProperty("totalWaterDemandLPD")
    private BigDecimal totalWaterDemandLPD;

    @JsonProperty("contextVariables")
    private Map<String, BigDecimal> contextVariables;
}