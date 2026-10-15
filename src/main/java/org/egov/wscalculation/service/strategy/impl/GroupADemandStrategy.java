package org.egov.wscalculation.service.strategy.impl;

import org.egov.wscalculation.helper.ExpressionEvaluator;
import org.springframework.stereotype.Component;

/** Strategy for revised Group A: residential demand (norm code A). */
@Component
public class GroupADemandStrategy extends AbstractWaterDemandGroupStrategy {

    public GroupADemandStrategy(ExpressionEvaluator expressionEvaluator) {
        super(expressionEvaluator);
    }

    @Override
    public boolean supports(String subCategoryCode, String parentUsageCode) {
        return subCategoryCode != null && "A".equalsIgnoreCase(subCategoryCode.trim())
                && "RESIDENTIAL".equalsIgnoreCase(parentUsageCode == null ? "" : parentUsageCode.trim());
    }

    @Override
    protected String expectedIfcCalculationBasis() {
        return "TOTAL_WATER_ALWAYS";
    }
}
