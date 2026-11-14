package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.egov.wscalculation.djbmonthlybilling.model.master.DJBAdditionalSewerageCharge;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlySewerageRule;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationResult;
import org.springframework.stereotype.Service;

@Service
public class SewerageCalculationService {

	private static final int MONEY_SCALE = 2;

	public SewerageCalculationResult calculate(SewerageCalculationContext context,
			List<DJBMonthlySewerageRule> regularRules, List<DJBAdditionalSewerageCharge> additionalRules) {

		if (context == null) {
			throw new IllegalArgumentException("Sewerage calculation context is required");
		}

		BigDecimal regular = calculateRegular(context, regularRules);
		AdditionalChargeSelection additional = calculateAdditional(context, additionalRules);

		BigDecimal total = regular.add(additional.amount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

		String explanation;
		if (total.signum() == 0) {
			explanation = "No sewerage charge applicable";
		} else {
			explanation = "Regular sewerage=" + regular + ", additional sewerage=" + additional.amount;
		}

		return SewerageCalculationResult.builder().regularSewerageCharge(regular)
				.additionalSewerageCharge(additional.amount).totalSewerageCharge(total)
				.regularRuleCode(findRegularRuleCode(context, regularRules)).additionalRuleCode(additional.ruleCode)
				.explanation(explanation).build();
	}

	private BigDecimal calculateRegular(SewerageCalculationContext context, List<DJBMonthlySewerageRule> rules) {

		if (!context.isSewerConnectionAvailable() || rules == null || rules.isEmpty()) {
			return moneyZero();
		}

		if (context.isWaterConnectionAvailable()) {
			DJBMonthlySewerageRule rule = rules.stream().filter(Objects::nonNull)
					.filter(r -> !Boolean.FALSE.equals(r.getActive()))
					.filter(r -> "WATER_CONNECTED".equalsIgnoreCase(r.getCode())).findFirst().orElse(null);

			if (rule == null || rule.getPercentage() == null || context.getWaterVolumetricCharge() == null) {
				return moneyZero();
			}

			return context.getWaterVolumetricCharge().multiply(rule.getPercentage())
					.divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP)
					.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		}

		String category = normalize(context.getConsumerCategory());

		BigDecimal monthlyAmount = rules.stream().filter(Objects::nonNull).filter(r -> !Boolean.FALSE.equals(r.getActive()))
				.filter(r -> !"WATER_CONNECTED".equalsIgnoreCase(r.getCode()))
				.filter(r -> "ALL".equalsIgnoreCase(normalize(r.getCategory()))
						|| category.equals(normalize(r.getCategory())))
				.filter(r -> Boolean.FALSE.equals(r.getRequiresWaterConnection())
						|| r.getRequiresWaterConnection() == null)
				.filter(r -> withinArea(r, context.getBuiltUpAreaSqm()))
				.sorted(Comparator.comparing(this::areaRangeStart).reversed())
				.map(DJBMonthlySewerageRule::getAmount)
				.filter(Objects::nonNull).findFirst().orElse(BigDecimal.ZERO);

		return money(monthlyAmount.multiply(resolveBillingPeriodMonths(context)));
	}

	private AdditionalChargeSelection calculateAdditional(SewerageCalculationContext context,
			List<DJBAdditionalSewerageCharge> rules) {

		if (!context.isAdditionalWaterSource() || rules == null || rules.isEmpty()) {
			return new AdditionalChargeSelection(moneyZero(), null);
		}

		String usage = normalize(context.getPropertyUsage());

		List<DJBAdditionalSewerageCharge> matches = rules.stream().filter(Objects::nonNull)
				.filter(r -> !Boolean.FALSE.equals(r.getActive()))
				.filter(r -> usage.equals(normalize(r.getPropertyUsage())))
				.sorted(Comparator.comparing(this::rangeStart).reversed()).collect(Collectors.toList());

		for (DJBAdditionalSewerageCharge rule : matches) {
			if (!rangeMatches(rule, context)) {
				continue;
			}

			return new AdditionalChargeSelection(calculateAdditionalRuleAmount(rule, context), rule.getCode());
		}

		return new AdditionalChargeSelection(moneyZero(), null);
	}

	private BigDecimal calculateAdditionalRuleAmount(DJBAdditionalSewerageCharge rule,
			SewerageCalculationContext context) {

		String type = normalize(rule.getChargeType());
		BigDecimal billingPeriodMonths = resolveBillingPeriodMonths(context);

		if ("FIXED_PER_SITE".equals(type)
				|| ("FIXED_MONTHLY".equals(type)
						&& "FUNCTIONAL_SITE".equalsIgnoreCase(normalize(rule.getUnit())))) {
			// DJB Table 7 states banquet hall charges as Rs.10,000 per functional
			// site, not as a monthly recurring charge.
			if (context.getFunctionalSiteCount() == null) {
				throw new IllegalArgumentException(
						"Functional site count is required for banquet-hall sewerage charge");
			}
			if (context.getFunctionalSiteCount() < 0) {
				throw new IllegalArgumentException("Functional site count cannot be negative");
			}
			return money(value(rule.getAmount()).multiply(BigDecimal.valueOf(context.getFunctionalSiteCount())));
		}

		if ("FIXED_MONTHLY".equals(type)) {
			return money(value(rule.getAmount()).multiply(billingPeriodMonths));
		}

		if ("BASE_PLUS_PER_BLOCK".equals(type)) {
			Integer count = context.getNumberOfRooms() != null ? context.getNumberOfRooms() : context.getNumberOfBeds();

			if (count == null) {
				throw new IllegalArgumentException("Rooms/beds are required for block-based additional sewerage");
			}

			Integer start = rule.getFromRooms() != null ? rule.getFromRooms() : rule.getFromBeds();

			int blockSize = rule.getBlockSize() == null || rule.getBlockSize() <= 0 ? 50 : rule.getBlockSize();

			int excess = Math.max(0, count - start + 1);
			int blocks = excess == 0 ? 0 : (excess + blockSize - 1) / blockSize;

			BigDecimal monthlyAmount = value(rule.getBaseAmount())
					.add(value(rule.getBlockAmount()).multiply(BigDecimal.valueOf(blocks)));

			return money(monthlyAmount.multiply(billingPeriodMonths));
		}

		throw new IllegalArgumentException("Unsupported additional sewerage charge type: " + rule.getChargeType());
	}

	private boolean rangeMatches(DJBAdditionalSewerageCharge rule, SewerageCalculationContext context) {

		Integer count = context.getNumberOfRooms() != null ? context.getNumberOfRooms() : context.getNumberOfBeds();

		if (count == null) {
			return rule.getFromRooms() == null && rule.getFromBeds() == null;
		}

		Integer from = rule.getFromRooms() != null ? rule.getFromRooms() : rule.getFromBeds();

		Integer to = rule.getToRooms() != null ? rule.getToRooms() : rule.getToBeds();

		if (from != null && count < from) {
			return false;
		}
		return to == null || count <= to;
	}

	private boolean withinArea(DJBMonthlySewerageRule rule, BigDecimal area) {

		if (area == null) {
			return false;
		}
		if (rule.getMinBuiltUpAreaSqm() != null) {
			int comparison = area.compareTo(rule.getMinBuiltUpAreaSqm());
			if (Boolean.TRUE.equals(rule.getMinBuiltUpAreaExclusive()) ? comparison <= 0 : comparison < 0) {
				return false;
			}
		}
		return rule.getMaxBuiltUpAreaSqm() == null || area.compareTo(rule.getMaxBuiltUpAreaSqm()) <= 0;
	}

	private String findRegularRuleCode(SewerageCalculationContext context, List<DJBMonthlySewerageRule> rules) {

		if (rules == null) {
			return null;
		}
		if (context.isWaterConnectionAvailable()) {
			return "WATER_CONNECTED";
		}

		String category = normalize(context.getConsumerCategory());
		return rules.stream().filter(Objects::nonNull).filter(r -> !Boolean.FALSE.equals(r.getActive()))
				.filter(r -> !"WATER_CONNECTED".equalsIgnoreCase(r.getCode()))
				.filter(r -> "ALL".equalsIgnoreCase(normalize(r.getCategory()))
						|| category.equals(normalize(r.getCategory())))
				.filter(r -> Boolean.FALSE.equals(r.getRequiresWaterConnection())
						|| r.getRequiresWaterConnection() == null)
				.filter(r -> withinArea(r, context.getBuiltUpAreaSqm()))
				.sorted(Comparator.comparing(this::areaRangeStart).reversed())
				.map(DJBMonthlySewerageRule::getCode)
				.findFirst().orElse(null);
	}

	private BigDecimal areaRangeStart(DJBMonthlySewerageRule rule) {
		return rule.getMinBuiltUpAreaSqm() == null ? BigDecimal.ZERO : rule.getMinBuiltUpAreaSqm();
	}

	private BigDecimal rangeStart(DJBAdditionalSewerageCharge rule) {
		Integer value = rule.getFromRooms() != null ? rule.getFromRooms() : rule.getFromBeds();
		return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
	}

	private BigDecimal resolveBillingPeriodMonths(SewerageCalculationContext context) {
		if (context != null && context.getBillingPeriodMonths() != null
				&& context.getBillingPeriodMonths().signum() > 0) {
			return context.getBillingPeriodMonths();
		}
		if (context == null || context.getBillingMonths() == null || context.getBillingMonths() <= 0) {
			return BigDecimal.ONE;
		}
		return BigDecimal.valueOf(context.getBillingMonths());
	}

	private BigDecimal money(BigDecimal value) {
		return value(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

	private BigDecimal value(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private BigDecimal moneyZero() {
		return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().replace("-", "_").replace(" ", "_").toUpperCase();
	}

	private static class AdditionalChargeSelection {
		private final BigDecimal amount;
		private final String ruleCode;

		private AdditionalChargeSelection(BigDecimal amount, String ruleCode) {
			this.amount = amount;
			this.ruleCode = ruleCode;
		}
	}
}
