package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.egov.wscalculation.djbmonthlybilling.model.BillingCorrection;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;
import org.egov.wscalculation.djbmonthlybilling.repository.BillingCorrectionDao;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.service.dto.CorrectionPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the DJB automatic-correction plan when an OK reading is received after
 * estimated billing cycles.
 *
 * This class deliberately does not call billing-service APIs. The current
 * ws-calculator source exposes bill fetch integration, but the supplied
 * calculator code does not provide a verified contract for cancellation,
 * adjustment and payment-settlement. Those operations belong in a separate
 * adapter once the billing-service contract is confirmed.
 */
@Service
public class CorrectionService {

	private final WaterBillingCycleDao billingCycleDao;
	private final BillingCorrectionDao billingCorrectionDao;

	public CorrectionService(WaterBillingCycleDao billingCycleDao, BillingCorrectionDao billingCorrectionDao) {
		this.billingCycleDao = billingCycleDao;
		this.billingCorrectionDao = billingCorrectionDao;
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
		correction.setCreatedby(actor);
		correction.setCreatedtime(currentTime);
		correction.setLastmodifiedby(actor);
		correction.setLastmodifiedtime(currentTime);

		billingCorrectionDao.save(correction);

		return correction;
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
	public CorrectionPlanResult processAutomaticCorrection(String tenantId, WaterBillingCycle currentOkCycle,
			String actor, long currentTime) {

		CorrectionPlan plan = buildCorrectionPlan(tenantId, currentOkCycle);

		if (plan == null || !plan.isCorrectionRequired()) {
			return CorrectionPlanResult.builder().correctionRequired(false).build();
		}

		BillingCorrection correction = createPendingCorrection(tenantId, plan, actor, currentTime);

		return CorrectionPlanResult.builder().correctionRequired(correction != null).correction(correction).plan(plan)
				.build();
	}

	@lombok.Builder
	@lombok.Data
	public static class CorrectionPlanResult {
		private boolean correctionRequired;
		private CorrectionPlan plan;
		private BillingCorrection correction;
	}

}