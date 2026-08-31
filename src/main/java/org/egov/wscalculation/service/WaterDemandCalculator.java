package org.egov.wscalculation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.egov.wscalculation.helper.ExpressionEvaluator;
import org.egov.wscalculation.helper.PropertyContextExtractor;
import org.egov.wscalculation.helper.UsageCodeResolver;
import org.egov.wscalculation.service.strategy.GroupDemandStrategy;
import org.egov.wscalculation.service.strategy.WaterDemandStrategyFactory;
import org.egov.wscalculation.web.models.Property;
import org.egov.wscalculation.web.models.WaterDemandResult;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaterDemandCalculator {

    private final PropertyContextExtractor contextExtractor;
    private final ExpressionEvaluator expressionEvaluator;
    private final UsageCodeResolver usageCodeResolver;
    private final WaterDemandStrategyFactory strategyFactory;

    public WaterDemandResult calculateAverageWaterDemand(Property property, Map<String, Object> masterData) {
        WaterDemandResult result = new WaterDemandResult();

        if (property == null || masterData == null || masterData.isEmpty()) {
            log.warn("Water demand calculation aborted: Property or MasterData is null/empty.");
            result.setTotalWaterDemand(BigDecimal.ZERO);
            return result;
        }

        List<Map<String, Object>> waterDemandNorms = usageCodeResolver.extractWaterDemandNorms(masterData);
        if (CollectionUtils.isEmpty(waterDemandNorms)) {
            log.warn("WaterDemandNorms MDMS configuration is missing or empty.");
            result.setTotalWaterDemand(BigDecimal.ZERO);
            return result;
        }

        String usageCode = usageCodeResolver.resolveUsageCategoryCode(property, masterData);
        Map<String, Object> matchedNorm = usageCodeResolver.findMatchingNormConfig(waterDemandNorms, usageCode);

        if (matchedNorm == null) {
            log.warn("No matching WaterDemandNorms found for code: '{}'", usageCode);
            result.setTotalWaterDemand(BigDecimal.ZERO);
            return result;
        }

        Boolean isActive = matchedNorm.get("isActive") == null || Boolean.parseBoolean(matchedNorm.get("isActive").toString());
        if (!isActive) {
            log.warn("WaterDemandNorms entry for Code '{}' is marked inactive in MDMS.", usageCode);
            result.setTotalWaterDemand(BigDecimal.ZERO);
            return result;
        }

        // Context variable extraction and raw occupancy evaluation
        Map<String, BigDecimal> contextVariables = contextExtractor.extractContextVariables(property);
        String formula = String.valueOf(matchedNorm.getOrDefault("minOccupancyFormula", "0"));
        BigDecimal rawOccupancy = expressionEvaluator.evaluateExpression(formula, contextVariables);

        result.setMatchedNormCode(usageCode);
        result.setFormulaUsed(formula);
        result.setContextVariables(contextVariables);
        result.setMatchedNormName((String) matchedNorm.get("name"));
        result.setCalculationBasisApplied(String.valueOf(matchedNorm.getOrDefault("ifcCalculationBasis", "TOTAL")));
        result.setCalculatedOccupancy(rawOccupancy);

        // Dispatch calculation execution to specific Group Strategy Class
        String parentUsageCode = String.valueOf(matchedNorm.getOrDefault("parentUsageCode", ""));
        GroupDemandStrategy strategy = strategyFactory.getStrategy(usageCode, parentUsageCode);
        strategy.processGroupDemand(result, matchedNorm, contextVariables);

        // Generate and log calculation breakdown report
        printCalculationReport(property, matchedNorm, result);

        return result;
    }

    public String resolveUsageCategoryCode(Property property, Map<String, Object> masterData) {
        return usageCodeResolver.resolveUsageCategoryCode(property, masterData);
    }

    public BigDecimal evaluateExpression(String expression, Map<String, BigDecimal> context) {
        return expressionEvaluator.evaluateExpression(expression, context);
    }

    /**
     * Synchronized Trace & Log method delegating directly to calculateAverageWaterDemand.
     */
    public Map<String, Object> traceWaterDemandLogDetails(Property property, Map<String, Object> masterData) {
        Map<String, Object> trace = new LinkedHashMap<>();
        try {
            WaterDemandResult result = calculateAverageWaterDemand(property, masterData);

            trace.put("resolvedDemandNormCode", result.getMatchedNormCode());
            trace.put("subCategoryCode", result.getMatchedNormCode());
            trace.put("categoryName", result.getMatchedNormName());
            trace.put("formula", result.getFormulaUsed());
            trace.put("contextVariables", result.getContextVariables());
            trace.put("calculatedOccupancy", result.getCalculatedOccupancy());
            trace.put("chosenLpcd", result.getChosenLpcd());
            trace.put("contingencyPercentage", result.getContingencyPercentage());
            trace.put("ifcCalculationBasis", result.getCalculationBasisApplied());
            trace.put("baseDemand", result.getBaseDemand());
            trace.put("totalWaterDemand", result.getTotalWaterDemand());
        } catch (Exception e) {
            log.error("Error generating water demand log trace", e);
            trace.put("error", e.getMessage());
        }
        return trace;
    }

    /**
     * Prints complete demand breakdown log into application trace/console.
     */
    private void printCalculationReport(Property property, Map<String, Object> norm, WaterDemandResult result) {
        try {
            BigDecimal occupancy = result.getCalculatedOccupancy();
            BigDecimal lpcd = result.getChosenLpcd();
            BigDecimal contingencyPct = result.getContingencyPercentage();
            BigDecimal totalDemand = result.getTotalWaterDemand();
            BigDecimal baseDemand = result.getBaseDemand() != null ? result.getBaseDemand() 
                    : ((occupancy != null && lpcd != null) ? occupancy.multiply(lpcd) : BigDecimal.ZERO);

            BigDecimal contingencyMultiplier = (contingencyPct != null && contingencyPct.compareTo(BigDecimal.ZERO) > 0)
                    ? contingencyPct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal contingencyDemandLiters = baseDemand.multiply(contingencyMultiplier);

            log.info("======================================================================");
            log.info("               WATER DEMAND CALCULATION REPORT                        ");
            log.info("======================================================================");
            log.info("Property ID          : {}",
                    property != null && property.getPropertyId() != null ? property.getPropertyId() : "N/A");
            log.info("Resolved Usage Code  : {}", result.getMatchedNormCode());
            log.info("Norm Category        : {}", norm != null ? norm.getOrDefault("name", "N/A") : "N/A");
            log.info("IFC Calculation Basis: {}", result.getCalculationBasisApplied());
            log.info("Applied Formula      : {}", result.getFormulaUsed());
            log.info("----------------------------------------------------------------------");
            log.info("                       INPUT PARAMETERS                               ");
            log.info("----------------------------------------------------------------------");

            if (result.getContextVariables() != null) {
                result.getContextVariables().forEach((k, v) -> {
                    if (v != null && v.compareTo(BigDecimal.ZERO) > 0) {
                        log.info("Input Parameter [{}] : {}", k, v);
                    }
                });
            }

            log.info("----------------------------------------------------------------------");
            log.info("                   DEMAND BREAKDOWN LOG                               ");
            log.info("----------------------------------------------------------------------");
            log.info("1. Calculated Occupancy : {} Persons/Units", occupancy);
            log.info("2. Applied LPCD Rate    : {} Liters/Person/Day", lpcd);
            log.info("3. Base Demand          : {} LPD (Occupancy x LPCD)", baseDemand);

            if (contingencyPct != null && contingencyPct.compareTo(BigDecimal.ZERO) > 0) {
                log.info("4. Contingency Percentage : {}%", contingencyPct);
                log.info("5. Contingency Volume     : +{} LPD",
                        contingencyDemandLiters.setScale(2, RoundingMode.HALF_UP));
            } else {
                log.info("4. Contingency Factor    : 0% (None Applied)");
            }

            log.info("----------------------------------------------------------------------");
            log.info("TOTAL CALCULATED DEMAND : {} LPD", totalDemand);
            log.info("======================================================================");
        } catch (Exception e) {
            log.error("Error generating calculation trace report", e);
        }
    }
}