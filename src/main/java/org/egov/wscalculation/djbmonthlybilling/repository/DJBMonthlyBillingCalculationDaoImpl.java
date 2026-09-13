package org.egov.wscalculation.djbmonthlybilling.repository;

import java.util.List;

import org.egov.wscalculation.djbmonthlybilling.model.DJBMonthlyBillingCalculation;
import org.egov.wscalculation.djbmonthlybilling.repository.rowmapper.DJBMonthlyBillingCalculationRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DJBMonthlyBillingCalculationDaoImpl implements DJBMonthlyBillingCalculationDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DJBMonthlyBillingQueryBuilder queryBuilder;

    @Autowired
    private DJBMonthlyBillingCalculationRowMapper rowMapper;

    @Override
    public int save(DJBMonthlyBillingCalculation c) {
        return jdbcTemplate.update(queryBuilder.insertBillingCalculation(),
                c.getId(), c.getTenantid(), c.getBillingcycleid(), c.getConnectionno(),
                c.getEngineversion(), c.getStatus(), c.getCalculatedtime(), c.getCalculatedby(),
                c.getSnapshotjson());
    }

    @Override
    public DJBMonthlyBillingCalculation findById(String tenantId, String id) {
        List<DJBMonthlyBillingCalculation> result = jdbcTemplate.query(
                queryBuilder.findBillingCalculationById(), rowMapper, tenantId, id);
        return result.isEmpty() ? null : result.get(0);
    }

    @Override
    public DJBMonthlyBillingCalculation findByBillingCycle(String tenantId, String billingCycleId) {
        List<DJBMonthlyBillingCalculation> result = jdbcTemplate.query(
                queryBuilder.findBillingCalculationByCycle(), rowMapper, tenantId, billingCycleId);
        return result.isEmpty() ? null : result.get(0);
    }
}
