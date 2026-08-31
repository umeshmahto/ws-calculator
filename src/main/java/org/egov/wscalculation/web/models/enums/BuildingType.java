package org.egov.wscalculation.web.models.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BuildingType {

    /* ---------------- Residential ---------------- */
    INDIVIDUAL_HOUSE("INDIVIDUAL_HOUSE"),
    EWS_FLAT("EWS_FLAT"),
    JANTA_FLAT("JANTA_FLAT"),
    LIG_FLAT("LIG_FLAT"),
    MIG_FLAT("MIG_FLAT"),
    HIG_FLAT("HIG_FLAT"),
    GROUP_HOUSING("GROUP_HOUSING"),
    APARTMENT("APARTMENT"),
    HOSTEL("HOSTEL"),
    SERVANT_QUARTER("SERVANT_QUARTER"),
    
    /* ---------------- Educational ---------------- */
    SCHOOL("SCHOOL"),
    COLLEGE("COLLEGE"),
    UNIVERSITY("UNIVERSITY"),

    /* ---------------- Medical ---------------- */
    HOSPITAL("HOSPITAL"),
    NURSING_HOME("NURSING_HOME"),
    SANATORIUM("SANATORIUM"),

    /* ---------------- Hospitality ---------------- */
    HOTEL("HOTEL"),
    GUEST_HOUSE("GUEST_HOUSE"),

    /* ---------------- Business ---------------- */
    OFFICE("OFFICE"),
    BANK("BANK"),
    BUSINESS_BUILDING("BUSINESS_BUILDING"),

    /* ---------------- Commercial ---------------- */
    SHOP("SHOP"),
    SHOPPING_MALL("SHOPPING_MALL"),
    RESTAURANT("RESTAURANT"),
    CINEMA("CINEMA"),
    MULTIPLEX("MULTIPLEX"),

    /* ---------------- Industrial ---------------- */
    INDUSTRIAL("INDUSTRIAL"),

    /* ---------------- Storage ---------------- */
    GODOWN("GODOWN"),
    WAREHOUSE("WAREHOUSE"),
    STORAGE("STORAGE"),

    /* ---------------- Assembly ---------------- */
    AUDITORIUM("AUDITORIUM"),
    BANQUET_HALL("BANQUET_HALL"),
    COMMUNITY_HALL("COMMUNITY_HALL"),

    /* ---------------- Hazardous ---------------- */
    HAZARDOUS("HAZARDOUS"),

    /* ---------------- Fallback ---------------- */
    UNKNOWN("UNKNOWN");

    private final String value;

    BuildingType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static BuildingType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return UNKNOWN;
        }

        for (BuildingType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }

        return UNKNOWN;
    }
}