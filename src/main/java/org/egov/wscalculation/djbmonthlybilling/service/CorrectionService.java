package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.BillingCorrection;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;
import org.egov.wscalculation.djbmonthlybilling.repository.BillingCorrectionDao;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.service.dto.CorrectionPlan;
import org.egov.wscalculation.repository.DemandRepository;
import org.egov.wscalculation.service.DemandService;
import org.egov.wscalculation.web.models.Demand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds the DJB automatic-correction plan when an OK reading is received after
 * estimated billing cycles.
 *
 * This service coordinates DJB correction state, captures any paid amount from
 * superseded average/provisional demands, and deactivates the superseded demand set.
 * The generic billing-service demand-create flow already moves the previous active
 * bill to historical state when the corrected demand is created, so DJB correction
 * does not call the generic bill-cancellation API.
 */
@Service
@Slf4j
public class CorrectionService {

	private final WaterBillingCycleDao billingCycleDao;
	private final BillingCorrectionDao billingCorrectionDao;
	private final DemandRepository demandRepository;
	private final DemandService demandService;
	private final ResidualCreditService residualCreditService;

	public CorrectionService(WaterBillingCycleDao billingCycleDao, BillingCorrectionDao billingCorrectionDao,
			DemandRepository demandRepository, DemandService demandService, ResidualCreditService residualCreditService) {
		this.billingCycleDao = billingCycleDao;
		this.billingCorrectionDao = billingCorrectionDao;
		this.demandRepository = demandRepository;
		this.demandService = demandService;
		this.residualCreditService = residualCreditService;
	}

	public CorrectionPlan buildCorrectionPlan(String tenantId, WaterBillingCycle currentOkCycle) {

		validateCurrentOkCycle(currentOkCycle);

		WaterBillingCycle previousOkCycle = billingCycleDao.findPreviousOkByConnectionBefore(tenantId,
				currentOkCycle.getConnectionno(), currentOkCycle.getBillingperiodto());

		if (previousOkCycle == null) {
			return CorrectionPlan.builder().connectionNo(currentOkCycle.getConnectionno())
					.currentOkBillingCycleId(currentOkCycle.getId())
					.currentOkReading(currentOkCycle.getCurrentreading())
					.cyclesToCorrect(new ArrayList<WaterBillingCycle>()).correctionRequired(false)
					.reason("No previous OK billing cycle found")
					.correctedConsumption(currentOkCycle.getCurrentreading()).build();
		}

		List<WaterBillingCycle> interveningCycles = billingCycleDao.findCyclesForCorrection(tenantId,
				currentOkCycle.getConnectionno(), previousOkCycle.getBillingperiodto(),
				currentOkCycle.getBillingperiodto());

		List<WaterBillingCycle> eligibleCycles = new ArrayList<>();

		if (interveningCycles != null) {
			for (WaterBillingCycle cycle : interveningCycles) {
				if (cycle == null || cycle.getBillingbasis() == null) {
					continue;
				}

				/*
				 * Only estimated billing is corrected automatically. An ACTUAL/CORRECTED_ACTUAL
				 * cycle terminates that assumption and therefore must not be cancelled by this
				 * plan.
				 */
				if (BillingBasis.AVERAGE.equals(cycle.getBillingbasis())
						|| BillingBasis.PROVISIONAL.equals(cycle.getBillingbasis())) {
					eligibleCycles.add(cycle);
				}
			}
		}

		BigDecimal previousReading = previousOkCycle.getCurrentreading();
		BigDecimal currentReading = currentOkCycle.getCurrentreading();

		if (previousReading == null || currentReading == null) {
			throw new IllegalStateException("Previous and current OK readings are required for correction");
		}

		BigDecimal correctedConsumption = currentReading.subtract(previousReading);

		if (correctedConsumption.signum() < 0) {
			throw new IllegalStateException("Current OK reading cannot be lower than previous OK reading");
		}

		return CorrectionPlan.builder().connectionNo(currentOkCycle.getConnectionno())
				.previousOkBillingCycleId(previousOkCycle.getId()).currentOkBillingCycleId(currentOkCycle.getId())
				.previousOkReading(previousReading).currentOkReading(currentReading)
				.correctedConsumption(correctedConsumption).cyclesToCorrect(eligibleCycles)
				.correctionRequired(!eligibleCycles.isEmpty())
				.reason(eligibleCycles.isEmpty() ? "No intervening average/provisional billing cycles"
						: "Intervening estimated billing cycles require automatic correction")
				.build();
	}

	@Transactional
	public BillingCorrection createPendingCorrection(String tenantId, CorrectionPlan plan, String actor,
			long currentTime) {

		if (plan == null || !plan.isCorrectionRequired()) {
			return null;
		}

		List<BillingCorrection> history = billingCorrectionDao.findByConnection(tenantId, plan.getConnectionNo());

		if (history != null) {
			for (BillingCorrection correction : history) {
				if (plan.getCurrentOkBillingCycleId().equals(correction.getTobillingcycleid())
						&& !CorrectionStatus.FAILED.equals(correction.getStatus())) {
					if (!CorrectionStatus.COMPLETED.equals(correction.getStatus())) {
						correction.setPaidadjustmentamount(normalizeMoney(plan.getPaidAdjustmentAmount()));
						plan.setAppliedPaidAdjustmentAmount(normalizeMoney(correction.getAppliedpaidadjustmentamount()));
						plan.setResidualPaidCreditAmount(normalizeMoney(correction.getResidualpaidcreditamount()));
						correction.setLastmodifiedby(actor);
						correction.setLastmodifiedtime(currentTime);
						billingCorrectionDao.update(correction);
					}
					return correction;
				}
			}
		}

		BillingCorrection correction = new BillingCorrection();

		correction.setId(UUID.randomUUID().toString());

		correction.setTenantid(tenantId);
		correction.setConnectionno(plan.getConnectionNo());
		correction.setFrombillingcycleid(plan.getPreviousOkBillingCycleId());
		correction.setTobillingcycleid(plan.getCurrentOkBillingCycleId());
		correction.setStatus(CorrectionStatus.PENDING);
		correction.setReason(plan.getReason());
		correction.setPaidadjustmentamount(normalizeMoney(plan.getPaidAdjustmentAmount()));
		correction.setAppliedpaidadjustmentamount(normalizeMoney(plan.getAppliedPaidAdjustmentAmount()));
		correction.setResidualpaidcreditamount(normalizeMoney(plan.getResidualPaidCreditAmount()));
		correction.setCreatedby(actor);
		correction.setCreatedtime(currentTime);
		correction.setLastmodifiedby(actor);
		correction.setLastmodifiedtime(currentTime);

		billingCorrectionDao.save(correction);

		return correction;
	}

	@Transactional
	public void updateCorrectionAmounts(String tenantId, BillingCorrection correction) {
		if (correction == null || !StringUtils.hasText(correction.getId())) {
			throw new IllegalArgumentException("Correction record is required to update paid adjustment amounts");
		}
		correction.setTenantid(tenantId);
		billingCorrectionDao.update(correction);
	}

	/**
	 * Deactivates the demands that are superseded by an automatic OK-to-OK
	 * correction before the corrected bill is fetched.
	 *
	 * The previous OK demand is still deactivated so that its original assessment
	 * is not billed again with the consolidated OK-to-OK demand. Its payment,
	 * however, is NOT treated as a correction credit because that payment belongs
	 * to the valid previous-OK assessment.
	 *
	 * For intervening average/provisional demands, any already-collected amount is
	 * captured in CorrectionPlan.paidAdjustmentAmount and is represented as a
	 * negative adjustment on the new corrected demand.
	 */
	public void deactivateSupersededDemands(RequestInfo requestInfo, String tenantId, CorrectionPlan plan) {

		if (plan == null || !plan.isCorrectionRequired()) {
			throw new IllegalArgumentException("Correction plan is required to deactivate superseded demands");
		}

		WaterBillingCycle previousOk = billingCycleDao.findById(tenantId, plan.getPreviousOkBillingCycleId());

		List<WaterBillingCycle> supersededCycles = new ArrayList<>();
		if (previousOk != null) {
			supersededCycles.add(previousOk);
		}
		if (plan.getCyclesToCorrect() != null) {
			supersededCycles.addAll(plan.getCyclesToCorrect());
		}

		List<Demand> demandsToCancel = new ArrayList<>();
		BigDecimal observedPaidAdjustment = BigDecimal.ZERO;

		/*
		 * Validate and collect the exact payment state again immediately before
		 * cancellation. The corrected demand was already created using the preflight
		 * amount stored in the plan; if the payment state changed in between, stop
		 * rather than producing a mismatched financial adjustment.
		 */
		for (WaterBillingCycle cycle : supersededCycles) {
			if (cycle == null || !StringUtils.hasText(cycle.getDemandid())) {
				continue;
			}

			Demand target = findDemandForCycle(requestInfo, tenantId, plan.getConnectionNo(), cycle);

			if (target == null) {
				throw new IllegalStateException("Superseded demand not found for billing cycle " + cycle.getId()
						+ ", demand " + cycle.getDemandid());
			}

			BigDecimal collected = calculateCollectedAmount(target);

			// Only intervening estimated cycles contribute a correction credit.
			if (isEligibleCorrectionCycle(cycle)) {
				observedPaidAdjustment = observedPaidAdjustment.add(collected);
			}

			if (Demand.StatusEnum.CANCELLED.equals(target.getStatus())) {
				continue;
			}

			// A paid previous-OK assessment is valid history, not a correction credit.
			if (collected.signum() > 0 && !isPreviousOkCycle(cycle, plan)) {
				// The amount is already captured in the plan; cancellation is safe because
				// the corrected demand carries the matching negative adjustment.
				logPaymentAdjustment(cycle, target, collected);
			}

			target.setStatus(Demand.StatusEnum.CANCELLED);
			demandsToCancel.add(target);
		}

		BigDecimal expectedPaidAdjustment = normalizeMoney(plan.getPaidAdjustmentAmount());
		if (observedPaidAdjustment.compareTo(expectedPaidAdjustment) != 0) {
			throw new IllegalStateException(
					"Paid DJB correction amount changed during correction for " + plan.getConnectionNo()
							+ ". Expected " + expectedPaidAdjustment + " but found "
							+ observedPaidAdjustment);
		}

		if (!demandsToCancel.isEmpty()) {
			List<Demand> updated = demandRepository.updateDemand(requestInfo, demandsToCancel);

			if (updated == null || updated.size() != demandsToCancel.size()) {
				throw new IllegalStateException("Billing-service did not return all cancelled superseded demands for "
						+ plan.getConnectionNo());
			}
		}
	}

	private Demand findDemandForCycle(RequestInfo requestInfo, String tenantId, String connectionNo,
			WaterBillingCycle cycle) {
		List<Demand> demands = demandService.searchDemand(tenantId, Collections.singleton(connectionNo),
				cycle.getBillingperiodfrom(), cycle.getBillingperiodto(), requestInfo, null, false, false);

		if (demands != null) {
			for (Demand demand : demands) {
				if (demand != null && cycle.getDemandid().equals(demand.getId())) {
					return demand;
				}
			}
		}
		return null;
	}

	private BigDecimal calculateCollectedAmount(Demand demand) {
		BigDecimal collected = BigDecimal.ZERO;
		if (demand != null && demand.getDemandDetails() != null) {
			for (org.egov.wscalculation.web.models.DemandDetail detail : demand.getDemandDetails()) {
				if (detail != null && detail.getCollectionAmount() != null
						&& detail.getCollectionAmount().signum() > 0) {
					collected = collected.add(detail.getCollectionAmount());
				}
			}
		}
		return normalizeMoney(collected);
	}

	private boolean isEligibleCorrectionCycle(WaterBillingCycle cycle) {
		return cycle != null
				&& (BillingBasis.AVERAGE.equals(cycle.getBillingbasis())
						|| BillingBasis.PROVISIONAL.equals(cycle.getBillingbasis()));
	}

	private boolean isPreviousOkCycle(WaterBillingCycle cycle, CorrectionPlan plan) {
		return cycle != null && plan != null
				&& plan.getPreviousOkBillingCycleId() != null
				&& plan.getPreviousOkBillingCycleId().equals(cycle.getId());
	}

	private void logPaymentAdjustment(WaterBillingCycle cycle, Demand target, BigDecimal collected) {
		log.info("[DJB-CORRECTION] Paid adjustment captured: cycleId={}, demandId={}, amount={}",
				cycle.getId(), target.getId(), collected);
	}

	private BigDecimal normalizeMoney(BigDecimal amount) {
		return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
	}

	/**
	 * Marks an automatic DJB correction as completed after the corrected demand and
	 * bill have actually been generated.
	 *
	 * Historical billing-cycle rows are retained for audit. The intervening
	 * estimated cycles are marked CORRECTED locally; their original demand and bill
	 * ids are preserved and also recorded on the correction transaction.
	 */
	@Transactional
	public void completeAutomaticCorrection(String tenantId, CorrectionPlan plan, String correctedDemandId,
			String correctedBillId, String actor, long currentTime) {

		if (plan == null || !plan.isCorrectionRequired()) {
			throw new IllegalArgumentException("Correction plan is required to complete automatic correction");
		}

		if (correctedDemandId == null || correctedDemandId.trim().isEmpty() || correctedBillId == null
				|| correctedBillId.trim().isEmpty()) {
			throw new IllegalArgumentException(
					"Corrected demand and bill ids are required to complete automatic correction");
		}

		List<BillingCorrection> history = billingCorrectionDao.findByConnection(tenantId, plan.getConnectionNo());

		BillingCorrection correctionToUpdate = null;
		if (history != null) {
			for (BillingCorrection correction : history) {
				if (plan.getCurrentOkBillingCycleId().equals(correction.getTobillingcycleid())
						&& !CorrectionStatus.FAILED.equals(correction.getStatus())) {
					correctionToUpdate = correction;
					break;
				}
			}
		}

		if (correctionToUpdate == null) {
			throw new IllegalStateException(
					"No pending DJB correction found for current billing cycle " + plan.getCurrentOkBillingCycleId());
		}

		List<String> oldDemandIds = new ArrayList<>();
		List<String> oldBillIds = new ArrayList<>();

		/*
		 * The previous OK cycle is also superseded by the consolidated OK-to-OK
		 * correction bill. Keep its demand/bill IDs in the audit trail and mark the
		 * local cycle as CORRECTED, while retaining the row for history.
		 */
		List<WaterBillingCycle> supersededCycles = new ArrayList<>();
		WaterBillingCycle previousOk = billingCycleDao.findById(tenantId, plan.getPreviousOkBillingCycleId());
		if (previousOk != null) {
			supersededCycles.add(previousOk);
		}
		if (plan.getCyclesToCorrect() != null) {
			supersededCycles.addAll(plan.getCyclesToCorrect());
		}

		for (WaterBillingCycle cycle : supersededCycles) {
			if (cycle == null) {
				continue;
			}

			if (cycle.getDemandid() != null && !cycle.getDemandid().trim().isEmpty()) {
				oldDemandIds.add(cycle.getDemandid());
			}

			if (cycle.getBillid() != null && !cycle.getBillid().trim().isEmpty()) {
				oldBillIds.add(cycle.getBillid());
			}

			cycle.setCorrectionstatus(CorrectionStatus.COMPLETED);
			cycle.setStatus(org.egov.wscalculation.djbmonthlybilling.model.enums.BillingCycleStatus.CORRECTED);
			cycle.setLastmodifiedby(actor);
			cycle.setLastmodifiedtime(currentTime);
			billingCycleDao.update(cycle);
		}

		correctionToUpdate.setStatus(CorrectionStatus.COMPLETED);
		correctionToUpdate.setPaidadjustmentamount(normalizeMoney(plan.getPaidAdjustmentAmount()));
		correctionToUpdate.setAppliedpaidadjustmentamount(
				normalizeMoney(plan.getAppliedPaidAdjustmentAmount()));
		correctionToUpdate.setResidualpaidcreditamount(
				normalizeMoney(plan.getResidualPaidCreditAmount()));
		correctionToUpdate.setOlddemandid(joinUnique(oldDemandIds));
		correctionToUpdate.setOldbillid(joinUnique(oldBillIds));
		correctionToUpdate.setCorrecteddemandid(correctedDemandId);
		correctionToUpdate.setCorrectedbillid(correctedBillId);
		correctionToUpdate.setLastmodifiedby(actor);
		correctionToUpdate.setLastmodifiedtime(currentTime);

		billingCorrectionDao.update(correctionToUpdate);

		/*
		 * If the previous average/provisional bills were overpaid relative to the
		 * corrected OK-to-OK liability, create a durable residual credit. The credit
		 * is applied by ResidualCreditService to future DJB monthly demands; it is not
		 * silently lost and no refund is assumed here.
		 */
		residualCreditService.createResidualCredit(correctionToUpdate, actor, currentTime);
	}

	private String joinUnique(List<String> values) {
		if (values == null || values.isEmpty()) {
			return null;
		}

		java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>(values);
		return String.join(",", unique);
	}

	/**
	 * Calculates the total amount already received against the intervening
	 * average/provisional demands. The previous OK demand is intentionally
	 * excluded because its payment is for a valid historical assessment.
	 */
	private BigDecimal calculatePaidAdjustmentAmount(RequestInfo requestInfo, String tenantId, CorrectionPlan plan) {
		BigDecimal total = BigDecimal.ZERO;
		if (plan == null || plan.getCyclesToCorrect() == null) {
			return total.setScale(2, RoundingMode.HALF_UP);
		}

		for (WaterBillingCycle cycle : plan.getCyclesToCorrect()) {
			if (cycle == null || !StringUtils.hasText(cycle.getDemandid())) {
				throw new IllegalStateException(
						"Eligible DJB correction cycle has no demand id: " + (cycle == null ? null : cycle.getId()));
			}

			Demand demand = findDemandForCycle(requestInfo, tenantId, plan.getConnectionNo(), cycle);
			if (demand == null) {
				throw new IllegalStateException(
						"Unable to find demand for eligible DJB correction cycle " + cycle.getId());
			}

			total = total.add(calculateCollectedAmount(demand));
		}

		return normalizeMoney(total);
	}

	private void validateCurrentOkCycle(WaterBillingCycle currentOkCycle) {

		if (currentOkCycle == null) {
			throw new IllegalArgumentException("Current billing cycle cannot be null");
		}

		if (!"OK".equalsIgnoreCase(currentOkCycle.getReadingqualitycode())) {
			throw new IllegalArgumentException("Automatic correction requires RQC=OK");
		}
	}

	/**
	 * Runs only automatic correction detection and creates the pending correction
	 * record. It does not cancel bills.
	 */
	public CorrectionPlanResult processAutomaticCorrection(RequestInfo requestInfo, String tenantId,
			WaterBillingCycle currentOkCycle, String actor, long currentTime) {

		CorrectionPlan plan = buildCorrectionPlan(tenantId, currentOkCycle);

		if (plan == null || !plan.isCorrectionRequired()) {
			return CorrectionPlanResult.builder().correctionRequired(false).build();
		}

		plan.setPaidAdjustmentAmount(
				calculatePaidAdjustmentAmount(requestInfo, tenantId, plan));

		BillingCorrection correction = createPendingCorrection(tenantId, plan, actor, currentTime);

		if (correction != null) {
			plan.setAppliedPaidAdjustmentAmount(
				normalizeMoney(correction.getAppliedpaidadjustmentamount()));
			plan.setResidualPaidCreditAmount(
				normalizeMoney(correction.getResidualpaidcreditamount()));
		}

		/*
		 * If the correction was already completed earlier, do not report it as a new
		 * pending correction. This makes the automatic-correction flow idempotent on
		 * retries and prevents COMPLETED from being overwritten back to PENDING.
		 */
		boolean correctionPending = correction != null && !CorrectionStatus.COMPLETED.equals(correction.getStatus());

		return CorrectionPlanResult.builder().correctionRequired(correctionPending).correction(correction)
				.plan(correctionPending ? plan : null).build();
	}

	@lombok.Builder
	@lombok.Data
	public static class CorrectionPlanResult {
		private boolean correctionRequired;
		private CorrectionPlan plan;
		private BillingCorrection correction;
	}

}