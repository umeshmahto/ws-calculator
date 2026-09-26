package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyRebate;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateItem;
import org.springframework.stereotype.Service;

@Service
public class RebateCalculationService {

	private static final int MONEY_SCALE = 2;

	/**
	 * Calculates the currently configured DJB monthly rebates.
	 *
	 * Supported rules:
	 * - FREE_WATER_20KL
	 * - RWH_10 / RWH_WATER_RECYCLING_15 (highest applicable rate only)
	 * - DJB_EMPLOYEE_50
	 *
	 * The service does not modify demand/bill records.
	 */
	public RebateCalculationResult calculate(RebateCalculationContext context, List<DJBMonthlyRebate> rebates) {

		if (context == null) {
			throw new IllegalArgumentException("Rebate calculation context is required");
		}

		List<RebateItem> items = new ArrayList<>();

		addFreeWaterRebate(context, rebates, items);
		addRwhRebate(context, rebates, items);
		addDjbEmployeeRebate(context, rebates, items);

		BigDecimal total = BigDecimal.ZERO;

		for (RebateItem item : items) {
			total = total.add(item.getRebateAmount());
		}

		total = total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

		String explanation = items.isEmpty() ? "No DJB monthly rebate applicable"
				: items.stream().map(RebateItem::getExplanation).collect(Collectors.joining("; "));

		return RebateCalculationResult.builder().totalRebate(total).rebateItems(items).explanation(explanation).build();
	}

	private void addFreeWaterRebate(RebateCalculationContext context, List<DJBMonthlyRebate> rebates,
			List<RebateItem> items) {

		DJBMonthlyRebate rule = findByCode(rebates, "FREE_WATER_20KL");

		if (rule == null || !Boolean.TRUE.equals(rule.getActive())) {
			return;
		}

		if (!isFreeWaterEligible(context, rule)) {
			return;
		}

		BigDecimal baseAmount = context.getFreeWaterEligibleAmount();

		/*
		 * The caller supplies the complete eligible water + sewerage bill amount.
		 * For an eligible Meter-OK reading at or below the free-water limit, the
		 * 100% concession therefore makes the user's payable amount zero.
		 */
		if (baseAmount == null || baseAmount.signum() <= 0) {
			return;
		}

		BigDecimal rebateAmount = percentage(baseAmount, rule.getRate());

		items.add(RebateItem.builder().code(rule.getCode()).name(rule.getName()).rate(rule.getRate())
				.baseAmount(money(baseAmount)).rebateAmount(rebateAmount)
				.explanation(rule.getRate() + "% free-water rebate applied on eligible amount").build());
	}

	private void addRwhRebate(RebateCalculationContext context, List<DJBMonthlyRebate> rebates,
			List<RebateItem> items) {

		if (context.getTotalBillBeforeRebate() == null || context.getTotalBillBeforeRebate().signum() <= 0) {
			return;
		}

		List<DJBMonthlyRebate> candidates = rebates == null ? new ArrayList<>()
				: rebates.stream().filter(Objects::nonNull).filter(r -> Boolean.TRUE.equals(r.getActive()))
						.filter(r -> isRwhRule(r.getCode())).filter(r -> r.getRate() != null)
						.filter(r -> r.getMinPropertyAreaSqm() == null || context.getPropertyAreaSqm() != null
								&& context.getPropertyAreaSqm().compareTo(r.getMinPropertyAreaSqm()) >= 0)
						.filter(r -> !Boolean.TRUE.equals(r.getRequiresFunctionalRwh()) || context.isFunctionalRwh())
						.filter(r -> !Boolean.TRUE.equals(r.getRequiresFunctionalWastewaterRecycling())
								|| context.isFunctionalWastewaterRecycling())
						.collect(Collectors.toList());

		if (candidates.isEmpty()) {
			return;
		}

		/*
		 * RWH rules can overlap by design (e.g. 10% RWH and 15% RWH + wastewater
		 * recycling). These are treated as alternatives, not additive rebates. We
		 * therefore use the highest applicable rate.
		 */
		DJBMonthlyRebate selected = candidates.stream().max(Comparator.comparing(DJBMonthlyRebate::getRate))
				.orElse(null);

		if (selected == null) {
			return;
		}

		BigDecimal rebateAmount = percentage(context.getTotalBillBeforeRebate(), selected.getRate());

		items.add(RebateItem.builder().code(selected.getCode()).name(selected.getName()).rate(selected.getRate())
				.baseAmount(money(context.getTotalBillBeforeRebate())).rebateAmount(rebateAmount)
				.explanation(selected.getRate() + "% RWH rebate applied on total bill").build());
	}

	private void addDjbEmployeeRebate(RebateCalculationContext context, List<DJBMonthlyRebate> rebates,
			List<RebateItem> items) {

		DJBMonthlyRebate rule = findByCode(rebates, "DJB_EMPLOYEE_50");
		if (rule == null || !Boolean.TRUE.equals(rule.getActive()) || rule.getRate() == null) {
			return;
		}

		if (!context.isDjbEmployeeEligible()) {
			return;
		}

		if (rule.getEligibleConnectionType() != null
				&& !matches(context.getConnectionType(), rule.getEligibleConnectionType())) {
			return;
		}

		Integer maxConnections = rule.getMaxEligibleConnections();
		Integer actualConnections = context.getEligibleConnectionCount();
		if (maxConnections != null && (actualConnections == null || actualConnections < 1
				|| actualConnections > maxConnections)) {
			return;
		}

		BigDecimal baseAmount = context.getTotalBillBeforeRebate();
		if (baseAmount == null || baseAmount.signum() <= 0) {
			return;
		}

		BigDecimal rebateAmount = percentage(baseAmount, rule.getRate());
		items.add(RebateItem.builder().code(rule.getCode()).name(rule.getName()).rate(rule.getRate())
				.baseAmount(money(baseAmount)).rebateAmount(rebateAmount)
				.explanation(rule.getRate() + "% DJB employee rebate applied on eligible domestic bill").build());
	}

	private boolean isFreeWaterEligible(RebateCalculationContext context, DJBMonthlyRebate rule) {

		BigDecimal monthlyConsumption = resolveMonthlyConsumption(context);
		if (monthlyConsumption == null || rule.getMaxConsumptionKl() == null) {
			return false;
		}

		/*
		 * The 20 KL ceiling is monthly. For a non-bulk domestic connection the
		 * normalized monthly consumption applies directly. For a bulk domestic
		 * connection DJB defines the free-water limit per
		 * dwelling unit, so the effective limit is 20 KL x dwelling units.
		 * Do not apply the single-unit 20 KL check before the bulk calculation.
		 */
		if (!context.isBulkConnection()
				&& monthlyConsumption.compareTo(rule.getMaxConsumptionKl()) > 0) {
			return false;
		}

		/*
		 * Prefer the eligibility lists in MDMS so that the billing rule remains
		 * configuration-driven. When a master does not provide a list, retain the DJB
		 * default of Meter OK / ACTUAL for the free-water rule.
		 */
		if (!isEligibleReadingAndBasis(context, rule)) {
			return false;
		}

		if (rule.getConsumerType() != null && !matches(context.getConsumerType(), rule.getConsumerType())) {
			return false;
		}

		if (rule.getPropertyCategory() != null && !matches(context.getPropertyCategory(), rule.getPropertyCategory())) {
			return false;
		}

		if (rule.getEligibleConnectionType() != null
				&& !matches(context.getConnectionType(), rule.getEligibleConnectionType())) {
			return false;
		}

		if (context.isBulkConnection()) {
			if (!Boolean.TRUE.equals(rule.getBulkApplicable())) {
				return false;
			}
			// DJB bulk domestic free-water eligibility is per dwelling unit.
			// Without a valid dwelling-unit count we must not grant the rebate.
			if (context.getDwellingUnitCount() == null || context.getDwellingUnitCount() <= 0) {
				return false;
			}
			BigDecimal bulkLimit = rule.getMaxConsumptionKl()
					.multiply(BigDecimal.valueOf(context.getDwellingUnitCount()));
			if (monthlyConsumption.compareTo(bulkLimit) > 0) {
				return false;
			}
		}

		return true;
	}

	/**
	 * DJB free-water eligibility is a monthly threshold. Production demand
	 * generation supplies the normalized monthly consumption for multi-month
	 * meter-reading cycles. For one-month/unit tests or legacy callers where the
	 * normalized value is not supplied, the raw consumption remains the correct
	 * monthly value.
	 */
	private BigDecimal resolveMonthlyConsumption(RebateCalculationContext context) {
		if (context.getMonthlyConsumption() != null) {
			return context.getMonthlyConsumption();
		}
		return context.getConsumption();
	}

	private boolean isEligibleReadingAndBasis(RebateCalculationContext context, DJBMonthlyRebate rule) {
		boolean readingEligible = rule.getEligibleReadingQualityCodes() == null
				|| rule.getEligibleReadingQualityCodes().isEmpty() || rule.getEligibleReadingQualityCodes().stream()
						.anyMatch(code -> code != null && code.equalsIgnoreCase(context.getReadingQualityCode()));

		String currentBasis = context.getBillingBasis() == null ? null : context.getBillingBasis().name();
		boolean basisEligible = rule.getEligibleBillingBasis() == null || rule.getEligibleBillingBasis().isEmpty()
				|| rule.getEligibleBillingBasis().stream().anyMatch(basis -> isConfiguredBillingBasisEligible(basis, context, currentBasis));

		if (rule.getEligibleReadingQualityCodes() == null || rule.getEligibleReadingQualityCodes().isEmpty()) {
			readingEligible = "OK".equalsIgnoreCase(context.getReadingQualityCode())
					&& BillingBasis.ACTUAL.equals(context.getBillingBasis());
		}

		if (rule.getEligibleBillingBasis() == null || rule.getEligibleBillingBasis().isEmpty()) {
			basisEligible = isMeterOkActualBasis(context);
		}

		return readingEligible && basisEligible;
	}

	private boolean isConfiguredBillingBasisEligible(String configuredBasis, RebateCalculationContext context, String currentBasis) {
		if (configuredBasis == null || currentBasis == null) {
			return false;
		}
		if (configuredBasis.equalsIgnoreCase(currentBasis)) {
			return true;
		}
		// DJB source says the free-water concession is based on Meter-OK. A corrected
		// actual cycle with RQC=OK is still an OK-meter billing outcome, so an MDMS rule
		// configured for ACTUAL also covers CORRECTED_ACTUAL.
		return "ACTUAL".equalsIgnoreCase(configuredBasis)
				&& "CORRECTED_ACTUAL".equalsIgnoreCase(currentBasis)
				&& "OK".equalsIgnoreCase(context.getReadingQualityCode());
	}

	private boolean isMeterOkActualBasis(RebateCalculationContext context) {
		if (!"OK".equalsIgnoreCase(context.getReadingQualityCode()) || context.getBillingBasis() == null) {
			return false;
		}
		return BillingBasis.ACTUAL.equals(context.getBillingBasis())
				|| BillingBasis.CORRECTED_ACTUAL.equals(context.getBillingBasis());
	}

	private boolean isRwhRule(String code) {
		return code != null && code.toUpperCase(Locale.ENGLISH).startsWith("RWH");
	}

	private DJBMonthlyRebate findByCode(List<DJBMonthlyRebate> rebates, String code) {

		if (rebates == null) {
			return null;
		}

		return rebates.stream().filter(Objects::nonNull).filter(r -> code.equalsIgnoreCase(r.getCode())).findFirst()
				.orElse(null);
	}

	private boolean matches(String actual, String expected) {
		return actual != null && expected != null && expected.equalsIgnoreCase(actual);
	}

	private BigDecimal percentage(BigDecimal base, BigDecimal rate) {

		if (base == null || rate == null) {
			return BigDecimal.ZERO.setScale(MONEY_SCALE);
		}

		return base.multiply(rate).divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP)
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

	private BigDecimal money(BigDecimal value) {
		return value == null ? BigDecimal.ZERO.setScale(MONEY_SCALE)
				: value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}
}