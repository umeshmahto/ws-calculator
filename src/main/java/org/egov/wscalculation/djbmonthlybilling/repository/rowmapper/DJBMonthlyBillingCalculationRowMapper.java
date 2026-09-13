package org.egov.wscalculation.djbmonthlybilling.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.egov.wscalculation.djbmonthlybilling.model.DJBMonthlyBillingCalculation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class DJBMonthlyBillingCalculationRowMapper implements RowMapper<DJBMonthlyBillingCalculation> {

    @Override
    public DJBMonthlyBillingCalculation mapRow(ResultSet rs, int rowNum) throws SQLException {
        return DJBMonthlyBillingCalculation.builder()
                .id(rs.getString("id"))
                .tenantid(rs.getString("tenantid"))
                .billingcycleid(rs.getString("billingcycleid"))
                .connectionno(rs.getString("connectionno"))
                .engineversion(rs.getString("engineversion"))
                .status(rs.getString("status"))
                .calculatedtime(rs.getLong("calculatedtime"))
                .calculatedby(rs.getString("calculatedby"))
                .snapshotjson(rs.getString("snapshotjson"))
                .build();
    }
}
