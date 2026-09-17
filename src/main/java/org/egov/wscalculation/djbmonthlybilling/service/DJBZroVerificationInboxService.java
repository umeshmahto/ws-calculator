package org.egov.wscalculation.djbmonthlybilling.service;

import java.util.ArrayList;
import java.util.List;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.ZroVerification;
import org.egov.wscalculation.djbmonthlybilling.model.ZroVerificationInboxRecord;
import org.egov.wscalculation.djbmonthlybilling.repository.ZroVerificationDao;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBZroVerificationInboxItem;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBZroVerificationSearchRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBZroVerificationSearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DJBZroVerificationInboxService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ZroVerificationDao zroVerificationDao;

    public DJBZroVerificationInboxService(ZroVerificationDao zroVerificationDao) {
        this.zroVerificationDao = zroVerificationDao;
    }

    public DJBZroVerificationSearchResponse search(DJBZroVerificationSearchRequest request) {
        validateRequest(request);

        RequestInfo requestInfo = request.getRequestInfo();
        String tenantId = StringUtils.hasText(request.getTenantId())
                ? request.getTenantId()
                : requestInfo.getUserInfo().getTenantId();

        int offset = request.getOffset() == null ? 0 : request.getOffset();
        int limit = request.getLimit() == null ? DEFAULT_LIMIT : request.getLimit();
        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative");
        }
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }

        if (StringUtils.hasText(request.getStatus()) && !"PENDING".equalsIgnoreCase(request.getStatus())) {
            return DJBZroVerificationSearchResponse.builder().cases(new ArrayList<>()).count(0).build();
        }

        List<ZroVerificationInboxRecord> records = zroVerificationDao.searchPending(tenantId,
                StringUtils.hasText(request.getConnectionNo()) ? request.getConnectionNo() : null, limit, offset);

        List<DJBZroVerificationInboxItem> items = new ArrayList<>(records.size());
        for (ZroVerificationInboxRecord record : records) {
            items.add(toInboxItem(record));
        }

        return DJBZroVerificationSearchResponse.builder()
                .cases(items)
                .count(zroVerificationDao.countPending(tenantId,
                        StringUtils.hasText(request.getConnectionNo()) ? request.getConnectionNo() : null))
                .build();
    }

    private DJBZroVerificationInboxItem toInboxItem(ZroVerificationInboxRecord record) {
        ZroVerification verification = record.getVerification();
        Long normalizationFrom = DJBConsumptionPeriodUtil.resolveNormalizationStart(
                record.getPreviousokreadingdate(), record.getBillingperiodfrom());
        java.math.BigDecimal monthlyConsumption = DJBConsumptionPeriodUtil.toMonthlyConsumption(
                record.getActualconsumption(), normalizationFrom, record.getBillingperiodto());
        java.math.BigDecimal billingDays = DJBConsumptionPeriodUtil.calculateElapsedDays(
                normalizationFrom, record.getBillingperiodto());

        return DJBZroVerificationInboxItem.builder()
                .verification(verification)
                .billingCycleId(record.getBillingcycleid())
                .connectionNo(verification.getConnectionno())
                .billingPeriodFrom(record.getBillingperiodfrom())
                .billingPeriodTo(record.getBillingperiodto())
                .previousReading(record.getPreviousreading())
                .previousOkReadingDate(record.getPreviousokreadingdate())
                .currentReading(record.getCurrentreading())
                .readingQualityCode(record.getReadingqualitycode())
                .billingBasis(record.getBillingbasis())
                .actualConsumption(record.getActualconsumption())
                .monthlyConsumption(monthlyConsumption)
                .billingDays(billingDays)
                .thresholdConsumption(verification.getPreviousconsumption() == null ? null
                        : verification.getPreviousconsumption().multiply(new java.math.BigDecimal("1.5")))
                .onePointFiveXFlag(record.getOnepointfivexflag())
                .billingCycleStatus(record.getBillingcyclestatus())
                .correctionStatus(record.getCorrectionstatus())
                .calculationId(record.getCalculationid())
                .demandId(record.getDemandid())
                .billId(record.getBillid())
                .build();
    }

    private void validateRequest(DJBZroVerificationSearchRequest request) {
        if (request == null || request.getRequestInfo() == null || request.getRequestInfo().getUserInfo() == null) {
            throw new IllegalArgumentException("RequestInfo.userInfo is required");
        }
        if (!StringUtils.hasText(request.getRequestInfo().getUserInfo().getTenantId())) {
            throw new IllegalArgumentException("RequestInfo.userInfo.tenantId is required");
        }
        if (StringUtils.hasText(request.getTenantId())
                && !request.getTenantId().equalsIgnoreCase(request.getRequestInfo().getUserInfo().getTenantId())) {
            throw new IllegalArgumentException("tenantId does not match RequestInfo.userInfo.tenantId");
        }
    }
}
