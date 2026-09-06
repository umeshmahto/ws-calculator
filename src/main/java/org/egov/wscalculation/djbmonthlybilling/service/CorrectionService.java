package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
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

/**
 * Builds the DJB automatic-correction plan when an OK reading is received after
 * estimated billing cycles.
 *
 * This service coordinates DJB correction state and deactivates superseded demands.
 * The generic billing-service demand-create flow already moves the previous active
 * bill to historical state when the corrected demand is created, so DJB correction
 * does not call the generic bill-cancellation API. Paid-demand settlement remains
 * a separate flow.
 */
@Service
public class CorrectionService {

	private final WaterBillingCycleDao billingCycleDao;
	private final BillingCorrectionDao billingCorrectionDao;
	private final DemandRepository demandRepository;
	private final DemandService demandService;

	public CorrectionService(WaterBillingCycleDao billingCycleDao, BillingCorrectionDao billingCorrectionDao,
			DemandRepository demandRepository, DemandService demandService) {
		this.billingCycleDao = billingCycleDao;
		this.billingCorrectionDao = billingCorrectionDao;
		this.demandRepository = demandRepository;
		this.demandService = demandService;
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

	/**
	 * Deactivates all demands that are superseded by an automatic OK-to-OK
	 * correction before the corrected bill is fetched.
	 *
	 * The previous OK demand is included because the final corrected demand spans
	 * from the previous OK reading to the current OK reading. Leaving that demand
	 * ACTIVE causes the generic billing-service _fetchbill endpoint to include the
	 * old demand along with the corrected demand in the same bill.
	 *
	 * Paid/partially-paid demands are deliberately not cancelled here. Until the
	 * DJB paid-demand settlement flow is implemented, failing fast is safer than
	 * generating a financially incorrect consolidated bill.
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

		/*
		 * Validate the full correction set before changing remote state. A paid or
		 * partially-paid demand cannot be silently cancelled because DJB requires the
		 * amount to be settled against the corrected bill.
		 */
		for (WaterBillingCycle cycle : supersededCycles) {
			if (cycle == null) {
				continue;
			}


			if (cycle.getDemandid() == null || cycle.getDemandid().trim().isEmpty()) {
				continue;
			}

			List<Demand> demands = demandService.searchDemand(tenantId, Collections.singleton(plan.getConnectionNo()),
					cycle.getBillingperiodfrom(), cycle.getBillingperiodto(), requestInfo, null, false, false);

			Demand target = null;
			if (demands != null) {
				for (Demand demand : demands) {
					if (demand != null && cycle.getDemandid().equals(demand.getId())) {
						target = demand;
						break;
					}
				}
			}

			if (target == null) {
				throw new IllegalStateException("Superseded demand not found for billing cycle " + cycle.getId()
						+ ", demand " + cycle.getDemandid());
			}

			if (Demand.StatusEnum.CANCELLED.equals(target.getStatus())) {
				continue;
			}

			BigDecimal collected = BigDecimal.ZERO;
			if (target.getDemandDetails() != null) {
				for (org.egov.wscalculation.web.models.DemandDetail detail : target.getDemandDetails()) {
					if (detail != null && detail.getCollectionAmount() != null) {
						collected = collected.add(detail.getCollectionAmount());
					}
				}
			}

			if (collected.signum() > 0) {
				throw new IllegalStateException(
						"Automatic correction requires paid-demand settlement before cancelling demand "
						+ target.getId());
			}

			target.setStatus(Demand.StatusEnum.CANCELLED);
			demandsToCancel.add(target);
		}

		if (!demandsToCancel.isEmpty()) {
			List<Demand> updated = demandRepository.updateDemand(requestInfo, demandsToCancel);

			if (updated == null || updated.size() != demandsToCancel.size()) {
				throw new IllegalStateException("Billing-service did not return all cancelled superseded demands for "
						+ plan.getConnectionNo());
			}
		}
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
		correctionToUpdate.setOlddemandid(joinUnique(oldDemandIds));
		correctionToUpdate.setOldbillid(joinUnique(oldBillIds));
		correctionToUpdate.setCorrecteddemandid(correctedDemandId);
		correctionToUpdate.setCorrectedbillid(correctedBillId);
		correctionToUpdate.setLastmodifiedby(actor);
		correctionToUpdate.setLastmodifiedtime(currentTime);

		billingCorrectionDao.update(correctionToUpdate);
	}

	private String joinUnique(List<String> values) {
		if (values == null || values.isEmpty()) {
			return null;
		}

		java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>(values);
		return String.join(",", unique);
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