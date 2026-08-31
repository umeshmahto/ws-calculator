package org.egov.wscalculation.web.models;

import java.math.BigDecimal;
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
public class InfrastructureChargeDetail {

    @JsonProperty("colonyCategory")
    private String colonyCategory;

    @JsonProperty("plotArea")
    private BigDecimal plotArea;

    @JsonProperty("minimumPlotArea")
    private BigDecimal minimumPlotArea;

    @JsonProperty("waterRatePerLPD")
    private BigDecimal waterRatePerLPD;

    @JsonProperty("sewerRatePerLPD")
    private BigDecimal sewerRatePerLPD;

    @JsonProperty("waterComponentIFC")
    private BigDecimal waterComponentIFC;

    @JsonProperty("sewerComponentIFC")
    private BigDecimal sewerComponentIFC;

    @JsonProperty("grossIFC")
    private BigDecimal grossIFC;

    @JsonProperty("rebatePercentage")
    private BigDecimal rebatePercentage;

    @JsonProperty("rebateAmount")
    private BigDecimal rebateAmount;

    @JsonProperty("netIFC")
    private BigDecimal netIFC;

    @JsonProperty("institutionalRebateApplied")
    private Boolean institutionalRebateApplied;

    @JsonProperty("institutionalRebateReason")
    private String institutionalRebateReason;

    @JsonProperty("institutionalRebatePercentage")
    private BigDecimal institutionalRebatePercentage;

    @JsonProperty("institutionalRebateAmount")
    private BigDecimal institutionalRebateAmount;

    @JsonProperty("dwellingRebateApplied")
    private Boolean dwellingRebateApplied;

    @JsonProperty("dwellingRebateReason")
    private String dwellingRebateReason;

    @JsonProperty("dwellingRebatePercentage")
    private BigDecimal dwellingRebatePercentage;

    @JsonProperty("dwellingRebateAmount")
    private BigDecimal dwellingRebateAmount;

    @JsonProperty("netIFCAfterColonyRebate")
    private BigDecimal netIFCAfterColonyRebate;

    @JsonProperty("netIFCAfterInstitutionalRebate")
    private BigDecimal netIFCAfterInstitutionalRebate;

    @JsonProperty("netIFCAfterDwellingRebate")
    private BigDecimal netIFCAfterDwellingRebate;
}