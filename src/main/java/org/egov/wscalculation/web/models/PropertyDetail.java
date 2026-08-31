package org.egov.wscalculation.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PropertyDetail {

    @JsonProperty("propertyId")
    private String propertyId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("propertyType")
    private String propertyType;

    @JsonProperty("usageCategory")
    private String usageCategory;
    
    @JsonProperty("waterConnectionUsageType")
    private String waterConnectionUsageType;

    @JsonProperty("colonyCategory")
    private String colonyCategory;

    @JsonProperty("localityCode")
    private String localityCode;

    @JsonProperty("landArea")
    private BigDecimal landArea;

    @JsonProperty("superBuiltUpArea")
    private BigDecimal superBuiltUpArea;

    @JsonProperty("farArea")
    private BigDecimal farArea;

    @JsonProperty("coveredArea")
    private BigDecimal coveredArea;

    @JsonProperty("numberOfDwellingUnits")
    private BigDecimal numberOfDwellingUnits;

    @JsonProperty("numberOfBeds")
    private BigDecimal numberOfBeds;

    @JsonProperty("numberOfRooms")
    private BigDecimal numberOfRooms;

    @JsonProperty("numberOfStudents")
    private BigDecimal numberOfStudents;

    @JsonProperty("numberOfStaff")
    private BigDecimal numberOfStaff;
}