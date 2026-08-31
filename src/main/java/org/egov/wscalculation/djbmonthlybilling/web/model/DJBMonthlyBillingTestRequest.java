package org.egov.wscalculation.djbmonthlybilling.web.model;

import java.math.BigDecimal;

import lombok.Data;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;

@Data
public class DJBMonthlyBillingTestRequest {

    private RequestInfo requestInfo;

    private String tenantId;
    private String connectionNo;

    private String tariffCategory;

    private BigDecimal consumption;

    private BigDecimal previousConsumption;

    private BillingBasis billingBasis;
    private String readingQualityCode;

    private boolean waterConnectionAvailable = true;
    private boolean sewerConnectionAvailable = true;
    private boolean additionalWaterSource;

    private String consumerCategory;
    private String propertyUsage;

    private BigDecimal builtUpAreaSqm;
    private Integer numberOfRooms;
    private Integer numberOfBeds;

    /*
     * Free-water rule deliberately receives the eligible monetary base
     * explicitly. The current rebate service does not infer tax-head scope.
     */
    private BigDecimal freeWaterEligibleAmount;
}
