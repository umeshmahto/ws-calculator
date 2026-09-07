package org.egov.wscalculation.djbmonthlybilling.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.egov.wscalculation.djbmonthlybilling.model.DJBResidualCreditAllocation;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ResidualCreditAllocationStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class DJBResidualCreditAllocationRowMapper implements RowMapper<DJBResidualCreditAllocation> {
    @Override
    public DJBResidualCreditAllocation mapRow(ResultSet rs, int rowNum) throws SQLException {
        DJBResidualCreditAllocation a = new DJBResidualCreditAllocation();
        a.setId(rs.getString("id"));
        a.setTenantid(rs.getString("tenantid"));
        a.setCreditid(rs.getString("creditid"));
        a.setBillingcycleid(rs.getString("billingcycleid"));
        a.setDemandid(rs.getString("demandid"));
        a.setBillid(rs.getString("billid"));
        a.setAppliedamount(rs.getBigDecimal("appliedamount"));
        a.setStatus(ResidualCreditAllocationStatus.valueOf(rs.getString("status")));
        a.setCreatedby(rs.getString("createdby"));
        long created = rs.getLong("createdtime");
        a.setCreatedtime(rs.wasNull() ? null : created);
        a.setLastmodifiedby(rs.getString("lastmodifiedby"));
        long modified = rs.getLong("lastmodifiedtime");
        a.setLastmodifiedtime(rs.wasNull() ? null : modified);
        return a;
    }
}
