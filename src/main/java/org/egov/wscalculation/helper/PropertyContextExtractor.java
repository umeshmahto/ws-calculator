package org.egov.wscalculation.helper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.egov.wscalculation.constants.WSCalculationConstant;
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
            builtUpArea = BigDecimal.valueOf(property.getSuperBuiltUpArea().doubleValue());
        }

        if (farArea.compareTo(BigDecimal.ZERO) == 0) {
            farArea = builtUpArea;
        }

        BigDecimal plotArea = property.getLandArea() != null ? BigDecimal.valueOf(property.getLandArea()) : getBigDecimalFromMap(details, "plotArea");

        BigDecimal duCount = getBigDecimalFromMap(details, WSCalculationConstant.NUMBER_OF_DWELLING_UNITS);
        if (duCount.compareTo(BigDecimal.ZERO) == 0) {
            duCount = getBigDecimalFromMap(details, "numberOfDwellingUnits");
        }

        BigDecimal bedsCount = getBigDecimalFromMap(details, "sanctioned_beds");
        if (bedsCount.compareTo(BigDecimal.ZERO) == 0) {
            bedsCount = getBigDecimalFromMap(details, "sanctionedBeds");
        }
        if (bedsCount.compareTo(BigDecimal.ZERO) == 0) {
            bedsCount = getBigDecimalFromMap(details, "noOfBeds");
        }
        if (bedsCount.compareTo(BigDecimal.ZERO) == 0) {
            bedsCount = getBigDecimalFromMap(details, "numberOfBeds");
        }

        BigDecimal roomsCount = getBigDecimalFromMap(details, "noOfRooms");
        if (roomsCount.compareTo(BigDecimal.ZERO) == 0) {
            roomsCount = getBigDecimalFromMap(details, "numberOfRooms");
        }

        BigDecimal studentsCount = getBigDecimalFromMap(details, "numberOfStudents");
        if (studentsCount.compareTo(BigDecimal.ZERO) == 0) {
            studentsCount = getBigDecimalFromMap(details, "noOfStudents");
        }
        
        BigDecimal seatsCount = getBigDecimalFromMap(details, "noOfSeats");
        if (seatsCount.compareTo(BigDecimal.ZERO) == 0) {
            seatsCount = getBigDecimalFromMap(details, "numberOfSeats");
        }

        BigDecimal staffCount = getBigDecimalFromMap(details, "noOfStaff");
        if (staffCount.compareTo(BigDecimal.ZERO) == 0) {
            staffCount = getBigDecimalFromMap(details, "numberOfStaff");
        }

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
        vars.put("highest_shift_strength", studentsCount);
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

        BigDecimal carpetArea = firstPositive(details, "carpetArea", "carpet_area");
        BigDecimal plotArea = property != null && property.getLandArea() != null
                ? BigDecimal.valueOf(property.getLandArea())
                : firstPositive(details, "plotArea", "plot_area", "landArea", "land_area");
        BigDecimal duCount = firstPositive(details, WSCalculationConstant.NUMBER_OF_DWELLING_UNITS,
                "numberOfDwellingUnits", "totalDU", "total_du", "dwellingUnits");

        BigDecimal bedsCount = firstPositive(details, "sanctioned_beds", "sanctionedBeds", "numberOfBeds",
                "noOfBeds", "totalBeds", "total_beds", "beds");
        BigDecimal roomsCount = firstPositive(details, "noOfRooms", "numberOfRooms", "totalRooms", "total_rooms");
        BigDecimal studentsCount = firstPositive(details, "numberOfStudents", "noOfStudents", "totalStudents", "total_students");
        BigDecimal staffCount = firstPositive(details, "numberOfStaff", "noOfStaff", "totalStaff", "total_staff");
        BigDecimal seatsCount = firstPositive(details, "noOfSeats", "numberOfSeats", "totalSeats", "total_seats");
        BigDecimal servantQuarterArea = firstPositive(details, "servantQuarterArea", "sqArea", "sq_area");

        BigDecimal totalStrength = firstPositive(details, "totalStrengthIncludingStaffAndStudents",
                "total_strength_including_staff_and_students", "totalStrengthIncludingBothShifts",
                "totalStudentsAndStaff", "total_students_and_staff", "doubleShiftTotalStrength", "totalStrength");
        if (totalStrength.compareTo(BigDecimal.ZERO) <= 0) {
            totalStrength = studentsCount.add(staffCount);
        }

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
        vars.put("highest_shift_strength", studentsCount);
        vars.put("total_students", studentsCount);
        vars.put("total_staff", staffCount);
        vars.put("total_strength_including_staff_and_students", totalStrength);
        vars.put("total_strength", totalStrength);
        vars.put("total_seats", seatsCount);
        vars.put("sq_area", servantQuarterArea);

        return vars;
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