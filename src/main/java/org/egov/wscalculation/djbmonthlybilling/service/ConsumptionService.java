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
	private static final int HISTORY_CYCLES_PER_MONTH_GUARD = 4;
	private final WaterBillingCycleDao billingCycleDao;

	public ConsumptionService(WaterBillingCycleDao billingCycleDao) {
		this.billingCycleDao = billingCycleDao;
	}

	public ConsumptionResult calculate(String tenantId, String connectionNo, WaterBillingCycle currentCycle,
			BillingBasisDecision decision, DJBMonthlyBillingRule rule) {

		if (BillingBasis.ACTUAL.equals(decision.getBillingBasis())) {
			BigDecimal actual = calculateActualConsumption(currentCycle);
			Long normalizationFrom = DJBConsumptionPeriodUtil.resolveNormalizationStart(
					currentCycle.getPreviousokreadingdate(), currentCycle.getBillingperiodfrom());
			BigDecimal monthlyConsumption = DJBConsumptionPeriodUtil.toMonthlyConsumption(actual, normalizationFrom,
					currentCycle.getBillingperiodto());
			BigDecimal previousMonthlyConsumption = decision.getPreviousConsumption();
			boolean onePointFiveX = isOnePointFiveX(monthlyConsumption, previousMonthlyConsumption, rule);

			return ConsumptionResult.builder().actualConsumption(actual).billingConsumption(actual)
					.previousConsumption(previousMonthlyConsumption).monthlyConsumption(monthlyConsumption)
					.deviationFactor(calculateDeviation(monthlyConsumption, previousMonthlyConsumption))
					.onePointFiveX(onePointFiveX).build();
		}

		BigDecimal average = calculateHistoricalAverage(tenantId, connectionNo, currentCycle.getBillingperiodto(),
				rule.getAverageLookbackMonths());
		if (average == null) {
			throw new IllegalStateException("No actual consumption history is available for average billing");
		}

		BigDecimal billingConsumption;
		boolean withinAverageLimit = BillingBasis.AVERAGE.equals(decision.getBillingBasis())
				&& decision.getAverageCycleCount() <= rule.getAverageMaximumCycles();
		boolean withinProvisionalLimit = BillingBasis.PROVISIONAL.equals(decision.getBillingBasis())
				&& decision.getProvisionalCycleCount() <= rule.getProvisionalMaximumCycles();
		if (withinAverageLimit || withinProvisionalLimit) {
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
		if (periodTo == null || lookbackMonths == null || lookbackMonths <= 0) {
			return null;
		}

		/*
		 * DJB's historical average is defined in months, not in database rows. A single
		 * meter-reading row can represent multiple billing months when readings are
		 * collected late. Fetch enough recent actual cycles to cover the configured
		 * lookback window and weight each cycle by the number of billing-calendar
		 * months it represents.
		 */
		int maxCycles = Math.max(lookbackMonths * HISTORY_CYCLES_PER_MONTH_GUARD, lookbackMonths);

		List<WaterBillingCycle> cycles = billingCycleDao.findPreviousActualCycles(tenantId, connectionNo, periodTo,
				maxCycles);

		if (cycles == null || cycles.isEmpty()) {
			return null;
		}

		BigDecimal targetMonths = BigDecimal.valueOf(lookbackMonths);
		BigDecimal remainingMonths = targetMonths;
		BigDecimal weightedMonthlyConsumption = BigDecimal.ZERO;
		BigDecimal observedMonths = BigDecimal.ZERO;

		for (WaterBillingCycle cycle : cycles) {
			if (remainingMonths.signum() <= 0 || cycle == null || cycle.getBillingconsumption() == null
					|| cycle.getBillingconsumption().signum() < 0) {
				continue;
			}

			Long normalizationFrom = DJBConsumptionPeriodUtil
					.resolveNormalizationStart(cycle.getPreviousokreadingdate(), cycle.getBillingperiodfrom());

			long cycleMonths = DJBConsumptionPeriodUtil.calculateBillingMonths(normalizationFrom,
					cycle.getBillingperiodto());

			if (cycleMonths <= 0) {
				continue;
			}

			BigDecimal cycleMonthlyConsumption = DJBConsumptionPeriodUtil.toBillingMonthConsumption(
					cycle.getBillingconsumption(), normalizationFrom, cycle.getBillingperiodto());

			if (cycleMonthlyConsumption == null) {
				continue;
			}

			BigDecimal includedMonths = BigDecimal.valueOf(cycleMonths).min(remainingMonths);

			weightedMonthlyConsumption = weightedMonthlyConsumption
					.add(cycleMonthlyConsumption.multiply(includedMonths, MC), MC);

			observedMonths = observedMonths.add(includedMonths, MC);

			remainingMonths = remainingMonths.subtract(includedMonths, MC);
		}

		if (observedMonths.signum() <= 0) {
			return null;
		}

		return weightedMonthlyConsumption.divide(observedMonths, 3, RoundingMode.HALF_UP);
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
