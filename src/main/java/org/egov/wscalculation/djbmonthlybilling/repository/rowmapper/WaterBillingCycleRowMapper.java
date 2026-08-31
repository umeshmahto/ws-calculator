package org.egov.wscalculation.djbmonthlybilling.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingCycleStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ZroStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class WaterBillingCycleRowMapper implements RowMapper<WaterBillingCycle> {

    @Override
    public WaterBillingCycle mapRow(ResultSet rs, int rowNum) throws SQLException {
        WaterBillingCycle cycle = new WaterBillingCycle();
        cycle.setId(rs.getString("id"));
        cycle.setTenantid(rs.getString("tenantid"));
        cycle.setConnectionno(rs.getString("connectionno"));
        cycle.setBillingperiodfrom(rs.getLong("billingperiodfrom"));
        cycle.setBillingperiodto(rs.getLong("billingperiodto"));
        cycle.setMeterreadingid(rs.getString("meterreadingid"));
        cycle.setReadingqualitycode(rs.getString("readingqualitycode"));
        cycle.setBillingbasis(BillingBasis.fromValue(rs.getString("billingbasis")));
        cycle.setPreviousokreading(nullableDecimal(rs, "previousokreading"));
        cycle.setPreviousokreadingdate(nullableLong(rs, "previousokreadingdate"));
        cycle.setCurrentreading(nullableDecimal(rs, "currentreading"));
        cycle.setCurrentreadingdate(nullableLong(rs, "currentreadingdate"));
        cycle.setActualconsumption(nullableDecimal(rs, "actualconsumption"));
        cycle.setAverageconsumption(nullableDecimal(rs, "averageconsumption"));
        cycle.setBillingconsumption(nullableDecimal(rs, "billingconsumption"));
        cycle.setPreviousconsumption(nullableDecimal(rs, "previousconsumption"));
        cycle.setDeviationfactor(nullableDecimal(rs, "deviationfactor"));
        Boolean flag = (Boolean) rs.getObject("onepointfivexflag");
        cycle.setOnepointfivexflag(flag == null ? Boolean.FALSE : flag);
        cycle.setAveragecyclecount(rs.getInt("averagecyclecount"));
        cycle.setProvisionalcyclecount(rs.getInt("provisionalcyclecount"));
        cycle.setZrostatus(ZroStatus.fromValue(rs.getString("zrostatus")));
        cycle.setZroremarks(rs.getString("zroremarks"));
        cycle.setZroactionby(rs.getString("zroactionby"));
        cycle.setZroactiondate(nullableLong(rs, "zroactiondate"));
        cycle.setCalculationid(rs.getString("calculationid"));
        cycle.setDemandid(rs.getString("demandid"));
        cycle.setBillid(rs.getString("billid"));
        cycle.setCorrectionstatus(CorrectionStatus.fromValue(rs.getString("correctionstatus")));
        cycle.setStatus(BillingCycleStatus.fromValue(rs.getString("status")));
        cycle.setCreatedby(rs.getString("createdby"));
        cycle.setCreatedtime(nullableLong(rs, "createdtime"));
        cycle.setLastmodifiedby(rs.getString("lastmodifiedby"));
        cycle.setLastmodifiedtime(nullableLong(rs, "lastmodifiedtime"));
        return cycle;
    }

    private java.math.BigDecimal nullableDecimal(ResultSet rs, String column) throws SQLException {
        java.math.BigDecimal value = rs.getBigDecimal(column);
        return rs.wasNull() ? null : value;
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
