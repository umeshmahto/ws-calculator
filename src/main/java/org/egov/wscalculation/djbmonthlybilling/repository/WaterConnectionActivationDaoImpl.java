package org.egov.wscalculation.djbmonthlybilling.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WaterConnectionActivationDaoImpl implements WaterConnectionActivationDao {

    private final JdbcTemplate jdbcTemplate;

    public WaterConnectionActivationDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long findActivationDate(String tenantId, String connectionNo) {
        /*
         * dateEffectiveFrom belongs to eg_ws_connection.
         * connectionExecutionDate belongs to eg_ws_service (wc), not eg_ws_connection.
         * Prefer the actual connection execution date for activation-age calculations,
         * then fall back to dateEffectiveFrom when execution date is unavailable.
         */
        String sql = "SELECT COALESCE(NULLIF(wc.connectionExecutionDate, 0), "
                + "NULLIF(conn.dateEffectiveFrom, 0)) AS activation_date "
                + "FROM eg_ws_connection conn "
                + "LEFT JOIN eg_ws_service wc ON wc.connection_id = conn.id "
                + "WHERE conn.tenantid = ? "
                + "AND conn.connectionNo = ? "
                + "ORDER BY conn.createdTime ASC LIMIT 1";

        List<Long> dates = jdbcTemplate.query(sql, (rs, rowNum) -> {
            long value = rs.getLong("activation_date");
            return rs.wasNull() ? null : value;
        }, tenantId, connectionNo);

        return dates.isEmpty() ? null : dates.get(0);
    }
}
