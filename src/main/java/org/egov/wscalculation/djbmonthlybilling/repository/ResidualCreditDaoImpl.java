package org.egov.wscalculation.djbmonthlybilling.repository;

import java.math.BigDecimal;
import java.util.List;

import org.egov.wscalculation.djbmonthlybilling.model.DJBResidualCredit;
import org.egov.wscalculation.djbmonthlybilling.model.DJBResidualCreditAllocation;
import org.egov.wscalculation.djbmonthlybilling.repository.rowmapper.DJBResidualCreditAllocationRowMapper;
import org.egov.wscalculation.djbmonthlybilling.repository.rowmapper.DJBResidualCreditRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ResidualCreditDaoImpl implements ResidualCreditDao {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DJBMonthlyBillingQueryBuilder queryBuilder;
    @Autowired private DJBResidualCreditRowMapper creditRowMapper;
    @Autowired private DJBResidualCreditAllocationRowMapper allocationRowMapper;

    @Override
    public List<DJBResidualCredit> findOpenCreditsForUpdate(String tenantId, String connectionNo) {
        return jdbcTemplate.query(queryBuilder.findOpenResidualCreditsForUpdate(), creditRowMapper,
                tenantId, connectionNo);
    }

    @Override
    public DJBResidualCredit findByIdForUpdate(String tenantId, String id) {
        List<DJBResidualCredit> result = jdbcTemplate.query(queryBuilder.findResidualCreditByIdForUpdate(),
                creditRowMapper, tenantId, id);
        return result.isEmpty() ? null : result.get(0);
    }

    @Override
    public DJBResidualCredit findBySourceCorrection(String tenantId, String sourceCorrectionId) {
        List<DJBResidualCredit> result = jdbcTemplate.query(queryBuilder.findResidualCreditBySourceCorrection(),
                creditRowMapper, tenantId, sourceCorrectionId);
        return result.isEmpty() ? null : result.get(0);
    }

    @Override
    public List<DJBResidualCreditAllocation> findAllocationsByBillingCycle(String tenantId, String billingCycleId) {
        return jdbcTemplate.query(queryBuilder.findResidualCreditAllocationsByBillingCycle(), allocationRowMapper,
                tenantId, billingCycleId);
    }

    @Override
    public int saveCredit(DJBResidualCredit credit) {
        return jdbcTemplate.update(queryBuilder.insertResidualCredit(), credit.getId(), credit.getTenantid(),
                credit.getConnectionno(), credit.getSourcecorrectionid(), credit.getOriginalamount(),
                credit.getRemainingamount(), credit.getReservedamount(), value(credit.getStatus()), credit.getCreatedby(),
                credit.getCreatedtime(), credit.getLastmodifiedby(), credit.getLastmodifiedtime());
    }

    @Override
    public int updateCredit(DJBResidualCredit credit) {
        return jdbcTemplate.update(queryBuilder.updateResidualCredit(), credit.getConnectionno(),
                credit.getSourcecorrectionid(), credit.getOriginalamount(), credit.getRemainingamount(),
                credit.getReservedamount(), value(credit.getStatus()), credit.getLastmodifiedby(),
                credit.getLastmodifiedtime(), credit.getTenantid(), credit.getId());
    }

    @Override
    public int saveAllocation(DJBResidualCreditAllocation allocation) {
        return jdbcTemplate.update(queryBuilder.insertResidualCreditAllocation(), allocation.getId(),
                allocation.getTenantid(), allocation.getCreditid(), allocation.getBillingcycleid(),
                allocation.getDemandid(), allocation.getBillid(), allocation.getAppliedamount(),
                value(allocation.getStatus()), allocation.getCreatedby(), allocation.getCreatedtime(),
                allocation.getLastmodifiedby(), allocation.getLastmodifiedtime());
    }

    @Override
    public int updateAllocation(DJBResidualCreditAllocation allocation) {
        return jdbcTemplate.update(queryBuilder.updateResidualCreditAllocation(), allocation.getDemandid(),
                allocation.getBillid(), allocation.getAppliedamount(), value(allocation.getStatus()),
                allocation.getLastmodifiedby(), allocation.getLastmodifiedtime(), allocation.getTenantid(),
                allocation.getId());
    }

    @Override
    public int reserveCredit(String tenantId, String creditId, BigDecimal amount, long currentTime, String actor) {
        return jdbcTemplate.update(queryBuilder.reserveResidualCredit(), amount, actor, currentTime, tenantId, creditId,
                amount);
    }

    @Override
    public int consumeReservedCredit(String tenantId, String creditId, BigDecimal amount, long currentTime,
            String actor) {
        return jdbcTemplate.update(queryBuilder.consumeReservedResidualCredit(), amount, amount, amount, actor, currentTime,
                tenantId, creditId, amount, amount);
    }

    @Override
    public int releaseReservedCredit(String tenantId, String creditId, BigDecimal amount, long currentTime,
            String actor) {
        return jdbcTemplate.update(queryBuilder.releaseReservedResidualCredit(), amount, actor, currentTime,
                tenantId, creditId, amount);
    }

    private String value(Enum<?> value) {
        return value == null ? null : value.toString();
    }
}
