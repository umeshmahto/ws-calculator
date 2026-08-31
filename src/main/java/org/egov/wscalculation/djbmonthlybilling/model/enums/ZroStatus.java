package org.egov.wscalculation.djbmonthlybilling.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ZroStatus {
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED");

    private final String value;

    ZroStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return value;
    }

    @JsonCreator
    public static ZroStatus fromValue(String text) {
        for (ZroStatus value : values()) {
            if (value.value.equalsIgnoreCase(text)) {
                return value;
            }
        }
        return null;
    }
}
