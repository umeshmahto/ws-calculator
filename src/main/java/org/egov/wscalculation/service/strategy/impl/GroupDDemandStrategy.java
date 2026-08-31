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
public class GroupDDemandStrategy implements GroupDemandStrategy {

    private static final BigDecimal THRESHOLD_12500 = new BigDecimal("12500");
    private static final BigDecimal OCCUPANCY_DENOMINATOR_1_5 = new BigDecimal("1.5");

    @Override
    public boolean supports(String subCategoryCode, String parentUsageCode) {
        if (subCategoryCode == null) return false;
        return subCategoryCode.startsWith("D-") || "ASSEMBLY".equalsIgnoreCase(parentUsageCode) || "RESTAURANT".equalsIgnoreCase(parentUsageCode);
    }

    @Override
    public void processGroupDemand(WaterDemandResult result, Map<String, Object> matchedNorm, Map<String, BigDecimal> contextVariables) {
        log.info("Executing Group D (Assembly / Auditoriums / Malls / Restaurants / Petrol Pumps) Demand Calculation Logic");

        String code = result.getMatchedNormCode();
        BigDecimal rawOccupancy = result.getCalculatedOccupancy();

        BigDecimal resolvedOccupancy = resolveAssemblyOccupancy(code, rawOccupancy, contextVariables);

        BigDecimal totalLpcd = getBigDecimal(matchedNorm, "totalLpcd");
        BigDecimal potableLpcd = getBigDecimal(matchedNorm, "potableLpcd");

        BigDecimal contingencyPct = resolveContingencyPercentage(code, getBigDecimal(matchedNorm, "contingencyPercentage"));

        BigDecimal contingencyMultiplier = BigDecimal.ONE;
        if (contingencyPct.compareTo(BigDecimal.ZERO) > 0) {
            contingencyMultiplier = BigDecimal.ONE.add(contingencyPct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        }

        BigDecimal initialTotalDemand = resolvedOccupancy.multiply(totalLpcd).multiply(contingencyMultiplier);
        String ifcBasis = String.valueOf(matchedNorm.getOrDefault("ifcCalculationBasis", "TOTAL_WATER_ALWAYS"));

        BigDecimal chosenLpcd = totalLpcd;
        if ("POTABLE_ONLY_IF_OVER_12500".equalsIgnoreCase(ifcBasis) && initialTotalDemand.compareTo(THRESHOLD_12500) > 0) {
            chosenLpcd = potableLpcd;
            log.info("Group D (SubCategory {}) 12,500 LPD Threshold Triggered: Total LPD {} > 12500. Applied Potable LPCD {}", code, initialTotalDemand, chosenLpcd);
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
     * Resolves Occupancy for Group D SubCategories:
     */
    private BigDecimal resolveAssemblyOccupancy(String code, BigDecimal rawOccupancy, Map<String, BigDecimal> contextVars) {
        if (code == null) return rawOccupancy != null ? rawOccupancy : BigDecimal.ZERO;

        BigDecimal farArea = contextVars.getOrDefault("far_area", BigDecimal.ZERO);
        BigDecimal plotArea = contextVars.getOrDefault("plot_area", 
                                contextVars.getOrDefault("land_area", BigDecimal.ZERO));

        // D-8 Petrol Pumps -> 1 Person per 1.5 Sqm of Total Plot Area
        if (code.equalsIgnoreCase("D-8") || code.contains("PETROL")) {
            if (plotArea.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal resolved = plotArea.divide(OCCUPANCY_DENOMINATOR_1_5, 0, RoundingMode.CEILING);
                log.info("Group D-8 Petrol Pump Occupancy resolved from Plot Area ({}/1.5): {}", plotArea, resolved);
                return resolved;
            }
        }

        if (farArea.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal resolved = farArea.divide(OCCUPANCY_DENOMINATOR_1_5, 0, RoundingMode.CEILING);
            log.info("Group D ({}) Occupancy resolved from FAR Area ({}/1.5): {}", code, farArea, resolved);
            return resolved;
        }

        return (rawOccupancy != null && rawOccupancy.compareTo(BigDecimal.ZERO) > 0) ? rawOccupancy : BigDecimal.ZERO;
    }

    private BigDecimal resolveContingencyPercentage(String code, BigDecimal mdmsContingency) {
        if (code == null) return mdmsContingency;
        if (code.startsWith("D-3") || code.startsWith("D-4") || code.startsWith("D-5") || code.startsWith("D-6")) {
            return BigDecimal.ZERO;
        }

        return mdmsContingency;
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? BigDecimal.ZERO : new BigDecimal(val.toString());
    }
}