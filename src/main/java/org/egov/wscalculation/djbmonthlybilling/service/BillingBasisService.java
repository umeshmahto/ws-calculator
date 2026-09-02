package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.util.List;

import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBReadingQualityCode;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.service.dto.BillingBasisDecision;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BillingBasisService {

	private final WaterBillingCycleDao billingCycleDao;

	public BillingBasisService(WaterBillingCycleDao billingCycleDao) {
		this.billingCycleDao = billingCycleDao;
	}

	public BillingBasisDecision determineBillingBasis(String tenantId, String connectionNo,
			WaterBillingCycle currentCycle, DJBReadingQualityCode rqc, DJBMonthlyBillingRule rule) {

		if (currentCycle == null || rqc == null || rule == null) {
			throw new IllegalArgumentException("Current cycle, RQC and DJB billing rule are required");
		}

		String code = currentCycle.getReadingqualitycode();
		if (!StringUtils.hasText(code)) {
			code = rqc.getCode();
		}

		BigDecimal previousConsumption = findPreviousConsumption(tenantId, connectionNo,
				currentCycle.getBillingperiodto());

		if ("OK".equalsIgnoreCase(rqc.getCode()) || "ACTUAL".equalsIgnoreCase(rqc.getBillingTreatment())) {
			return BillingBasisDecision.builder().billingBasis(BillingBasis.ACTUAL).readingQualityCode(code)
					.averageCycleCount(0).provisionalCycleCount(0).requiresZro(false)
					.actualReadingAvailable(currentCycle.getCurrentreading() != null)
					.previousConsumption(previousConsumption).build();
		}

		if (!"AVERAGE".equalsIgnoreCase(rqc.getBillingTreatment())) {
			throw new IllegalArgumentException("Unsupported DJB RQC billing treatment: " + rqc.getBillingTreatment());
		}

		// Query recent cycles, not only AVERAGE rows. A non-average cycle must
		// terminate
		// the consecutive streak; otherwise an isolated old AVERAGE would be counted.
		List<WaterBillingCycle> recent = billingCycleDao.findCyclesForConnection(tenantId, connectionNo,
				currentCycle.getBillingperiodto(), 24);

		int averageCount = 0;
		int provisionalCount = 0;
		for (WaterBillingCycle cycle : recent) {
			if (BillingBasis.AVERAGE.equals(cycle.getBillingbasis())) {
				if (provisionalCount > 0)
					break;
				averageCount++;
			} else if (BillingBasis.PROVISIONAL.equals(cycle.getBillingbasis())) {
				if (averageCount > 0)
					break;
				provisionalCount++;
			} else {
				break;
			}
		}

		// DJB keeps the RQC treatment as Average even after the first two cycles.
		// ConsumptionService applies the configured post-limit floor (higher of
		// historical average and 25 KL in the current DJB rule). It must NOT turn
		// an Average RQC into Provisional merely because the average-cycle limit
		// was reached.
		return BillingBasisDecision.builder().billingBasis(BillingBasis.AVERAGE).readingQualityCode(code)
				.averageCycleCount(averageCount + 1).provisionalCycleCount(provisionalCount).requiresZro(false)
				.actualReadingAvailable(false).previousConsumption(previousConsumption).build();
	}

	private BigDecimal findPreviousConsumption(String tenantId, String connectionNo, Long periodTo) {
		List<WaterBillingCycle> cycles = billingCycleDao.findPreviousActualCycles(tenantId, connectionNo, periodTo, 1);
		if (cycles == null || cycles.isEmpty()) {
			return null;
		}
		return cycles.get(0).getBillingconsumption();
	}
}
