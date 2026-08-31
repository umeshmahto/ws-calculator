package org.egov.wscalculation.djbmonthlybilling.model.master;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DJBMonthlySewerageRule {
    private String code;
    private String name;
    private String category;
    private String chargeType;
    private BigDecimal percentage;
    private BigDecimal minBuiltUpAreaSqm;
    private BigDecimal maxBuiltUpAreaSqm;
    private BigDecimal amount;
    private Boolean requiresWaterConnection;
    private Boolean active;
}
