package org.egov.wscalculation.djbmonthlybilling.web.model;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.TariffCalculationResult;

@Data
@Builder
public class DJBMonthlyBillingTestResponse {

    private String tenantId;
    private String connectionNo;

    private String readingQualityCode;
    private String billingBasis;

    private BigDecimal consumption;
    private BigDecimal previousConsumption;

    private TariffCalculationResult water;
    private SewerageCalculationResult sewerage;
    private RebateCalculationResult rebate;

    private BigDecimal totalBeforeRebate;
    private BigDecimal netAmountAfterRebate;
}
