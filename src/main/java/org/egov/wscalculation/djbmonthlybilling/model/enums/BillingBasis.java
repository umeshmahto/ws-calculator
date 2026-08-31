package org.egov.wscalculation.djbmonthlybilling.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BillingBasis {
    ACTUAL("ACTUAL"),
    AVERAGE("AVERAGE"),
    PROVISIONAL("PROVISIONAL"),
    CORRECTED_ACTUAL("CORRECTED_ACTUAL");

    private final String value;

    BillingBasis(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return value;
    }

    @JsonCreator
    public static BillingBasis fromValue(String text) {
        for (BillingBasis value : values()) {
            if (value.value.equalsIgnoreCase(text)) {
                return value;
            }
        }
        return null;
    }
}
