package org.egov.wscalculation.djbmonthlybilling.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.egov.wscalculation.djbmonthlybilling.model.DJBResidualCredit;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ResidualCreditStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class DJBResidualCreditRowMapper implements RowMapper<DJBResidualCredit> {
    @Override
    public DJBResidualCredit mapRow(ResultSet rs, int rowNum) throws SQLException {
        DJBResidualCredit c = new DJBResidualCredit();
        c.setId(rs.getString("id"));
        c.setTenantid(rs.getString("tenantid"));
        c.setConnectionno(rs.getString("connectionno"));
        c.setSourcecorrectionid(rs.getString("sourcecorrectionid"));
        c.setOriginalamount(rs.getBigDecimal("originalamount"));
        c.setRemainingamount(rs.getBigDecimal("remainingamount"));
        c.setReservedamount(rs.getBigDecimal("reservedamount"));
        c.setStatus(ResidualCreditStatus.valueOf(rs.getString("status")));
        c.setCreatedby(rs.getString("createdby"));
        long created = rs.getLong("createdtime");
        c.setCreatedtime(rs.wasNull() ? null : created);
        c.setLastmodifiedby(rs.getString("lastmodifiedby"));
        long modified = rs.getLong("lastmodifiedtime");
        c.setLastmodifiedtime(rs.wasNull() ? null : modified);
        return c;
    }
}
