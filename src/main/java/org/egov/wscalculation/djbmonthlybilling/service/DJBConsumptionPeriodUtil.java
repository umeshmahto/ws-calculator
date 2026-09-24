package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Utility for meter-consumption period normalization.
 *
 * DJB uses monthly consumption as the comparison basis for the 1.5x rule, while
 * the actual tariff calculation continues to use the meter-to-meter consumption
 * for the billing period. Historical-average calculations use calendar billing
 * months represented by each stored cycle.
 */
public final class DJBConsumptionPeriodUtil {

	private static final BigDecimal MILLIS_PER_DAY = BigDecimal.valueOf(86_400_000L);

	private static final BigDecimal NORMALIZATION_DAYS = BigDecimal.valueOf(30L);

	private static final int SCALE = 6;

	/** DJB billing dates are calendar dates in India Standard Time. */
	private static final ZoneId DJB_ZONE = ZoneId.of("Asia/Kolkata");

	private DJBConsumptionPeriodUtil() {
	}

	public static BigDecimal calculateElapsedDays(Long from, Long to) {
		if (from == null || to == null || to <= from) {
			return null;
		}

		return BigDecimal.valueOf(to - from).divide(MILLIS_PER_DAY, SCALE, RoundingMode.HALF_UP);
	}

	/**
	 * Returns the effective start date for monthly normalization.
	 *
	 * For an actual reading after one or more estimated cycles, the comparison
	 * period starts from the last valid OK reading date.
	 */
	public static Long resolveNormalizationStart(Long previousOkReadingDate, Long billingPeriodFrom) {

		return previousOkReadingDate != null ? previousOkReadingDate : billingPeriodFrom;
	}

	/**
	 * Calculates the number of calendar billing months represented by a period.
	 *
	 * Examples: Jan -> Feb = 1 month Jan -> Apr = 3 months Jan -> Jul = 6 months
	 * Jan -> Jan(next year) = 12 months Jan -> Jan(2 years later) = 24 months
	 *
	 * A valid positive period inside a single calendar month is treated as one
	 * billing month, because a billing cycle cannot contribute zero months.
	 */
	public static long calculateBillingMonths(Long from, Long to) {
		if (from == null || to == null || to <= from) {
			return 0L;
		}

		YearMonth startMonth = YearMonth.from(Instant.ofEpochMilli(from).atZone(DJB_ZONE));

		YearMonth endMonth = YearMonth.from(Instant.ofEpochMilli(to).atZone(DJB_ZONE));

		long months = ChronoUnit.MONTHS.between(startMonth, endMonth);

		return Math.max(months, 1L);
	}

	/**
	 * Converts a period consumption into a 30-day monthly equivalent.
	 *
	 * This is used for the DJB 1.5x/ZRO decision. It deliberately uses elapsed days
	 * so a partial month can be normalized proportionally.
	 */
	public static BigDecimal toMonthlyConsumption(BigDecimal consumption, Long from, Long to) {

		if (consumption == null) {
			return null;
		}

		BigDecimal days = calculateElapsedDays(from, to);

		if (days == null || days.signum() <= 0) {
			return null;
		}

		return consumption.multiply(NORMALIZATION_DAYS).divide(days, SCALE, RoundingMode.HALF_UP);
	}

	/**
	 * Converts a billing-cycle consumption into a monthly value using the number of
	 * calendar billing months represented by the cycle.
	 *
	 * Example: 60 KL over 3 months = 20 KL/month 100 KL over 5 months = 20 KL/month
	 * 240 KL over 12 months = 20 KL/month
	 */
	public static BigDecimal toBillingMonthConsumption(BigDecimal consumption, Long from, Long to) {

		if (consumption == null || consumption.signum() < 0) {
			return null;
		}

		long months = calculateBillingMonths(from, to);

		if (months <= 0) {
			return null;
		}

		return consumption.divide(BigDecimal.valueOf(months), SCALE, RoundingMode.HALF_UP);
	}
}
