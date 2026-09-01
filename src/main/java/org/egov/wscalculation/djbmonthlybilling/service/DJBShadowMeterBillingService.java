package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingCycleStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBReadingQualityCode;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.service.dto.BillingBasisDecision;
import org.egov.wscalculation.djbmonthlybilling.service.dto.ConsumptionResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.MonthlyBillingCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.master.DJBMonthlyBillingMasterProvider;
import org.egov.wscalculation.service.MeterService;
import org.egov.wscalculation.web.models.MeterConnectionRequest;
import org.egov.wscalculation.web.models.MeterReading;
import org.egov.wscalculation.djbmonthlybilling.service.CorrectionService.CorrectionPlanResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DJBShadowMeterBillingService {

    private final MeterService meterService;
    private final DJBMonthlyBillingMasterProvider masterProvider;
    private final DJBMonthlyBillingService monthlyBillingService;
    private final WaterBillingCycleDao billingCycleDao;
    private final CorrectionService correctionService;
    private final DJBMonthlyDemandService demandService;

    public DJBShadowMeterBillingService(MeterService meterService,
            DJBMonthlyBillingMasterProvider masterProvider,
            DJBMonthlyBillingService monthlyBillingService,
            WaterBillingCycleDao billingCycleDao,
            CorrectionService correctionService,
            DJBMonthlyDemandService demandService) {

        this.meterService = meterService;
        this.masterProvider = masterProvider;
        this.monthlyBillingService = monthlyBillingService;
        this.billingCycleDao = billingCycleDao;
        this.correctionService = correctionService;
        this.demandService = demandService;
    }

    @Transactional
    public List<MeterReading> createAndCalculate(
            MeterConnectionRequest request) {

        validate(request);

        MeterReading reading = request.getMeterReading();

        /*
         * Shadow endpoint reuses the existing meter create flow for persistence
         * only. It must not invoke the generic current-reading minus last-reading
         * demand path.
         */
        Boolean originalGenerateDemand =
                reading.getGenerateDemand();
        reading.setGenerateDemand(Boolean.FALSE);

        try {
            MeterConnectionRequest persistenceRequest =
                    MeterConnectionRequest.builder()
                            .requestInfo(request.getRequestInfo())
                            .meterReading(reading)
                            .build();

            List<MeterReading> saved =
                    meterService.createMeterReading(
                            persistenceRequest);

            processDjbBilling(
                    reading,
                    request.getRequestInfo());

            return saved;

        } finally {
            reading.setGenerateDemand(originalGenerateDemand);
        }
    }

    private void processDjbBilling(
            MeterReading reading,
            RequestInfo requestInfo) {

        String tenantId = reading.getTenantId();
        String connectionNo = reading.getConnectionNo();

        DJBMonthlyBillingRule rule =
                masterProvider.getBillingRule(
                        requestInfo, tenantId);

        DJBReadingQualityCode rqc =
                masterProvider.findReadingQualityCode(
                        requestInfo,
                        tenantId,
                        reading.getReadingQualityCode());

        long from = reading.getLastReadingDate();
        long to = reading.getCurrentReadingDate();

        if (to < from) {
            long tmp = from;
            from = to;
            to = tmp;
        }

        WaterBillingCycle cycle =
                billingCycleDao.findByConnectionAndPeriod(
                        tenantId,
                        connectionNo,
                        from,
                        to);

        boolean existing = cycle != null;

        if (!existing) {
            cycle = new WaterBillingCycle();
            cycle.setId(UUID.randomUUID().toString());
            cycle.setTenantid(tenantId);
            cycle.setConnectionno(connectionNo);
            cycle.setBillingperiodfrom(from);
            cycle.setBillingperiodto(to);
            cycle.setMeterreadingid(reading.getId());
            cycle.setCreatedby(actor(requestInfo));
            cycle.setCreatedtime(System.currentTimeMillis());
        } else {
            /*
             * A repeat of the same billing period is an update, not a second
             * demand. Keep the same cycle ID and only update its calculation.
             */
            cycle.setMeterreadingid(reading.getId());
        }

        cycle.setReadingqualitycode(
                reading.getReadingQualityCode());

        cycle.setCurrentreading(
                BigDecimal.valueOf(
                        reading.getCurrentReading()));

        cycle.setCurrentreadingdate(
                reading.getCurrentReadingDate());

        WaterBillingCycle previousOk =
                billingCycleDao.findPreviousOkByConnectionBefore(
                        tenantId,
                        connectionNo,
                        to);

        if (previousOk != null) {
            cycle.setPreviousokreading(
                    previousOk.getCurrentreading());
            cycle.setPreviousokreadingdate(
                    previousOk.getCurrentreadingdate());
        } else {
            cycle.setPreviousokreading(
                    BigDecimal.valueOf(
                            reading.getLastReading()));
            cycle.setPreviousokreadingdate(
                    reading.getLastReadingDate());
        }

        cycle.setStatus(BillingCycleStatus.CREATED);

        MonthlyBillingCalculationResult calculation =
                monthlyBillingService.determineCycle(
                        tenantId,
                        connectionNo,
                        cycle,
                        rqc,
                        rule);

        BillingBasisDecision decision =
                calculation.getBillingBasisDecision();

        ConsumptionResult result =
                calculation.getConsumptionResult();

        cycle.setActualconsumption(
                result.getActualConsumption());
        cycle.setAverageconsumption(
                result.getAverageConsumption());
        cycle.setBillingconsumption(
                result.getBillingConsumption());
        cycle.setPreviousconsumption(
                result.getPreviousConsumption());
        cycle.setDeviationfactor(
                result.getDeviationFactor());
        cycle.setOnepointfivexflag(
                result.isOnePointFiveX());

        /*
         * IMPORTANT: keep the billing-basis decision and both cycle counters
         * produced by BillingBasisService. These values drive the DJB rule
         * for the first two average rounds and the post-average round.
         */
        cycle.setAveragecyclecount(
                decision.getAverageCycleCount());
        cycle.setProvisionalcyclecount(
                decision.getProvisionalCycleCount());
        cycle.setBillingbasis(
                decision.getBillingBasis());

        cycle.setCorrectionstatus(
                CorrectionStatus.NOT_REQUIRED);
        cycle.setStatus(
                BillingCycleStatus.CALCULATED);
        cycle.setLastmodifiedby(actor(requestInfo));
        cycle.setLastmodifiedtime(System.currentTimeMillis());

        if (!existing) {
            billingCycleDao.save(cycle);
        } else {
            billingCycleDao.update(cycle);
        }

        /*
         * A later OK reading automatically creates a correction plan for the
         * intervening estimated cycles. No manual bill-cancel API is invoked.
         */
        if (BillingBasis.ACTUAL.equals(cycle.getBillingbasis())) {
            CorrectionPlanResult correction =
                    correctionService.processAutomaticCorrection(
                            tenantId,
                            cycle,
                            actor(requestInfo),
                            System.currentTimeMillis());

            if (correction != null
                    && correction.isCorrectionRequired()) {
                cycle.setCorrectionstatus(
                        CorrectionStatus.PENDING);
                billingCycleDao.update(cycle);
            }
        }

        /*
         * Generate the generic UPYOG demand from the DJB-calculated amounts.
         * billing-service itself remains generic.
         */
        DJBMonthlyDemandService.DemandResult demandResult =
                demandService.createDemand(
                        requestInfo,
                        cycle);

        if (demandResult.isDemandCreated()
                && demandResult.getDemand() != null) {

            cycle.setDemandid(
                    demandResult.getDemand().getId());
            cycle.setStatus(
                    BillingCycleStatus.DEMAND_CREATED);

            /*
             * Keep the correction flag if this is an automatic corrected-actual
             * period; otherwise it remains NOT_REQUIRED.
             */
            if (CorrectionStatus.PENDING.equals(
                    cycle.getCorrectionstatus())) {
                cycle.setLastmodifiedby(actor(requestInfo));
                cycle.setLastmodifiedtime(
                        System.currentTimeMillis());
            }

            billingCycleDao.update(cycle);
        } else if (demandResult.isZroRequired()) {
            cycle.setStatus(
                    BillingCycleStatus.CALCULATED);
            billingCycleDao.update(cycle);
        }
    }

    private void validate(
            MeterConnectionRequest request) {

        if (request == null
                || request.getMeterReading() == null) {
            throw new IllegalArgumentException(
                    "meterReadings is required");
        }

        MeterReading reading =
                request.getMeterReading();

        if (!"dl.djb".equalsIgnoreCase(
                reading.getTenantId())) {
            throw new IllegalArgumentException(
                    "DJB shadow API only supports tenant dl.djb");
        }

        if (reading.getCurrentReading() == null
                || reading.getCurrentReadingDate() == null
                || reading.getLastReading() == null
                || reading.getLastReadingDate() == null) {
            throw new IllegalArgumentException(
                    "last/current reading and dates are required");
        }

        if (reading.getReadingQualityCode() == null) {
            throw new IllegalArgumentException(
                    "readingQualityCode is required");
        }
    }

    private String actor(RequestInfo requestInfo) {

        if (requestInfo != null
                && requestInfo.getUserInfo() != null) {

            if (requestInfo.getUserInfo().getUuid() != null) {
                return requestInfo.getUserInfo().getUuid();
            }

            if (requestInfo.getUserInfo().getUserName() != null) {
                return requestInfo.getUserInfo().getUserName();
            }
        }

        return "SYSTEM";
    }
}
