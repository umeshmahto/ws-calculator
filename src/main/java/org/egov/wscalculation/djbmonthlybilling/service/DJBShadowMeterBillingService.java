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
import org.egov.wscalculation.djbmonthlybilling.service.CorrectionService.CorrectionPlanResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.BillingBasisDecision;
import org.egov.wscalculation.djbmonthlybilling.service.dto.ConsumptionResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.MonthlyBillingCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.master.DJBMonthlyBillingMasterProvider;
import org.egov.wscalculation.repository.WSCalculationDao;
import org.egov.wscalculation.service.EnrichmentService;
import org.egov.wscalculation.web.models.MeterConnectionRequest;
import org.egov.wscalculation.web.models.MeterReading;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class DJBShadowMeterBillingService {

	private final WSCalculationDao wSCalculationDao;
	private final EnrichmentService enrichmentService;
	private final DJBMonthlyBillingMasterProvider masterProvider;
	private final DJBMonthlyBillingService monthlyBillingService;
	private final WaterBillingCycleDao billingCycleDao;
	private final CorrectionService correctionService;
	private final DJBMonthlyDemandService demandService;
	private final TransactionTemplate transactionTemplate;

	public DJBShadowMeterBillingService(WSCalculationDao wSCalculationDao, EnrichmentService enrichmentService,
			DJBMonthlyBillingMasterProvider masterProvider, DJBMonthlyBillingService monthlyBillingService,
			WaterBillingCycleDao billingCycleDao, CorrectionService correctionService,
			DJBMonthlyDemandService demandService, PlatformTransactionManager transactionManager) {

		this.wSCalculationDao = wSCalculationDao;
		this.enrichmentService = enrichmentService;
		this.masterProvider = masterProvider;
		this.monthlyBillingService = monthlyBillingService;
		this.billingCycleDao = billingCycleDao;
		this.correctionService = correctionService;
		this.demandService = demandService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public List<MeterReading> createAndCalculate(MeterConnectionRequest request) {

		validate(request);

		MeterReading reading = request.getMeterReading();

		/*
		 * Shadow endpoint persists the reading directly through the DAO and then runs
		 * the exact same DJB monthly-billing orchestration used by the real
		 * /meterConnection/_create flow. This avoids a MeterService ->
		 * DJBShadowMeterBillingService -> MeterService circular dependency while
		 * keeping the legacy demand path out.
		 */
		Boolean originalGenerateDemand = reading.getGenerateDemand();
		reading.setGenerateDemand(Boolean.FALSE);

		try {
			reading.setStatus(null);
			enrichmentService.enrichMeterReadingRequest(request.getRequestInfo(), reading);
			MeterConnectionRequest persistenceRequest = MeterConnectionRequest.builder()
					.requestInfo(request.getRequestInfo()).meterReading(reading).build();
			wSCalculationDao.saveMeterReading(persistenceRequest);

			processDjbBilling(reading, request.getRequestInfo());

			return java.util.Collections.singletonList(reading);

		} finally {
			reading.setGenerateDemand(originalGenerateDemand);
		}
	}

	public void processDjbBilling(MeterReading reading, RequestInfo requestInfo) {

		String tenantId = reading.getTenantId();
		String connectionNo = reading.getConnectionNo();

		/*
		 * External MDMS calls and DJB calculation are deliberately executed outside a
		 * database transaction. Only short local persistence operations below are
		 * transactional. This prevents a slow/unavailable external service from holding
		 * database locks and prevents a remote failure from rolling back unrelated
		 * local work.
		 */
		DJBMonthlyBillingRule rule = masterProvider.getBillingRule(requestInfo, tenantId);
		DJBReadingQualityCode rqc = masterProvider.findReadingQualityCode(requestInfo, tenantId,
				reading.getReadingQualityCode());

		long from = Math.min(reading.getLastReadingDate(), reading.getCurrentReadingDate());
		long to = Math.max(reading.getLastReadingDate(), reading.getCurrentReadingDate());

		WaterBillingCycle cycle = billingCycleDao.findByConnectionAndPeriod(tenantId, connectionNo, from, to);
		boolean existing = cycle != null;

		if (existing && (StringUtils.hasText(cycle.getBillid())
				|| BillingCycleStatus.BILL_GENERATED.equals(cycle.getStatus()))) {
			throw new IllegalStateException(
					"Billing cycle already has a generated bill for the same period. Use correction/revision flow instead of meter _create: "
							+ cycle.getId());
		}

		if (!existing) {
			cycle = new WaterBillingCycle();
			cycle.setId(UUID.randomUUID().toString());
			cycle.setTenantid(tenantId);
			cycle.setConnectionno(connectionNo);
			cycle.setBillingperiodfrom(from);
			cycle.setBillingperiodto(to);
			cycle.setCreatedby(actor(requestInfo));
			cycle.setCreatedtime(System.currentTimeMillis());
		}

		cycle.setMeterreadingid(reading.getId());
		cycle.setReadingqualitycode(reading.getReadingQualityCode());
		cycle.setCurrentreading(BigDecimal.valueOf(reading.getCurrentReading()));
		cycle.setCurrentreadingdate(reading.getCurrentReadingDate());

		WaterBillingCycle previousOk = billingCycleDao.findPreviousOkByConnectionBefore(tenantId, connectionNo, to);
		if (previousOk != null) {
			cycle.setPreviousokreading(previousOk.getCurrentreading());
			cycle.setPreviousokreadingdate(previousOk.getCurrentreadingdate());
		} else {
			cycle.setPreviousokreading(BigDecimal.valueOf(reading.getLastReading()));
			cycle.setPreviousokreadingdate(reading.getLastReadingDate());
		}

		cycle.setStatus(BillingCycleStatus.CREATED);

		MonthlyBillingCalculationResult calculation = monthlyBillingService.determineCycle(tenantId, connectionNo,
				cycle, rqc, rule);
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
		cycle.setBillingbasis(decision.getBillingBasis());
		cycle.setCorrectionstatus(CorrectionStatus.NOT_REQUIRED);
		cycle.setStatus(BillingCycleStatus.CALCULATED);
		cycle.setLastmodifiedby(actor(requestInfo));
		cycle.setLastmodifiedtime(System.currentTimeMillis());

		final WaterBillingCycle calculatedCycle = cycle;
		final boolean cycleExisted = existing;
		transactionTemplate.executeWithoutResult(status -> {
			WaterBillingCycle current = billingCycleDao.findByConnectionAndPeriod(tenantId, connectionNo, from, to);
			if (!cycleExisted && current != null) {
				throw new IllegalStateException(
						"Billing cycle was created concurrently for the same connection and period: "
								+ current.getId());
			}
			if (cycleExisted && current != null && (StringUtils.hasText(current.getBillid())
					|| BillingCycleStatus.BILL_GENERATED.equals(current.getStatus()))) {
				throw new IllegalStateException(
						"Billing cycle already has a generated bill for the same period: " + current.getId());
			}
			if (cycleExisted) {
				billingCycleDao.update(calculatedCycle);
			} else {
				billingCycleDao.save(calculatedCycle);
			}
		});

		processCalculatedCycleAfterDecision(requestInfo, cycle);
	}


	/**
	 * Completes billing after the DJB billing-basis decision is persisted.
	 *
	 * For a 1.5x domestic cycle, correction/demand creation is deliberately held
	 * until ZRO APPROVE. A rejected cycle is handled separately by the fallback
	 * provisional path.
	 */
	public void processCalculatedCycleAfterDecision(RequestInfo requestInfo, WaterBillingCycle cycle) {
		if (cycle == null) {
			throw new IllegalArgumentException("Billing cycle is required");
		}

		/*
		 * A flagged domestic 1.5x cycle must not create a correction plan or demand
		 * before ZRO approves it. This is the key separation between the verification
		 * decision and the billing decision.
		 */
		if (Boolean.TRUE.equals(cycle.getOnepointfivexflag())
				&& !org.egov.wscalculation.djbmonthlybilling.model.enums.ZroStatus.APPROVED
						.equals(cycle.getZrostatus())) {
			if (cycle.getStatus() != BillingCycleStatus.CALCULATED) {
				cycle.setStatus(BillingCycleStatus.CALCULATED);
				persistCycleUpdate(cycle);
			}
			return;
		}

		CorrectionPlanResult correction = null;

		if (BillingBasis.ACTUAL.equals(cycle.getBillingbasis())) {
			correction = correctionService.processAutomaticCorrection(requestInfo, cycle.getTenantid(), cycle,
					actor(requestInfo), System.currentTimeMillis());

			if (correction != null && correction.isCorrectionRequired()) {
				cycle.setCorrectionstatus(CorrectionStatus.PENDING);
				persistCycleUpdate(cycle);
			}
		}

		BigDecimal paidAdjustmentAmount = BigDecimal.ZERO;
		if (correction != null && correction.getPlan() != null) {
			paidAdjustmentAmount = correction.getPlan().getPaidAdjustmentAmount();
		}

		DJBMonthlyDemandService.DemandResult demandResult = demandService.createDemand(requestInfo, cycle,
				paidAdjustmentAmount);

		if (!demandResult.isDemandCreated()) {
			if (demandResult.isZroRequired()) {
				cycle.setStatus(BillingCycleStatus.CALCULATED);
				persistCycleUpdate(cycle);
			}
			return;
		}

		if (correction != null && correction.getPlan() != null
				&& demandResult.getAppliedPaidAdjustmentAmount() != null
				&& demandResult.getResidualPaidCreditAmount() != null) {
			correction.getPlan().setAppliedPaidAdjustmentAmount(demandResult.getAppliedPaidAdjustmentAmount());
			correction.getPlan().setResidualPaidCreditAmount(demandResult.getResidualPaidCreditAmount());
			correction.getCorrection().setAppliedpaidadjustmentamount(demandResult.getAppliedPaidAdjustmentAmount());
			correction.getCorrection().setResidualpaidcreditamount(demandResult.getResidualPaidCreditAmount());
			correction.getCorrection().setLastmodifiedby(actor(requestInfo));
			correction.getCorrection().setLastmodifiedtime(System.currentTimeMillis());
			correctionService.updateCorrectionAmounts(cycle.getTenantid(), correction.getCorrection());
		}

		if (demandResult.getDemand() != null && StringUtils.hasText(demandResult.getDemand().getId())) {
			cycle.setDemandid(demandResult.getDemand().getId());
		}

		if (CorrectionStatus.PENDING.equals(cycle.getCorrectionstatus())) {
			if (correction != null && correction.getPlan() != null) {
				correctionService.deactivateSupersededDemands(requestInfo, cycle.getTenantid(), correction.getPlan());
			}

			if (!StringUtils.hasText(cycle.getBillid())) {
				try {
					String correctedBillId = demandResult.getDemand() != null
							? demandService.fetchBillForExistingDemand(requestInfo, demandResult.getDemand())
							: demandService.fetchBillForExistingDemand(requestInfo, cycle.getTenantid(), cycle.getConnectionno());

					if (StringUtils.hasText(correctedBillId)) {
						cycle.setBillid(correctedBillId);
						cycle.setStatus(BillingCycleStatus.BILL_GENERATED);
						if (correction != null && correction.getPlan() != null) {
							correctionService.completeAutomaticCorrection(cycle.getTenantid(), correction.getPlan(),
									cycle.getDemandid(), correctedBillId, actor(requestInfo), System.currentTimeMillis());
							cycle.setCorrectionstatus(CorrectionStatus.COMPLETED);
						}
					}
				} catch (RuntimeException ex) {
					cycle.setStatus(BillingCycleStatus.DEMAND_CREATED);
					cycle.setCorrectionstatus(CorrectionStatus.PENDING);
				}
			} else {
				cycle.setStatus(BillingCycleStatus.BILL_GENERATED);
				if (correction != null && correction.getPlan() != null) {
					correctionService.completeAutomaticCorrection(cycle.getTenantid(), correction.getPlan(),
							cycle.getDemandid(), cycle.getBillid(), actor(requestInfo), System.currentTimeMillis());
					cycle.setCorrectionstatus(CorrectionStatus.COMPLETED);
				}
			}
		} else if (StringUtils.hasText(demandResult.getBillId())) {
			cycle.setBillid(demandResult.getBillId());
			cycle.setStatus(BillingCycleStatus.BILL_GENERATED);
		} else if (!StringUtils.hasText(cycle.getBillid()) && StringUtils.hasText(cycle.getDemandid())) {
			/*
			 * Recovery path for a demand that was persisted successfully while bill
			 * generation failed. The existing demand is reused; no second demand is created.
			 */
			try {
				String recoveredBillId = demandService.fetchBillForExistingDemand(requestInfo, cycle.getTenantid(),
						cycle.getConnectionno());
				if (StringUtils.hasText(recoveredBillId)) {
					cycle.setBillid(recoveredBillId);
					cycle.setStatus(BillingCycleStatus.BILL_GENERATED);
				} else {
					cycle.setStatus(BillingCycleStatus.DEMAND_CREATED);
				}
			} catch (RuntimeException ex) {
				cycle.setStatus(BillingCycleStatus.DEMAND_CREATED);
			}
		} else {
			cycle.setStatus(BillingCycleStatus.DEMAND_CREATED);
		}

		cycle.setLastmodifiedby(actor(requestInfo));
		cycle.setLastmodifiedtime(System.currentTimeMillis());
		persistCycleUpdate(cycle);
	}

	private void persistCycleUpdate(WaterBillingCycle cycle) {
		transactionTemplate.executeWithoutResult(status -> billingCycleDao.update(cycle));
	}

	/**
	 * Rejects a new meter-reading request only when the same billing period is
	 * already finalized. A cycle with a missing demand/bill is intentionally
	 * allowed so a previous partially completed external call can be retried
	 * safely.
	 */
	public void validateCanCreateBillingCycle(MeterReading reading) {
		if (reading == null || reading.getTenantId() == null || reading.getConnectionNo() == null
				|| reading.getLastReadingDate() == null || reading.getCurrentReadingDate() == null) {
			return;
		}
		if (!"dl.djb".equalsIgnoreCase(reading.getTenantId())) {
			return;
		}
		long from = Math.min(reading.getLastReadingDate(), reading.getCurrentReadingDate());
		long to = Math.max(reading.getLastReadingDate(), reading.getCurrentReadingDate());
		WaterBillingCycle existing = billingCycleDao.findByConnectionAndPeriod(reading.getTenantId(),
				reading.getConnectionNo(), from, to);
		if (existing != null && (StringUtils.hasText(existing.getBillid())
				|| BillingCycleStatus.BILL_GENERATED.equals(existing.getStatus()))) {
			throw new IllegalStateException("A billing cycle with the same connection and period is already billed: "
					+ existing.getId() + ". Use correction/revision flow instead.");
		}
	}

	private void validate(MeterConnectionRequest request) {

		if (request == null || request.getMeterReading() == null) {
			throw new IllegalArgumentException("meterReadings is required");
		}

		MeterReading reading = request.getMeterReading();

		if (!"dl.djb".equalsIgnoreCase(reading.getTenantId())) {
			throw new IllegalArgumentException("DJB shadow API only supports tenant dl.djb");
		}

		if (reading.getCurrentReading() == null || reading.getCurrentReadingDate() == null
				|| reading.getLastReading() == null || reading.getLastReadingDate() == null) {
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
