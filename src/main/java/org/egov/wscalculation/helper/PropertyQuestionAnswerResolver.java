package org.egov.wscalculation.helper;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves property-specific answers using stable questionCode values.
 *
 * Persisted payload contract:
 * additionalDetails.questions = [
 *   { "questionCode": "numberOfDwellingUnits", "answer": "2" }
 * ]
 *
 * Display text is deliberately not used as an identifier. This allows the UI to
 * localize questionCode independently without changing calculation behaviour.
 * Legacy sibling fields (numberOfDwellingUnits, numberOfBeds, numberOfRooms,
 * numberOfStudents, numberOfStaff, etc.) are deliberately ignored.
 */
public final class PropertyQuestionAnswerResolver {

    public static final String NUMBER_OF_DWELLING_UNITS = "numberOfDwellingUnits";
    public static final String TOTAL_STRENGTH_INCLUDING_STAFF_AND_STUDENTS = "totalStrengthIncludingStaffAndStudents";
    public static final String TOTAL_STRENGTH = "totalStrength";
    public static final String NUMBER_OF_BEDS = "numberOfBeds";
    public static final String NUMBER_OF_ROOMS = "numberOfRooms";
    public static final String PREMISES_HAS_RESIDENTIAL_UNITS = "PREMISES_HAS_RESIDENTIAL_UNITS";

    private PropertyQuestionAnswerResolver() {
    }

    /**
     * Returns all supported numeric answers as normalized calculation variables.
     * Unknown question codes are ignored here (they may be non-calculation form
     * questions); configured questions are separately validated by
     * validateRequiredQuestions.
     */
    public static Map<String, BigDecimal> extractNumericAnswers(Map<String, Object> additionalDetails) {
        Map<String, String> answers = extractAnswers(additionalDetails);
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : answers.entrySet()) {
            String variable = calculationVariable(entry.getKey());
            if (variable == null) {
                continue;
            }
            BigDecimal value = parseNumericAnswer(entry.getValue(), entry.getKey(), variable);
            values.put(variable, value);
        }
        return values;
    }

    /** Returns the raw answer for a stable questionCode, or null when absent. */
    public static String getAnswer(Map<String, Object> additionalDetails, String questionCode) {
        if (questionCode == null || questionCode.trim().isEmpty()) {
            return null;
        }
        return extractAnswers(additionalDetails).get(normalizeCode(questionCode));
    }

    /** Returns a numeric answer for a stable questionCode, or null when absent. */
    public static BigDecimal getNumericAnswer(Map<String, Object> additionalDetails, String questionCode) {
        String answer = getAnswer(additionalDetails, questionCode);
        if (answer == null || answer.trim().isEmpty()) {
            return null;
        }
        String variable = calculationVariable(normalizeCode(questionCode));
        return parseNumericAnswer(answer, questionCode, variable == null ? questionCode : variable);
    }

    /**
     * Every code listed by PropertyType2.questions is a required form answer.
     * This prevents a missing answer from silently becoming zero in a formula.
     */
    public static void validateRequiredQuestions(Map<String, Object> additionalDetails,
            Object configuredQuestions, String propertyTypeCode) {
        if (!(configuredQuestions instanceof Iterable<?>)) {
            return;
        }
        for (Object configuredQuestion : (Iterable<?>) configuredQuestions) {
            String questionCode = questionCodeFromDefinition(configuredQuestion);
            if (questionCode == null || questionCode.trim().isEmpty()) {
                throw new IllegalStateException("PropertyType2.questions contains an empty/non-coded question for property type '"
                        + propertyTypeCode + "'. Store stable question codes, not display labels.");
            }
            String answer = getAnswer(additionalDetails, questionCode);
            if (answer == null || answer.trim().isEmpty()) {
                throw new IllegalStateException("Required property question answer is missing. propertyType='"
                        + propertyTypeCode + "', questionCode='" + questionCode + "'.");
            }
            String variable = calculationVariable(questionCode);
            if (variable != null) {
                parseNumericAnswer(answer, questionCode, variable);
            }
        }
    }

    /**
     * Validates conditional Domestic/Non-Domestic selection against the
     * questionCode-driven categoryTypeSelectionRule in PropertyType2.
     */
    @SuppressWarnings("unchecked")
    public static void validateCategoryTypeSelection(Map<String, Object> additionalDetails,
            Map<String, Object> propertyTypeEntry, String selectedCategoryType) {
        if (propertyTypeEntry == null || !(propertyTypeEntry.get("categoryTypeSelectionRule") instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> selectionRule = (Map<String, Object>) propertyTypeEntry.get("categoryTypeSelectionRule");
        Object rawCode = selectionRule.get("questionCode");
        String questionCode = rawCode == null ? null : rawCode.toString().trim();
        if (questionCode == null || questionCode.isEmpty()) {
            throw new IllegalStateException("PropertyType2.categoryTypeSelectionRule is missing questionCode for property type '"
                    + propertyTypeEntry.get("code") + "'.");
        }
        String answer = getAnswer(additionalDetails, questionCode);
        if (answer == null || answer.trim().isEmpty()) {
            throw new IllegalStateException("Required category-selection answer is missing. propertyType='"
                    + propertyTypeEntry.get("code") + "', questionCode='" + questionCode + "'.");
        }

        Object rawRules = selectionRule.get("rules");
        if (!(rawRules instanceof Iterable<?>)) {
            throw new IllegalStateException("PropertyType2.categoryTypeSelectionRule.rules is missing for questionCode '"
                    + questionCode + "'.");
        }
        String expectedCategoryType = null;
        for (Object rawRule : (Iterable<?>) rawRules) {
            if (!(rawRule instanceof Map<?, ?>)) continue;
            Map<?, ?> rule = (Map<?, ?>) rawRule;
            Object ruleAnswer = rule.get("answer");
            if (ruleAnswer != null && ruleAnswer.toString().trim().equalsIgnoreCase(answer.trim())) {
                Object category = rule.get("categoryType");
                expectedCategoryType = category == null ? null : category.toString().trim();
                break;
            }
        }
        if (expectedCategoryType == null) {
            throw new IllegalStateException("Answer '" + answer + "' is not configured for questionCode '"
                    + questionCode + "' on property type '" + propertyTypeEntry.get("code") + "'.");
        }
        if (selectedCategoryType == null || !normalizeCategory(selectedCategoryType).equals(normalizeCategory(expectedCategoryType))) {
            throw new IllegalStateException("categoryType does not match the question answer. propertyType='"
                    + propertyTypeEntry.get("code") + "', questionCode='" + questionCode + "', answer='" + answer
                    + "', expectedCategoryType='" + expectedCategoryType + "', selectedCategoryType='"
                    + selectedCategoryType + "'.");
        }
    }

    /**
     * Builds a unique questionCode -> answer map. A label-only legacy item is
     * rejected instead of attempting language-dependent matching.
     */
    private static Map<String, String> extractAnswers(Map<String, Object> additionalDetails) {
        if (additionalDetails == null) {
            return Collections.emptyMap();
        }
        Object rawQuestions = additionalDetails.get("questions");
        if (rawQuestions == null) {
            return Collections.emptyMap();
        }
        if (!(rawQuestions instanceof Iterable<?>)) {
            throw new IllegalArgumentException("additionalDetails.questions must be an array of questionCode/answer objects.");
        }

        Map<String, String> answers = new LinkedHashMap<>();
        for (Object rawQuestion : (Iterable<?>) rawQuestions) {
            if (!(rawQuestion instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("Each additionalDetails.questions item must contain questionCode and answer.");
            }
            Map<?, ?> item = (Map<?, ?>) rawQuestion;
            Object rawCode = item.get("questionCode");
            if (rawCode == null || rawCode.toString().trim().isEmpty()) {
                throw new IllegalArgumentException("Each property question answer must contain questionCode. Display labels are not identifiers.");
            }
            String code = normalizeCode(rawCode.toString());
            Object rawAnswer = item.get("answer");
            String answer = rawAnswer == null ? null : rawAnswer.toString().trim();
            String previous = answers.putIfAbsent(code, answer);
            if (previous != null && answer != null && !previous.equals(answer)) {
                throw new IllegalArgumentException("Conflicting answers were supplied for questionCode '" + rawCode + "'.");
            }
        }
        return answers;
    }

    private static String questionCodeFromDefinition(Object definition) {
        if (definition == null) return null;
        if (definition instanceof Map<?, ?>) {
            Object code = ((Map<?, ?>) definition).get("questionCode");
            return code == null ? null : code.toString().trim();
        }
        // Current MDMS schema intentionally stores an array of code strings.
        return definition.toString().trim();
    }

    private static String calculationVariable(String normalizedCode) {
        switch (normalizeCode(normalizedCode)) {
            case "NUMBEROFDWELLINGUNITS":
                return "total_du";
            case "NUMBEROFBEDS":
                return "sanctioned_beds";
            case "NUMBEROFROOMS":
                return "total_rooms";
            case "TOTALSTRENGTHINCLUDINGSTAFFANDSTUDENTS":
                return "total_strength_including_staff_and_students";
            case "TOTALSTRENGTH":
                return "total_strength";
            default:
                return null;
        }
    }

    private static BigDecimal parseNumericAnswer(String rawAnswer, String questionCode, String variable) {
        String raw = rawAnswer == null ? "" : rawAnswer.trim().replace(",", "");
        final BigDecimal parsed;
        try {
            parsed = new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Answer for questionCode '" + questionCode
                    + "' must be numeric; received '" + rawAnswer + "'.", ex);
        }
        if (parsed.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Answer for questionCode '" + questionCode + "' cannot be negative.");
        }
        if ("opd_area".equals(variable)) {
            return parsed;
        }
        if (parsed.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Answer for count questionCode '" + questionCode
                    + "' must be a whole number; received '" + rawAnswer + "'.");
        }
        return parsed;
    }

    private static String normalizeCode(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private static String normalizeCategory(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
    }
}
