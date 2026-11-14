package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariff;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariffSlab;
import org.egov.wscalculation.djbmonthlybilling.service.dto.TariffCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.TariffSlabCharge;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Calculates DJB progressive water tariff slabs and the applicable monthly
 * service charge.
 *
 * DJB tariff slab limits are monthly limits. For an actual meter-reading
 * period, each monthly slab capacity is prorated by (billing days / 30).
 * Consumption is then allocated progressively through every slab; it is not
 * charged completely at the rate of the final slab reached.
 *
 * AVERAGE/PROVISIONAL billing values are already monthly consumption values
 * and must therefore be passed with a billing-period factor of 1.
 */
@Service
public class TariffCalculationService {

    private static final int MONEY_SCALE = 2;
    private static final int CONSUMPTION_SCALE = 3;
    private static final int CALCULATION_SCALE = 12;
    private static final ZoneId DJB_ZONE = ZoneId.of("Asia/Kolkata");

    public TariffCalculationResult calculate(BigDecimal consumption, String requestedCategory,
            List<DJBMonthlyWaterTariff> tariffs) {
        return calculate(consumption, requestedCategory, tariffs, BigDecimal.ONE, 1L, null, null);
    }

    /**
     * Backward-compatible overload for callers that provide whole billing
     * months. Each month is treated as one 30-day tariff month.
     */
    public TariffCalculationResult calculate(BigDecimal consumption, String requestedCategory,
            List<DJBMonthlyWaterTariff> tariffs, long billingMonths) {
        return calculate(consumption, requestedCategory, tariffs, BigDecimal.valueOf(billingMonths),
                billingMonths, null, null);
    }

    /** Backward-compatible date-aware overload using whole calendar months. */
    public TariffCalculationResult calculate(BigDecimal consumption, String requestedCategory,
            List<DJBMonthlyWaterTariff> tariffs, long billingMonths, Long billingPeriodTo) {
        return calculate(consumption, requestedCategory, tariffs, BigDecimal.valueOf(billingMonths),
                billingMonths, null, billingPeriodTo);
    }

    /** Backward-compatible date-aware overload using whole calendar months. */
    public TariffCalculationResult calculate(BigDecimal consumption, String requestedCategory,
            List<DJBMonthlyWaterTariff> tariffs, long billingMonths, Long billingPeriodFrom,
            Long billingPeriodTo) {
        return calculate(consumption, requestedCategory, tariffs, BigDecimal.valueOf(billingMonths),
                billingMonths, billingPeriodFrom, billingPeriodTo);
    }

    /**
     * Production overload.
     *
     * @param consumption actual period consumption for ACTUAL/CORRECTED_ACTUAL,
     *                    or monthly consumption for AVERAGE/PROVISIONAL
     * @param billingPeriodMonths tariff-period factor (actual billing days / 30;
     *                            use 1 for monthly average/provisional values)
     * @param calendarBillingMonths legacy whole calendar-month count retained for
     *                              other monthly fixed-charge rules and reporting
     */
    public TariffCalculationResult calculate(BigDecimal consumption, String requestedCategory,
            List<DJBMonthlyWaterTariff> tariffs, BigDecimal billingPeriodMonths,
            long calendarBillingMonths, Long billingPeriodFrom, Long billingPeriodTo) {

        validateConsumption(consumption);
        validateBillingPeriodMonths(billingPeriodMonths);
        if (calendarBillingMonths <= 0L) {
            throw new IllegalArgumentException("Calendar billing months must be greater than zero");
        }

        DJBMonthlyWaterTariff tariff = resolveTariff(requestedCategory, tariffs, billingPeriodTo);
        if (tariff == null) {
            throw new IllegalArgumentException(
                    "DJB monthly tariff not configured for category: " + requestedCategory);
        }
        validateTariffCoverage(tariff, billingPeriodFrom, billingPeriodTo);

        List<DJBMonthlyWaterTariffSlab> slabs = sortedActiveSlabs(tariff);
        validateSlabs(tariff, slabs);

        // Keep full precision for slab selection and allocation. Do not round
        // monthly consumption before deciding which service-charge slab applies.
        BigDecimal monthlyConsumption = consumption.divide(billingPeriodMonths,
                CALCULATION_SCALE, RoundingMode.HALF_UP);

        List<TariffSlabCharge> slabCharges = new ArrayList<TariffSlabCharge>();
        BigDecimal totalVolumetricCharge = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalAllocatedUnits = BigDecimal.ZERO;

        for (DJBMonthlyWaterTariffSlab slab : slabs) {
            BigDecimal slabUnits = calculatePeriodSlabUnits(consumption, billingPeriodMonths, slab);
            if (slabUnits.signum() <= 0) {
                continue;
            }

            BigDecimal rate = slab.getRatePerKl();
            BigDecimal slabCharge = slabUnits.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            totalAllocatedUnits = totalAllocatedUnits.add(slabUnits);
            totalVolumetricCharge = totalVolumetricCharge.add(slabCharge);

            slabCharges.add(TariffSlabCharge.builder()
                    .from(zeroIfNull(slab.getFrom()))
                    .to(slab.getTo())
                    .units(slabUnits.setScale(CONSUMPTION_SCALE, RoundingMode.HALF_UP))
                    .ratePerKl(rate)
                    .charge(slabCharge)
                    .build());
        }

        if (totalAllocatedUnits.compareTo(consumption) != 0) {
            throw new IllegalStateException("DJB tariff slabs did not allocate the complete consumption. Allocated="
                    + totalAllocatedUnits + ", consumption=" + consumption + ", tariff=" + tariff.getId());
        }

        // The service charge is a fixed monthly amount selected by the slab in
        // which normalized monthly consumption falls, then prorated for the
        // actual billing-period factor (e.g. Rs.292.82 x 2.7 months).
        DJBMonthlyWaterTariffSlab serviceChargeSlab = findServiceChargeSlab(monthlyConsumption, slabs);
        BigDecimal monthlyServiceCharge = serviceChargeSlab == null
                ? BigDecimal.ZERO
                : zeroIfNull(serviceChargeSlab.getServiceCharge());
        BigDecimal serviceCharge = monthlyServiceCharge.multiply(billingPeriodMonths)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalWaterCharge = totalVolumetricCharge.add(serviceCharge)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return TariffCalculationResult.builder()
                .tariffId(tariff.getId())
                .category(tariff.getCategory())
                .consumption(consumption.setScale(CONSUMPTION_SCALE, RoundingMode.HALF_UP))
                .monthlyConsumption(monthlyConsumption.setScale(CONSUMPTION_SCALE, RoundingMode.HALF_UP))
                .billingMonths(calendarBillingMonths)
                .billingPeriodMonths(billingPeriodMonths.stripTrailingZeros())
                .waterVolumetricCharge(totalVolumetricCharge)
                .serviceCharge(serviceCharge)
                .totalWaterCharge(totalWaterCharge)
                .slabCharges(slabCharges)
                .build();
    }

    /**
     * Allocates consumption progressively to this slab for the complete period.
     * A monthly band [from, to] has a period capacity of (to - from) * months.
     */
    private BigDecimal calculatePeriodSlabUnits(BigDecimal totalConsumption,
            BigDecimal billingPeriodMonths, DJBMonthlyWaterTariffSlab slab) {

        BigDecimal from = zeroIfNull(slab.getFrom());
        BigDecimal periodLowerLimit = from.multiply(billingPeriodMonths);
        BigDecimal unitsAboveLowerLimit = totalConsumption.subtract(periodLowerLimit);
        if (unitsAboveLowerLimit.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        if (slab.getTo() == null) {
            return unitsAboveLowerLimit;
        }

        BigDecimal monthlyCapacity = slab.getTo().subtract(from);
        BigDecimal periodCapacity = monthlyCapacity.multiply(billingPeriodMonths);
        return unitsAboveLowerLimit.min(periodCapacity).max(BigDecimal.ZERO);
    }

    private void validateSlabs(DJBMonthlyWaterTariff tariff, List<DJBMonthlyWaterTariffSlab> slabs) {
        if (slabs.isEmpty()) {
            throw new IllegalArgumentException("No active slabs configured for DJB tariff: " + tariff.getId());
        }

        BigDecimal expectedFrom = BigDecimal.ZERO;
        boolean openEndedSeen = false;
        for (int i = 0; i < slabs.size(); i++) {
            DJBMonthlyWaterTariffSlab slab = slabs.get(i);
            BigDecimal from = zeroIfNull(slab.getFrom());
            BigDecimal to = slab.getTo();

            if (openEndedSeen) {
                throw new IllegalArgumentException("DJB tariff has a slab after an open-ended slab: " + tariff.getId());
            }
            if (from.signum() < 0 || from.compareTo(expectedFrom) != 0) {
                throw new IllegalArgumentException("DJB tariff slabs must be contiguous and start at zero. Tariff: "
                        + tariff.getId() + ", expected from=" + expectedFrom + ", actual from=" + from);
            }
            if (to != null && to.compareTo(from) <= 0) {
                throw new IllegalArgumentException("Invalid DJB tariff slab: from=" + from + ", to=" + to);
            }
            if (slab.getRatePerKl() == null || slab.getRatePerKl().signum() < 0) {
                throw new IllegalArgumentException("Invalid rate in DJB tariff slab: " + tariff.getId());
            }
            if (slab.getServiceCharge() == null || slab.getServiceCharge().signum() < 0) {
                throw new IllegalArgumentException("Invalid service charge in DJB tariff slab: " + tariff.getId());
            }

            if (to == null) {
                openEndedSeen = true;
                if (i != slabs.size() - 1) {
                    throw new IllegalArgumentException("Open-ended DJB tariff slab must be the last slab: " + tariff.getId());
                }
            } else {
                expectedFrom = to;
            }
        }
    }

    private DJBMonthlyWaterTariffSlab findServiceChargeSlab(BigDecimal monthlyConsumption,
            List<DJBMonthlyWaterTariffSlab> slabs) {
        for (DJBMonthlyWaterTariffSlab slab : slabs) {
            BigDecimal from = zeroIfNull(slab.getFrom());
            BigDecimal to = slab.getTo();
            boolean matches = monthlyConsumption.compareTo(from) >= 0
                    && (to == null ? monthlyConsumption.compareTo(from) > 0
                            : monthlyConsumption.compareTo(to) <= 0);
            if (matches) {
                return slab;
            }
        }
        return null;
    }

    private void validateTariffCoverage(DJBMonthlyWaterTariff tariff, Long billingPeriodFrom,
            Long billingPeriodTo) {
        if (billingPeriodFrom == null || billingPeriodTo == null) {
            return;
        }

        LocalDate billingFrom = Instant.ofEpochMilli(billingPeriodFrom).atZone(DJB_ZONE).toLocalDate();
        LocalDate billingTo = Instant.ofEpochMilli(billingPeriodTo).atZone(DJB_ZONE).toLocalDate();
        LocalDate tariffFrom = parseEffectiveDate(tariff.getEffectiveFrom(), "effectiveFrom", tariff.getId());
        LocalDate tariffTo = parseEffectiveDate(tariff.getEffectiveTo(), "effectiveTo", tariff.getId());

        if (tariffFrom != null && billingFrom.isBefore(tariffFrom)) {
            throw new IllegalArgumentException(
                    "DJB billing period starts before tariff effectiveFrom; split the billing cycle before tariff revision");
        }
        if (tariffTo != null && billingTo.isAfter(tariffTo)) {
            throw new IllegalArgumentException(
                    "DJB billing period ends after tariff effectiveTo; split the billing cycle before tariff revision");
        }
    }

    private DJBMonthlyWaterTariff resolveTariff(String requestedCategory,
            List<DJBMonthlyWaterTariff> tariffs, Long billingPeriodTo) {

        if (tariffs == null || tariffs.isEmpty()) {
            return null;
        }

        String normalized = normalizeCategory(requestedCategory);
        LocalDate effectiveDate = billingPeriodTo == null ? null
                : Instant.ofEpochMilli(billingPeriodTo).atZone(DJB_ZONE).toLocalDate();

        DJBMonthlyWaterTariff selected = null;
        LocalDate selectedFrom = null;
        for (DJBMonthlyWaterTariff tariff : tariffs) {
            if (tariff == null || !Boolean.TRUE.equals(tariff.getActive())
                    || !normalizeCategory(tariff.getCategory()).equals(normalized)) {
                continue;
            }
            if (effectiveDate == null) {
                return tariff;
            }

            LocalDate from = parseEffectiveDate(tariff.getEffectiveFrom(), "effectiveFrom", tariff.getId());
            LocalDate to = parseEffectiveDate(tariff.getEffectiveTo(), "effectiveTo", tariff.getId());
            if (from != null && effectiveDate.isBefore(from)) {
                continue;
            }
            if (to != null && effectiveDate.isAfter(to)) {
                continue;
            }
            if (selected == null || (from != null && (selectedFrom == null || from.isAfter(selectedFrom)))) {
                selected = tariff;
                selectedFrom = from;
            }
        }
        return selected;
    }

    private LocalDate parseEffectiveDate(String value, String field, String tariffId) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid " + field + " for DJB tariff " + tariffId + ": " + value, e);
        }
    }

    private List<DJBMonthlyWaterTariffSlab> sortedActiveSlabs(DJBMonthlyWaterTariff tariff) {
        List<DJBMonthlyWaterTariffSlab> result = new ArrayList<DJBMonthlyWaterTariffSlab>();
        if (tariff.getSlabs() != null) {
            for (DJBMonthlyWaterTariffSlab slab : tariff.getSlabs()) {
                if (slab != null) {
                    result.add(slab);
                }
            }
        }
        result.sort(Comparator.comparing(slab -> zeroIfNull(slab.getFrom())));
        return result;
    }

    private String normalizeCategory(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().replace("-", "_").replace(" ", "_").toUpperCase();
        if ("RESIDENTIAL".equals(normalized) || "DOMESTIC_CONSUMER".equals(normalized)) {
            return "DOMESTIC";
        }
        if ("NON_DOMESTIC".equals(normalized) || "BUSINESS".equals(normalized)) {
            return "COMMERCIAL";
        }
        return normalized;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateConsumption(BigDecimal consumption) {
        if (consumption == null) {
            throw new IllegalArgumentException("Consumption is required for tariff calculation");
        }
        if (consumption.signum() < 0) {
            throw new IllegalArgumentException("Consumption cannot be negative");
        }
    }

    private void validateBillingPeriodMonths(BigDecimal billingPeriodMonths) {
        if (billingPeriodMonths == null || billingPeriodMonths.signum() <= 0) {
            throw new IllegalArgumentException("Billing-period month factor must be greater than zero");
        }
    }
}
