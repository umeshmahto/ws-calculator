package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.wscalculation.config.WSCalculationConfiguration;
import org.egov.wscalculation.constants.WSCalculationConstant;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingCycleStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ZroStatus;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBAdditionalSewerageCharge;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyRebate;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlySewerageRule;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariff;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.TariffCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.master.DJBMonthlyBillingMasterProvider;
import org.egov.wscalculation.djbmonthlybilling.service.ResidualCreditService.CreditReservationResult;
import org.egov.wscalculation.djbmonthlybilling.repository.ZroVerificationDao;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.model.ZroVerification;
import org.egov.wscalculation.producer.WSCalculationProducer;
import org.egov.wscalculation.repository.DemandRepository;
import org.egov.wscalculation.repository.ServiceRequestRepository;
import org.egov.wscalculation.util.CalculatorUtil;
import org.egov.wscalculation.util.WSCalculationUtil;
import org.egov.wscalculation.web.models.Demand;
import org.egov.wscalculation.web.models.DemandDetail;
import org.egov.wscalculation.web.models.DemandNotificationObj;
import org.egov.wscalculation.web.models.Property;
import org.egov.wscalculation.web.models.RequestInfoWrapper;
import org.egov.wscalculation.web.models.WaterConnection;
import org.egov.wscalculation.web.models.WaterConnectionRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DJBMonthlyDemandService {

	private static final int MONEY_SCALE = 2;

	private final DJBMonthlyBillingMasterProvider masterProvider;
	private final TariffCalculationService tariffCalculationService;
	private final ConsumptionService consumptionService;
	private final SewerageCalculationService sewerageCalculationService;
	private final RebateCalculationService rebateCalculationService;
	private final DemandRepository demandRepository;
	private final CalculatorUtil calculatorUtil;
	private final WSCalculationUtil wsCalculationUtil;
	private final WSCalculationConfiguration config;
	private final ServiceRequestRepository serviceRequestRepository;
	private final ObjectMapper objectMapper;
	private final WSCalculationProducer wsCalculationProducer;
	private final ResidualCreditService residualCreditService;
	private final ZroVerificationDao zroVerificationDao;
	private final WaterBillingCycleDao billingCycleDao;

	public DJBMonthlyDemandService(DJBMonthlyBillingMasterProvider masterProvider,
			TariffCalculationService tariffCalculationService, ConsumptionService consumptionService, SewerageCalculationService sewerageCalculationService,
			RebateCalculationService rebateCalculationService, DemandRepository demandRepository,
			CalculatorUtil calculatorUtil, WSCalculationUtil wsCalculationUtil, WSCalculationConfiguration config,
			ServiceRequestRepository serviceRequestRepository, ObjectMapper objectMapper,
			WSCalculationProducer wsCalculationProducer, ResidualCreditService residualCreditService,
			ZroVerificationDao zroVerificationDao, WaterBillingCycleDao billingCycleDao) {

		this.masterProvider = masterProvider;
		this.tariffCalculationService = tariffCalculationService;
		this.consumptionService = consumptionService;
		this.sewerageCalculationService = sewerageCalculationService;
		this.rebateCalculationService = rebateCalculationService;
		this.demandRepository = demandRepository;
		this.calculatorUtil = calculatorUtil;
		this.wsCalculationUtil = wsCalculationUtil;
		this.config = config;
		this.serviceRequestRepository = serviceRequestRepository;
		this.objectMapper = objectMapper;
		this.wsCalculationProducer = wsCalculationProducer;
		this.residualCreditService = residualCreditService;
		this.zroVerificationDao = zroVerificationDao;
		this.billingCycleDao = billingCycleDao;
	}

	/**
	 * Creates a generic UPYOG Demand from the already calculated DJB billing cycle.
	 * This service owns only DJB rule calculation and mapping. The generic
	 * billing-service remains untouched.
	 *
	 * After demand creation, the generic billing-service bill fetch API is invoked
	 * for normal (non-ZRO, non-pending-correction) cycles. The billing-service
	 * remains completely generic.
	 */
	public DemandResult createDemand(RequestInfo requestInfo, WaterBillingCycle cycle) {
		return createDemand(requestInfo, cycle, BigDecimal.ZERO);
	}

	/**
	 * Creates a DJB demand and, for an automatic correction, applies the total
	 * amount already collected against superseded average/provisional demands as
	 * a negative adjustment.
	 */
	public DemandResult createDemand(RequestInfo requestInfo, WaterBillingCycle cycle,
			BigDecimal paidAdjustmentAmount) {

		validateCycle(cycle);
		BigDecimal paidAdjustment = paidAdjustmentAmount == null
				? BigDecimal.ZERO
				: paidAdjustmentAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

		// Idempotency: the same billing cycle must never create a second demand.
		if (org.springframework.util.StringUtils.hasText(cycle.getDemandid())) {
			// Recover a credit reservation that may have been left RESERVED after a prior
			// attempt successfully created the Demand but failed before credit confirmation.
			if (!CorrectionStatus.PENDING.equals(cycle.getCorrectionstatus())) {
				residualCreditService.recoverPendingReservations(cycle.getTenantid(), cycle.getId(),
						cycle.getDemandid(), "SYSTEM", System.currentTimeMillis());
			}
			return DemandResult.builder().demandCreated(true).zroRequired(false)
					.message("DJB demand already exists for billing cycle").build();
		}

		BigDecimal consumption = cycle.getBillingconsumption();
		if (consumption == null) {
			throw new IllegalStateException("Billing consumption is required before demand generation");
		}

		String tenantId = cycle.getTenantid();
		String connectionNo = cycle.getConnectionno();

		WaterConnection connection = loadWaterConnection(requestInfo, connectionNo, tenantId);

		Property property = wsCalculationUtil.getProperty(
				WaterConnectionRequest.builder().requestInfo(requestInfo).waterConnection(connection).build());

		String category = resolveTariffCategory(connection, property);

		/*
		 * DJB 1.5x/ZRO is applicable only for domestic connections. Use the same
		 * resolved tariff category that drives the DJB tariff calculation instead of
		 * performing a second connection lookup in a separate isDomestic() method.
		 * This avoids silently bypassing ZRO when connection lookup/category data is
		 * temporarily unavailable.
		 */
		if (Boolean.TRUE.equals(cycle.getOnepointfivexflag())
				&& "DOMESTIC".equalsIgnoreCase(category)
				&& !ZroStatus.APPROVED.equals(cycle.getZrostatus())) {

			/*
			 * PENDING means ZRO has not decided yet, so no demand may be generated.
			 * REJECTED is different: DJB's 1.5x rule requires the rejected high
			 * consumption to fall back to average/provisional billing rather than
			 * disappearing from billing altogether. The ZRO rejection handler prepares
			 * the fallback provisional billing values before calling this method.
			 */
			if (!ZroStatus.REJECTED.equals(cycle.getZrostatus())) {
				cycle.setZrostatus(ZroStatus.PENDING);
				cycle.setZroremarks("Consumption exceeds DJB 1.5x threshold; ZRO verification required");
				cycle.setStatus(BillingCycleStatus.CALCULATED);
				cycle.setLastmodifiedby(actorForDemand(requestInfo));
				cycle.setLastmodifiedtime(System.currentTimeMillis());

				createPendingZroVerification(cycle, actorForDemand(requestInfo));

				if (billingCycleDao.update(cycle) != 1) {
					throw new IllegalStateException(
							"Failed to persist DJB ZRO PENDING status for billing cycle " + cycle.getId());
				}

				return DemandResult.builder().demandCreated(false).zroRequired(true)
						.message("Demand not generated because DJB 1.5x ZRO verification is required").build();
			}

		}

		List<DJBMonthlyWaterTariff> tariffs = masterProvider.getWaterTariffs(requestInfo, tenantId);

		TariffCalculationResult water = tariffCalculationService.calculate(consumption, category, tariffs);

		SewerageCalculationContext sewerageContext = SewerageCalculationContext.builder()
				.waterVolumetricCharge(water.getWaterVolumetricCharge()).waterConnectionAvailable(true)
				.sewerConnectionAvailable(true).additionalWaterSource(isAdditionalWaterSource(connection))
				.consumerCategory(category).propertyUsage(property.getUsageCategory())
				.builtUpAreaSqm(property.getSuperBuiltUpArea()).build();

		List<DJBMonthlySewerageRule> sewerageRules = masterProvider.getSewerageRules(requestInfo, tenantId);

		List<DJBAdditionalSewerageCharge> additionalSewerageRules = masterProvider
				.getAdditionalSewerageCharges(requestInfo, tenantId);

		SewerageCalculationResult sewerage = sewerageCalculationService.calculate(sewerageContext, sewerageRules,
				additionalSewerageRules);

		BigDecimal grossAmount = water.getTotalWaterCharge().add(sewerage.getTotalSewerageCharge())
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

		List<DJBMonthlyRebate> rebates = masterProvider.getRebates(requestInfo, tenantId);

		RebateCalculationContext rebateContext = RebateCalculationContext.builder().consumption(consumption)
				.billingBasis(cycle.getBillingbasis()).readingQualityCode(cycle.getReadingqualitycode())
				.consumerType(category).propertyCategory(category).connectionType(category).bulkConnection(false)
				.propertyAreaSqm(property.getSuperBuiltUpArea()).functionalRwh(false)
				.functionalWastewaterRecycling(false).totalBillBeforeRebate(grossAmount)
				.freeWaterEligibleAmount(water.getTotalWaterCharge()).build();

		RebateCalculationResult rebate = rebateCalculationService.calculate(rebateContext, rebates);

		BigDecimal baseNetAmount = grossAmount.subtract(rebate.getTotalRebate()).setScale(MONEY_SCALE,
				RoundingMode.HALF_UP);

		if (baseNetAmount.signum() < 0) {
			throw new IllegalStateException("DJB net demand amount cannot be negative: " + baseNetAmount);
		}

		/*
		 * A previously paid average/provisional assessment is a credit against the
		 * corrected OK-to-OK bill. Apply only the amount that can actually settle the
		 * corrected bill. If more was paid than the corrected amount, retain the
		 * excess as a separately auditable residual credit instead of creating a
		 * negative payable demand.
		 */
		BigDecimal appliedPaidAdjustment = paidAdjustment.min(baseNetAmount)
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		BigDecimal residualPaidCredit = paidAdjustment.subtract(appliedPaidAdjustment)
				.max(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

		BigDecimal carryForwardCreditApplied = BigDecimal.ZERO.setScale(MONEY_SCALE);
		CreditReservationResult creditReservation = CreditReservationResult.empty();

		/*
		 * A residual credit created by an earlier automatic correction is applied only
		 * to a normal future monthly bill. It is deliberately not mixed into the
		 * current correction transaction because the current correction may itself
		 * create a new residual credit.
		 */
		if (!CorrectionStatus.PENDING.equals(cycle.getCorrectionstatus())) {
			BigDecimal amountAvailableForCarryForward = baseNetAmount.subtract(appliedPaidAdjustment)
					.max(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
			creditReservation = residualCreditService.reserveForBillingCycle(tenantId, connectionNo, cycle.getId(),
					amountAvailableForCarryForward, actorForDemand(requestInfo), System.currentTimeMillis());
			carryForwardCreditApplied = residualCreditService.getTotalAppliedAmount(creditReservation);
		}

		BigDecimal netAmount = baseNetAmount.subtract(appliedPaidAdjustment).subtract(carryForwardCreditApplied)
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

		List<DemandDetail> demandDetails = new java.util.ArrayList<>();

		/*
		 * Generic WS tax-head contract:
		 *   - WS_CHARGE for gross water + sewer charge
		 *   - WS_TIME_REBATE for normal DJB rebate
		 *   - WS_TIME_ADHOC_REBATE for a paid DJB correction adjustment
		 *     (an existing generic WS tax head; no DJB-specific tax head is introduced)
		 */
		if (grossAmount.signum() > 0) {
			demandDetails.add(DemandDetail.builder().taxHeadMasterCode(WSCalculationConstant.WS_CHARGE)
					.taxAmount(grossAmount).collectionAmount(BigDecimal.ZERO).tenantId(tenantId).build());
		}

		if (rebate.getTotalRebate().signum() > 0) {
			demandDetails.add(DemandDetail.builder().taxHeadMasterCode(WSCalculationConstant.WS_TIME_REBATE)
					.taxAmount(rebate.getTotalRebate().negate().setScale(MONEY_SCALE, RoundingMode.HALF_UP))
					.collectionAmount(BigDecimal.ZERO).tenantId(tenantId).build());
		}

		if (appliedPaidAdjustment.signum() > 0) {
			/*
			 * Use the existing generic WS adhoc-rebate tax head rather than inventing a
			 * DJB-specific financial head. This keeps billing-service generic while making
			 * the prior paid amount a real negative bill detail.
			 */
			demandDetails.add(DemandDetail.builder()
					.taxHeadMasterCode(WSCalculationConstant.WS_TIME_ADHOC_REBATE)
					.taxAmount(appliedPaidAdjustment.negate().setScale(MONEY_SCALE, RoundingMode.HALF_UP))
					.collectionAmount(BigDecimal.ZERO).tenantId(tenantId).build());
		}

		if (carryForwardCreditApplied.signum() > 0) {
			/*
			 * Carry-forward of an earlier correction credit uses the same existing generic
			 * WS adhoc-rebate tax head. It is a real negative demand detail, so the
			 * generic billing-service naturally includes it in the next bill.
			 */
			demandDetails.add(DemandDetail.builder()
					.taxHeadMasterCode(WSCalculationConstant.WS_TIME_ADHOC_REBATE)
					.taxAmount(carryForwardCreditApplied.negate().setScale(MONEY_SCALE, RoundingMode.HALF_UP))
					.collectionAmount(BigDecimal.ZERO).tenantId(tenantId).build());
		}

		if (demandDetails.isEmpty()) {
			throw new IllegalStateException("No positive DJB demand detail could be generated");
		}

		User payer = resolvePayer(connection, property);

		Long taxPeriodFrom = cycle.getBillingperiodfrom();
		if (CorrectionStatus.PENDING.equals(cycle.getCorrectionstatus()) && cycle.getPreviousokreadingdate() != null) {
			/*
			 * DJB automatic correction is a single actual demand from the previous OK
			 * reading to the current OK reading.
			 */
			taxPeriodFrom = cycle.getPreviousokreadingdate();
		}

		Demand demand = Demand.builder().tenantId(tenantId).consumerCode(connectionNo).consumerType("waterConnection")
				.businessService(config.getBusinessService()).payer(payer).taxPeriodFrom(taxPeriodFrom)
				.taxPeriodTo(cycle.getBillingperiodto()).demandDetails(demandDetails)
				.minimumAmountPayable(config.getMinimumPayableAmount())
				.billExpiryTime(config.getDemandBillExpiryTime() == null ? null
						: System.currentTimeMillis() + config.getDemandBillExpiryTime())
				.status(Demand.StatusEnum.ACTIVE).additionalDetails(buildAdditionalDetails(cycle, category, grossAmount,
						rebate.getTotalRebate(), netAmount, appliedPaidAdjustment, residualPaidCredit,
						carryForwardCreditApplied, creditReservation.getAllocationIds(), property.getPropertyId()))
				.build();

		DemandNotificationObj notification = DemandNotificationObj.builder().requestInfo(requestInfo).tenantId(tenantId)
				.waterConnectionIds(Collections.singleton(connectionNo))
				.billingCycle(WSCalculationConstant.Monthly_Billing_Period).build();

		List<Demand> response;
		try {
			response = demandRepository.saveDemand(requestInfo, Collections.singletonList(demand), notification);
		} catch (RuntimeException ex) {
			if (creditReservation.isNewReservation()) {
				residualCreditService.releaseReservations(tenantId, cycle.getId(), actorForDemand(requestInfo),
						System.currentTimeMillis());
			}
			throw ex;
		}

		if (CollectionUtils.isEmpty(response) || response.get(0) == null
				|| !StringUtils.hasText(response.get(0).getId())) {
			throw new IllegalStateException("Billing-service returned no demand id for " + connectionNo);
		}

		Demand created = response.get(0);

		if (creditReservation.isNewReservation()) {
			residualCreditService.confirmReservations(tenantId, cycle.getId(), created.getId(),
					actorForDemand(requestInfo), System.currentTimeMillis());
		}

		/*
		 * Follow the same generic UPYOG pattern as the existing
		 * DemandService.createDemand(): after a normal demand is created, fetchBill()
		 * is invoked. 1.5x/ZRO and pending automatic-correction cycles deliberately do
		 * not enter this ordinary bill path.
		 */
		String billId = null;
		if (!CorrectionStatus.PENDING.equals(cycle.getCorrectionstatus())) {
			billId = fetchAndGetBillId(requestInfo, created);
			residualCreditService.attachBillId(tenantId, cycle.getId(), billId, actorForDemand(requestInfo),
					System.currentTimeMillis());
		}

		return DemandResult.builder().demandCreated(true).zroRequired(false).demand(created).billId(billId)
				.grossAmount(grossAmount).rebateAmount(rebate.getTotalRebate()).netAmount(netAmount)
				.appliedPaidAdjustmentAmount(appliedPaidAdjustment)
				.residualPaidCreditAmount(residualPaidCredit)
				.carriedForwardCreditAppliedAmount(carryForwardCreditApplied)
				.carriedForwardCreditAllocationIds(creditReservation.getAllocationIds())
				.message(StringUtils.hasText(billId) ? "DJB demand and bill created successfully"
						: "DJB demand created successfully")
				.build();
	}

	/**
	 * Generates the fallback bill required when a domestic DJB 1.5x case is rejected
	 * by ZRO. The rejected actual consumption is NOT billed; the cycle is converted
	 * to DJB provisional billing. For the first two consecutive provisional rounds
	 * the billing consumption is the past actual average; from the next round onward
	 * it is the higher of past average and the configured 25 KL floor.
	 */
	public DemandResult createRejectedOnePointFiveFallbackDemand(RequestInfo requestInfo, WaterBillingCycle cycle) {
		validateCycle(cycle);

		if (!Boolean.TRUE.equals(cycle.getOnepointfivexflag())) {
			throw new IllegalArgumentException(
					"Rejected 1.5x fallback billing is only valid for a DJB 1.5x flagged billing cycle");
		}

		if (StringUtils.hasText(cycle.getDemandid()) || StringUtils.hasText(cycle.getBillid())) {
			throw new IllegalStateException(
					"Cannot generate rejected 1.5x fallback bill because demand/bill already exists for billing cycle "
							+ cycle.getId());
		}

		String tenantId = cycle.getTenantid();
		String connectionNo = cycle.getConnectionno();
		DJBMonthlyBillingRule rule = masterProvider.getBillingRule(requestInfo, tenantId);

		BigDecimal historicalAverage = consumptionService.calculateHistoricalAverage(tenantId, connectionNo,
				cycle.getBillingperiodto(), rule.getAverageLookbackMonths());
		if (historicalAverage == null) {
			throw new IllegalStateException(
					"No actual consumption history is available for rejected DJB 1.5x fallback billing");
		}

		int consecutiveEstimatedCycles = 0;
		List<WaterBillingCycle> recent = billingCycleDao.findCyclesForConnection(tenantId, connectionNo,
				cycle.getBillingperiodto(), 24);
		if (recent != null) {
			for (WaterBillingCycle previous : recent) {
				/*
				 * A ZRO rejection starts a DJB PROVISIONAL billing sequence. Do not
				 * consume the two-cycle provisional allowance because an older cycle was
				 * billed on the separate AVERAGE basis (for example MLOC/PLOC/RDDT/ADF).
				 * The DJB rules treat those as different billing treatments.
				 */
				if (BillingBasis.PROVISIONAL.equals(previous.getBillingbasis())) {
					consecutiveEstimatedCycles++;
				} else {
					break;
				}
			}
		}

		int currentProvisionalCycle = consecutiveEstimatedCycles + 1;
		BigDecimal minimumPostAverage = BigDecimal.valueOf(rule.getMinimumPostAverageConsumptionKl().longValue());
		BigDecimal billingConsumption = currentProvisionalCycle <= rule.getProvisionalMaximumCycles()
				? historicalAverage
				: historicalAverage.max(minimumPostAverage);

		cycle.setAverageconsumption(historicalAverage);
		cycle.setBillingconsumption(billingConsumption);
		cycle.setBillingbasis(BillingBasis.PROVISIONAL);
		cycle.setAveragecyclecount(0);
		cycle.setProvisionalcyclecount(currentProvisionalCycle);
		cycle.setStatus(BillingCycleStatus.CALCULATED);
		cycle.setLastmodifiedby(actorForDemand(requestInfo));
		cycle.setLastmodifiedtime(System.currentTimeMillis());

		if (billingCycleDao.update(cycle) != 1) {
			throw new IllegalStateException(
					"Failed to persist rejected DJB 1.5x fallback billing values for " + cycle.getId());
		}

		return createDemand(requestInfo, cycle);
	}

	/**
	 * Uses the same generic UPYOG bill-fetch contract used by the existing water
	 * calculation flow:
	 *
	 * POST {billing-service}/bill/v2/_fetchbill ?tenantId=... &consumerCode=...
	 * &businessService=WS
	 *
	 * The endpoint searches an existing bill and generates one when there is no
	 * valid bill for the criteria.
	 */
	private String fetchAndGetBillId(RequestInfo requestInfo, Demand demand) {

		StringBuilder url = calculatorUtil.getFetchBillURL(demand.getTenantId(), demand.getConsumerCode());

		Object result = serviceRequestRepository.fetchResult(url,
				RequestInfoWrapper.builder().requestInfo(requestInfo).build());

		if (result == null) {
			throw new IllegalStateException(
					"Billing-service returned null bill response for " + demand.getConsumerCode());
		}

		/*
		 * Emit the same payment trigger used by the existing generic
		 * DemandService.fetchBill() flow. This is notification/event handling, not
		 * demand or bill creation itself.
		 */
		Map<String, Object> billResponse = new HashMap<>();
		billResponse.put("requestInfo", requestInfo);
		billResponse.put("billResponse", result);
		wsCalculationProducer.push(config.getPayTriggers(), billResponse);

		String billId = extractBillId(result);

		if (!StringUtils.hasText(billId)) {
			throw new IllegalStateException(
					"Billing-service returned bill response without bill id for " + demand.getConsumerCode());
		}

		return billId;
	}

	private String extractBillId(Object response) {

		JsonNode root = objectMapper.valueToTree(response);

		JsonNode billNode = findNodeIgnoreCase(root, "bill");

		if (billNode == null) {
			billNode = findNodeIgnoreCase(root, "bills");
		}

		if (billNode == null) {
			return null;
		}

		if (billNode.isArray()) {
			for (JsonNode bill : billNode) {
				JsonNode id = bill.get("id");
				if (id != null && !id.isNull() && StringUtils.hasText(id.asText())) {
					return id.asText();
				}
			}
		}

		if (billNode.isObject()) {
			JsonNode id = billNode.get("id");
			if (id != null && !id.isNull() && StringUtils.hasText(id.asText())) {
				return id.asText();
			}
		}

		return null;
	}

	private JsonNode findNodeIgnoreCase(JsonNode node, String fieldName) {

		if (node == null) {
			return null;
		}

		if (node.isObject()) {

			java.util.Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> entry = fields.next();

				if (entry.getKey().equalsIgnoreCase(fieldName)) {
					return entry.getValue();
				}

				JsonNode nested = findNodeIgnoreCase(entry.getValue(), fieldName);

				if (nested != null) {
					return nested;
				}
			}
		}

		if (node.isArray()) {
			for (JsonNode child : node) {
				JsonNode nested = findNodeIgnoreCase(child, fieldName);

				if (nested != null) {
					return nested;
				}
			}
		}

		return null;
	}

	/**
	 * Fetches a bill for a demand that has already been created.
	 *
	 * This is used by the DJB automatic-correction flow after the final
	 * corrected-actual demand already exists. It deliberately reuses the existing
	 * generic UPYOG billing-service /_fetchbill contract and does not create
	 * another Demand.
	 */
	public String fetchBillForExistingDemand(RequestInfo requestInfo, Demand demand) {

		if (demand == null || !StringUtils.hasText(demand.getTenantId())
				|| !StringUtils.hasText(demand.getConsumerCode())) {
			throw new IllegalArgumentException(
					"TenantId and consumerCode are required to fetch bill for existing demand");
		}

		return fetchAndGetBillId(requestInfo, demand);
	}

	/**
	 * Fetches the bill for an already-created DJB demand when the Demand object
	 * itself is not available (for example, an idempotent retry after demand
	 * creation). The generic _fetchbill endpoint searches by tenant, consumer and
	 * WS business service, so no second Demand is created here.
	 */
	public String fetchBillForExistingDemand(RequestInfo requestInfo, String tenantId, String connectionNo) {

		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(connectionNo)) {
			throw new IllegalArgumentException("TenantId and connectionNo are required to fetch an existing DJB bill");
		}

		StringBuilder url = calculatorUtil.getFetchBillURL(tenantId, connectionNo);

		Object result = serviceRequestRepository.fetchResult(url,
				RequestInfoWrapper.builder().requestInfo(requestInfo).build());

		if (result == null) {
			throw new IllegalStateException("Billing-service returned null bill response for " + connectionNo);
		}

		Map<String, Object> billResponse = new HashMap<>();
		billResponse.put("requestInfo", requestInfo);
		billResponse.put("billResponse", result);
		wsCalculationProducer.push(config.getPayTriggers(), billResponse);

		String billId = extractBillId(result);

		if (!StringUtils.hasText(billId)) {
			throw new IllegalStateException(
					"Billing-service returned bill response without bill id for " + connectionNo);
		}

		return billId;
	}

	private WaterConnection loadWaterConnection(RequestInfo requestInfo, String connectionNo, String tenantId) {

		List<WaterConnection> connections = calculatorUtil.getWaterConnection(requestInfo, connectionNo, tenantId);

		if (CollectionUtils.isEmpty(connections)) {
			throw new IllegalStateException("Water connection not found: " + connectionNo);
		}

		return calculatorUtil.getWaterConnectionObject(connections);
	}

	private User resolvePayer(WaterConnection connection, Property property) {

		if (!CollectionUtils.isEmpty(connection.getConnectionHolders())) {
			return connection.getConnectionHolders().get(0).toCommonUser();
		}

		if (!CollectionUtils.isEmpty(property.getOwners())) {
			return property.getOwners().get(0).toCommonUser();
		}

		throw new IllegalStateException("No owner/payer found for water connection " + connection.getConnectionNo());
	}

	private String resolveTariffCategory(WaterConnection connection, Property property) {

		String candidate = connection.getConnectionCategory();

		if (!StringUtils.hasText(candidate)) {
			candidate = property.getUsageCategory();
		}

		if (!StringUtils.hasText(candidate)) {
			throw new IllegalStateException("Cannot determine DJB tariff category for " + connection.getConnectionNo());
		}

		String normalized = candidate.trim().replace("-", "_").replace(" ", "_").toUpperCase();

		if (normalized.contains("DOMESTIC") || normalized.contains("RESIDENTIAL") || normalized.contains("CAT_I")) {
			return "DOMESTIC";
		}

		if (normalized.contains("COMMERCIAL") || normalized.contains("NON_DOMESTIC") || normalized.contains("CAT_II")
				|| normalized.contains("BUSINESS")) {
			return "COMMERCIAL";
		}

		throw new IllegalStateException("Unsupported DJB tariff category: " + candidate);
	}

	private boolean isAdditionalWaterSource(WaterConnection connection) {

		String source = connection.getWaterSource();

		return source != null && (source.toUpperCase().contains("BORE") || source.toUpperCase().contains("BOREWELL"));
	}

	private Map<String, Object> buildAdditionalDetails(WaterBillingCycle cycle, String category, BigDecimal grossAmount,
			BigDecimal rebateAmount, BigDecimal netAmount, BigDecimal appliedPaidAdjustmentAmount,
			BigDecimal residualPaidCreditAmount, BigDecimal carryForwardCreditApplied, List<String> creditAllocationIds,
			String propertyId) {

		Map<String, Object> details = new HashMap<>();
		details.put("djbBillingCycleId", cycle.getId());
		details.put("djbBillingBasis", cycle.getBillingbasis() == null ? null : cycle.getBillingbasis().toString());
		details.put("readingQualityCode", cycle.getReadingqualitycode());
		details.put("billingPeriodFrom", cycle.getBillingperiodfrom());
		details.put("billingPeriodTo", cycle.getBillingperiodto());
		details.put("tariffCategory", category);
		details.put("grossAmount", grossAmount);
		details.put("rebateAmount", rebateAmount);
		details.put("netAmount", netAmount);
		if (appliedPaidAdjustmentAmount != null && appliedPaidAdjustmentAmount.signum() > 0) {
			details.put("paidCorrectionAdjustment", appliedPaidAdjustmentAmount);
			details.put("correctionAdjustmentTaxHead", WSCalculationConstant.WS_TIME_ADHOC_REBATE);
		}
		if (residualPaidCreditAmount != null && residualPaidCreditAmount.signum() > 0) {
			details.put("residualPaidCorrectionCredit", residualPaidCreditAmount);
		}
		if (carryForwardCreditApplied != null && carryForwardCreditApplied.signum() > 0) {
			details.put("carriedForwardResidualCredit", carryForwardCreditApplied);
			if (!CollectionUtils.isEmpty(creditAllocationIds)) {
				details.put("carriedForwardResidualCreditAllocationIds", creditAllocationIds);
			}
		}
		details.put("propertyId", propertyId);

		return details;
	}

	private void createPendingZroVerification(WaterBillingCycle cycle, String actor) {

		ZroVerification existing = zroVerificationDao.findByBillingCycle(cycle.getTenantid(), cycle.getId());

		if (existing != null) {
			if (ZroStatus.APPROVED.equals(existing.getStatus())) {
				return;
			}
			if (ZroStatus.PENDING.equals(existing.getStatus())) {
				return;
			}
			// Keep the existing rejected state explicit. A rejected cycle must be
			// re-submitted as a new billing-cycle review rather than silently reopened.
			existing.setStatus(ZroStatus.PENDING);
			existing.setRemarks(cycle.getZroremarks());
			existing.setActionby(null);
			existing.setActiondate(null);
			existing.setLastmodifiedby(actor);
			existing.setLastmodifiedtime(System.currentTimeMillis());
			zroVerificationDao.update(existing);
			return;
		}

		long now = System.currentTimeMillis();
		ZroVerification verification = new ZroVerification();
		verification.setId(java.util.UUID.randomUUID().toString());
		verification.setTenantid(cycle.getTenantid());
		verification.setBillingcycleid(cycle.getId());
		verification.setConnectionno(cycle.getConnectionno());
		verification.setConsumption(cycle.getActualconsumption());
		verification.setPreviousconsumption(cycle.getPreviousconsumption());
		verification.setDeviationfactor(cycle.getDeviationfactor());
		verification.setStatus(ZroStatus.PENDING);
		verification.setRemarks(cycle.getZroremarks());
		verification.setCreatedby(actor);
		verification.setCreatedtime(now);
		verification.setLastmodifiedby(actor);
		verification.setLastmodifiedtime(now);
		zroVerificationDao.save(verification);
	}

	private String actorForDemand(RequestInfo requestInfo) {
		if (requestInfo != null && requestInfo.getUserInfo() != null
				&& StringUtils.hasText(requestInfo.getUserInfo().getUuid())) {
			return requestInfo.getUserInfo().getUuid();
		}
		return "SYSTEM";
	}

	private void validateCycle(WaterBillingCycle cycle) {

		if (cycle == null) {
			throw new IllegalArgumentException("Billing cycle is required");
		}

		if (!StringUtils.hasText(cycle.getTenantid()) || !StringUtils.hasText(cycle.getConnectionno())) {
			throw new IllegalArgumentException("Billing cycle tenant and connection are required");
		}

		if (cycle.getBillingperiodfrom() == null || cycle.getBillingperiodto() == null) {
			throw new IllegalArgumentException("Billing period is required");
		}
	}

	@lombok.Builder
	@lombok.Data
	public static class DemandResult {
		private boolean demandCreated;
		private boolean zroRequired;
		private Demand demand;
		private String billId;
		private BigDecimal grossAmount;
		private BigDecimal rebateAmount;
		private BigDecimal netAmount;
		private BigDecimal appliedPaidAdjustmentAmount;
		private BigDecimal residualPaidCreditAmount;
		private BigDecimal carriedForwardCreditAppliedAmount;
		private List<String> carriedForwardCreditAllocationIds;
		private String message;
	}
}
