package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.service.dto.BillingBasisDecision;
import org.egov.wscalculation.djbmonthlybilling.service.dto.ConsumptionResult;
import org.springframework.stereotype.Service;

@Service
public class ConsumptionService {

	private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);
	private final WaterBillingCycleDao billingCycleDao;

	public ConsumptionService(WaterBillingCycleDao billingCycleDao) {
		this.billingCycleDao = billingCycleDao;
	}

	public ConsumptionResult calculate(String tenantId, String connectionNo, WaterBillingCycle currentCycle,
			BillingBasisDecision decision, DJBMonthlyBillingRule rule) {

		if (BillingBasis.ACTUAL.equals(decision.getBillingBasis())) {
			BigDecimal actual = calculateActualConsumption(currentCycle);
			return ConsumptionResult.builder().actualConsumption(actual).billingConsumption(actual)
					.previousConsumption(decision.getPreviousConsumption())
					.deviationFactor(calculateDeviation(actual, decision.getPreviousConsumption()))
					.onePointFiveX(isOnePointFiveX(actual, decision.getPreviousConsumption(), rule)).build();
		}

		BigDecimal average = calculateHistoricalAverage(tenantId, connectionNo, currentCycle.getBillingperiodto(),
				rule.getAverageLookbackMonths());
		if (average == null) {
			throw new IllegalStateException("No actual consumption history is available for average billing");
		}

		BigDecimal billingConsumption;
		if (BillingBasis.AVERAGE.equals(decision.getBillingBasis())
				&& decision.getAverageCycleCount() <= rule.getAverageMaximumCycles()) {
			billingConsumption = average;
		} else {
			BigDecimal minimum = BigDecimal.valueOf(rule.getMinimumPostAverageConsumptionKl().longValue());
			billingConsumption = average.max(minimum);
		}

		return ConsumptionResult.builder().averageConsumption(average).billingConsumption(billingConsumption)
				.previousConsumption(decision.getPreviousConsumption()).build();
	}

	public BigDecimal calculateActualConsumption(WaterBillingCycle currentCycle) {
		if (currentCycle.getCurrentreading() == null || currentCycle.getPreviousokreading() == null) {
			throw new IllegalStateException("Previous OK reading and current reading are required");
		}
		BigDecimal actual = currentCycle.getCurrentreading().subtract(currentCycle.getPreviousokreading(), MC);
		if (actual.signum() < 0) {
			throw new IllegalStateException("Current reading cannot be lower than previous OK reading");
		}
		return actual;
	}

	public BigDecimal calculateHistoricalAverage(String tenantId, String connectionNo, Long periodTo,
			Integer lookbackMonths) {
		List<WaterBillingCycle> cycles = billingCycleDao.findPreviousActualCycles(tenantId, connectionNo, periodTo,
				lookbackMonths);
		if (cycles == null || cycles.isEmpty())
			return null;

		BigDecimal total = BigDecimal.ZERO;
		int count = 0;
		for (WaterBillingCycle c : cycles) {
			if (c.getBillingconsumption() != null) {
				total = total.add(c.getBillingconsumption(), MC);
				count++;
			}
		}
		return count == 0 ? null : total.divide(BigDecimal.valueOf(count), 3, RoundingMode.HALF_UP);
	}

	private BigDecimal calculateDeviation(BigDecimal current, BigDecimal previous) {
		if (current == null || previous == null || previous.signum() <= 0)
			return null;
		return current.divide(previous, 6, RoundingMode.HALF_UP);
	}

	private boolean isOnePointFiveX(BigDecimal current, BigDecimal previous, DJBMonthlyBillingRule rule) {
		if (current == null || previous == null || previous.signum() <= 0)
			return false;
		BigDecimal factor = BigDecimal.valueOf(rule.getHighConsumptionMultiplier());
		BigDecimal threshold = BigDecimal.valueOf(rule.getHighConsumptionThresholdKl());
		return current.compareTo(previous.multiply(factor, MC)) > 0 && current.compareTo(threshold) >= 0;
	}
}
