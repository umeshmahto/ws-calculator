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
	 * Supported by this step: 1. FREE_WATER_20KL 2. RWH rebates (10% / 15% based on
	 * configured master conditions)
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
		 * Do not infer the tax-head scope of the free-water exemption here. The DJB
		 * source says free water up to 20 KL is 100% and requires Meter OK basis, but
		 * it does not in the supplied section define every tax-head that must be
		 * zeroed. The caller supplies the eligible amount explicitly.
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

	private boolean isFreeWaterEligible(RebateCalculationContext context, DJBMonthlyRebate rule) {

		if (context.getConsumption() == null || rule.getMaxConsumptionKl() == null
				|| context.getConsumption().compareTo(rule.getMaxConsumptionKl()) > 0) {
			return false;
		}

		/*
		 * DJB specifies the 20 KL rebate only for Meter OK basis. We require both the
		 * billing basis and the field remark to agree.
		 */
		if (!BillingBasis.ACTUAL.equals(context.getBillingBasis())) {
			return false;
		}

		if (!"OK".equalsIgnoreCase(context.getReadingQualityCode())) {
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

		if (!Boolean.TRUE.equals(rule.getBulkApplicable()) && context.isBulkConnection()) {
			return false;
		}

		return true;
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