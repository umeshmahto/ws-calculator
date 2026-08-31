package org.egov.wscalculation.web.models;

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
public class CalculationDetail {
	
	@JsonProperty("propertyDetail")
    private PropertyDetail propertyDetail;

    @JsonProperty("waterDemandDetail")
    private WaterDemandDetail waterDemandDetail;

    @JsonProperty("infrastructureChargeDetail")
    private InfrastructureChargeDetail infrastructureChargeDetail;
}