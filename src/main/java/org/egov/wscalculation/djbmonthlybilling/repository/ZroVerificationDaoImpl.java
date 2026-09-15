package org.egov.wscalculation.djbmonthlybilling.repository;

import java.util.List;
import org.egov.wscalculation.djbmonthlybilling.model.ZroVerification;
import org.egov.wscalculation.djbmonthlybilling.repository.rowmapper.ZroVerificationRowMapper;
import org.egov.wscalculation.djbmonthlybilling.repository.rowmapper.ZroVerificationInboxRowMapper;
import org.egov.wscalculation.djbmonthlybilling.model.ZroVerificationInboxRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ZroVerificationDaoImpl implements ZroVerificationDao {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DJBMonthlyBillingQueryBuilder queryBuilder;
    @Autowired private ZroVerificationRowMapper rowMapper;
    @Autowired private ZroVerificationInboxRowMapper inboxRowMapper;

    @Override public int save(ZroVerification v) {
        return jdbcTemplate.update(queryBuilder.insertZro(), v.getId(), v.getTenantid(), v.getBillingcycleid(),
                v.getConnectionno(), v.getConsumption(), v.getPreviousconsumption(), v.getDeviationfactor(),
                value(v.getStatus()), v.getRemarks(), v.getActionby(), v.getActiondate(), v.getCreatedby(),
                v.getCreatedtime(), v.getLastmodifiedby(), v.getLastmodifiedtime());
    }
    @Override public int update(ZroVerification v) {
        return jdbcTemplate.update(queryBuilder.updateZro(), v.getBillingcycleid(), v.getConnectionno(),
                v.getConsumption(), v.getPreviousconsumption(), v.getDeviationfactor(), value(v.getStatus()),
                v.getRemarks(), v.getActionby(), v.getActiondate(), v.getLastmodifiedby(), v.getLastmodifiedtime(),
                v.getTenantid(), v.getId());
    }
    @Override public ZroVerification findById(String t, String id) {
        List<ZroVerification> r = jdbcTemplate.query(queryBuilder.findZroById(), rowMapper, t, id);
        return r.isEmpty() ? null : r.get(0);
    }
    @Override public ZroVerification findByBillingCycle(String t, String id) {
        List<ZroVerification> r = jdbcTemplate.query(queryBuilder.findZroByBillingCycle(), rowMapper, t, id);
        return r.isEmpty() ? null : r.get(0);
    }

    @Override public List<ZroVerificationInboxRecord> searchPending(String tenantId, String connectionNo, int limit, int offset) {
        if (connectionNo == null || connectionNo.trim().isEmpty()) {
            return jdbcTemplate.query(queryBuilder.searchPendingZro(), inboxRowMapper, tenantId, limit, offset);
        }
        return jdbcTemplate.query(queryBuilder.searchPendingZroByConnection(), inboxRowMapper, tenantId, connectionNo, limit, offset);
    }

    @Override public int countPending(String tenantId, String connectionNo) {
        Integer count;
        if (connectionNo == null || connectionNo.trim().isEmpty()) {
            count = jdbcTemplate.queryForObject(queryBuilder.countPendingZro(), Integer.class, tenantId);
        } else {
            count = jdbcTemplate.queryForObject(queryBuilder.countPendingZroByConnection(), Integer.class, tenantId, connectionNo);
        }
        return count == null ? 0 : count;
    }
    private String value(Enum<?> v) { return v == null ? null : v.toString(); }
}
