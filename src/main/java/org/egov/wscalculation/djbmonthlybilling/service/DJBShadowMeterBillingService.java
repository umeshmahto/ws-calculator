package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingCycleStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBReadingQualityCode;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.service.dto.BillingBasisDecision;
import org.egov.wscalculation.djbmonthlybilling.service.dto.ConsumptionResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.CorrectionPlan;
import org.egov.wscalculation.djbmonthlybilling.service.dto.MonthlyBillingCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.master.DJBMonthlyBillingMasterProvider;
import org.egov.wscalculation.service.MeterService;
import org.egov.wscalculation.web.models.MeterConnectionRequest;
import org.egov.wscalculation.web.models.MeterReading;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DJBShadowMeterBillingService {

    private final MeterService meterService;
    private final DJBMonthlyBillingMasterProvider masterProvider;
    private final DJBMonthlyBillingService monthlyBillingService;
    private final WaterBillingCycleDao billingCycleDao;
    private final CorrectionService correctionService;

    public DJBShadowMeterBillingService(
            MeterService meterService,
            DJBMonthlyBillingMasterProvider masterProvider,
            DJBMonthlyBillingService monthlyBillingService,
            WaterBillingCycleDao billingCycleDao,
            CorrectionService correctionService) {

        this.meterService = meterService;
        this.masterProvider = masterProvider;
        this.monthlyBillingService = monthlyBillingService;
        this.billingCycleDao = billingCycleDao;
        this.correctionService = correctionService;
    }

    @Transactional
    public List<MeterReading> createAndCalculate(
            MeterConnectionRequest request) {

        validate(request);

        MeterReading reading = request.getMeterReading();

        /*
         * Reuse the production meter-reading create path only for validation,
         * enrichment and persistence. Demand generation is explicitly disabled
         * in the copy so the existing legacy demand flow cannot run.
         */
        Boolean originalGenerateDemand = reading.getGenerateDemand();
        reading.setGenerateDemand(Boolean.FALSE);

        try {
            MeterConnectionRequest persistenceRequest = MeterConnectionRequest.builder().requestInfo(request.getRequestInfo())
            		.meterReading(reading).build();

            List<MeterReading> saved = meterService.createMeterReading(persistenceRequest);
            processDjbBilling(reading, request.getRequestInfo());
            return saved;

        } finally {
            reading.setGenerateDemand(originalGenerateDemand);
        }
    }

    private void processDjbBilling(MeterReading reading,RequestInfo requestInfo) {

        String tenantId = reading.getTenantId();
        String connectionNo = reading.getConnectionNo();

        DJBMonthlyBillingRule rule = masterProvider.getBillingRule(requestInfo, tenantId);
        DJBReadingQualityCode rqc = masterProvider.findReadingQualityCode(requestInfo,tenantId,reading.getReadingQualityCode());

        long from = reading.getLastReadingDate();
        long to = reading.getCurrentReadingDate();

        if (to < from) {
            long tmp = from;
            from = to;
            to = tmp;
        }

        WaterBillingCycle cycle = billingCycleDao.findByConnectionAndPeriod(tenantId,connectionNo,from,to);

        if (cycle == null) {
            cycle = new WaterBillingCycle();
            cycle.setId(UUID.randomUUID().toString());
            cycle.setTenantid(tenantId);
            cycle.setConnectionno(connectionNo);
            cycle.setBillingperiodfrom(from);
            cycle.setBillingperiodto(to);
            cycle.setMeterreadingid(reading.getId());
            cycle.setCreatedby(actor(requestInfo));
            cycle.setCreatedtime(System.currentTimeMillis());
        }

        cycle.setReadingqualitycode(reading.getReadingQualityCode());
        cycle.setCurrentreading(
                BigDecimal.valueOf(reading.getCurrentReading()));
        cycle.setCurrentreadingdate(reading.getCurrentReadingDate());

        /*
         * Previous OK is from DJB billing-cycle history. For the first test
         * reading, the meter API's lastReading is used as the baseline.
         */
        WaterBillingCycle previousOk =
                billingCycleDao.findPreviousOkByConnectionBefore(
                        tenantId,
                        connectionNo,
                        to);

        if (previousOk != null) {
            cycle.setPreviousokreading(previousOk.getCurrentreading());
            cycle.setPreviousokreadingdate(previousOk.getCurrentreadingdate());
        } else {
            cycle.setPreviousokreading(
                    BigDecimal.valueOf(reading.getLastReading()));
            cycle.setPreviousokreadingdate(reading.getLastReadingDate());
        }

        cycle.setStatus(BillingCycleStatus.CREATED);

        MonthlyBillingCalculationResult calculation =
                monthlyBillingService.determineCycle(
                        tenantId,
                        connectionNo,
                        cycle,
                        rqc,
                        rule);

        BillingBasisDecision decision = calculation.getBillingBasisDecision();
        ConsumptionResult result = calculation.getConsumptionResult();

        cycle.setActualconsumption(result.getActualConsumption());
        cycle.setAverageconsumption(result.getAverageConsumption());
        cycle.setBillingconsumption(result.getBillingConsumption());
        cycle.setPreviousconsumption(result.getPreviousConsumption());
        cycle.setDeviationfactor(result.getDeviationFactor());
        cycle.setOnepointfivexflag(result.isOnePointFiveX());
        cycle.setAveragecyclecount(decision.getAverageCycleCount());
        cycle.setProvisionalcyclecount(decision.getProvisionalCycleCount());

        /*
         * This shadow API only tests monthly-basis and consumption persistence.
         * Tariff/sewerage/rebate are tested by the separate calculation API
         * until the full monthly orchestrator is wired.
         */
        cycle.setBillingbasis(decision.getBillingBasis());

        cycle.setCorrectionstatus(CorrectionStatus.NOT_REQUIRED);

        if ("OK".equalsIgnoreCase(reading.getReadingQualityCode())) {
            CorrectionPlan correctionPlan =
                    correctionService.buildCorrectionPlan(
                            tenantId,
                            cycle);

            if (correctionPlan.isCorrectionRequired()) {
                correctionService.createPendingCorrection(
                        tenantId,
                        correctionPlan,
                        actor(requestInfo),
                        System.currentTimeMillis());
                cycle.setCorrectionstatus(CorrectionStatus.PENDING);
            }
        }

        cycle.setStatus(BillingCycleStatus.CALCULATED);
        cycle.setLastmodifiedby(actor(requestInfo));
        cycle.setLastmodifiedtime(System.currentTimeMillis());

        if (cycle.getCreatedtime() == null) {
            cycle.setCreatedtime(System.currentTimeMillis());
        }

        if (cycle.getCreatedby() == null) {
            cycle.setCreatedby(actor(requestInfo));
        }

        if (cycleExists(
                tenantId,
                connectionNo,
                from,
                to)) {
            billingCycleDao.update(cycle);
        } else {
            billingCycleDao.save(cycle);
        }
    }

    private boolean cycleExists(String tenantId,String connectionNo,long from,long to) {
        return billingCycleDao.findByConnectionAndPeriod(tenantId,connectionNo,from,to) != null;
    }

    private void validate(MeterConnectionRequest request) {

        if (request == null || request.getMeterReading() == null) {
            throw new IllegalArgumentException("meterReadings is required");
        }

        MeterReading reading = request.getMeterReading();

        if (!"dl.djb".equalsIgnoreCase(reading.getTenantId())) {
            throw new IllegalArgumentException("DJB shadow API only supports tenant dl.djb");
        }

        if (reading.getCurrentReading() == null
                || reading.getCurrentReadingDate() == null
                || reading.getLastReading() == null
                || reading.getLastReadingDate() == null) {
            throw new IllegalArgumentException("last/current reading and dates are required");
        }

        if (reading.getReadingQualityCode() == null) {
            throw new IllegalArgumentException("readingQualityCode is required");
        }
    }

    private String actor(RequestInfo requestInfo) {

        if (requestInfo != null && requestInfo.getUserInfo() != null) {

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
