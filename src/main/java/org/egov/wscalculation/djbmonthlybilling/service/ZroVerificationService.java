package org.egov.wscalculation.djbmonthlybilling.service;

import java.util.Locale;
import java.util.UUID;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.ZroVerification;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingCycleStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ZroStatus;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.repository.ZroVerificationDao;
import org.egov.wscalculation.web.models.Demand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ZroVerificationService {

    private final WaterBillingCycleDao billingCycleDao;
    private final ZroVerificationDao zroVerificationDao;
    private final DJBMonthlyDemandService demandService;

    public ZroVerificationService(
            WaterBillingCycleDao billingCycleDao,
            ZroVerificationDao zroVerificationDao,
            DJBMonthlyDemandService demandService) {
        this.billingCycleDao = billingCycleDao;
        this.zroVerificationDao = zroVerificationDao;
        this.demandService = demandService;
    }

    @Transactional
    public ZroVerificationResult update(RequestInfo requestInfo, String billingCycleId,
            String action, String remarks) {

        if (requestInfo == null || requestInfo.getUserInfo() == null
                || !StringUtils.hasText(requestInfo.getUserInfo().getUuid())) {
            throw new IllegalArgumentException("RequestInfo.userInfo.uuid is required for ZRO action");
        }

        String tenantId = requestInfo.getUserInfo().getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("RequestInfo.userInfo.tenantId is required for ZRO action");
        }

        WaterBillingCycle cycle = billingCycleDao.findById(tenantId, billingCycleId);
        if (cycle == null) {
            throw new IllegalArgumentException("Billing cycle not found: " + billingCycleId);
        }

        if (!Boolean.TRUE.equals(cycle.getOnepointfivexflag())) {
            throw new IllegalStateException("ZRO action is only valid for a DJB 1.5x flagged billing cycle");
        }

        String normalizedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(normalizedAction) && !"REJECT".equals(normalizedAction)) {
            throw new IllegalArgumentException("ZRO action must be APPROVE or REJECT");
        }

        String actor = requestInfo.getUserInfo().getUuid();
        ZroVerification verification = zroVerificationDao.findByBillingCycle(tenantId, billingCycleId);
        if (verification == null) {
            long now = System.currentTimeMillis();
            verification = new ZroVerification();
            verification.setId(UUID.randomUUID().toString());
            verification.setTenantid(tenantId);
            verification.setBillingcycleid(cycle.getId());
            verification.setConnectionno(cycle.getConnectionno());
            verification.setConsumption(cycle.getActualconsumption());
            verification.setPreviousconsumption(cycle.getPreviousconsumption());
            verification.setDeviationfactor(cycle.getDeviationfactor());
            verification.setStatus(ZroStatus.PENDING);
            verification.setCreatedby(actor);
            verification.setCreatedtime(now);
            verification.setLastmodifiedby(actor);
            verification.setLastmodifiedtime(now);
            zroVerificationDao.save(verification);
        }

        if (ZroStatus.APPROVED.equals(verification.getStatus())
                || ZroStatus.REJECTED.equals(verification.getStatus())) {
            if (("APPROVE".equals(normalizedAction) && ZroStatus.APPROVED.equals(verification.getStatus()))
                    || ("REJECT".equals(normalizedAction) && ZroStatus.REJECTED.equals(verification.getStatus()))) {
                return result(cycle, verification, cycle.getDemandid(), cycle.getBillid(),
                        "ZRO action already applied");
            }
            throw new IllegalStateException(
                    "ZRO verification is already " + verification.getStatus()
                            + " for billing cycle " + billingCycleId);
        }

        long now = System.currentTimeMillis();
        verification.setRemarks(StringUtils.hasText(remarks) ? remarks.trim() : verification.getRemarks());
        verification.setActionby(actor);
        verification.setActiondate(now);
        verification.setLastmodifiedby(actor);
        verification.setLastmodifiedtime(now);

        if ("REJECT".equals(normalizedAction)) {
            verification.setStatus(ZroStatus.REJECTED);
            cycle.setZrostatus(ZroStatus.REJECTED);
            cycle.setZroremarks(verification.getRemarks());
            cycle.setStatus(BillingCycleStatus.CALCULATED);
            cycle.setLastmodifiedby(actor);
            cycle.setLastmodifiedtime(now);
            zroVerificationDao.update(verification);
            billingCycleDao.update(cycle);

            return result(cycle, verification, null, null, "DJB 1.5x consumption rejected by ZRO verification");
        }

        verification.setStatus(ZroStatus.APPROVED);
        cycle.setZrostatus(ZroStatus.APPROVED);
        cycle.setZroremarks(verification.getRemarks());
        cycle.setStatus(BillingCycleStatus.CALCULATED);
        cycle.setLastmodifiedby(actor);
        cycle.setLastmodifiedtime(now);
        zroVerificationDao.update(verification);
        billingCycleDao.update(cycle);

        // createDemand() is allowed to proceed once cycle.zrostatus == APPROVED.
        // It performs the normal DJB calculation -> generic demand save -> bill fetch.
        DJBMonthlyDemandService.DemandResult demandResult = demandService.createDemand(requestInfo, cycle);

        if (!demandResult.isDemandCreated() || demandResult.getDemand() == null
                || !StringUtils.hasText(demandResult.getDemand().getId())) {
            throw new IllegalStateException("ZRO approval did not create a demand for billing cycle " + billingCycleId);
        }

        Demand demand = demandResult.getDemand();
        cycle.setDemandid(demand.getId());
        cycle.setBillid(demandResult.getBillId());
        cycle.setStatus(StringUtils.hasText(demandResult.getBillId())
                ? BillingCycleStatus.BILL_GENERATED
                : BillingCycleStatus.DEMAND_CREATED);
        cycle.setLastmodifiedby(actor);
        cycle.setLastmodifiedtime(System.currentTimeMillis());
        billingCycleDao.update(cycle);

        return result(cycle, verification, demand.getId(), demandResult.getBillId(),
                "DJB 1.5x consumption approved; demand and bill generated");
    }

    private ZroVerificationResult result(
            WaterBillingCycle cycle,
            ZroVerification verification,
            String demandId,
            String billId,
            String message) {
        return ZroVerificationResult.builder()
                .verification(verification)
                .billingCycle(cycle)
                .demandId(demandId)
                .billId(billId)
                .message(message)
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class ZroVerificationResult {
        private ZroVerification verification;
        private WaterBillingCycle billingCycle;
        private String demandId;
        private String billId;
        private String message;
    }
}
