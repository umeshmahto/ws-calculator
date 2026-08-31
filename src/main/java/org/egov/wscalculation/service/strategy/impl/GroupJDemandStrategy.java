package org.egov.wscalculation.service.strategy.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.egov.wscalculation.constants.WSCalculationConstant;
import org.egov.wscalculation.service.strategy.GroupDemandStrategy;
import org.egov.wscalculation.web.models.WaterDemandResult;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GroupJDemandStrategy implements GroupDemandStrategy {

    private static final BigDecimal THRESHOLD_12500 = new BigDecimal("12500");
    private static final BigDecimal OCCUPANCY_DENOMINATOR_10 = new BigDecimal("10.0");

    @Override
    public boolean supports(String subCategoryCode, String parentUsageCode) {
        if (subCategoryCode == null) return false;
        return subCategoryCode.startsWith("J") || "HAZARDOUS".equalsIgnoreCase(parentUsageCode) || "NONRESIDENTIAL.HAZARDOUS".equalsIgnoreCase(parentUsageCode);
    }

    @Override
    public void processGroupDemand(WaterDemandResult result, Map<String, Object> matchedNorm, Map<String, BigDecimal> contextVariables) {
        log.info("Executing Group J (Hazardous Buildings) Demand Calculation Logic");

        String subCategoryCode = result.getMatchedNormCode();
        BigDecimal rawOccupancy = result.getCalculatedOccupancy();

        BigDecimal resolvedOccupancy = resolveHazardousOccupancy(matchedNorm, rawOccupancy, contextVariables);

        BigDecimal totalLpcd = getBigDecimal(matchedNorm, "totalLpcd");
        BigDecimal potableLpcd = getBigDecimal(matchedNorm, "potableLpcd");
        BigDecimal contingencyPct = getBigDecimal(matchedNorm, "contingencyPercentage");

        BigDecimal contingencyMultiplier = BigDecimal.ONE;
        if (contingencyPct.compareTo(BigDecimal.ZERO) > 0) {
            contingencyMultiplier = BigDecimal.ONE.add(contingencyPct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        }

        BigDecimal initialTotalDemand = resolvedOccupancy.multiply(totalLpcd).multiply(contingencyMultiplier);
        String ifcBasis = String.valueOf(matchedNorm.getOrDefault("ifcCalculationBasis", "POTABLE_ONLY_IF_OVER_12500"));

        BigDecimal chosenLpcd = totalLpcd;
        if ("POTABLE_ONLY_IF_OVER_12500".equalsIgnoreCase(ifcBasis) && initialTotalDemand.compareTo(THRESHOLD_12500) > 0) {
            chosenLpcd = potableLpcd;
            log.info("Group J ({}) 12,500 LPD Threshold Triggered: Total LPD {} > 12500. Applied Potable LPCD Rate {}", subCategoryCode, initialTotalDemand, chosenLpcd);
        }

        BigDecimal baseDemand = resolvedOccupancy.multiply(chosenLpcd);
        BigDecimal finalDemand = baseDemand.multiply(contingencyMultiplier).setScale(WSCalculationConstant.RESULT_SCALE, RoundingMode.HALF_UP);

        result.setCalculatedOccupancy(resolvedOccupancy);
        result.setChosenLpcd(chosenLpcd);
        result.setBaseDemand(baseDemand);
        result.setContingencyPercentage(contingencyPct);
        result.setTotalWaterDemand(finalDemand);
    }

    /**
     * Dynamically resolves occupancy based on MDMS occupancyBasis property.
     */
    private BigDecimal resolveHazardousOccupancy(Map<String, Object> matchedNorm, BigDecimal rawOccupancy, Map<String, BigDecimal> contextVars) {
        String occupancyBasis = String.valueOf(matchedNorm.getOrDefault("occupancyBasis", "FAR_AREA"));
        BigDecimal farArea = contextVars.getOrDefault("far_area", BigDecimal.ZERO);
        BigDecimal plotArea = contextVars.getOrDefault("plot_area", BigDecimal.ZERO);

        BigDecimal areaToUse = "PLOT_AREA".equalsIgnoreCase(occupancyBasis) ? (plotArea.compareTo(BigDecimal.ZERO) > 0 ? plotArea : farArea): (farArea.compareTo(BigDecimal.ZERO) > 0 ? farArea : plotArea);

        if (areaToUse.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal denominator = getBigDecimal(matchedNorm, "occupancyDenominator");
            if (denominator.compareTo(BigDecimal.ZERO) == 0) {
                denominator = OCCUPANCY_DENOMINATOR_10;
            }

            BigDecimal resolved = areaToUse.divide(denominator, 0, RoundingMode.CEILING);
            log.info("Group J Occupancy resolved using Area ({}/{}): {}", areaToUse, denominator, resolved);
            return resolved;
        }

        return (rawOccupancy != null && rawOccupancy.compareTo(BigDecimal.ZERO) > 0) ? rawOccupancy : BigDecimal.ZERO;
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? BigDecimal.ZERO : new BigDecimal(val.toString());
    }
}