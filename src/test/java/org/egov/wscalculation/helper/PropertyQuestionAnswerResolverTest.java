package org.egov.wscalculation.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PropertyQuestionAnswerResolverTest {

    @Test
    void resolvesDwellingUnitsOnlyFromQuestionCodeAnswer() {
        Map<String, Object> details = details(
                question("numberOfDwellingUnits", "2"));
        // Deprecated field is deliberately different and must be ignored.
        details.put("numberOfDwellingUnits", "99");

        Map<String, BigDecimal> values = PropertyQuestionAnswerResolver.extractNumericAnswers(details);

        assertEquals(new BigDecimal("2"), values.get("total_du"));
    }

    @Test
    void doesNotResolveAnAnswerFromLocalizedDisplayText() {
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("question", "Number of dwelling units");
        legacy.put("answer", "2");
        Map<String, Object> details = details(legacy);

        assertThrows(IllegalArgumentException.class,
                () -> PropertyQuestionAnswerResolver.extractNumericAnswers(details));
    }

    @Test
    void mapsBedsRoomsAndSchoolStrengthByStableCodes() {
        Map<String, Object> details = details(
                question("numberOfBeds", "30"),
                question("numberOfRooms", "12"),
                question("totalStrengthIncludingStaffAndStudents", "250"),
                question("totalStrength", "75"));

        Map<String, BigDecimal> values = PropertyQuestionAnswerResolver.extractNumericAnswers(details);

        assertEquals(new BigDecimal("30"), values.get("sanctioned_beds"));
        assertEquals(new BigDecimal("12"), values.get("total_rooms"));
        assertEquals(new BigDecimal("250"), values.get("total_strength_including_staff_and_students"));
        assertEquals(new BigDecimal("75"), values.get("total_strength"));
    }

    @Test
    void configuredQuestionMustHaveAnAnswer() {
        Map<String, Object> details = details(question("numberOfRooms", "10"));
        List<String> configured = Arrays.asList("numberOfDwellingUnits");

        assertThrows(IllegalStateException.class,
                () -> PropertyQuestionAnswerResolver.validateRequiredQuestions(details, configured, "ApartmentComplex"));
    }

    @Test
    void categorySelectionIsResolvedByQuestionCodeAndAnswer() {
        Map<String, Object> details = details(question("PREMISES_HAS_RESIDENTIAL_UNITS", "NO"));
        details.put("categoryType", "Non-Domestic");

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("questionCode", "PREMISES_HAS_RESIDENTIAL_UNITS");
        rule.put("rules", Arrays.asList(
                rule("YES", "Domestic"), rule("NO", "Non-Domestic")));
        Map<String, Object> propertyType = new LinkedHashMap<>();
        propertyType.put("code", "KiranaGroceryShop");
        propertyType.put("categoryTypeSelectionRule", rule);

        PropertyQuestionAnswerResolver.validateCategoryTypeSelection(details, propertyType, "Non-Domestic");
        assertThrows(IllegalStateException.class,
                () -> PropertyQuestionAnswerResolver.validateCategoryTypeSelection(details, propertyType, "Domestic"));
    }

    private Map<String, Object> details(Map<String, Object>... questionAnswers) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("questions", Arrays.asList(questionAnswers));
        return details;
    }

    private Map<String, Object> question(String code, String answer) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("questionCode", code);
        question.put("answer", answer);
        return question;
    }

    private Map<String, Object> rule(String answer, String categoryType) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("answer", answer);
        rule.put("categoryType", categoryType);
        return rule;
    }
}
