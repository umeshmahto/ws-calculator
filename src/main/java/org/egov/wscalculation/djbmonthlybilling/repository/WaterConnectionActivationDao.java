package org.egov.wscalculation.djbmonthlybilling.repository;

/**
 * This DAO intentionally keeps connection master-data access out of the
 * calculation services so that billing-rule calculations remain deterministic
 * and database-local.
 */
public interface WaterConnectionActivationDao {

    /**
     * Returns the earliest effective activation date available for the
     * connection. Returns {@code null} when no usable activation/effective
     * date exists in the connection master.
     */
    Long findActivationDate(String tenantId, String connectionNo);
}
