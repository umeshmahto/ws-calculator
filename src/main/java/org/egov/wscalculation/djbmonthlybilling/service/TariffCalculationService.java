package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariff;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariffSlab;
import org.egov.wscalculation.djbmonthlybilling.service.dto.TariffCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.TariffSlabCharge;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * DJB monthly water tariff calculation.
 *
 * Responsibilities: - Resolve the DJB tariff by consumer category. - Apply
 * single applicable tariff rate based on the overall monthly consumption band. - Select the applicable monthly service charge.
 *
 * This class does not create tax-head estimates, demands or bills. Existing
 * EstimationService is intentionally untouched.
 */
@Service
public class TariffCalculationService {

	private static final int MONEY_SCALE = 2;
	private static final int CONSUMPTION_SCALE = 3;

	public TariffCalculationResult calculate(BigDecimal consumption, String requestedCategory,
			List<DJBMonthlyWaterTariff> tariffs) {
		return calculate(consumption, requestedCategory, tariffs, 1L);
	}

	/**
	 * Calculates tariff for a meter-to-meter consumption that can represent one or
	 * more DJB billing months. Tariff slabs and service charges are monthly, so the
	 * slab is selected from monthly-equivalent consumption and the resulting monthly
	 * charges are multiplied by the number of billing months.
	 *
	 * <p>The actual meter-to-meter consumption is retained in the result for audit
	 * and downstream billing; only the tariff slab selection and monthly fixed
	 * service charge are normalised.</p>
	 */
	public TariffCalculationResult calculate(BigDecimal consumption, String requestedCategory,
			List<DJBMonthlyWaterTariff> tariffs, long billingMonths) {
		return calculate(consumption, requestedCategory, tariffs, billingMonths, null, null);
	}

	/**
	 * Calculates tariff using the tariff version effective on the billing period end date.
	 * The legacy overload remains available for unit/legacy callers that do not supply a date.
	 */
	public TariffCalculationResult calculate(BigDecimal consumption, String requestedCategory,
			List<DJBMonthlyWaterTariff> tariffs, long billingMonths, Long billingPeriodTo) {
		return calculate(consumption, requestedCategory, tariffs, billingMonths, null, billingPeriodTo);
	}

	/**
	 * Date-aware production tariff calculation. A selected tariff must cover the complete
	 * billing period; otherwise silently applying a single tariff across a revision boundary
	 * would produce an incorrect bill. Such a cycle must be split before demand generation.
	 */
	public TariffCalculationResult calculate(BigDecimal consumption, String requestedCategory,
			List<DJBMonthlyWaterTariff> tariffs, long billingMonths, Long billingPeriodFrom, Long billingPeriodTo) {

		validateConsumption(consumption);
		if (billingMonths <= 0) {
			throw new IllegalArgumentException("Billing months must be greater than zero");
		}

		BigDecimal monthlyConsumption = consumption.divide(
				BigDecimal.valueOf(billingMonths), CONSUMPTION_SCALE + 3, RoundingMode.HALF_UP);

		DJBMonthlyWaterTariff tariff = resolveTariff(requestedCategory, tariffs, billingPeriodTo);

		if (tariff == null) {
			throw new IllegalArgumentException("DJB monthly tariff not configured for category: " + requestedCategory);
		}

		validateTariffCoverage(tariff, billingPeriodFrom, billingPeriodTo);

		List<DJBMonthlyWaterTariffSlab> slabs = sortedActiveSlabs(tariff);

		if (slabs.isEmpty()) {
			throw new IllegalArgumentException("No active slabs configured for DJB tariff: " + tariff.getId());
		}

		// DJB tariff bands are defined on monthly water consumption. For a delayed
		// meter reading that covers multiple billing months, select the slab from the
		// monthly-equivalent consumption rather than the raw meter delta.
		DJBMonthlyWaterTariffSlab applicableSlab = findApplicableSlab(monthlyConsumption, slabs);
		if (applicableSlab == null) {
			throw new IllegalArgumentException(
					"No applicable DJB tariff slab for monthly consumption: " + monthlyConsumption);
		}

		BigDecimal from = zeroIfNull(applicableSlab.getFrom());
		BigDecimal to = applicableSlab.getTo();

		if (to != null && to.compareTo(from) <= 0) {
			throw new IllegalArgumentException("Invalid DJB tariff slab: from=" + from + ", to=" + to);
		}

		if (applicableSlab.getRatePerKl() == null || applicableSlab.getRatePerKl().signum() < 0) {
			throw new IllegalArgumentException("Invalid rate in DJB tariff slab");
		}

		BigDecimal monthlyWaterCharge = monthlyConsumption.multiply(applicableSlab.getRatePerKl())
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		BigDecimal waterCharge = monthlyWaterCharge.multiply(BigDecimal.valueOf(billingMonths))
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

		List<TariffSlabCharge> slabCharges = new ArrayList<>();
		slabCharges.add(TariffSlabCharge.builder().from(from).to(to)
				.units(monthlyConsumption.setScale(CONSUMPTION_SCALE, RoundingMode.HALF_UP))
				.ratePerKl(applicableSlab.getRatePerKl()).charge(waterCharge).build());

		BigDecimal monthlyServiceCharge = findServiceCharge(monthlyConsumption, slabs);
		BigDecimal serviceCharge = monthlyServiceCharge.multiply(BigDecimal.valueOf(billingMonths))
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

		BigDecimal totalWaterCharge = waterCharge.add(serviceCharge).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

		return TariffCalculationResult.builder().tariffId(tariff.getId()).category(tariff.getCategory())
				.consumption(consumption.setScale(CONSUMPTION_SCALE, RoundingMode.HALF_UP))
				.monthlyConsumption(monthlyConsumption.setScale(CONSUMPTION_SCALE, RoundingMode.HALF_UP))
				.billingMonths(billingMonths)
				.waterVolumetricCharge(waterCharge).serviceCharge(serviceCharge).totalWaterCharge(totalWaterCharge)
				.slabCharges(slabCharges).build();
	}

	private void validateTariffCoverage(DJBMonthlyWaterTariff tariff, Long billingPeriodFrom, Long billingPeriodTo) {
		if (billingPeriodFrom == null || billingPeriodTo == null) {
			return;
		}

		LocalDate billingFrom = Instant.ofEpochMilli(billingPeriodFrom).atZone(ZoneOffset.UTC).toLocalDate();
		LocalDate billingTo = Instant.ofEpochMilli(billingPeriodTo).atZone(ZoneOffset.UTC).toLocalDate();
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

	private DJBMonthlyWaterTariffSlab findApplicableSlab(BigDecimal consumption,
			List<DJBMonthlyWaterTariffSlab> slabs) {

		for (DJBMonthlyWaterTariffSlab slab : slabs) {
			BigDecimal from = zeroIfNull(slab.getFrom());
			BigDecimal to = slab.getTo();

			// Treat slab upper bounds as inclusive. Since slabs are sorted by
			// lower bound, an exact boundary such as 20 or 30 resolves to the
			// first matching slab (0-20, then 20-30, etc.).
			boolean matches = consumption.compareTo(from) >= 0
					&& (to == null || consumption.compareTo(to) <= 0);

			if (matches) {
				return slab;
			}
		}

		return null;
	}

	private BigDecimal findServiceCharge(BigDecimal consumption, List<DJBMonthlyWaterTariffSlab> slabs) {

		/*
		 * Tariff service charge is associated with the consumption slab. Boundary
		 * handling is deliberately deterministic: 20 KL -> the slab ending at 20; 30 KL
		 * -> the slab ending at 30; >30 KL -> the open-ended slab.
		 */
		for (DJBMonthlyWaterTariffSlab slab : slabs) {

			BigDecimal from = zeroIfNull(slab.getFrom());
			BigDecimal to = slab.getTo();

			boolean matches;
			if (to == null) {
				matches = consumption.compareTo(from) > 0
						|| consumption.compareTo(BigDecimal.ZERO) == 0 && from.signum() == 0;
			} else {
				matches = consumption.compareTo(from) >= 0 && consumption.compareTo(to) <= 0;
			}

			if (matches) {
				return slab.getServiceCharge() == null ? BigDecimal.ZERO.setScale(MONEY_SCALE)
						: slab.getServiceCharge().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
			}
		}

		return BigDecimal.ZERO.setScale(MONEY_SCALE);
	}

	private DJBMonthlyWaterTariff resolveTariff(String requestedCategory,
			List<DJBMonthlyWaterTariff> tariffs, Long billingPeriodTo) {

		if (tariffs == null || tariffs.isEmpty()) {
			return null;
		}

		String normalized = normalizeCategory(requestedCategory);
		LocalDate effectiveDate = billingPeriodTo == null ? null
				: Instant.ofEpochMilli(billingPeriodTo).atZone(ZoneOffset.UTC).toLocalDate();

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
			throw new IllegalArgumentException(
					"Invalid " + field + " for DJB tariff " + tariffId + ": " + value, e);
		}
	}

	private List<DJBMonthlyWaterTariffSlab> sortedActiveSlabs(DJBMonthlyWaterTariff tariff) {

		List<DJBMonthlyWaterTariffSlab> result = new ArrayList<>();

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
}