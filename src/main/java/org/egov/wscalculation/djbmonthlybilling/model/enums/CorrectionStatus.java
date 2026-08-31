package org.egov.wscalculation.djbmonthlybilling.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CorrectionStatus {
    NOT_REQUIRED("NOT_REQUIRED"),
    PENDING("PENDING"),
    IN_PROGRESS("IN_PROGRESS"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED");

    private final String value;

    CorrectionStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return value;
    }

    @JsonCreator
    public static CorrectionStatus fromValue(String text) {
        for (CorrectionStatus value : values()) {
            if (value.value.equalsIgnoreCase(text)) {
                return value;
            }
        }
        return null;
    }
}
