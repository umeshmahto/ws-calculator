package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.egov.wscalculation.djbmonthlybilling.model.BillingCorrection;
import org.egov.wscalculation.djbmonthlybilling.model.DJBResidualCredit;
import org.egov.wscalculation.djbmonthlybilling.model.DJBResidualCreditAllocation;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ResidualCreditAllocationStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ResidualCreditStatus;
import org.egov.wscalculation.djbmonthlybilling.repository.ResidualCreditDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains residual DJB correction credits and applies them to future monthly
 * demands without modifying generic billing-service behaviour.
 */
@Service
@Slf4j
public class ResidualCreditService {

    private static final int MONEY_SCALE = 2;

    private final ResidualCreditDao residualCreditDao;

    public ResidualCreditService(ResidualCreditDao residualCreditDao) {
        this.residualCreditDao = residualCreditDao;
    }

    @Transactional
    public CreditReservationResult reserveForBillingCycle(String tenantId, String connectionNo,
            String billingCycleId, BigDecimal billBaseAmount, String actor, long currentTime) {

        BigDecimal required = normalize(billBaseAmount);
        if (required.signum() <= 0 || !StringUtils.hasText(billingCycleId)) {
            return CreditReservationResult.empty();
        }

        List<DJBResidualCreditAllocation> existing = residualCreditDao.findAllocationsByBillingCycle(tenantId,
                billingCycleId);
        if (!CollectionUtils.isEmpty(existing)) {
            BigDecimal applied = BigDecimal.ZERO;
            BigDecimal reserved = BigDecimal.ZERO;
            List<String> ids = new ArrayList<>();
            for (DJBResidualCreditAllocation allocation : existing) {
                if (allocation == null) {
                    continue;
                }
                if (ResidualCreditAllocationStatus.APPLIED.equals(allocation.getStatus())) {
                    applied = applied.add(normalize(allocation.getAppliedamount()));
                    ids.add(allocation.getId());
                } else if (ResidualCreditAllocationStatus.RESERVED.equals(allocation.getStatus())) {
                    reserved = reserved.add(normalize(allocation.getAppliedamount()));
                    ids.add(allocation.getId());
                }
            }
            return CreditReservationResult.builder().appliedAmount(normalize(applied))
                    .reservedAmount(normalize(reserved)).allocationIds(ids).newReservation(false).build();
        }

        BigDecimal remainingNeed = required;
        BigDecimal totalReserved = BigDecimal.ZERO;
        List<String> allocationIds = new ArrayList<>();

        List<DJBResidualCredit> credits = residualCreditDao.findOpenCreditsForUpdate(tenantId, connectionNo);
        if (CollectionUtils.isEmpty(credits)) {
            return CreditReservationResult.empty();
        }

        for (DJBResidualCredit credit : credits) {
            if (credit == null || !StringUtils.hasText(credit.getId()) || remainingNeed.signum() <= 0) {
                continue;
            }

            BigDecimal remaining = normalize(credit.getRemainingamount());
            BigDecimal reserved = normalize(credit.getReservedamount());
            BigDecimal available = remaining.subtract(reserved).max(BigDecimal.ZERO);
            BigDecimal amount = remainingNeed.min(available).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (amount.signum() <= 0) {
                continue;
            }

            int updated = residualCreditDao.reserveCredit(tenantId, credit.getId(), amount, currentTime, actor);
            if (updated != 1) {
                throw new IllegalStateException("Unable to reserve DJB residual credit " + credit.getId());
            }

            DJBResidualCreditAllocation allocation = new DJBResidualCreditAllocation();
            allocation.setId(UUID.randomUUID().toString());
            allocation.setTenantid(tenantId);
            allocation.setCreditid(credit.getId());
            allocation.setBillingcycleid(billingCycleId);
            allocation.setAppliedamount(amount);
            allocation.setStatus(ResidualCreditAllocationStatus.RESERVED);
            allocation.setCreatedby(actor);
            allocation.setCreatedtime(currentTime);
            allocation.setLastmodifiedby(actor);
            allocation.setLastmodifiedtime(currentTime);
            if (residualCreditDao.saveAllocation(allocation) != 1) {
                throw new IllegalStateException("Unable to create DJB residual credit allocation");
            }

            allocationIds.add(allocation.getId());
            totalReserved = totalReserved.add(amount);
            remainingNeed = remainingNeed.subtract(amount);
        }

        return CreditReservationResult.builder().appliedAmount(BigDecimal.ZERO.setScale(MONEY_SCALE))
                .reservedAmount(normalize(totalReserved)).allocationIds(allocationIds)
                .newReservation(!allocationIds.isEmpty()).build();
    }

    @Transactional
    public void confirmReservations(String tenantId, String billingCycleId, String demandId, String actor,
            long currentTime) {
        List<DJBResidualCreditAllocation> allocations = residualCreditDao.findAllocationsByBillingCycle(tenantId,
                billingCycleId);
        if (CollectionUtils.isEmpty(allocations)) {
            return;
        }

        for (DJBResidualCreditAllocation allocation : allocations) {
            if (allocation == null || !ResidualCreditAllocationStatus.RESERVED.equals(allocation.getStatus())) {
                continue;
            }

            DJBResidualCredit credit = residualCreditDao.findByIdForUpdate(tenantId, allocation.getCreditid());
            if (credit == null) {
                throw new IllegalStateException("DJB residual credit not found: " + allocation.getCreditid());
            }

            BigDecimal amount = normalize(allocation.getAppliedamount());
            int updated = residualCreditDao.consumeReservedCredit(tenantId, credit.getId(), amount, currentTime, actor);
            if (updated != 1) {
                throw new IllegalStateException("Unable to consume reserved DJB residual credit: " + credit.getId());
            }

            allocation.setDemandid(demandId);
            allocation.setStatus(ResidualCreditAllocationStatus.APPLIED);
            allocation.setLastmodifiedby(actor);
            allocation.setLastmodifiedtime(currentTime);
            if (residualCreditDao.updateAllocation(allocation) != 1) {
                throw new IllegalStateException("Unable to update DJB residual credit allocation: " + allocation.getId());
            }
        }
    }

    @Transactional
    public void releaseReservations(String tenantId, String billingCycleId, String actor, long currentTime) {
        List<DJBResidualCreditAllocation> allocations = residualCreditDao.findAllocationsByBillingCycle(tenantId,
                billingCycleId);
        if (CollectionUtils.isEmpty(allocations)) {
            return;
        }

        for (DJBResidualCreditAllocation allocation : allocations) {
            if (allocation == null || !ResidualCreditAllocationStatus.RESERVED.equals(allocation.getStatus())) {
                continue;
            }

            BigDecimal amount = normalize(allocation.getAppliedamount());
            int updated = residualCreditDao.releaseReservedCredit(tenantId, allocation.getCreditid(), amount,
                    currentTime, actor);
            if (updated != 1) {
                throw new IllegalStateException("Unable to release reserved DJB residual credit: "
                        + allocation.getCreditid());
            }

            allocation.setStatus(ResidualCreditAllocationStatus.RELEASED);
            allocation.setLastmodifiedby(actor);
            allocation.setLastmodifiedtime(currentTime);
            residualCreditDao.updateAllocation(allocation);
        }
    }

    @Transactional
    public void recoverPendingReservations(String tenantId, String billingCycleId, String demandId, String actor,
            long currentTime) {
        confirmReservations(tenantId, billingCycleId, demandId, actor, currentTime);
    }

    @Transactional
    public void attachBillId(String tenantId, String billingCycleId, String billId, String actor, long currentTime) {
        if (!StringUtils.hasText(billId)) {
            return;
        }
        List<DJBResidualCreditAllocation> allocations = residualCreditDao.findAllocationsByBillingCycle(tenantId,
                billingCycleId);
        if (CollectionUtils.isEmpty(allocations)) {
            return;
        }
        for (DJBResidualCreditAllocation allocation : allocations) {
            if (allocation == null || !ResidualCreditAllocationStatus.APPLIED.equals(allocation.getStatus())
                    || StringUtils.hasText(allocation.getBillid())) {
                continue;
            }
            allocation.setBillid(billId);
            allocation.setLastmodifiedby(actor);
            allocation.setLastmodifiedtime(currentTime);
            residualCreditDao.updateAllocation(allocation);
        }
    }

    @Transactional
    public void createResidualCredit(BillingCorrection correction, String actor, long currentTime) {
        if (correction == null || !StringUtils.hasText(correction.getTenantid())
                || !StringUtils.hasText(correction.getConnectionno()) || !StringUtils.hasText(correction.getId())) {
            return;
        }

        BigDecimal amount = normalize(correction.getResidualpaidcreditamount());
        if (amount.signum() <= 0) {
            return;
        }

        if (residualCreditDao.findBySourceCorrection(correction.getTenantid(), correction.getId()) != null) {
            return;
        }

        DJBResidualCredit credit = new DJBResidualCredit();
        credit.setId(UUID.randomUUID().toString());
        credit.setTenantid(correction.getTenantid());
        credit.setConnectionno(correction.getConnectionno());
        credit.setSourcecorrectionid(correction.getId());
        credit.setOriginalamount(amount);
        credit.setRemainingamount(amount);
        credit.setReservedamount(BigDecimal.ZERO.setScale(MONEY_SCALE));
        credit.setStatus(ResidualCreditStatus.OPEN);
        credit.setCreatedby(actor);
        credit.setCreatedtime(currentTime);
        credit.setLastmodifiedby(actor);
        credit.setLastmodifiedtime(currentTime);
        if (residualCreditDao.saveCredit(credit) != 1) {
            throw new IllegalStateException("Unable to persist DJB residual credit for correction " + correction.getId());
        }

        log.info("[DJB-CORRECTION] Residual credit created: creditId={}, correctionId={}, connectionNo={}, amount={}",
                credit.getId(), correction.getId(), correction.getConnectionno(), amount);
    }

    public BigDecimal getTotalAppliedAmount(CreditReservationResult result) {
        if (result == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        return normalize(result.getAppliedAmount().add(result.getReservedAmount()));
    }

    private BigDecimal normalize(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    @Data
    @Builder
    public static class CreditReservationResult {
        @Builder.Default private BigDecimal appliedAmount = BigDecimal.ZERO;
        @Builder.Default private BigDecimal reservedAmount = BigDecimal.ZERO;
        @Builder.Default private List<String> allocationIds = Collections.emptyList();
        @Builder.Default private boolean newReservation = false;

        public static CreditReservationResult empty() {
            return CreditReservationResult.builder().build();
        }
    }
}
