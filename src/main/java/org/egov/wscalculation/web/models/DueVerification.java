package org.egov.wscalculation.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a due verification record linked to a water connection application.
 * A single applicationNo can have multiple DueVerification entries (one per kno).
 * kno (K-Number) uniquely identifies a consumer in the due verification context.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DueVerification {

    /**
     * K-Number — unique consumer identifier. Primary key in eg_ws_due_verification.
     */
    @JsonProperty("kno")
    private String kno;

    @JsonProperty("fullName")
    private String fullName;

    @JsonProperty("fullAddress")
    private String fullAddress;

    /**
     * Due amount as a string (e.g. "1500"). Stored as varchar to match payload format.
     */
    @JsonProperty("dueAmount")
    private String dueAmount;

    /**
     * Total amount as a string (e.g. "1500"). Stored as varchar to match payload format.
     */
    @JsonProperty("totalAmount")
    private String totalAmount;

    @JsonProperty("remarks")
    private String remarks;

}
