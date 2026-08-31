package org.egov.wscalculation.djbmonthlybilling.repository;

import java.util.List;

import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.repository.rowmapper.WaterBillingCycleRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WaterBillingCycleDaoImpl implements WaterBillingCycleDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DJBMonthlyBillingQueryBuilder queryBuilder;

    @Autowired
    private WaterBillingCycleRowMapper rowMapper;

    @Override
    public int save(WaterBillingCycle c) {
        return jdbcTemplate.update(queryBuilder.insertBillingCycle(),
                c.getId(), c.getTenantid(), c.getConnectionno(), c.getBillingperiodfrom(), c.getBillingperiodto(),
                c.getMeterreadingid(), c.getReadingqualitycode(), value(c.getBillingbasis()),
                c.getPreviousokreading(), c.getPreviousokreadingdate(), c.getCurrentreading(), c.getCurrentreadingdate(),
                c.getActualconsumption(), c.getAverageconsumption(), c.getBillingconsumption(), c.getPreviousconsumption(),
                c.getDeviationfactor(), c.getOnepointfivexflag(), c.getAveragecyclecount(), c.getProvisionalcyclecount(),
                value(c.getZrostatus()), c.getZroremarks(), c.getZroactionby(), c.getZroactiondate(),
                c.getCalculationid(), c.getDemandid(), c.getBillid(), value(c.getCorrectionstatus()), value(c.getStatus()),
                c.getCreatedby(), c.getCreatedtime(), c.getLastmodifiedby(), c.getLastmodifiedtime());
    }

    @Override
    public int update(WaterBillingCycle c) {
        return jdbcTemplate.update(queryBuilder.updateBillingCycle(),
                c.getConnectionno(), c.getBillingperiodfrom(), c.getBillingperiodto(), c.getMeterreadingid(),
                c.getReadingqualitycode(), value(c.getBillingbasis()), c.getPreviousokreading(), c.getPreviousokreadingdate(),
                c.getCurrentreading(), c.getCurrentreadingdate(), c.getActualconsumption(), c.getAverageconsumption(),
                c.getBillingconsumption(), c.getPreviousconsumption(), c.getDeviationfactor(), c.getOnepointfivexflag(),
                c.getAveragecyclecount(), c.getProvisionalcyclecount(), value(c.getZrostatus()), c.getZroremarks(),
                c.getZroactionby(), c.getZroactiondate(), c.getCalculationid(), c.getDemandid(), c.getBillid(),
                value(c.getCorrectionstatus()), value(c.getStatus()), c.getLastmodifiedby(), c.getLastmodifiedtime(),
                c.getTenantid(), c.getId());
    }

    @Override public WaterBillingCycle findById(String t, String id) {
        List<WaterBillingCycle> r = jdbcTemplate.query(queryBuilder.findById(), rowMapper, t, id);
        return r.isEmpty() ? null : r.get(0);
    }

    @Override public WaterBillingCycle findByConnectionAndPeriod(String t, String c, Long f, Long to) {
        List<WaterBillingCycle> r = jdbcTemplate.query(queryBuilder.findByConnectionAndPeriod(), rowMapper, t, c, f, to);
        return r.isEmpty() ? null : r.get(0);
    }

    @Override public WaterBillingCycle findLatestByConnection(String t, String c) {
        List<WaterBillingCycle> r = jdbcTemplate.query(queryBuilder.findLatestByConnection(), rowMapper, t, c);
        return r.isEmpty() ? null : r.get(0);
    }

    @Override public WaterBillingCycle findLatestOkByConnection(String t, String c) {
        List<WaterBillingCycle> r = jdbcTemplate.query(queryBuilder.findLatestOkByConnection(), rowMapper, t, c);
        return r.isEmpty() ? null : r.get(0);
    }

    @Override public List<WaterBillingCycle> findPreviousActualCycles(String t, String c, Long p, int l) {
        return jdbcTemplate.query(queryBuilder.findPreviousActualCycles(), rowMapper, t, c, p, l);
    }

    @Override public List<WaterBillingCycle> findCyclesForConnection(String t, String c, Long p, int l) {
        return jdbcTemplate.query(queryBuilder.findCyclesForConnection(), rowMapper, t, c, p, l);
    }

    @Override public List<WaterBillingCycle> findCyclesForCorrection(String t, String c, Long f, Long to) {
        return jdbcTemplate.query(queryBuilder.findCyclesForCorrection(), rowMapper, t, c, f, to);
    }

    private String value(Enum<?> value) { return value == null ? null : value.toString(); }

    @Override
    public WaterBillingCycle findPreviousOkByConnectionBefore(
            String tenantId,
            String connectionNo,
            Long beforeBillingPeriodTo) {

        List<WaterBillingCycle> result =
                jdbcTemplate.query(
                        queryBuilder.findPreviousOkByConnectionBefore(),
                        rowMapper,
                        tenantId,
                        connectionNo,
                        beforeBillingPeriodTo);

        return result.isEmpty() ? null : result.get(0);
    }
}
