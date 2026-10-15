package org.egov.wscalculation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.egov.wscalculation.helper.ExpressionEvaluator;
import org.egov.wscalculation.helper.PropertyContextExtractor;
import org.egov.wscalculation.helper.UsageCodeResolver;
import org.egov.wscalculation.service.strategy.GroupDemandStrategy;
import org.egov.wscalculation.service.strategy.WaterDemandStrategyFactory;
import org.egov.wscalculation.web.models.Property;
import org.egov.wscalculation.web.models.WaterDemandResult;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculates the demand basis used by Infrastructure Charge (IFC).
 *
 * Revised WaterDemandNorms2 calculations are routed through the A/B/C Strategy
 * implementations. Occupancy formulas and LPCD rates are read from MDMS; the
 * strategy layer validates group membership and IFC policy. Unsupported legacy
 * group codes fail fast instead of silently calculating zero or using stale rules.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaterDemandCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PropertyContextExtractor contextExtractor;
    private final ExpressionEvaluator expressionEvaluator;
    private final UsageCodeResolver usageCodeResolver;
    private final WaterDemandStrategyFactory strategyFactory;

    public WaterDemandResult calculateAverageWaterDemand(Property property, Map<String, Object> masterData) {
        if (property == null) {
            throw new IllegalArgumentException("Property is required for water-demand/IFC calculation.");
        }
        if (masterData == null || masterData.isEmpty()) {
            throw new IllegalStateException("MDMS master data is required for water-demand/IFC calculation.");
        }
        if (!usageCodeResolver.hasWaterDemandNorms2Master(masterData)) {
            throw new IllegalStateException("WaterDemandNorms2 master is missing from the MDMS response. "
                    + "The revised DJB norms require WaterDemandNorms2; legacy Group D-J fallback is disabled.");
        }

        List<Map<String, Object>> revisedNorms = usageCodeResolver.extractWaterDemandNorms2(masterData);
        if (CollectionUtils.isEmpty(revisedNorms)) {
            throw new IllegalStateException("WaterDemandNorms2 master is present but contains no norm entries.");
        }
        validateSupportedNormMaster(revisedNorms);
        return calculateUsingRevisedNorms(property, masterData, revisedNorms);
    }

    /**
     * Validate the active norms as a single MDMS configuration before using any
     * row. This catches stale D-J groups and duplicate codes even when the current
     * property happens to resolve to a valid A/B/C row.
     */
    private void validateSupportedNormMaster(List<Map<String, Object>> norms) {
        Set<String> seenCodes = new HashSet<>();
        for (Map<String, Object> norm : norms) {
            if (norm == null) {
                continue;
            }
            boolean active = norm.get("isActive") == null
                    || Boolean.parseBoolean(norm.get("isActive").toString());
            if (!active) {
                continue;
            }

            Object rawCodeValue = norm.get("subCategoryCode") != null
                    ? norm.get("subCategoryCode") : norm.get("code");
            String normCode = rawCodeValue == null ? "" : rawCodeValue.toString().trim().toUpperCase(java.util.Locale.ROOT);
            if (normCode.matches("[A-Z][0-9]+")) {
                normCode = normCode.substring(0, 1) + "-" + normCode.substring(1);
            }
            if (StringUtils.isBlank(normCode)) {
                throw new IllegalStateException("Every active WaterDemandNorms2 entry must have a subCategoryCode/code.");
            }
            if (!seenCodes.add(normCode)) {
                throw new IllegalStateException("Duplicate active WaterDemandNorms2 code '" + normCode
                        + "'. Each revised norm code must be unique.");
            }

            String parentUsageCode = norm.get("parentUsageCode") == null
                    ? "" : norm.get("parentUsageCode").toString().trim();
            strategyFactory.getStrategy(normCode, parentUsageCode);
        }
    }

    private WaterDemandResult calculateUsingRevisedNorms(Property property, Map<String, Object> masterData,
            List<Map<String, Object>> norms) {
        WaterDemandResult result = emptyResult();
        String usageCode = usageCodeResolver.resolveUsageCategoryCode(property, masterData);
        if (StringUtils.isBlank(usageCode)) {
            throw new IllegalStateException("Unable to resolve a WaterDemandNorms2 subCategoryCode for property "
                    + property.getPropertyId() + ". Configure demandNormCode/subCategoryCode in MDMS PropertyType2.");
        }

        Map<String, Object> matchedNorm = usageCodeResolver.findExactMatchingNormConfig(norms, usageCode);
        if (matchedNorm == null) {
            throw new IllegalStateException("No exact WaterDemandNorms2 configuration matches usage code '" + usageCode
                    + "'. Configure an explicit demandNormCode mapping in MDMS before calculating IFC.");
        }

        boolean active = matchedNorm.get("isActive") == null
                || Boolean.parseBoolean(matchedNorm.get("isActive").toString());
        if (!active) {
            throw new IllegalStateException("WaterDemandNorms2 entry '" + usageCode
                    + "' is inactive and cannot be used for IFC calculation.");
        }

        String parentUsageCode = matchedNorm.get("parentUsageCode") == null
                ? "" : matchedNorm.get("parentUsageCode").toString().trim();
        GroupDemandStrategy strategy = strategyFactory.getStrategy(usageCode, parentUsageCode);
        Map<String, BigDecimal> context = contextExtractor.extractContextVariablesForNormsV2(property);

        result.setMatchedNormCode(usageCode);
        result.setMatchedNormName(matchedNorm.get("name") == null ? null : matchedNorm.get("name").toString());
        result.setContextVariables(context);
        strategy.processGroupDemand(result, matchedNorm, context);

        printCalculationReport(property, matchedNorm, result);
        return result;
    }

    private WaterDemandResult emptyResult() {
        WaterDemandResult result = new WaterDemandResult();
        result.setRawOccupancy(BigDecimal.ZERO);
        result.setCalculatedOccupancy(BigDecimal.ZERO);
        result.setChosenLpcd(BigDecimal.ZERO);
        result.setBaseDemand(BigDecimal.ZERO);
        result.setContingencyPercentage(BigDecimal.ZERO);
        result.setTotalWaterDemand(BigDecimal.ZERO);
        result.setStandardTotalLpcd(BigDecimal.ZERO);
        result.setPotableLpcd(BigDecimal.ZERO);
        result.setNonPotableLpcd(BigDecimal.ZERO);
        result.setPotableThresholdApplied(Boolean.FALSE);
        return result;
    }

    public String resolveUsageCategoryCode(Property property, Map<String, Object> masterData) {
        return usageCodeResolver.resolveUsageCategoryCode(property, masterData);
    }

    public BigDecimal evaluateExpression(String expression, Map<String, BigDecimal> context) {
        return expressionEvaluator.evaluateExpression(expression, context);
    }

    /** Returns a trace payload suitable for audit logs and the calculation response. */
    public Map<String, Object> traceWaterDemandLogDetails(Property property, Map<String, Object> masterData) {
        try {
            return buildTrace(property, calculateAverageWaterDemand(property, masterData));
        } catch (Exception e) {
            log.error("Error generating water demand log trace", e);
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("error", e.getMessage());
            return trace;
        }
    }

    /** Build the trace from an already-calculated result to avoid re-running formulas. */
    public Map<String, Object> traceWaterDemandLogDetails(Property property, WaterDemandResult result) {
        return buildTrace(property, result);
    }

    private Map<String, Object> buildTrace(Property property, WaterDemandResult result) {
        Map<String, Object> trace = new LinkedHashMap<>();
        if (result == null) {
            trace.put("error", "Water demand result is null.");
            return trace;
        }
        trace.put("resolvedDemandNormCode", result.getMatchedNormCode());
        trace.put("subCategoryCode", result.getMatchedNormCode());
        trace.put("categoryName", result.getMatchedNormName());
        trace.put("formula", result.getFormulaUsed());
        trace.put("contextVariables", sanitizeAreaFieldsForTrace(result.getContextVariables()));
        trace.put("rawOccupancy", result.getRawOccupancy());
        trace.put("calculatedOccupancy", result.getCalculatedOccupancy());
        trace.put("standardTotalLpcd", result.getStandardTotalLpcd());
        trace.put("potableLpcd", result.getPotableLpcd());
        trace.put("nonPotableLpcd", result.getNonPotableLpcd());
        trace.put("chosenLpcd", result.getChosenLpcd());
        trace.put("contingencyPercentage", result.getContingencyPercentage());
        trace.put("ifcCalculationBasis", result.getCalculationBasisApplied());
        trace.put("potableThresholdApplied", result.getPotableThresholdApplied());
        trace.put("baseDemand", result.getBaseDemand());
        trace.put("totalWaterDemand", result.getTotalWaterDemand());
        return trace;
    }


    /** Prevents FAR/covered-area aliases from leaking through the public audit trace. */
    private Map<String, BigDecimal> sanitizeAreaFieldsForTrace(Map<String, BigDecimal> contextVariables) {
        Map<String, BigDecimal> sanitized = new LinkedHashMap<>();
        if (contextVariables != null) {
            sanitized.putAll(contextVariables);
        }
        sanitized.keySet().removeIf(key -> {
            if (key == null) {
                return false;
            }
            String normalized = key.replace("_", "").replace("-", "").replace(" ", "")
                    .toLowerCase(java.util.Locale.ROOT);
            return "fararea".equals(normalized) || "coveredarea".equals(normalized);
        });
        return sanitized;
    }

    private void printCalculationReport(Property property, Map<String, Object> norm, WaterDemandResult result) {
        try {
            BigDecimal occupancy = result.getCalculatedOccupancy() != null ? result.getCalculatedOccupancy() : BigDecimal.ZERO;
            BigDecimal lpcd = result.getChosenLpcd() != null ? result.getChosenLpcd() : BigDecimal.ZERO;
            BigDecimal contingencyPct = result.getContingencyPercentage() != null ? result.getContingencyPercentage() : BigDecimal.ZERO;
            BigDecimal totalDemand = result.getTotalWaterDemand() != null ? result.getTotalWaterDemand() : BigDecimal.ZERO;
            BigDecimal baseDemand = result.getBaseDemand() != null ? result.getBaseDemand() : occupancy.multiply(lpcd);

            BigDecimal contingencyMultiplier = contingencyPct.compareTo(BigDecimal.ZERO) > 0
                    ? contingencyPct.divide(HUNDRED, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal contingencyDemandLiters = baseDemand.multiply(contingencyMultiplier);

            log.info("======================================================================");
            log.info("               WATER DEMAND / IFC CALCULATION REPORT                 ");
            log.info("======================================================================");
            log.info("Property ID           : {}", property != null && property.getPropertyId() != null ? property.getPropertyId() : "N/A");
            log.info("Resolved Usage Code   : {}", result.getMatchedNormCode());
            log.info("Norm Category         : {}", norm != null ? norm.getOrDefault("name", "N/A") : "N/A");
            log.info("IFC Calculation Basis : {}", result.getCalculationBasisApplied());
            log.info("Applied Formula       : {}", result.getFormulaUsed());
            log.info("Calculated Occupancy  : {}", occupancy);
            log.info("Standard Total LPCD   : {}", result.getStandardTotalLpcd());
            log.info("Potable LPCD          : {}", result.getPotableLpcd());
            log.info("Non-potable LPCD      : {}", result.getNonPotableLpcd());
            log.info("Chosen LPCD for IFC   : {}", lpcd);
            log.info("Contingency           : {}%", contingencyPct);
            log.info("Contingency volume    : {} LPD", contingencyDemandLiters.setScale(2, RoundingMode.HALF_UP));
            log.info("Potable threshold used: {}", result.getPotableThresholdApplied());
            log.info("Base Demand           : {} LPD", baseDemand);
            log.info("TOTAL IFC DEMAND      : {} LPD", totalDemand);
            log.info("======================================================================");
        } catch (Exception e) {
            log.error("Error generating water demand trace report", e);
        }
    }
}
