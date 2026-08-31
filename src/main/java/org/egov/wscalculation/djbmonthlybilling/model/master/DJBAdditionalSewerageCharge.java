package org.egov.wscalculation.djbmonthlybilling.model.master;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DJBAdditionalSewerageCharge {
    private String code;
    private String propertyUsage;
    private Integer fromRooms;
    private Integer toRooms;
    private Integer fromBeds;
    private Integer toBeds;
    private String chargeType;
    private BigDecimal amount;
    private String unit;
    private BigDecimal baseAmount;
    private BigDecimal blockAmount;
    private Integer blockSize;
    private Boolean active;
}
