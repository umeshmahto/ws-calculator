package org.egov.wscalculation.djbmonthlybilling.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.egov.wscalculation.djbmonthlybilling.model.BillingCorrection;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class BillingCorrectionRowMapper implements RowMapper<BillingCorrection> {
    @Override
    public BillingCorrection mapRow(ResultSet rs, int rowNum) throws SQLException {
        BillingCorrection c = new BillingCorrection();
        c.setId(rs.getString("id"));
        c.setTenantid(rs.getString("tenantid"));
        c.setConnectionno(rs.getString("connectionno"));
        c.setFrombillingcycleid(rs.getString("frombillingcycleid"));
        c.setTobillingcycleid(rs.getString("tobillingcycleid"));
        c.setStatus(CorrectionStatus.fromValue(rs.getString("status")));
        c.setReason(rs.getString("reason"));
        c.setPaidadjustmentamount(rs.getBigDecimal("paidadjustmentamount"));
        c.setAppliedpaidadjustmentamount(rs.getBigDecimal("appliedpaidadjustmentamount"));
        c.setResidualpaidcreditamount(rs.getBigDecimal("residualpaidcreditamount"));
        c.setOlddemandid(rs.getString("olddemandid"));
        c.setOldbillid(rs.getString("oldbillid"));
        c.setCorrecteddemandid(rs.getString("correcteddemandid"));
        c.setCorrectedbillid(rs.getString("correctedbillid"));
        c.setCreatedby(rs.getString("createdby"));
        long created = rs.getLong("createdtime");
        c.setCreatedtime(rs.wasNull() ? null : created);
        c.setLastmodifiedby(rs.getString("lastmodifiedby"));
        long modified = rs.getLong("lastmodifiedtime");
        c.setLastmodifiedtime(rs.wasNull() ? null : modified);
        return c;
    }
}
