package org.egov.wscalculation.djbmonthlybilling.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WaterConnectionActivationDaoImpl implements WaterConnectionActivationDao {

    private static final String FIND_ACTIVATION_DATE =
            "SELECT MIN(CASE WHEN dateEffectiveFrom IS NOT NULL AND dateEffectiveFrom > 0 "
                    + "THEN dateEffectiveFrom END) AS activation_date "
                    + "FROM eg_ws_connection "
                    + "WHERE tenantid = ? AND connectionno = ?";

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public WaterConnectionActivationDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long findActivationDate(String tenantId, String connectionNo) {
        List<Long> result = jdbcTemplate.query(
                FIND_ACTIVATION_DATE,
                (rs, rowNum) -> rs.getObject("activation_date") == null
                        ? null
                        : rs.getLong("activation_date"),
                tenantId,
                connectionNo);

        return result.isEmpty() ? null : result.get(0);
    }
}
