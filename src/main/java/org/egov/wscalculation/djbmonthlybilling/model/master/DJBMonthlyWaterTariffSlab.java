package org.egov.wscalculation.djbmonthlybilling.model.master;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DJBMonthlyWaterTariffSlab {
    private BigDecimal from;
    private BigDecimal to;
    private BigDecimal ratePerKl;
    private BigDecimal serviceCharge;
}
