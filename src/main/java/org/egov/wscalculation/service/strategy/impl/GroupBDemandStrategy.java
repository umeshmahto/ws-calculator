package org.egov.wscalculation.service.strategy.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.egov.wscalculation.helper.ExpressionEvaluator;
import org.springframework.stereotype.Component;

/** Strategy for revised Group B norms B-1 through B-4. */
@Component
public class GroupBDemandStrategy extends AbstractWaterDemandGroupStrategy {

    private static final Set<String> SUPPORTED_CODES = new HashSet<>(Arrays.asList("B-1", "B-2", "B-3", "B-4"));

    public GroupBDemandStrategy(ExpressionEvaluator expressionEvaluator) {
        super(expressionEvaluator);
    }

    @Override
    public boolean supports(String subCategoryCode, String parentUsageCode) {
        return subCategoryCode != null && SUPPORTED_CODES.contains(normalize(subCategoryCode))
                && "INSTITUTIONAL".equalsIgnoreCase(parentUsageCode == null ? "" : parentUsageCode.trim());
    }

    @Override
    protected String expectedIfcCalculationBasis() {
        return "POTABLE_ONLY_IF_OVER_12500";
    }

    private String normalize(String code) {
        String value = code.trim().toUpperCase(java.util.Locale.ROOT);
        if (value.matches("B[0-9]+")) {
            return value.substring(0, 1) + "-" + value.substring(1);
        }
        return value;
    }
}
