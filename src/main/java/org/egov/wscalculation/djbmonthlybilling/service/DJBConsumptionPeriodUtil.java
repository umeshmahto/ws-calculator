package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility for normalising meter-to-meter consumption to a monthly equivalent.
 *
 * DJB's 1.5x rule is expressed on a per-month basis. The tariff/billing amount
 * itself continues to use the actual meter-to-meter consumption; this utility is
 * only used for the 1.5x/ZRO decision and its audit representation.
 */
public final class DJBConsumptionPeriodUtil {

    private static final BigDecimal MILLIS_PER_DAY = new BigDecimal(86400000L);
    private static final BigDecimal NORMALIZATION_DAYS = new BigDecimal("30");
    private static final int SCALE = 6;

    private DJBConsumptionPeriodUtil() {
    }

    public static BigDecimal calculateElapsedDays(Long from, Long to) {
        if (from == null || to == null || to <= from) {
            return null;
        }
        return BigDecimal.valueOf(to - from).divide(MILLIS_PER_DAY, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Converts a period consumption into a 30-day monthly equivalent.
     * Fractional-day periods are supported so same-day readings can still be
     * evaluated on the same monthly-equivalent basis.
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
}
