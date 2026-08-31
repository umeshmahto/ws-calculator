package org.egov.wscalculation.service.strategy;

import java.math.BigDecimal;
import java.util.Map;
import org.egov.wscalculation.web.models.WaterDemandResult;

public interface GroupDemandStrategy {

    /**
     * Identifies whether this strategy handles the given subCategoryCode or parentUsageCode.
     */
    boolean supports(String subCategoryCode, String parentUsageCode);

    /**
     * Processes exact calculation logic, occupancy resolutions, LPCD rates, and IFC thresholds.
     */
    void processGroupDemand(WaterDemandResult result, Map<String, Object> matchedNorm, Map<String, BigDecimal> contextVariables);
}