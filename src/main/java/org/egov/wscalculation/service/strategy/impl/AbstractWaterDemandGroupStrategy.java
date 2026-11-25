package org.egov.wscalculation.service.strategy.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.egov.wscalculation.constants.WSCalculationConstant;
import org.egov.wscalculation.helper.ExpressionEvaluator;
import org.egov.wscalculation.service.strategy.GroupDemandStrategy;
import org.egov.wscalculation.web.models.WaterDemandResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractWaterDemandGroupStrategy implements GroupDemandStrategy {

    private static final BigDecimal IFC_POTABLE_THRESHOLD_LPD = new BigDecimal("12500");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ExpressionEvaluator expressionEvaluator;

    protected AbstractWaterDemandGroupStrategy(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    /** IFC basis prescribed for this parent group in the revised norms. */
    protected abstract String expectedIfcCalculationBasis();

    @Override
    public final void processGroupDemand(WaterDemandResult result, Map<String, Object> matchedNorm,
            Map<String, BigDecimal> contextVariables) {
        if (result == null || matchedNorm == null) {
            throw new IllegalArgumentException("Water demand result and matched norm are required.");
        }

        Map<String, BigDecimal> context = contextVariables == null
                ? Collections.<String, BigDecimal>emptyMap() : contextVariables;
        String normCode = normalizeCode(firstNonBlank(matchedNorm.get("subCategoryCode"),
                matchedNorm.get("code"), result.getMatchedNormCode()));
        String parentUsageCode = asString(matchedNorm.get("parentUsageCode"));

        if (!supports(normCode, parentUsageCode)) {
            throw new IllegalStateException("Norm '" + normCode + "' with parentUsageCode '" + parentUsageCode
                    + "' was routed to an incompatible water-demand strategy.");
        }

        validateRequiredInputs(normCode, context);
        if ("C-4".equals(normCode)) {
            validateC4FacilityPlotAreaRule(matchedNorm, context);
        }

        String occupancyFormula = asString(matchedNorm.get("minOccupancyFormula"));
        // C-4 minimum bed count differs by facility type in the revised DJB sheet:
        // Hospital = 1 bed / 80 sqm; Nursing Home = 1 bed / 60 sqm.
        if ("C-4".equals(normCode)) {
            occupancyFormula = "MAX(sanctioned_beds, minimum_beds_by_area)";
        }
        if (StringUtils.isBlank(occupancyFormula)) {
            throw new IllegalStateException("minOccupancyFormula is required for WaterDemandNorms2 code " + normCode);
        }

        BigDecimal rawOccupancy = expressionEvaluator.evaluateExpression(occupancyFormula, context);
        if (rawOccupancy == null || rawOccupancy.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Calculated occupancy cannot be negative for WaterDemandNorms2 code " + normCode);
        }

        // Area-based occupancy may be fractional; statutory occupancy is rounded up.
        BigDecimal occupancy = rawOccupancy.setScale(0, RoundingMode.CEILING);
        if (occupancy.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("minOccupancyFormula did not resolve a positive occupancy for WaterDemandNorms2 code "
                    + normCode + ". Verify the required property inputs.");
        }

        // C-4's LPCD changes at 100 effective beds. Feed the calculated
        // (sanctioned-vs-area minimum) bed count into the MDMS formula so that
        // the rates remain configuration-driven rather than hard-coded here.
        if ("C-4".equals(normCode)) {
            context.put("effective_beds", occupancy);
        }
        BigDecimal totalLpcd = configuredRate(matchedNorm, "totalLpcd", "totalLpcdFormula", context);
        BigDecimal potableLpcd = configuredRate(matchedNorm, "potableLpcd", "potableLpcdFormula", context);
        BigDecimal nonPotableLpcd = configuredRate(matchedNorm, "nonPotableLpcd", "nonPotableLpcdFormula", context);
        BigDecimal contingencyPercentage = configuredNumber(matchedNorm, "contingencyPercentage", BigDecimal.ZERO);

        if (totalLpcd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("totalLpcd/totalLpcdFormula must resolve to a positive value for norm " + normCode);
        }
        if (potableLpcd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("potableLpcd/potableLpcdFormula must resolve to a positive value for norm " + normCode);
        }
        if (nonPotableLpcd.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("nonPotableLpcd cannot be negative for norm " + normCode);
        }
        if (contingencyPercentage.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("contingencyPercentage cannot be negative for norm " + normCode);
        }

        String ifcBasis = resolveIfcBasis(matchedNorm, normCode, context);
        if (StringUtils.isBlank(ifcBasis)) {
            throw new IllegalStateException("ifcCalculationBasis is required for WaterDemandNorms2 code " + normCode);
        }

        BigDecimal contingencyMultiplier = BigDecimal.ONE.add(
                contingencyPercentage.divide(HUNDRED, 8, RoundingMode.HALF_UP));
        // The revised norms define the 12,500 LPD condition against total calculated
        // water demand. Include configured floating/contingency demand in the total
        // used for threshold selection, then apply the same contingency multiplier
        // to whichever IFC basis (total or potable) is selected.
        BigDecimal ifcThresholdLpd = configuredNumber(matchedNorm, "ifcThresholdLpd", IFC_POTABLE_THRESHOLD_LPD);
        if (ifcThresholdLpd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("ifcThresholdLpd must be positive for norm " + normCode);
        }
        BigDecimal opdPotableDemand = BigDecimal.ZERO;
        if ("C-4".equals(normCode)) {
            // OPD occupancy is the higher of 1 person per 1.5 sqm of OPD area
            // or 20 persons per bed. OPD demand is 15 LPCD potable.
            BigDecimal opdByArea = value(context, "opd_area")
                    .divide(new BigDecimal("1.5"), 8, RoundingMode.CEILING);
            BigDecimal opdByBeds = occupancy.multiply(new BigDecimal("20"));
            opdPotableDemand = opdByArea.max(opdByBeds).multiply(new BigDecimal("15"));
        }

        BigDecimal totalDemandBeforeIfcBasis = occupancy.multiply(totalLpcd).add(opdPotableDemand);
        BigDecimal potableDemandBeforeIfcBasis = occupancy.multiply(potableLpcd).add(opdPotableDemand);

        BigDecimal chosenLpcd = totalLpcd;
        boolean potableThresholdApplied = false;
        BigDecimal baseDemand = totalDemandBeforeIfcBasis;
        BigDecimal totalDemandForThreshold = totalDemandBeforeIfcBasis.multiply(contingencyMultiplier);
        if ("POTABLE_ONLY_IF_OVER_12500".equalsIgnoreCase(ifcBasis)
                && totalDemandForThreshold.compareTo(ifcThresholdLpd) > 0) {
            chosenLpcd = potableLpcd;
            potableThresholdApplied = true;
            baseDemand = potableDemandBeforeIfcBasis;
        } else if ("TOTAL_WATER_ALWAYS".equalsIgnoreCase(ifcBasis)) {
            baseDemand = totalDemandBeforeIfcBasis;
        }

        BigDecimal ifcDemand = baseDemand.multiply(contingencyMultiplier)
                .setScale(WSCalculationConstant.RESULT_SCALE, RoundingMode.HALF_UP);

        result.setMatchedNormCode(normCode);
        result.setMatchedNormName(asString(matchedNorm.get("name")));
        result.setFormulaUsed(occupancyFormula);
        result.setRawOccupancy(rawOccupancy);
        result.setCalculationBasisApplied(ifcBasis);
        result.setContextVariables(context);
        result.setCalculatedOccupancy(occupancy);
        result.setChosenLpcd(chosenLpcd);
        result.setBaseDemand(baseDemand);
        result.setContingencyPercentage(contingencyPercentage);
        result.setTotalWaterDemand(ifcDemand);
        result.setStandardTotalLpcd(totalLpcd);
        result.setPotableLpcd(potableLpcd);
        result.setNonPotableLpcd(nonPotableLpcd);
        result.setOpdPotableDemand(opdPotableDemand);
        result.setPotableThresholdApplied(potableThresholdApplied);

        log.info("Revised water-demand strategy applied: strategy={}, normCode={}, occupancy={}, formula={}, "
                        + "totalLpcd={}, potableLpcd={}, nonPotableLpcd={}, demandBeforeBasis={}, basis={}, "
                        + "thresholdApplied={}, ifcDemandLPD={}",
                getClass().getSimpleName(), normCode, occupancy, occupancyFormula, totalLpcd, potableLpcd,
                nonPotableLpcd, totalDemandBeforeIfcBasis, ifcBasis, potableThresholdApplied, ifcDemand);
    }

    @SuppressWarnings("unchecked")
    private String resolveIfcBasis(Map<String, Object> norm, String normCode, Map<String, BigDecimal> context) {
        Object byUseClass = norm.get("ifcCalculationBasisByUseClass");
        if (byUseClass instanceof Map) {
            Map<String, Object> rules = (Map<String, Object>) byUseClass;
            String useClass = value(context, "is_assembly_use").compareTo(BigDecimal.ZERO) > 0
                    ? "ASSEMBLY" : "MERCANTILE";
            Object configured = rules.get(useClass);
            if (configured == null) {
                throw new IllegalStateException("Missing ifcCalculationBasisByUseClass rule '" + useClass
                        + "' for norm " + normCode + ".");
            }
            return configured.toString().trim();
        }
        String configured = asString(norm.get("ifcCalculationBasis"));
        if (StringUtils.isBlank(configured) || "CONDITIONAL_BY_USE_CLASS".equalsIgnoreCase(configured)) {
            throw new IllegalStateException("Norm " + normCode
                    + " requires ifcCalculationBasisByUseClass configuration for conditional IFC rules.");
        }
        return configured;
    }

    @SuppressWarnings("unchecked")
    private void validateC4FacilityPlotAreaRule(Map<String, Object> norm, Map<String, BigDecimal> context) {
        Object rawRules = norm.get("facilityPlotAreaRules");
        if (!(rawRules instanceof Map)) {
            throw new IllegalStateException("C-4 facilityPlotAreaRules must be configured in WaterDemandNorms2.");
        }
        Map<String, Object> rules = (Map<String, Object>) rawRules;
        String facilityType = value(context, "is_hospital_property").compareTo(BigDecimal.ZERO) > 0
                ? "HOSPITAL" : "NURSING_HOME";
        Object rawRule = rules.get(facilityType);
        if (rawRule == null || StringUtils.isBlank(rawRule.toString())) {
            throw new IllegalStateException("C-4 plot-area applicability rule is missing for " + facilityType + ".");
        }
        String condition = rawRule.toString().trim();
        BigDecimal applicable = expressionEvaluator.evaluateExpression("IF(" + condition + ", 1, 0)", context);
        if (applicable.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalStateException("C-4 " + facilityType + " is not applicable for plot area "
                    + value(context, "plot_area") + " sqm. Applicable rule from MDMS: " + condition);
        }
    }

    private void validateRequiredInputs(String normCode, Map<String, BigDecimal> context) {
        switch (normCode) {
            case "A-1":
                requirePositiveInput(context, "total_du", normCode);
                break;
            case "B-1":
                // B-1 occupancy is MAX(built_up_area / 4, declared total strength).
                // A positive built-up area is sufficient to compute the area-based
                // minimum when a property type (e.g. Destitute Home) does not collect
                // staff/student strength. When strength is supplied, the configured
                // MAX formula still uses it whenever it exceeds the area-based value.
                requirePositiveInput(context, "built_up_area", normCode);
                break;
            case "B-2":
            case "B-3":
            case "B-4":
            case "C-3":
                requirePositiveInput(context, "built_up_area", normCode);
                break;
            case "C-1":
                requirePositiveInput(context, "built_up_area", normCode);
                break;
            case "C-2":
                if (value(context, "sanctioned_beds_is_specified").compareTo(BigDecimal.ZERO) > 0) {
                    requirePositiveInput(context, "sanctioned_beds", normCode);
                } else {
                    requirePositiveInput(context, "total_rooms", normCode);
                    requirePositiveInput(context, "built_up_area", normCode);
                }
                break;
            case "C-4":
                requirePositiveInput(context, "sanctioned_beds", normCode);
                requirePositiveInput(context, "built_up_area", normCode);
                requirePositiveInput(context, "plot_area", normCode);
                break;
            default:
                throw new IllegalStateException("Unsupported revised WaterDemandNorms2 code '" + normCode + "'. "
                        + "Only A-1, B-1..B-4 and C-1..C-4 are supported.");
        }
    }

    private void requirePositiveInput(Map<String, BigDecimal> context, String variable, String normCode) {
        if (value(context, variable).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Required WaterDemandNorms2 input '" + variable
                    + "' must be greater than zero for norm " + normCode
                    + ". Verify the application/property payload and MDMS field mapping.");
        }
    }

    private BigDecimal value(Map<String, BigDecimal> context, String variable) {
        BigDecimal value = context.get(variable);
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal configuredRate(Map<String, Object> norm, String valueKey, String formulaKey,
            Map<String, BigDecimal> context) {
        Object rawValue = norm.get(valueKey);
        if (rawValue != null && StringUtils.isNotBlank(rawValue.toString())
                && !"null".equalsIgnoreCase(rawValue.toString().trim())) {
            return asDecimal(rawValue, BigDecimal.ZERO);
        }
        String formula = asString(norm.get(formulaKey));
        return StringUtils.isBlank(formula) ? BigDecimal.ZERO : expressionEvaluator.evaluateExpression(formula, context);
    }

    private BigDecimal configuredNumber(Map<String, Object> norm, String key, BigDecimal defaultValue) {
        Object rawValue = norm.get(key);
        return rawValue == null ? defaultValue : asDecimal(rawValue, defaultValue);
    }

    private BigDecimal asDecimal(Object value, BigDecimal defaultValue) {
        if (value == null || StringUtils.isBlank(value.toString())
                || "null".equalsIgnoreCase(value.toString().trim())) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid numeric value in WaterDemandNorms2 configuration: " + value, ex);
        }
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.isNotBlank(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private String normalizeCode(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.matches("[A-Z][0-9]+")) {
            return normalized.substring(0, 1) + "-" + normalized.substring(1);
        }
        return normalized;
    }
}
