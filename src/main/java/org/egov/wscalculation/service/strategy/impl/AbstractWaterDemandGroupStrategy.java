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

        String occupancyFormula = asString(matchedNorm.get("minOccupancyFormula"));
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

        String ifcBasis = asString(matchedNorm.get("ifcCalculationBasis"));
        String expectedBasis = expectedIfcCalculationBasis();
        if (StringUtils.isBlank(ifcBasis)) {
            throw new IllegalStateException("ifcCalculationBasis is required for WaterDemandNorms2 code " + normCode);
        }
        if (!expectedBasis.equalsIgnoreCase(ifcBasis)) {
            throw new IllegalStateException("Invalid ifcCalculationBasis '" + ifcBasis + "' for norm " + normCode
                    + ". Expected '" + expectedBasis + "' according to the revised DJB norms.");
        }

        BigDecimal contingencyMultiplier = BigDecimal.ONE.add(
                contingencyPercentage.divide(HUNDRED, 8, RoundingMode.HALF_UP));
        BigDecimal totalDemandBeforeIfcBasis = occupancy.multiply(totalLpcd).multiply(contingencyMultiplier);

        BigDecimal chosenLpcd = totalLpcd;
        boolean potableThresholdApplied = false;
        if ("POTABLE_ONLY_IF_OVER_12500".equalsIgnoreCase(ifcBasis)
                && totalDemandBeforeIfcBasis.compareTo(IFC_POTABLE_THRESHOLD_LPD) > 0) {
            chosenLpcd = potableLpcd;
            potableThresholdApplied = true;
        }

        BigDecimal baseDemand = occupancy.multiply(chosenLpcd);
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
        result.setPotableThresholdApplied(potableThresholdApplied);

        log.info("Revised water-demand strategy applied: strategy={}, normCode={}, occupancy={}, formula={}, "
                        + "totalLpcd={}, potableLpcd={}, nonPotableLpcd={}, demandBeforeBasis={}, basis={}, "
                        + "thresholdApplied={}, ifcDemandLPD={}",
                getClass().getSimpleName(), normCode, occupancy, occupancyFormula, totalLpcd, potableLpcd,
                nonPotableLpcd, totalDemandBeforeIfcBasis, ifcBasis, potableThresholdApplied, ifcDemand);
    }

    private void validateRequiredInputs(String normCode, Map<String, BigDecimal> context) {
        switch (normCode) {
            case "A":
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
                break;
            default:
                throw new IllegalStateException("Unsupported revised WaterDemandNorms2 code '" + normCode + "'. "
                        + "Only A, B-1..B-4 and C-1..C-4 are supported.");
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
