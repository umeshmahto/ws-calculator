package org.egov.wscalculation.djbmonthlybilling.model.master;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DJBMonthlyRebate {
    private String code;
    private String name;
    private String rebateType;
    private BigDecimal rate;
    private BigDecimal maxConsumptionKl;
    private BigDecimal minPropertyAreaSqm;
    private List<String> eligibleReadingQualityCodes = new ArrayList<>();
    private List<String> eligibleBillingBasis = new ArrayList<>();
    private String consumerType;
    private String propertyCategory;
    private String eligibleConnectionType;
    private Integer maxEligibleConnections;
    private Boolean bulkApplicable;
    private Boolean requiresFunctionalRwh;
    private Boolean requiresFunctionalWastewaterRecycling;
    private Boolean active;
}
