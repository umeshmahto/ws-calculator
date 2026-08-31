package org.egov.wscalculation.djbmonthlybilling.model.master;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DJBMonthlyBillingRule {
    private String code;
    private String name;
    private Integer averageLookbackMonths;
    private Integer averageMaximumCycles;
    private Integer provisionalMaximumCycles;
    private Integer minimumPostAverageConsumptionKl;
    private Double highConsumptionMultiplier;
    private Integer highConsumptionThresholdKl;
    private Boolean monthlyBilling;
    private Boolean active;
}
