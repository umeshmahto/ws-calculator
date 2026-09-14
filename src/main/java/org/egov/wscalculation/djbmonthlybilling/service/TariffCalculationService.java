package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

		validateConsumption(consumption);

		DJBMonthlyWaterTariff tariff = resolveTariff(requestedCategory, tariffs);

		if (tariff == null) {
			throw new IllegalArgumentException("DJB monthly tariff not configured for category: " + requestedCategory);
		}

		List<DJBMonthlyWaterTariffSlab> slabs = sortedActiveSlabs(tariff);

		if (slabs.isEmpty()) {
			throw new IllegalArgumentException("No active slabs configured for DJB tariff: " + tariff.getId());
		}

		// Apply the single tariff rate corresponding to the overall monthly consumption.
		// The source DJB document defines consumption bands and rates, but does not
		// explicitly prescribe progressive splitting across multiple bands. Until
		// DJB confirms that interpretation, use one applicable slab for the full
		// consumption.
		DJBMonthlyWaterTariffSlab applicableSlab = findApplicableSlab(consumption, slabs);
		if (applicableSlab == null) {
			throw new IllegalArgumentException("No applicable DJB tariff slab for consumption: " + consumption);
		}

		BigDecimal from = zeroIfNull(applicableSlab.getFrom());
		BigDecimal to = applicableSlab.getTo();

		if (to != null && to.compareTo(from) <= 0) {
			throw new IllegalArgumentException("Invalid DJB tariff slab: from=" + from + ", to=" + to);
		}

		if (applicableSlab.getRatePerKl() == null || applicableSlab.getRatePerKl().signum() < 0) {
			throw new IllegalArgumentException("Invalid rate in DJB tariff slab");
		}

		BigDecimal waterCharge = consumption.multiply(applicableSlab.getRatePerKl())
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

		List<TariffSlabCharge> slabCharges = new ArrayList<>();
		BigDecimal charge = waterCharge;
		slabCharges.add(TariffSlabCharge.builder().from(from).to(to)
				.units(consumption.setScale(CONSUMPTION_SCALE, RoundingMode.HALF_UP))
				.ratePerKl(applicableSlab.getRatePerKl()).charge(charge).build());

		BigDecimal serviceCharge = findServiceCharge(consumption, slabs);

		BigDecimal totalWaterCharge = waterCharge.add(serviceCharge).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

		return TariffCalculationResult.builder().tariffId(tariff.getId()).category(tariff.getCategory())
				.consumption(consumption.setScale(CONSUMPTION_SCALE, RoundingMode.HALF_UP))
				.waterVolumetricCharge(waterCharge).serviceCharge(serviceCharge).totalWaterCharge(totalWaterCharge)
				.slabCharges(slabCharges).build();
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

	private DJBMonthlyWaterTariff resolveTariff(String requestedCategory, List<DJBMonthlyWaterTariff> tariffs) {

		if (tariffs == null || tariffs.isEmpty()) {
			return null;
		}

		String normalized = normalizeCategory(requestedCategory);

		for (DJBMonthlyWaterTariff tariff : tariffs) {
			if (!Boolean.TRUE.equals(tariff.getActive())) {
				continue;
			}

			if (normalizeCategory(tariff.getCategory()).equals(normalized)) {
				return tariff;
			}
		}

		return null;
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