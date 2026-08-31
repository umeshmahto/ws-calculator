package org.egov.wscalculation.djbmonthlybilling.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.egov.wscalculation.djbmonthlybilling.model.ZroVerification;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ZroStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class ZroVerificationRowMapper implements RowMapper<ZroVerification> {
    @Override
    public ZroVerification mapRow(ResultSet rs, int rowNum) throws SQLException {
        ZroVerification v = new ZroVerification();
        v.setId(rs.getString("id"));
        v.setTenantid(rs.getString("tenantid"));
        v.setBillingcycleid(rs.getString("billingcycleid"));
        v.setConnectionno(rs.getString("connectionno"));
        v.setConsumption(rs.getBigDecimal("consumption"));
        v.setPreviousconsumption(rs.getBigDecimal("previousconsumption"));
        v.setDeviationfactor(rs.getBigDecimal("deviationfactor"));
        v.setStatus(ZroStatus.fromValue(rs.getString("status")));
        v.setRemarks(rs.getString("remarks"));
        v.setActionby(rs.getString("actionby"));
        long actionDate = rs.getLong("actiondate");
        v.setActiondate(rs.wasNull() ? null : actionDate);
        v.setCreatedby(rs.getString("createdby"));
        long created = rs.getLong("createdtime");
        v.setCreatedtime(rs.wasNull() ? null : created);
        v.setLastmodifiedby(rs.getString("lastmodifiedby"));
        long modified = rs.getLong("lastmodifiedtime");
        v.setLastmodifiedtime(rs.wasNull() ? null : modified);
        return v;
    }
}
