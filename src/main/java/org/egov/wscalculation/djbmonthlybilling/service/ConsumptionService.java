package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterConnectionActivationDao;
import org.egov.wscalculation.djbmonthlybilling.service.dto.BillingBasisDecision;
import org.egov.wscalculation.djbmonthlybilling.service.dto.ConsumptionResult;
import org.springframework.stereotype.Service;

@Service
public class ConsumptionService {

	private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);
	private static final int HISTORY_CYCLES_PER_MONTH_GUARD = 4;
	private static final ZoneId DJB_ZONE = ZoneId.of("Asia/Kolkata");
	private final WaterBillingCycleDao billingCycleDao;
	private final WaterConnectionActivationDao activationDao;

	public ConsumptionService(WaterBillingCycleDao billingCycleDao, WaterConnectionActivationDao activationDao) {
		this.billingCycleDao = billingCycleDao;
		this.activationDao = activationDao;
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

		Long lookbackStart = calculateLookbackStart(currentCycle.getBillingperiodto(), rule.getAverageLookbackMonths());
		List<WaterBillingCycle> historyCycles = loadHistoricalActualCycles(tenantId, connectionNo,
				currentCycle.getBillingperiodto(), rule.getAverageLookbackMonths());
		BigDecimal average = calculateHistoricalAverage(historyCycles, rule.getAverageLookbackMonths(),
				lookbackStart, currentCycle.getBillingperiodto());
		if (average == null) {
			throw new IllegalStateException("No actual consumption history is available for average billing");
		}

		BigDecimal effectiveAverage = average.setScale(3, RoundingMode.HALF_UP);
		if (BillingBasis.AVERAGE.equals(decision.getBillingBasis())
				&& decision.getAverageCycleCount() <= rule.getAverageMaximumCycles()) {
			Long activationDate = activationDao.findActivationDate(tenantId, connectionNo);
			if (isLessThanOneYearOld(activationDate, currentCycle.getBillingperiodto())) {
				BigDecimal maximumAvailableConsumption = calculateMaximumAvailableConsumption(
						historyCycles, activationDate);
				if (maximumAvailableConsumption != null) {
					effectiveAverage = maximumAvailableConsumption.setScale(3, RoundingMode.HALF_UP);
				}
			}
		}

		BigDecimal billingConsumption;
		boolean withinAverageLimit = BillingBasis.AVERAGE.equals(decision.getBillingBasis())
				&& decision.getAverageCycleCount() <= rule.getAverageMaximumCycles();
		boolean withinProvisionalLimit = BillingBasis.PROVISIONAL.equals(decision.getBillingBasis())
				&& decision.getProvisionalCycleCount() <= rule.getProvisionalMaximumCycles();
		if (withinAverageLimit || withinProvisionalLimit) {
			billingConsumption = effectiveAverage;
		} else {
			BigDecimal minimum = BigDecimal.valueOf(rule.getMinimumPostAverageConsumptionKl().longValue())
					.setScale(3, RoundingMode.HALF_UP);
			billingConsumption = effectiveAverage.max(minimum);
		}

		billingConsumption = billingConsumption.setScale(3, RoundingMode.HALF_UP);

		return ConsumptionResult.builder().averageConsumption(effectiveAverage).billingConsumption(billingConsumption)
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

		Long lookbackStart = calculateLookbackStart(periodTo, lookbackMonths);
		List<WaterBillingCycle> cycles = loadHistoricalActualCycles(tenantId, connectionNo, periodTo, lookbackMonths);
		return calculateHistoricalAverage(cycles, lookbackMonths, lookbackStart, periodTo);
	}

	private List<WaterBillingCycle> loadHistoricalActualCycles(String tenantId, String connectionNo, Long periodTo,
			Integer lookbackMonths) {
		if (periodTo == null || lookbackMonths == null || lookbackMonths <= 0) {
			return java.util.Collections.emptyList();
		}

		Long lookbackStart = calculateLookbackStart(periodTo, lookbackMonths);
		int maxCycles = Math.max(lookbackMonths * HISTORY_CYCLES_PER_MONTH_GUARD, lookbackMonths);
		List<WaterBillingCycle> cycles = billingCycleDao.findPreviousActualCyclesWithinPeriod(tenantId, connectionNo,
				lookbackStart, periodTo, maxCycles);
		return cycles == null ? java.util.Collections.emptyList() : cycles;
	}

	private Long calculateLookbackStart(Long periodTo, Integer lookbackMonths) {
		if (periodTo == null || lookbackMonths == null || lookbackMonths <= 0) {
			return null;
		}
		return Instant.ofEpochMilli(periodTo).atZone(ZoneOffset.UTC).toLocalDate()
				.minusMonths(lookbackMonths).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
	}

	private BigDecimal calculateHistoricalAverage(List<WaterBillingCycle> cycles, Integer lookbackMonths,
			Long lookbackStart, Long periodTo) {
		if (cycles == null || cycles.isEmpty() || lookbackMonths == null || lookbackMonths <= 0
				|| lookbackStart == null || periodTo == null) {
			return null;
		}

		BigDecimal targetMonths = BigDecimal.valueOf(lookbackMonths);
		BigDecimal remainingMonths = targetMonths;
		BigDecimal weightedMonthlyConsumption = BigDecimal.ZERO;
		BigDecimal observedMonths = BigDecimal.ZERO;

		for (WaterBillingCycle cycle : cycles) {
			if (remainingMonths.signum() <= 0 || cycle == null || cycle.getBillingconsumption() == null
					|| cycle.getBillingconsumption().signum() < 0 || cycle.getBillingperiodto() == null) {
				continue;
			}

			Long normalizationFrom = DJBConsumptionPeriodUtil
					.resolveNormalizationStart(cycle.getPreviousokreadingdate(), cycle.getBillingperiodfrom());
			if (normalizationFrom == null || cycle.getBillingperiodto() <= normalizationFrom) {
				continue;
			}

			// Only the portion of a historical cycle that falls inside the DJB
			// 12-month window is eligible for the average. The monthly-equivalent
			// consumption is weighted by the clipped overlap, preventing an older
			// multi-month cycle from leaking outside the configured lookback period.
			long overlapFrom = Math.max(normalizationFrom, lookbackStart);
			long overlapTo = Math.min(cycle.getBillingperiodto(), periodTo);
			if (overlapTo <= overlapFrom) {
				continue;
			}

			long cycleMonths = DJBConsumptionPeriodUtil.calculateBillingMonths(normalizationFrom, cycle.getBillingperiodto());
			long overlapMonths = DJBConsumptionPeriodUtil.calculateBillingMonths(overlapFrom, overlapTo);
			if (cycleMonths <= 0 || overlapMonths <= 0) {
				continue;
			}

			BigDecimal cycleMonthlyConsumption = DJBConsumptionPeriodUtil.toBillingMonthConsumption(
					cycle.getBillingconsumption(), normalizationFrom, cycle.getBillingperiodto());
			if (cycleMonthlyConsumption == null) {
				continue;
			}

			BigDecimal includedMonths = BigDecimal.valueOf(Math.min(cycleMonths, overlapMonths))
					.min(remainingMonths);
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

	private boolean isLessThanOneYearOld(Long activationDate, Long billingPeriodTo) {
		if (activationDate == null || billingPeriodTo == null || billingPeriodTo <= activationDate) {
			return false;
		}

		LocalDate activation = Instant.ofEpochMilli(activationDate).atZone(DJB_ZONE).toLocalDate();
		LocalDate billingDate = Instant.ofEpochMilli(billingPeriodTo).atZone(DJB_ZONE).toLocalDate();
		return billingDate.isBefore(activation.plusYears(1));
	}

	/**
	 * DJB Table 24 requires the first two average-remark bills for a connection
	 * active for less than one year to use the maximum available consumption.
	 *
	 * The maximum is calculated on a monthly-equivalent basis so a late meter
	 * reading covering multiple months does not dominate the result merely because
	 * it represents more than one billing month.
	 */
	private BigDecimal calculateMaximumAvailableConsumption(List<WaterBillingCycle> cycles, Long activationDate) {
		if (cycles == null || cycles.isEmpty() || activationDate == null) {
			return null;
		}

		BigDecimal maximum = null;
		for (WaterBillingCycle cycle : cycles) {
			if (cycle == null || cycle.getBillingconsumption() == null
					|| cycle.getBillingconsumption().signum() < 0
					|| cycle.getBillingperiodto() == null
					|| cycle.getBillingperiodto() <= activationDate) {
				continue;
			}

			Long normalizationFrom = DJBConsumptionPeriodUtil.resolveNormalizationStart(
					cycle.getPreviousokreadingdate(), cycle.getBillingperiodfrom());
			if (normalizationFrom == null || normalizationFrom < activationDate) {
				normalizationFrom = activationDate;
			}

			BigDecimal cycleMonthlyConsumption = DJBConsumptionPeriodUtil.toBillingMonthConsumption(
					cycle.getBillingconsumption(), normalizationFrom, cycle.getBillingperiodto());
			if (cycleMonthlyConsumption == null) {
				continue;
			}

			maximum = maximum == null ? cycleMonthlyConsumption : maximum.max(cycleMonthlyConsumption);
		}

		return maximum;
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
