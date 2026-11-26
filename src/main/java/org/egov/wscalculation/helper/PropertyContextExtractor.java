package org.egov.wscalculation.helper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.egov.wscalculation.web.models.Property;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PropertyContextExtractor {

    private final ObjectMapper mapper;

    public Map<String, BigDecimal> extractContextVariables(Property property) {
        Map<String, BigDecimal> vars = new HashMap<>();
        Map<String, Object> details = getPropertyAdditionalDetailsMap(property);

        BigDecimal farArea = getBigDecimalFromMap(details, "farArea");
        if (farArea.compareTo(BigDecimal.ZERO) == 0) {
            farArea = getBigDecimalFromMap(details, "far_area");
        }

        BigDecimal builtUpArea = getBigDecimalFromMap(details, "builtUpArea");
        if (builtUpArea.compareTo(BigDecimal.ZERO) == 0) {
            builtUpArea = getBigDecimalFromMap(details, "carpetArea");
        }
        if (builtUpArea.compareTo(BigDecimal.ZERO) == 0 && details.get("coveredArea") != null) {
            builtUpArea = parseBigDecimal(details.get("coveredArea"));
        }
        if (builtUpArea.compareTo(BigDecimal.ZERO) == 0 && property.getSuperBuiltUpArea() != null) {
            builtUpArea = property.getSuperBuiltUpArea();
        }
        if (builtUpArea.compareTo(BigDecimal.ZERO) <= 0) {
            builtUpArea = sumActiveUnitBuiltUpArea(property);
        }

        if (farArea.compareTo(BigDecimal.ZERO) == 0) {
            farArea = builtUpArea;
        }

        BigDecimal plotArea = property.getLandArea() != null ? BigDecimal.valueOf(property.getLandArea()) : getBigDecimalFromMap(details, "plotArea");

        // All occupancy values are sourced from questions[] by stable questionCode.
        // Legacy sibling fields such as numberOfDwellingUnits/numberOfBeds are ignored.
        Map<String, BigDecimal> questionAnswers = PropertyQuestionAnswerResolver.extractNumericAnswers(details);
        BigDecimal duCount = questionAnswers.getOrDefault("total_du", BigDecimal.ZERO);
        BigDecimal bedsCount = questionAnswers.getOrDefault("sanctioned_beds", BigDecimal.ZERO);
        BigDecimal roomsCount = questionAnswers.getOrDefault("total_rooms", BigDecimal.ZERO);
        BigDecimal studentsCount = questionAnswers.getOrDefault("total_students", BigDecimal.ZERO);
        BigDecimal seatsCount = questionAnswers.getOrDefault("total_seats", BigDecimal.ZERO);
        BigDecimal staffCount = questionAnswers.getOrDefault("total_staff", BigDecimal.ZERO);
        BigDecimal combinedStrength = questionAnswers.getOrDefault("total_strength_including_staff_and_students", BigDecimal.ZERO);

        BigDecimal sqArea = getBigDecimalFromMap(details, "servantQuarterArea");
        if (sqArea.compareTo(BigDecimal.ZERO) == 0) {
            sqArea = getBigDecimalFromMap(details, "sqArea");
        }

        vars.put("far_area", farArea);
        vars.put("built_up_area", builtUpArea);
        vars.put("carpet_area", builtUpArea);
        vars.put("covered_area", builtUpArea);
        vars.put("plot_area", plotArea);
        vars.put("total_du", duCount);
        vars.put("dwelling_units", duCount);
        vars.put("sanctioned_beds", bedsCount);
        vars.put("specified_beds", bedsCount);
        vars.put("total_beds", bedsCount);
        vars.put("total_rooms", roomsCount);
        vars.put("highest_shift_strength", combinedStrength);
        vars.put("total_students", studentsCount);
        vars.put("total_seats", seatsCount);
        vars.put("total_staff", staffCount);
        vars.put("sq_area", sqArea);

        return vars;
    }


    /**
     * Builds normalized context variables for the configuration-driven
     * WaterDemandNorms2 schema. Unlike the legacy context, built_up_area is never
     * substituted with carpet area or FAR area alone because the revised norms
     * explicitly require built-up area (FAR + non-FAR area).
     */
    public Map<String, BigDecimal> extractContextVariablesForNormsV2(Property property) {
        Map<String, BigDecimal> vars = new HashMap<>();
        Map<String, Object> details = getPropertyAdditionalDetailsMap(property);

        BigDecimal farArea = firstPositive(details, "farArea", "far_area", "farBuiltUpArea");
        BigDecimal nonFarArea = firstPositive(details, "nonFarArea", "nonFARArea", "nonFarBuiltUpArea", "non_far_area");
        BigDecimal builtUpArea = firstPositive(details, "builtUpArea", "built_up_area", "totalBuiltUpArea", "totalBuiltupArea");
        if (builtUpArea.compareTo(BigDecimal.ZERO) <= 0
                && hasAnyKey(details, "farArea", "far_area", "farBuiltUpArea")
                && hasAnyKey(details, "nonFarArea", "nonFARArea", "nonFarBuiltUpArea", "non_far_area")) {
            builtUpArea = farArea.add(nonFarArea);
        }
        if (builtUpArea.compareTo(BigDecimal.ZERO) <= 0 && property != null && property.getSuperBuiltUpArea() != null) {
            builtUpArea = property.getSuperBuiltUpArea();
        }
        if (builtUpArea.compareTo(BigDecimal.ZERO) <= 0) {
            builtUpArea = firstPositive(details, "coveredArea", "covered_area");
        }
        if (builtUpArea.compareTo(BigDecimal.ZERO) <= 0) {
            builtUpArea = sumActiveUnitBuiltUpArea(property);
        }

        BigDecimal carpetArea = firstPositive(details, "carpetArea", "carpet_area");
        BigDecimal plotArea = property != null && property.getLandArea() != null
                ? BigDecimal.valueOf(property.getLandArea())
                : firstPositive(details, "plotArea", "plot_area", "landArea", "land_area");
        // Question answers are the only source for occupancy/capacity inputs.
        // Do not read numberOfDwellingUnits, numberOfBeds, numberOfRooms, students
        // or staff from sibling additionalDetails fields.
        Map<String, BigDecimal> questionAnswers = PropertyQuestionAnswerResolver.extractNumericAnswers(details);
        BigDecimal duCount = questionAnswers.getOrDefault("total_du", BigDecimal.ZERO);
        BigDecimal bedsCount = questionAnswers.getOrDefault("sanctioned_beds", BigDecimal.ZERO);
        BigDecimal roomsCount = questionAnswers.getOrDefault("total_rooms", BigDecimal.ZERO);
        BigDecimal studentsCount = questionAnswers.getOrDefault("total_students", BigDecimal.ZERO);
        BigDecimal staffCount = questionAnswers.getOrDefault("total_staff", BigDecimal.ZERO);
        BigDecimal seatsCount = questionAnswers.getOrDefault("total_seats", BigDecimal.ZERO);
        BigDecimal servantQuarterArea = firstPositive(details, "servantQuarterArea", "sqArea", "sq_area");

        BigDecimal totalStrengthIncludingStaffAndStudents = questionAnswers.getOrDefault(
                "total_strength_including_staff_and_students", BigDecimal.ZERO);
        BigDecimal totalStrength = questionAnswers.getOrDefault("total_strength", BigDecimal.ZERO);

        vars.put("far_area", farArea);
        vars.put("non_far_area", nonFarArea);
        vars.put("built_up_area", builtUpArea);
        vars.put("total_built_up_area", builtUpArea);
        vars.put("carpet_area", carpetArea);
        vars.put("covered_area", firstPositive(details, "coveredArea", "covered_area").max(builtUpArea));
        vars.put("plot_area", plotArea);
        vars.put("land_area", plotArea);
        vars.put("total_du", duCount);
        vars.put("dwelling_units", duCount);
        vars.put("sanctioned_beds", bedsCount);
        vars.put("specified_beds", bedsCount);
        vars.put("total_beds", bedsCount);
        vars.put("sanctioned_beds_is_specified", bedsCount.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.ONE : BigDecimal.ZERO);
        vars.put("total_rooms", roomsCount);
        vars.put("number_of_rooms", roomsCount);
        vars.put("highest_shift_strength", totalStrengthIncludingStaffAndStudents);
        vars.put("total_students", studentsCount);
        vars.put("total_staff", staffCount);
        vars.put("total_strength_including_staff_and_students", totalStrengthIncludingStaffAndStudents);
        vars.put("total_strength_including_students_and_staff_of_both_shifts",totalStrengthIncludingStaffAndStudents);
        vars.put("total_strength", totalStrength);
        vars.put("total_seats", seatsCount);
        vars.put("sq_area", servantQuarterArea);

        // Revised DJB C-4 and B-2 use-class inputs. The property type itself is
        // already selected in the common property form; these are derived flags,
        // not additional citizen questions.
        boolean opdAreaProvided = questionAnswers.containsKey("opd_area")
                || (details.containsKey("opdArea") && details.get("opdArea") != null)
                || (details.containsKey("opd_area") && details.get("opd_area") != null);
        BigDecimal questionOpdArea = questionAnswers.get("opd_area");
        BigDecimal detailOpdArea = null;
        Object rawOpdArea = details.containsKey("opdArea") ? details.get("opdArea") : details.get("opd_area");
        if (rawOpdArea != null) {
            try {
                detailOpdArea = new BigDecimal(rawOpdArea.toString().trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("additionalDetails.opdArea must be numeric; received '"
                        + rawOpdArea + "'.", ex);
            }
            if (detailOpdArea.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("additionalDetails.opdArea cannot be negative.");
            }
        }
        if (questionOpdArea != null && detailOpdArea != null
                && questionOpdArea.compareTo(detailOpdArea) != 0) {
            throw new IllegalArgumentException("Conflicting OPD area values were supplied in questions[opdArea] and additionalDetails.opdArea.");
        }
        BigDecimal opdArea = questionOpdArea != null ? questionOpdArea
                : (detailOpdArea == null ? BigDecimal.ZERO : detailOpdArea);
        vars.put("opd_area", opdArea);
        vars.put("opd_area_provided", opdAreaProvided ? BigDecimal.ONE : BigDecimal.ZERO);
        // These classification flags are populated from PropertyType2 MDMS by
        // WaterDemandCalculator; do not infer facility/use classes from labels.
        vars.put("is_assembly_use", BigDecimal.ZERO);
        vars.put("is_hospital_property", BigDecimal.ZERO);

        return vars;
    }

    /**
     * Some Property payloads keep construction areas only under units[].constructionDetail.
     * Use those values only as a fallback, so explicitly captured aggregate area fields remain
     * authoritative and unit areas are not double-counted.
     */
    private BigDecimal sumActiveUnitBuiltUpArea(Property property) {
        if (property == null || property.getUnits() == null || property.getUnits().isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (org.egov.wscalculation.web.models.Unit unit : property.getUnits()) {
            if (unit == null || Boolean.FALSE.equals(unit.getActive()) || unit.getConstructionDetail() == null) {
                continue;
            }
            BigDecimal area = unit.getConstructionDetail().getBuiltUpArea();
            if (area == null || area.compareTo(BigDecimal.ZERO) <= 0) {
                area = unit.getConstructionDetail().getSuperBuiltUpArea();
            }
            if (area == null || area.compareTo(BigDecimal.ZERO) <= 0) {
                area = unit.getConstructionDetail().getCarpetArea();
            }
            if (area != null && area.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(area);
            }
        }
        return total;
    }

    private boolean hasAnyKey(Map<String, Object> details, String... keys) {
        if (details == null) {
            return false;
        }
        for (String key : keys) {
            if (details.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal firstPositive(Map<String, Object> details, String... keys) {
        if (details == null) {
            return BigDecimal.ZERO;
        }
        for (String key : keys) {
            BigDecimal value = getBigDecimalFromMap(details, key);
            if (value.compareTo(BigDecimal.ZERO) > 0) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }

    private String firstNonBlank(Map<String, Object> details, String... keys) {
        if (details == null) return null;
        for (String key : keys) {
            Object value = details.get(key);
            if (value != null && !value.toString().trim().isEmpty()) return value.toString().trim();
        }
        return null;
    }

    public Map<String, Object> getPropertyAdditionalDetailsMap(Property property) {
        if (property == null || property.getAdditionalDetails() == null) {
            return new HashMap<>();
        }
        return mapper.convertValue(property.getAdditionalDetails(), new TypeReference<Map<String, Object>>() {});
    }

    public BigDecimal getBigDecimalFromMap(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return BigDecimal.ZERO;
        }
        return parseBigDecimal(map.get(key));
    }

    public BigDecimal parseBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(val.toString().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}