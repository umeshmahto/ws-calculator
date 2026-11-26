package org.egov.wscalculation.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ExpressionEvaluatorTest {

    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();

    @Test
    void evaluatesBothShiftStrengthFormula() {
        Map<String, BigDecimal> context = new HashMap<>();
        context.put("built_up_area", new BigDecimal("400"));
        context.put("total_strength_including_students_and_staff_of_both_shifts", new BigDecimal("760"));

        assertEquals(new BigDecimal("760"), evaluator.evaluateExpression(
                "MAX(built_up_area / 4.0, total_strength_including_students_and_staff_of_both_shifts)", context));
    }

    @Test
    void rejectsUnknownMdmsFormulaVariableInsteadOfSilentlyUsingZero() {
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluateExpression("built_up_area / unknown_area", Collections.singletonMap(
                        "built_up_area", new BigDecimal("400"))));
    }

    @Test
    void rejectsDivisionByZero() {
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluateExpression("built_up_area / 0", Collections.singletonMap(
                        "built_up_area", new BigDecimal("400"))));
    }
}
