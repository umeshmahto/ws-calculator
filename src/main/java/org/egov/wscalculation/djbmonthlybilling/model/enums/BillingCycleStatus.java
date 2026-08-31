package org.egov.wscalculation.djbmonthlybilling.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BillingCycleStatus {
    CREATED("CREATED"),
    CALCULATED("CALCULATED"),
    DEMAND_CREATED("DEMAND_CREATED"),
    BILL_GENERATED("BILL_GENERATED"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED"),
    CORRECTED("CORRECTED");

    private final String value;

    BillingCycleStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return value;
    }

    @JsonCreator
    public static BillingCycleStatus fromValue(String text) {
        for (BillingCycleStatus value : values()) {
            if (value.value.equalsIgnoreCase(text)) {
                return value;
            }
        }
        return null;
    }
}
