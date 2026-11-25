package org.egov.wscalculation.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimationRequest {

    @NotNull
    @Valid
    @JsonProperty("RequestInfo")
    private RequestInfo requestInfo;

    @JsonProperty("tenantId")
    private String tenantId; 

    @JsonProperty("connectionType")
    private String connectionType; 

    @JsonProperty("colonyCategory")
    private String colonyCategory; 
    
    // --- UI Form Fields ---
    @JsonProperty("categoryType")
    private String categoryType; 

    @JsonProperty("propertyCategory")
    private String propertyCategory; 

    @JsonProperty("propertyType")
    private String propertyType; 

    @JsonProperty("usageCategory")
    private String usageCategory; 

    @JsonProperty("waterConnectionUsageType")
    private String waterConnectionUsageType;
    
    @JsonProperty("landArea")
    private Double landArea;

    @JsonProperty("builtUpArea")
    private BigDecimal builtUpArea;

    @JsonProperty("farArea")
    private BigDecimal farArea;

    @JsonProperty("numberOfFloors")
    private Integer numberOfFloors;

    // Occupancy inputs are supplied as questionCode/answer pairs, matching the
    // Property.additionalDetails.questions payload persisted by PropertyTax.
    @JsonProperty("questions")
    private List<PropertyQuestionAnswer> questions;

    @JsonProperty("opdArea")
    private BigDecimal opdArea;

    @JsonProperty("servantQuarterArea")
    private Double servantQuarterArea; // Servant

    @JsonProperty("is12ABCertificate")
    private Boolean is12ABCertificate;

}