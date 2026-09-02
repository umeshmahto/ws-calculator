package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBAdditionalSewerageCharge;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyRebate;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlySewerageRule;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariff;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.TariffCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.master.DJBMonthlyBillingMasterProvider;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingTestRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingTestResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DJBMonthlyBillingTestService {

	private static final int MONEY_SCALE = 2;

	private final DJBMonthlyBillingMasterProvider masterProvider;
	private final TariffCalculationService tariffCalculationService;
	private final SewerageCalculationService sewerageCalculationService;
	private final RebateCalculationService rebateCalculationService;

	public DJBMonthlyBillingTestService(DJBMonthlyBillingMasterProvider masterProvider,
			TariffCalculationService tariffCalculationService, SewerageCalculationService sewerageCalculationService,
			RebateCalculationService rebateCalculationService) {

		this.masterProvider = masterProvider;
		this.tariffCalculationService = tariffCalculationService;
		this.sewerageCalculationService = sewerageCalculationService;
		this.rebateCalculationService = rebateCalculationService;
	}

	public DJBMonthlyBillingTestResponse calculate(DJBMonthlyBillingTestRequest request) {

		validate(request);

		RequestInfo requestInfo = request.getRequestInfo();
		String tenantId = request.getTenantId();

		List<DJBMonthlyWaterTariff> tariffs = masterProvider.getWaterTariffs(requestInfo, tenantId);

		TariffCalculationResult water = tariffCalculationService.calculate(request.getConsumption(),
				request.getTariffCategory(), tariffs);

		List<DJBMonthlySewerageRule> sewerRules = masterProvider.getSewerageRules(requestInfo, tenantId);

		List<DJBAdditionalSewerageCharge> additionalSewerRules = masterProvider
				.getAdditionalSewerageCharges(requestInfo, tenantId);

		SewerageCalculationContext sewerContext = SewerageCalculationContext.builder()
				.waterVolumetricCharge(water.getWaterVolumetricCharge())
				.waterConnectionAvailable(request.isWaterConnectionAvailable())
				.sewerConnectionAvailable(request.isSewerConnectionAvailable())
				.additionalWaterSource(request.isAdditionalWaterSource())
				.consumerCategory(request.getConsumerCategory()).propertyUsage(request.getPropertyUsage())
				.builtUpAreaSqm(request.getBuiltUpAreaSqm()).numberOfRooms(request.getNumberOfRooms())
				.numberOfBeds(request.getNumberOfBeds()).build();

		SewerageCalculationResult sewerage = sewerageCalculationService.calculate(sewerContext, sewerRules,
				additionalSewerRules);

		BigDecimal totalBeforeRebate = water.getTotalWaterCharge().add(sewerage.getTotalSewerageCharge())
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

		List<DJBMonthlyRebate> rebates = masterProvider.getRebates(requestInfo, tenantId);

		RebateCalculationContext rebateContext = RebateCalculationContext.builder()
				.consumption(request.getConsumption()).billingBasis(request.getBillingBasis())
				.readingQualityCode(request.getReadingQualityCode()).consumerType(request.getConsumerCategory())
				.propertyCategory(request.getConsumerCategory()).connectionType(request.getTariffCategory())
				.bulkConnection(false).propertyAreaSqm(request.getBuiltUpAreaSqm()).functionalRwh(false)
				.functionalWastewaterRecycling(false).totalBillBeforeRebate(totalBeforeRebate)
				.freeWaterEligibleAmount(request.getFreeWaterEligibleAmount()).build();

		RebateCalculationResult rebate = rebateCalculationService.calculate(rebateContext, rebates);

		BigDecimal netAmount = totalBeforeRebate.subtract(rebate.getTotalRebate()).setScale(MONEY_SCALE,
				RoundingMode.HALF_UP);

		return DJBMonthlyBillingTestResponse.builder().tenantId(tenantId).connectionNo(request.getConnectionNo())
				.readingQualityCode(request.getReadingQualityCode())
				.billingBasis(request.getBillingBasis() == null ? null : request.getBillingBasis().toString())
				.consumption(request.getConsumption()).previousConsumption(request.getPreviousConsumption())
				.water(water).sewerage(sewerage).rebate(rebate).totalBeforeRebate(totalBeforeRebate)
				.netAmountAfterRebate(netAmount).build();
	}

	private void validate(DJBMonthlyBillingTestRequest request) {

		if (request == null) {
			throw new IllegalArgumentException("Request is required");
		}

		if (!StringUtils.hasText(request.getTenantId())) {
			throw new IllegalArgumentException("tenantId is required");
		}

		if (request.getRequestInfo() == null) {
			throw new IllegalArgumentException("requestInfo is required");
		}

		if (!StringUtils.hasText(request.getTariffCategory())) {
			throw new IllegalArgumentException("tariffCategory is required");
		}

		if (request.getConsumption() == null) {
			throw new IllegalArgumentException("consumption is required");
		}

		if (request.getConsumption().signum() < 0) {
			throw new IllegalArgumentException("consumption cannot be negative");
		}

		if (!StringUtils.hasText(request.getReadingQualityCode())) {
			throw new IllegalArgumentException("readingQualityCode is required");
		}
	}
}
