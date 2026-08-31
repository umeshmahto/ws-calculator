package org.egov.wscalculation.djbmonthlybilling.model.master;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DJBMonthlyWaterTariff {
    private String id;
    private String category;
    private String connectionType;
    private Boolean active;
    private String effectiveFrom;
    private String effectiveTo;
    private List<DJBMonthlyWaterTariffSlab> slabs = new ArrayList<>();
}
