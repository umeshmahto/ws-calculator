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
public class GroupCDemandStrategy implements GroupDemandStrategy {

    private static final BigDecimal THRESHOLD_12500 = new BigDecimal("12500");
    private static final BigDecimal BED_THRESHOLD_100 = new BigDecimal("100");

    @Override
    public boolean supports(String subCategoryCode, String parentUsageCode) {
        if (subCategoryCode == null) return false;
        return subCategoryCode.startsWith("C-") || "INSTITUTIONAL".equalsIgnoreCase(parentUsageCode) || "HOSPITAL".equalsIgnoreCase(parentUsageCode);
    }

    @Override
    public void processGroupDemand(WaterDemandResult result, Map<String, Object> matchedNorm, Map<String, BigDecimal> contextVariables) {
        log.info("Executing Group C (Institutional & Hospitals) Demand Calculation Logic");

        String code = result.getMatchedNormCode();
        BigDecimal rawOccupancy = result.getCalculatedOccupancy();

        BigDecimal resolvedOccupancy = resolveInstitutionalOccupancy(code, rawOccupancy, contextVariables);

        BigDecimal totalLpcd = getBigDecimal(matchedNorm, "totalLpcd");
        BigDecimal potableLpcd = getBigDecimal(matchedNorm, "potableLpcd");
        BigDecimal contingencyPct = getBigDecimal(matchedNorm, "contingencyPercentage");

        if (code != null && (code.startsWith("C-1") || code.contains("HOSPITAL") || code.contains("NURSING"))) {
            if (resolvedOccupancy.compareTo(BED_THRESHOLD_100) <= 0) {
                totalLpcd = new BigDecimal("340");
                potableLpcd = new BigDecimal("250");
                log.info("C-1 Bed Count ({}) <= 100. Overridden LPCD -> Total: 340, Potable: 250", resolvedOccupancy);
            } else {
                totalLpcd = new BigDecimal("450");
                potableLpcd = new BigDecimal("360");
                log.info("C-1 Bed Count ({}) > 100. Overridden LPCD -> Total: 450, Potable: 360", resolvedOccupancy);
            }
        }

        BigDecimal contingencyMultiplier = BigDecimal.ONE;
        if (contingencyPct.compareTo(BigDecimal.ZERO) > 0) {
            contingencyMultiplier = BigDecimal.ONE.add(contingencyPct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        }

        BigDecimal initialTotalDemand = resolvedOccupancy.multiply(totalLpcd).multiply(contingencyMultiplier);
        String ifcBasis = String.valueOf(matchedNorm.getOrDefault("ifcCalculationBasis", "TOTAL"));

        BigDecimal chosenLpcd = totalLpcd;
        if ("POTABLE_ONLY_IF_OVER_12500".equalsIgnoreCase(ifcBasis) && initialTotalDemand.compareTo(THRESHOLD_12500) > 0) {
            chosenLpcd = potableLpcd;
            log.info("Group C Institutional Threshold Triggered: Total LPD {} > 12500. Applied Potable LPCD Rate {}", initialTotalDemand, chosenLpcd);
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
     * Resolves Occupancy for Group C SubCategories:
     */
    private BigDecimal resolveInstitutionalOccupancy(String code, BigDecimal rawOccupancy, Map<String, BigDecimal> contextVars) {
        if (code == null) return rawOccupancy;

        BigDecimal farArea = contextVars.getOrDefault("far_area", BigDecimal.ZERO);
        BigDecimal sanctionedBeds = contextVars.getOrDefault("sanctioned_beds", contextVars.getOrDefault("total_beds", contextVars.getOrDefault("specified_beds", BigDecimal.ZERO)));

        if (code.equalsIgnoreCase("C-1_HOSPITAL")) {
            BigDecimal bedsFromFar = (farArea.compareTo(BigDecimal.ZERO) > 0) ? farArea.divide(new BigDecimal("80.0"), 0, RoundingMode.CEILING) : BigDecimal.ZERO;
            BigDecimal resolved = sanctionedBeds.max(bedsFromFar);
            log.info("C-1_HOSPITAL Beds: Sanctioned={}, FAR Beds={}, Resolved={}", sanctionedBeds, bedsFromFar, resolved);
            return resolved.compareTo(BigDecimal.ZERO) > 0 ? resolved : rawOccupancy;
        }

        if (code.equalsIgnoreCase("C-1_NURSING") || code.equalsIgnoreCase("C-1_NURSING_HOME")) {
            BigDecimal bedsFromFar = (farArea.compareTo(BigDecimal.ZERO) > 0) ? farArea.divide(new BigDecimal("60.0"), 0, RoundingMode.CEILING) : BigDecimal.ZERO;
            BigDecimal resolved = sanctionedBeds.max(bedsFromFar);
            log.info("C-1_NURSING Beds: Sanctioned={}, FAR Beds={}, Resolved={}", sanctionedBeds, bedsFromFar, resolved);
            return resolved.compareTo(BigDecimal.ZERO) > 0 ? resolved : rawOccupancy;
        }

        if (code.startsWith("C-2") || code.startsWith("C-3")) {
            if (farArea.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal resolved = farArea.divide(new BigDecimal("7.5"), 0, RoundingMode.CEILING);
                log.info("Group {} Occupancy from FAR Area ({}/7.5): {}", code, farArea, resolved);
                return resolved;
            }
        }

        return (rawOccupancy != null && rawOccupancy.compareTo(BigDecimal.ZERO) > 0) ? rawOccupancy : BigDecimal.ZERO;
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? BigDecimal.ZERO : new BigDecimal(val.toString());
    }
}