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
public class GroupBDemandStrategy implements GroupDemandStrategy {

    private static final BigDecimal THRESHOLD_12500 = new BigDecimal("12500");
    private static final BigDecimal FAR_AREA_PER_PERSON_DENOMINATOR = new BigDecimal("4");

    @Override
    public boolean supports(String subCategoryCode, String parentUsageCode) {
        if (subCategoryCode == null) return false;
        return subCategoryCode.startsWith("B-") || "EDUCATIONAL".equalsIgnoreCase(parentUsageCode);
    }

    @Override
    public void processGroupDemand(WaterDemandResult result, Map<String, Object> matchedNorm, Map<String, BigDecimal> contextVariables) {
        log.info("Executing Group B (Educational Buildings) Demand Calculation Logic");

        String code = result.getMatchedNormCode();
        BigDecimal rawOccupancy = result.getCalculatedOccupancy();

        BigDecimal resolvedOccupancy = resolveEducationalOccupancy(rawOccupancy, contextVariables);

        BigDecimal totalLpcd = getBigDecimal(matchedNorm, "totalLpcd");       // e.g., 45
        BigDecimal potableLpcd = getBigDecimal(matchedNorm, "potableLpcd");     // e.g., 15
        BigDecimal contingencyPct = getBigDecimal(matchedNorm, "contingencyPercentage"); // e.g., 10

        // 10% Floating/Contingency Multiplier
        BigDecimal contingencyMultiplier = BigDecimal.ONE;
        if (contingencyPct.compareTo(BigDecimal.ZERO) > 0) {
            contingencyMultiplier = BigDecimal.ONE.add(contingencyPct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        }

        // Initial Total LPD = Occupancy * totalLpcd * contingencyMultiplier
        BigDecimal initialTotalDemand = resolvedOccupancy.multiply(totalLpcd).multiply(contingencyMultiplier);
        String ifcBasis = String.valueOf(matchedNorm.getOrDefault("ifcCalculationBasis", "TOTAL"));

        BigDecimal chosenLpcd = totalLpcd;
        if ("POTABLE_ONLY_IF_OVER_12500".equalsIgnoreCase(ifcBasis) && initialTotalDemand.compareTo(THRESHOLD_12500) > 0) {
            chosenLpcd = potableLpcd;
            log.info("Group B Educational Threshold Triggered: Total LPD {} > 12500. Applied Potable LPCD Rate {}", initialTotalDemand, chosenLpcd);
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
     * Resolves Educational Occupancy:
     * Higher of (FAR Area / 4 sqm) OR (Highest Shift Student Count / Total Students)
     */
    private BigDecimal resolveEducationalOccupancy(BigDecimal rawOccupancy, Map<String, BigDecimal> contextVars) {
        BigDecimal farArea = contextVars.getOrDefault("far_area", BigDecimal.ZERO);

        BigDecimal shiftStudents = contextVars.getOrDefault("highest_shift_strength", contextVars.getOrDefault("total_students", BigDecimal.ZERO));

        BigDecimal occupancyFromFar = BigDecimal.ZERO;
        if (farArea.compareTo(BigDecimal.ZERO) > 0) {
            occupancyFromFar = farArea.divide(FAR_AREA_PER_PERSON_DENOMINATOR, 0, RoundingMode.CEILING);
        }

        BigDecimal calculatedOccupancy = occupancyFromFar.max(shiftStudents);

        if (calculatedOccupancy.compareTo(BigDecimal.ZERO) > 0) {
            log.info("Group B Occupancy Resolved: FAR Occupancy={}, Shift Students={}, Applied Max={}", occupancyFromFar, shiftStudents, calculatedOccupancy);
            return calculatedOccupancy;
        }
        return (rawOccupancy != null && rawOccupancy.compareTo(BigDecimal.ZERO) > 0) ? rawOccupancy : BigDecimal.ZERO;
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? BigDecimal.ZERO : new BigDecimal(val.toString());
    }
}