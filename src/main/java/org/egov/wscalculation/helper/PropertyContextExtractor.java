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