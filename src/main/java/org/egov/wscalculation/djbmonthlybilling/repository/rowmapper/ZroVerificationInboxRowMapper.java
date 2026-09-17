package org.egov.wscalculation.djbmonthlybilling.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.egov.wscalculation.djbmonthlybilling.model.ZroVerification;
import org.egov.wscalculation.djbmonthlybilling.model.ZroVerificationInboxRecord;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingCycleStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ZroStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class ZroVerificationInboxRowMapper implements RowMapper<ZroVerificationInboxRecord> {
    @Override
    public ZroVerificationInboxRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        ZroVerificationInboxRecord r = new ZroVerificationInboxRecord();
        ZroVerification v = new ZroVerification();
        v.setId(rs.getString("zro_id"));
        v.setTenantid(rs.getString("zro_tenantid"));
        v.setBillingcycleid(rs.getString("zro_billingcycleid"));
        v.setConnectionno(rs.getString("zro_connectionno"));
        v.setConsumption(rs.getBigDecimal("zro_consumption"));
        v.setPreviousconsumption(rs.getBigDecimal("zro_previousconsumption"));
        v.setDeviationfactor(rs.getBigDecimal("zro_deviationfactor"));
        v.setStatus(ZroStatus.fromValue(rs.getString("zro_status")));
        v.setRemarks(rs.getString("zro_remarks"));
        v.setActionby(rs.getString("zro_actionby"));
        v.setActiondate(nullableLong(rs, "zro_actiondate"));
        v.setCreatedby(rs.getString("zro_createdby"));
        v.setCreatedtime(nullableLong(rs, "zro_createdtime"));
        v.setLastmodifiedby(rs.getString("zro_lastmodifiedby"));
        v.setLastmodifiedtime(nullableLong(rs, "zro_lastmodifiedtime"));
        r.setVerification(v);
        r.setBillingcycleid(rs.getString("cycle_id"));
        r.setBillingperiodfrom(rs.getLong("billingperiodfrom"));
        r.setBillingperiodto(rs.getLong("billingperiodto"));
        r.setPreviousreading(rs.getBigDecimal("previousokreading"));
        r.setPreviousokreadingdate(nullableLong(rs, "previousokreadingdate"));
        r.setCurrentreading(rs.getBigDecimal("currentreading"));
        r.setReadingqualitycode(rs.getString("readingqualitycode"));
        r.setBillingbasis(rs.getString("billingbasis"));
        r.setActualconsumption(rs.getBigDecimal("actualconsumption"));
        r.setOnepointfivexflag(rs.getBoolean("onepointfivexflag"));
        r.setBillingcyclestatus(rs.getString("cycle_status"));
        r.setCorrectionstatus(rs.getString("correctionstatus"));
        r.setCalculationid(rs.getString("calculationid"));
        r.setDemandid(rs.getString("demandid"));
        r.setBillid(rs.getString("billid"));
        return r;
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
