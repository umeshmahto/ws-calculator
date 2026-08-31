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
public class GroupADemandStrategy implements GroupDemandStrategy {

    private static final BigDecimal THRESHOLD_12500 = new BigDecimal("12500");
    private static final BigDecimal CARPET_AREA_THRESHOLD_40 = new BigDecimal("40");
    private static final BigDecimal FAR_AREA_PER_BED_DENOMINATOR = new BigDecimal("12.5");

    @Override
    public boolean supports(String subCategoryCode, String parentUsageCode) {
        if (subCategoryCode == null) return false;
        return subCategoryCode.startsWith("A-") || "RESIDENTIAL".equalsIgnoreCase(parentUsageCode) || "HOTEL".equalsIgnoreCase(parentUsageCode);
    }

    @Override
    public void processGroupDemand(WaterDemandResult result, Map<String, Object> matchedNorm, Map<String, BigDecimal> contextVariables) {
        log.info("Executing Group A (Residential & Hotels) Demand Calculation Logic");

        String code = result.getMatchedNormCode();
        BigDecimal rawOccupancy = result.getCalculatedOccupancy();

        BigDecimal resolvedOccupancy = resolveOccupancy(code, rawOccupancy, contextVariables);

        BigDecimal totalLpcd = getBigDecimal(matchedNorm, "totalLpcd");
        BigDecimal potableLpcd = getBigDecimal(matchedNorm, "potableLpcd");
        BigDecimal contingencyPct = getBigDecimal(matchedNorm, "contingencyPercentage");

        // 10% Additional Demand Multiplier
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
            log.info("Group A Hotel Threshold Triggered: Total LPD {} > 12500. Applied Potable LPCD Rate {}", initialTotalDemand, chosenLpcd);
        }

        BigDecimal baseDemand = resolvedOccupancy.multiply(chosenLpcd);
        BigDecimal finalDemand = baseDemand.multiply(contingencyMultiplier).setScale(WSCalculationConstant.RESULT_SCALE, RoundingMode.HALF_UP);

        result.setCalculatedOccupancy(resolvedOccupancy);
        result.setChosenLpcd(chosenLpcd);
        result.setBaseDemand(baseDemand);
        result.setContingencyPercentage(contingencyPct);
        result.setTotalWaterDemand(finalDemand);
    }

    private BigDecimal resolveOccupancy(String code, BigDecimal rawOccupancy, Map<String, BigDecimal> contextVars) {
        if (code == null) return rawOccupancy;

        // ==========================================
        // HOTEL CATEGORIES HANDLING (A-5 & A-6)
        // ==========================================
        if (code.equalsIgnoreCase("A-5") || code.equalsIgnoreCase("A-6")) {
            BigDecimal sanctionedBeds = contextVars.getOrDefault("sanctioned_beds", contextVars.getOrDefault("total_beds", BigDecimal.ZERO));

            if (sanctionedBeds.compareTo(BigDecimal.ZERO) > 0) {
                log.info("Hotel {} Bed Count Resolved via Sanctioned Beds: {}", code, sanctionedBeds);
                return sanctionedBeds;
            }

            // For A-5 (Standard Hotels), Fallback Formula: MAX( far_area / 12.5, total_rooms * 2 )
            if (code.equalsIgnoreCase("A-5")) {
                BigDecimal farArea = contextVars.getOrDefault("far_area", BigDecimal.ZERO);
                BigDecimal totalRooms = contextVars.getOrDefault("total_rooms", BigDecimal.ZERO);

                BigDecimal bedsFromFar = farArea.divide(FAR_AREA_PER_BED_DENOMINATOR, 0, RoundingMode.CEILING);
                BigDecimal bedsFromRooms = totalRooms.multiply(new BigDecimal("2"));

                BigDecimal calculatedBeds = bedsFromFar.max(bedsFromRooms);
                log.info("Hotel A-5 Fallback Calculated Beds: FAR Beds={}, Room Beds={}, Applied Max={}", bedsFromFar, bedsFromRooms, calculatedBeds);
                return calculatedBeds.compareTo(BigDecimal.ZERO) > 0 ? calculatedBeds : rawOccupancy;
            }
            return rawOccupancy;
        }

        // ==========================================
        // RESIDENTIAL CATEGORIES HANDLING (A-2, A-4)
        // ==========================================
        if (code.startsWith("A-2") || code.startsWith("A-4")) {
            BigDecimal sqArea = contextVars.getOrDefault("servantQuarterArea", contextVars.getOrDefault("sq_area", contextVars.getOrDefault("sqArea", BigDecimal.ZERO)));

            BigDecimal servantUnitsCount = resolveServantUnitsCount(contextVars, sqArea);

            if (code.equalsIgnoreCase("A-2_SERVANT_ROOM")) {
                return servantUnitsCount.multiply(new BigDecimal("1"));
            }

            if (code.equalsIgnoreCase("A-2_SERVANT_QUARTER")) {
                BigDecimal personsPerQuarter = (sqArea.compareTo(new BigDecimal("25")) > 0) ? new BigDecimal("5") : new BigDecimal("3");
                return servantUnitsCount.multiply(personsPerQuarter);
            }

            BigDecimal duCount = contextVars.getOrDefault("total_du", contextVars.getOrDefault("dwelling_units", BigDecimal.ZERO));
            BigDecimal kitchenCount = contextVars.getOrDefault("total_kitchens", BigDecimal.ZERO);
            BigDecimal floorCount = contextVars.getOrDefault("total_floors", BigDecimal.ONE);

            if (kitchenCount.compareTo(BigDecimal.ZERO) > 0) {
                duCount = kitchenCount.max(floorCount);
            }

            BigDecimal mainDuOccupancy = rawOccupancy;
            if (mainDuOccupancy == null || mainDuOccupancy.compareTo(BigDecimal.ZERO) == 0) {
                mainDuOccupancy = duCount.multiply(new BigDecimal("5"));
            }

            BigDecimal carpetArea = contextVars.getOrDefault("carpet_area", BigDecimal.ZERO);
            if (carpetArea.compareTo(BigDecimal.ZERO) > 0 && carpetArea.compareTo(CARPET_AREA_THRESHOLD_40) < 0) {
                mainDuOccupancy = duCount.multiply(new BigDecimal("5"));
            }

            BigDecimal servantOccupancy = BigDecimal.ZERO;
            if (sqArea.compareTo(BigDecimal.ZERO) > 0) {
                if (sqArea.compareTo(new BigDecimal("11")) < 0) {
                    servantOccupancy = servantUnitsCount.multiply(new BigDecimal("1"));
                } else if (sqArea.compareTo(new BigDecimal("25")) <= 0) {
                    servantOccupancy = servantUnitsCount.multiply(new BigDecimal("3"));
                } else {
                    servantOccupancy = servantUnitsCount.multiply(new BigDecimal("5"));
                }
            }

            return mainDuOccupancy.add(servantOccupancy);
        }

        return rawOccupancy;
    }

    private BigDecimal resolveServantUnitsCount(Map<String, BigDecimal> contextVars, BigDecimal sqArea) {
        BigDecimal count = contextVars.get("total_servant_units");
        if (count == null || count.compareTo(BigDecimal.ZERO) <= 0) {
            count = contextVars.get("total_sq");
        }
        if (count == null || count.compareTo(BigDecimal.ZERO) <= 0) {
            count = contextVars.get("total_servant_rooms");
        }

        if ((count == null || count.compareTo(BigDecimal.ZERO) <= 0) && sqArea.compareTo(BigDecimal.ZERO) > 0) {
            return BigDecimal.ONE;
        }

        return count != null ? count : BigDecimal.ZERO;
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? BigDecimal.ZERO : new BigDecimal(val.toString());
    }
}