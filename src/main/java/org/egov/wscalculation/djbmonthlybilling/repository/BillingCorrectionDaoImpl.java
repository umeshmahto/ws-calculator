package org.egov.wscalculation.djbmonthlybilling.repository;

import java.util.List;
import org.egov.wscalculation.djbmonthlybilling.model.BillingCorrection;
import org.egov.wscalculation.djbmonthlybilling.repository.rowmapper.BillingCorrectionRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BillingCorrectionDaoImpl implements BillingCorrectionDao {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DJBMonthlyBillingQueryBuilder queryBuilder;
    @Autowired private BillingCorrectionRowMapper rowMapper;

    @Override public int save(BillingCorrection c) {
        return jdbcTemplate.update(queryBuilder.insertCorrection(), c.getId(), c.getTenantid(), c.getConnectionno(),
                c.getFrombillingcycleid(), c.getTobillingcycleid(), value(c.getStatus()), c.getReason(),
                c.getPaidadjustmentamount(), c.getAppliedpaidadjustmentamount(), c.getResidualpaidcreditamount(),
                c.getOlddemandid(), c.getOldbillid(), c.getCorrecteddemandid(), c.getCorrectedbillid(),
                c.getCreatedby(), c.getCreatedtime(), c.getLastmodifiedby(), c.getLastmodifiedtime());
    }
    @Override public int update(BillingCorrection c) {
        return jdbcTemplate.update(queryBuilder.updateCorrection(), c.getConnectionno(), c.getFrombillingcycleid(),
                c.getTobillingcycleid(), value(c.getStatus()), c.getReason(), c.getPaidadjustmentamount(),
                c.getAppliedpaidadjustmentamount(), c.getResidualpaidcreditamount(),
                c.getOlddemandid(), c.getOldbillid(), c.getCorrecteddemandid(), c.getCorrectedbillid(),
                c.getLastmodifiedby(), c.getLastmodifiedtime(),
                c.getTenantid(), c.getId());
    }
    @Override public BillingCorrection findById(String t, String id) {
        List<BillingCorrection> r = jdbcTemplate.query(queryBuilder.findCorrectionById(), rowMapper, t, id);
        return r.isEmpty() ? null : r.get(0);
    }
    @Override public List<BillingCorrection> findByConnection(String t, String c) {
        return jdbcTemplate.query(queryBuilder.findCorrectionsByConnection(), rowMapper, t, c);
    }
    private String value(Enum<?> v) { return v == null ? null : v.toString(); }
}
