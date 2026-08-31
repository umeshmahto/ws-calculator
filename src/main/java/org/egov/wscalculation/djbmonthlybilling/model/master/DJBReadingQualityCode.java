package org.egov.wscalculation.djbmonthlybilling.model.master;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DJBReadingQualityCode {
    private String code;
    private String name;
    private String billingTreatment;
    private Boolean active;
}
