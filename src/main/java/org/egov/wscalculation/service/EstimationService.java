package org.egov.wscalculation.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.egov.wscalculation.constants.WSCalculationConstant;
import org.egov.wscalculation.web.models.*;
import org.egov.wscalculation.util.CalculatorUtil;
import org.egov.wscalculation.util.ResponseInfoFactory;
import org.egov.wscalculation.util.WSCalculationUtil;
import org.egov.wscalculation.util.WaterCessUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;


@Service
@Slf4j
public class EstimationService {

	@Autowired
	private WaterCessUtil waterCessUtil;
	
	@Autowired
	private CalculatorUtil calculatorUtil;

	@Autowired
	private MasterDataService masterDataService;

	@Autowired
	private ObjectMapper mapper;
	
	@Autowired
	private WSCalculationUtil wSCalculationUtil;
	
	@Autowired
	private PayService payService;

	@Autowired
	private WaterDemandCalculator waterDemandCalculator;
	
	@Autowired
    private ResponseInfoFactory responseInfoFactory;

	/**
	 * Generates a List of Tax head estimates with tax head code, tax head
	 * category and the amount to be collected for the key.
	 *
	 * @param criteria
	 *            criteria based on which calculation will be done.
	 * @param requestInfo
	 *            request info from incoming request.
	 * @return Map<String, Double>
	 */
	@SuppressWarnings("rawtypes")
	public Map<String, List> getEstimationMap(CalculationCriteria criteria, RequestInfo requestInfo,
			Map<String, Object> masterData) {
		String tenantId = requestInfo.getUserInfo().getTenantId();
		if (criteria.getWaterConnection() == null && !StringUtils.isEmpty(criteria.getConnectionNo())) {
			List<WaterConnection> waterConnectionList = calculatorUtil.getWaterConnection(requestInfo, criteria.getConnectionNo(), tenantId);
			WaterConnection waterConnection = calculatorUtil.getWaterConnectionObject(waterConnectionList);
			criteria.setWaterConnection(waterConnection);
		}
		if (criteria.getWaterConnection() == null || StringUtils.isEmpty(criteria.getConnectionNo())) {
			StringBuilder builder = new StringBuilder();
			builder.append("Water Connection are not present for ")
					.append(StringUtils.isEmpty(criteria.getConnectionNo()) ? "" : criteria.getConnectionNo())
					.append(" connection no");
			throw new CustomException("WATER_CONNECTION_NOT_FOUND", builder.toString());
		}
		Map<String, JSONArray> billingSlabMaster = new HashMap<>();
		Map<String, JSONArray> timeBasedExemptionMasterMap = new HashMap<>();
		ArrayList<String> billingSlabIds = new ArrayList<>();
		billingSlabMaster.put(WSCalculationConstant.WC_BILLING_SLAB_MASTER,
				(JSONArray) masterData.get(WSCalculationConstant.WC_BILLING_SLAB_MASTER));
		billingSlabMaster.put(WSCalculationConstant.CALCULATION_ATTRIBUTE_CONST,
				(JSONArray) masterData.get(WSCalculationConstant.CALCULATION_ATTRIBUTE_CONST));
		timeBasedExemptionMasterMap.put(WSCalculationConstant.WC_WATER_CESS_MASTER,
				(JSONArray) (masterData.getOrDefault(WSCalculationConstant.WC_WATER_CESS_MASTER, null)));
		timeBasedExemptionMasterMap.put(WSCalculationConstant.WC_REBATE_MASTER,
				(JSONArray) (masterData.getOrDefault(WSCalculationConstant.WC_REBATE_MASTER, null)));
		// mDataService.setWaterConnectionMasterValues(requestInfo, tenantId,
		// billingSlabMaster,
		// timeBasedExemptionMasterMap);
		BigDecimal taxAmt = getWaterEstimationCharge(criteria.getWaterConnection(), criteria, billingSlabMaster, billingSlabIds,
				requestInfo);
		List<TaxHeadEstimate> taxHeadEstimates = getEstimatesForTax(taxAmt, criteria.getWaterConnection(),
				timeBasedExemptionMasterMap, RequestInfoWrapper.builder().requestInfo(requestInfo).build());

		Map<String, List> estimatesAndBillingSlabs = new HashMap<>();
		estimatesAndBillingSlabs.put("estimates", taxHeadEstimates);
		// Billing slab id
		estimatesAndBillingSlabs.put("billingSlabIds", billingSlabIds);
		return estimatesAndBillingSlabs;
	}

	/**
	 * 
	 * @param waterCharge WaterCharge amount
	 * @param connection - Connection Object
	 * @param timeBasedExemptionsMasterMap List of Exemptions for the connection
	 * @param requestInfoWrapper - RequestInfo Wrapper object
	 * @return - Returns list of TaxHeadEstimates
	 */
	private List<TaxHeadEstimate> getEstimatesForTax(BigDecimal waterCharge,
			WaterConnection connection,
			Map<String, JSONArray> timeBasedExemptionsMasterMap, RequestInfoWrapper requestInfoWrapper) {
		List<TaxHeadEstimate> estimates = new ArrayList<>();
		// water_charge
		estimates.add(TaxHeadEstimate.builder().taxHeadCode(WSCalculationConstant.WS_CHARGE)
				.estimateAmount(waterCharge.setScale(2, 2)).build());

		// Water_cess
		if (timeBasedExemptionsMasterMap.get(WSCalculationConstant.WC_WATER_CESS_MASTER) != null) {
			List<Object> waterCessMasterList = timeBasedExemptionsMasterMap
					.get(WSCalculationConstant.WC_WATER_CESS_MASTER);
			BigDecimal waterCess;
			waterCess = waterCessUtil.getWaterCess(waterCharge, WSCalculationConstant.Assessment_Year, waterCessMasterList);
			estimates.add(TaxHeadEstimate.builder().taxHeadCode(WSCalculationConstant.WS_WATER_CESS)
					.estimateAmount(waterCess.setScale(2, 2)).build());
		}
		

		if (timeBasedExemptionsMasterMap.get(WSCalculationConstant.WC_REBATE_MASTER) != null) {
			BigDecimal rebate;
			rebate = payService.getApplicableRebate(waterCharge,null,  timeBasedExemptionsMasterMap.get(WSCalculationConstant.WC_REBATE_MASTER));
			estimates.add(TaxHeadEstimate.builder().taxHeadCode(WSCalculationConstant.WS_TIME_REBATE)
					.estimateAmount(rebate.negate().setScale(2, 2)).build());
		}
		
		return estimates;
	}

	/**
	 * method to do a first level filtering on the slabs based on the values
	 * present in the Water Details
	 */

	public BigDecimal getWaterEstimationCharge(WaterConnection waterConnection, CalculationCriteria criteria, 
			Map<String, JSONArray> billingSlabMaster, ArrayList<String> billingSlabIds, RequestInfo requestInfo) {
		BigDecimal waterCharge = BigDecimal.ZERO;
		if (billingSlabMaster.get(WSCalculationConstant.WC_BILLING_SLAB_MASTER) == null)
			throw new CustomException("BILLING_SLAB_NOT_FOUND", "Billing Slab are Empty");
		List<BillingSlab> mappingBillingSlab;
		try {
			mappingBillingSlab = mapper.readValue(
					billingSlabMaster.get(WSCalculationConstant.WC_BILLING_SLAB_MASTER).toJSONString(),
					mapper.getTypeFactory().constructCollectionType(List.class, BillingSlab.class));
		} catch (IOException e) {
			throw new CustomException("PARSING_ERROR", "Billing Slab can not be parsed!");
		}
		JSONObject calculationAttributeMaster = new JSONObject();
		calculationAttributeMaster.put(WSCalculationConstant.CALCULATION_ATTRIBUTE_CONST, billingSlabMaster.get(WSCalculationConstant.CALCULATION_ATTRIBUTE_CONST));
        String calculationAttribute = getCalculationAttribute(calculationAttributeMaster, waterConnection.getConnectionType());
		List<BillingSlab> billingSlabs = getSlabsFiltered(waterConnection, mappingBillingSlab, calculationAttribute, requestInfo);
		if (billingSlabs == null || billingSlabs.isEmpty())
			throw new CustomException("BILLING_SLAB_NOT_FOUND", "Billing Slab are Empty");
		if (billingSlabs.size() > 1)
			throw new CustomException("INVALID_BILLING_SLAB",
					"More than one billing slab found");
		billingSlabIds.add(billingSlabs.get(0).getId());
		log.debug(" Billing Slab Id For Water Charge Calculation --->  " + billingSlabIds.toString());

		// WaterCharge Calculation
		Double totalUOM = getUnitOfMeasurement(waterConnection, calculationAttribute, criteria);
		if (totalUOM == 0.0)
			return waterCharge;
		BillingSlab billSlab = billingSlabs.get(0);
		// IF calculation type is flat then take flat rate else take slab and calculate the charge
		//For metered connection calculation on graded fee slab
		//For Non metered connection calculation on normal connection
		if (isRangeCalculation(calculationAttribute)) {
			if (waterConnection.getConnectionType().equalsIgnoreCase(WSCalculationConstant.meteredConnectionType)) {
				for (Slab slab : billSlab.getSlabs()) {
					if (totalUOM > slab.getTo()) {
						waterCharge = waterCharge.add(BigDecimal.valueOf(((slab.getTo()) - (slab.getFrom())) * slab.getCharge()));
						totalUOM = totalUOM - ((slab.getTo()) - (slab.getFrom()));
					} else if (totalUOM < slab.getTo()) {
						waterCharge = waterCharge.add(BigDecimal.valueOf(totalUOM * slab.getCharge()));
						totalUOM = ((slab.getTo()) - (slab.getFrom())) - totalUOM;
						break;
					}
				}
				if (billSlab.getMinimumCharge() > waterCharge.doubleValue()) {
					waterCharge = BigDecimal.valueOf(billSlab.getMinimumCharge());
				}
			} else if (waterConnection.getConnectionType()
					.equalsIgnoreCase(WSCalculationConstant.nonMeterdConnection)) {
				for (Slab slab : billSlab.getSlabs()) {
					if (totalUOM >= slab.getFrom() && totalUOM < slab.getTo()) {
						waterCharge = BigDecimal.valueOf((totalUOM * slab.getCharge()));
						if (billSlab.getMinimumCharge() > waterCharge.doubleValue()) {
							waterCharge = BigDecimal.valueOf(billSlab.getMinimumCharge());
						}
						break;
					}
				}
			}
		} else {
			waterCharge = BigDecimal.valueOf(billSlab.getMinimumCharge());
		}
		return waterCharge;
	}

	private List<BillingSlab> getSlabsFiltered(WaterConnection waterConnection, List<BillingSlab> billingSlabs,
			String calculationAttribute, RequestInfo requestInfo) {

		Property property = wSCalculationUtil.getProperty(
				WaterConnectionRequest.builder().waterConnection(waterConnection).requestInfo(requestInfo).build());
		// get billing Slab
		log.debug(" the slabs count : " + billingSlabs.size());
		final String buildingType = (property.getUsageCategory() != null) ? property.getUsageCategory().split("\\.")[0]
				: "";
		// final String buildingType = "Domestic";
		final String connectionType = waterConnection.getConnectionType();

		return billingSlabs.stream().filter(slab -> {
			boolean isBuildingTypeMatching = slab.getBuildingType().equalsIgnoreCase(buildingType);
			boolean isConnectionTypeMatching = slab.getConnectionType().equalsIgnoreCase(connectionType);
			boolean isCalculationAttributeMatching = slab.getCalculationAttribute()
					.equalsIgnoreCase(calculationAttribute);
			return isBuildingTypeMatching && isConnectionTypeMatching && isCalculationAttributeMatching;
		}).collect(Collectors.toList());
	}
	
	private String getCalculationAttribute(Map<String, Object> calculationAttributeMap, String connectionType) {
		if (calculationAttributeMap == null)
			throw new CustomException("CALCULATION_ATTRIBUTE_MASTER_NOT_FOUND",
					"Calculation attribute master not found!!");
		JSONArray filteredMasters = JsonPath.read(calculationAttributeMap,
				"$.CalculationAttribute[?(@.name=='" + connectionType + "')]");
		if (!CollectionUtils.isEmpty(filteredMasters)) {
			JSONObject master = mapper.convertValue(filteredMasters.get(0), JSONObject.class);
			return master.getAsString(WSCalculationConstant.ATTRIBUTE);
		} else {
			throw new CustomException("CALCULATION_ATTRIBUTE_MASTER_NOT_FOUND",
					"Calculation attribute master not found the connection type :" + connectionType);
		}
	}
	
	/**
	 * 
	 * @param type will be calculation Attribute
	 * @return true if calculation Attribute is not Flat else false
	 */
	private boolean isRangeCalculation(String type) {
		return !type.equalsIgnoreCase(WSCalculationConstant.flatRateCalculationAttribute);
	}
	
	public String getAssessmentYear() {
		LocalDateTime localDateTime = LocalDateTime.now();
		int currentMonth = localDateTime.getMonthValue();
		String assessmentYear;
		if (currentMonth >= Month.APRIL.getValue()) {
			assessmentYear = YearMonth.now().getYear() + "-";
			assessmentYear = assessmentYear
					+ (Integer.toString(YearMonth.now().getYear() + 1).substring(2, assessmentYear.length() - 1));
		} else {
			assessmentYear = YearMonth.now().getYear() - 1 + "-";
			assessmentYear = assessmentYear
					+ (Integer.toString(YearMonth.now().getYear()).substring(2, assessmentYear.length() - 1));

		}
		return assessmentYear;
	}
	
	private Double getUnitOfMeasurement(WaterConnection waterConnection, String calculationAttribute,
			CalculationCriteria criteria) {
		Double totalUnit = 0.0;
		if (waterConnection.getConnectionType().equals(WSCalculationConstant.meteredConnectionType)) {
			totalUnit = (criteria.getCurrentReading() - criteria.getLastReading());
			return totalUnit;
		} else if (waterConnection.getConnectionType().equals(WSCalculationConstant.nonMeterdConnection)
				&& calculationAttribute.equalsIgnoreCase(WSCalculationConstant.noOfTapsConst)) {
			if (waterConnection.getNoOfTaps() == null)
				return totalUnit;
			return new Double(waterConnection.getNoOfTaps());
		} else if (waterConnection.getConnectionType().equals(WSCalculationConstant.nonMeterdConnection)
				&& calculationAttribute.equalsIgnoreCase(WSCalculationConstant.pipeSizeConst)) {
			if (waterConnection.getPipeSize() == null)
				return totalUnit;
			return waterConnection.getPipeSize();
		}
		return 0.0;
	}
	
	public Map<String, Object> getQuarterStartAndEndDate(Map<String, Object> billingPeriod){
		Date date = new Date();
		Calendar fromDateCalendar = Calendar.getInstance();
		fromDateCalendar.setTime(date);
		fromDateCalendar.set(Calendar.MONTH, fromDateCalendar.get(Calendar.MONTH)/3 * 3);
		fromDateCalendar.set(Calendar.DAY_OF_MONTH, 1);
		setTimeToBeginningOfDay(fromDateCalendar);
		Calendar toDateCalendar = Calendar.getInstance();
		toDateCalendar.setTime(date);
		toDateCalendar.set(Calendar.MONTH, toDateCalendar.get(Calendar.MONTH)/3 * 3 + 2);
		toDateCalendar.set(Calendar.DAY_OF_MONTH, toDateCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
		setTimeToEndofDay(toDateCalendar);
		billingPeriod.put(WSCalculationConstant.STARTING_DATE_APPLICABLES, fromDateCalendar.getTimeInMillis());
		billingPeriod.put(WSCalculationConstant.ENDING_DATE_APPLICABLES, toDateCalendar.getTimeInMillis());
		return billingPeriod;
	}
	
	public Map<String, Object> getMonthStartAndEndDate(Map<String, Object> billingPeriod){
		Date date = new Date();
		Calendar monthStartDate = Calendar.getInstance();
		monthStartDate.setTime(date);
		monthStartDate.set(Calendar.DAY_OF_MONTH, monthStartDate.getActualMinimum(Calendar.DAY_OF_MONTH));
		setTimeToBeginningOfDay(monthStartDate);
	    
		Calendar monthEndDate = Calendar.getInstance();
		monthEndDate.setTime(date);
		monthEndDate.set(Calendar.DAY_OF_MONTH, monthEndDate.getActualMaximum(Calendar.DAY_OF_MONTH));
		setTimeToEndofDay(monthEndDate);
		billingPeriod.put(WSCalculationConstant.STARTING_DATE_APPLICABLES, monthStartDate.getTimeInMillis());
		billingPeriod.put(WSCalculationConstant.ENDING_DATE_APPLICABLES, monthEndDate.getTimeInMillis());
		return billingPeriod;
	}
	
	private static void setTimeToBeginningOfDay(Calendar calendar) {
	    calendar.set(Calendar.HOUR_OF_DAY, 0);
	    calendar.set(Calendar.MINUTE, 0);
	    calendar.set(Calendar.SECOND, 0);
	    calendar.set(Calendar.MILLISECOND, 0);
	}

	private static void setTimeToEndofDay(Calendar calendar) {
	    calendar.set(Calendar.HOUR_OF_DAY, 23);
	    calendar.set(Calendar.MINUTE, 59);
	    calendar.set(Calendar.SECOND, 59);
	    calendar.set(Calendar.MILLISECOND, 999);
	}
	
	
	/**
	 * 
	 * @param criteria - Calculation Search Criteria
	 * @param requestInfo - Request Info Object
	 * @param masterData - Master Data map
	 * @return Fee Estimation Map
	 */
	@SuppressWarnings("rawtypes")
	public Map<String, List> getFeeEstimation(CalculationCriteria criteria, RequestInfo requestInfo,
			Map<String, Object> masterData) {
		if (StringUtils.isEmpty(criteria.getWaterConnection()) && !StringUtils.isEmpty(criteria.getApplicationNo())) {
			SearchCriteria searchCriteria = new SearchCriteria();
			searchCriteria.setApplicationNumber(criteria.getApplicationNo());
			searchCriteria.setTenantId(criteria.getTenantId());
			WaterConnection waterConnection = calculatorUtil.getWaterConnectionOnApplicationNO(requestInfo, searchCriteria, requestInfo.getUserInfo().getTenantId());
			criteria.setWaterConnection(waterConnection);
		}
		if (StringUtils.isEmpty(criteria.getWaterConnection())) {
			throw new CustomException("WATER_CONNECTION_NOT_FOUND",
					"Water Connection are not present for " + criteria.getApplicationNo() + " Application no");
		}
		ArrayList<String> billingSlabIds = new ArrayList<>();
		billingSlabIds.add("");
		List<TaxHeadEstimate> taxHeadEstimates = getTaxHeadForFeeEstimation(criteria, masterData, requestInfo);
		Map<String, List> estimatesAndBillingSlabs = new HashMap<>();
		estimatesAndBillingSlabs.put("estimates", taxHeadEstimates);
		// //Billing slab id
		estimatesAndBillingSlabs.put("billingSlabIds", billingSlabIds);
		return estimatesAndBillingSlabs;
	}


	/**
	 *
	 * @param criteria Calculation Search Criteria
	 * @param masterData - Master Data
	 * @param requestInfo - RequestInfo
	 * @return return all tax heads
	 */
	private List<TaxHeadEstimate> getTaxHeadForFeeEstimation(CalculationCriteria criteria, Map<String, Object> masterData, RequestInfo requestInfo) {

		log.info("MDMS(getMasterMap) loaded keys = {}", masterData.keySet());
		JSONArray feeSlab = (JSONArray) masterData.getOrDefault(WSCalculationConstant.WC_FEESLAB_MASTER, null);
		if (feeSlab == null)
			throw new CustomException("FEE_SLAB_NOT_FOUND", "fee slab master data not found!!");

		Property property = wSCalculationUtil.getProperty(WaterConnectionRequest.builder().waterConnection(criteria.getWaterConnection()).requestInfo(requestInfo).build());

		String localityCode = property.getAddress().getLocality().getCode();
		String colonyCategory = masterDataService.getColonyCategory(localityCode, requestInfo, property.getTenantId());

		/*
		 * ------------------------------------------------------------------		 *
		 * If colony category cannot be resolved or is not configured, use E category.
		 * Remove this fallback once colony mapping is available.
		 * ------------------------------------------------------------------
		 */
		if (colonyCategory == null || colonyCategory.trim().isEmpty() || "UNKNOWN".equalsIgnoreCase(colonyCategory)) {
			colonyCategory = "E";
			log.warn("Colony category not found. Using default colony category '{}' for demo.",colonyCategory);
		}

		BigDecimal taxAndCessPercentage = BigDecimal.ZERO;

		String requestConnectionCategory = criteria.getWaterConnection().getConnectionCategory();

		if ((requestConnectionCategory == null || requestConnectionCategory.trim().isEmpty()) && criteria.getWaterConnection().getAdditionalDetails() != null) {

			JSONObject additionalDetails = mapper.convertValue(criteria.getWaterConnection().getAdditionalDetails(), JSONObject.class);
			requestConnectionCategory = additionalDetails.getAsString("categoryType");
		}
		log.info("Connection Category = {}", requestConnectionCategory);

		requestConnectionCategory = normalizeConnectionCategory(requestConnectionCategory);

		log.info("Normalized Connection Category : {}", requestConnectionCategory);

		// Use a dynamic map to collect matching matrix charges by their TaxHead code
		Map<String, BigDecimal> dynamicFeeMap = new HashMap<>();

		for (Object obj : feeSlab) {
			try {
				JSONObject fee = mapper.convertValue(obj, JSONObject.class);
				Boolean isActive = (Boolean) fee.get("isActive");
				if (Boolean.FALSE.equals(isActive)) {
					continue;
				}

				/* Filter by relationType for Mutation connection */
				if (criteria.getWaterConnection() != null && WSCalculationConstant.MUTATION_WATER_CONNECTION.equalsIgnoreCase(criteria.getWaterConnection().getApplicationType())) {
					String mdmsRelationType = fee.getAsString("relationType");
					if (mdmsRelationType != null) {
						boolean isBloodRelation = false;
						if (criteria.getWaterConnection().getAdditionalDetails() != null) {
							try {
								Map<String, Object> additionalDetails = mapper.convertValue(criteria.getWaterConnection().getAdditionalDetails(), Map.class);
								if (additionalDetails != null && additionalDetails.get("isBloodRelation") != null) {
									String isBlood = String.valueOf(additionalDetails.get("isBloodRelation"));
									if ("true".equalsIgnoreCase(isBlood)) {
										isBloodRelation = true;
									}
								}
							} catch (Exception e) {
								log.error("[WS-MUTATION-ESTIMATE] Error reading isBloodRelation from additionalDetails", e);
							}
						}
						
						if (isBloodRelation && !"BLOOD".equalsIgnoreCase(mdmsRelationType)) {
							continue;
						}
						if (!isBloodRelation && !"NON_BLOOD".equalsIgnoreCase(mdmsRelationType)) {
							continue;
						}
					}
				}

				String feeComponent = fee.getAsString(WSCalculationConstant.FEE_COMPONENT);
				String taxHeadCode = fee.getAsString(WSCalculationConstant.TAX_HEAD_CODE);

				BigDecimal amount = BigDecimal.ZERO;
				if (fee.get(WSCalculationConstant.AMOUNT) != null) {
					amount = new BigDecimal(fee.getAsNumber(WSCalculationConstant.AMOUNT).toString());
				}

				/* Filter by Connection Category */
				String mdmsConnectionCategory = fee.getAsString(WSCalculationConstant.CONNECTION_CATEGORY);

				if (mdmsConnectionCategory != null && requestConnectionCategory != null && !mdmsConnectionCategory.equalsIgnoreCase(requestConnectionCategory)) {
					log.info("Skipping {} because Connection Category mismatch. MDMS={}, Request={}", taxHeadCode,mdmsConnectionCategory, requestConnectionCategory);
					continue;
				}

				List<String> categories = mapper.convertValue(fee.get(WSCalculationConstant.COLONY_CATEGORIES), new TypeReference<List<String>>() {});

				if (categories != null && !categories.isEmpty() && !categories.contains(colonyCategory)) {
					log.info("Skipping {} because Colony mismatch. MDMS={}, Property={}", taxHeadCode,categories, colonyCategory);
					continue;
				}

				if (WSCalculationConstant.TAX_PERCENTAGE.equalsIgnoreCase(feeComponent)) {
					taxAndCessPercentage = amount;
					continue;
				}

				if (WSCalculationConstant.METER_FEE.equalsIgnoreCase(feeComponent)) {
					if (criteria.getWaterConnection().getConnectionType() == null || !criteria.getWaterConnection().getConnectionType().equalsIgnoreCase(WSCalculationConstant.meteredConnectionType)) {
						continue;
					}
				}
				log.info("Matched Fee -> ConnectionCategory={}, Colony={}, Component={}, TaxHead={}, Amount={}", mdmsConnectionCategory,categories,feeComponent,taxHeadCode,amount);

				if (taxHeadCode != null && amount.compareTo(BigDecimal.ZERO) > 0 && shouldIncludeFee(criteria.getWaterConnection(), feeComponent, taxHeadCode)) {
					dynamicFeeMap.put(taxHeadCode, amount);
					log.info("Added TaxHead {} => {}", taxHeadCode, amount);
				}

			} catch (Exception e) {
				log.error("Error processing Fee Slab.", e);
			}
		}

		log.info("Dynamic Fee Map = {}", dynamicFeeMap);
		log.info("Dynamic Fee Count = {}", dynamicFeeMap.size());

		BigDecimal roadCuttingCharge = BigDecimal.ZERO;
		BigDecimal roadCuttingChargeBerm = BigDecimal.ZERO;
		BigDecimal roadCuttingChargeBMPrefixRoad = BigDecimal.ZERO;

		BigDecimal infrastructureCharge = BigDecimal.ZERO;

		if (WSCalculationConstant.NEW_WATER_CONNECTION.equalsIgnoreCase(criteria.getWaterConnection().getApplicationType())) {
			infrastructureCharge = calculateInfrastructureCharge(criteria, property, masterData, colonyCategory);
		}
		BigDecimal usageTypeCharge = BigDecimal.ZERO;

		log.info("========== IFC DEBUG ==========");
		log.info("ApplicationType={}", criteria.getWaterConnection().getApplicationType());
		log.info("ColonyCategory={}", colonyCategory);
		log.info("InfrastructureCharge={}", infrastructureCharge);
		log.info("================================");

		if (!WSCalculationConstant.NEW_WATER_CONNECTION.equalsIgnoreCase(
				criteria.getWaterConnection().getApplicationType())
				&& criteria.getWaterConnection().getRoadCuttingInfo() != null) {
			for (RoadCuttingInfo roadCuttingInfo : criteria.getWaterConnection().getRoadCuttingInfo()) {
				BigDecimal singleRoadCuttingCharge = BigDecimal.ZERO;
				if (roadCuttingInfo.getRoadType() != null)
					singleRoadCuttingCharge = getChargeForRoadCutting(masterData, roadCuttingInfo.getRoadType(),
							roadCuttingInfo.getRoadCuttingArea());
				if (roadCuttingInfo.getRoadType().equalsIgnoreCase(WSCalculationConstant.ROAD_TYPE_BERM_CUTTING_KATCHA))
					roadCuttingChargeBerm = singleRoadCuttingCharge;
				else if (roadCuttingInfo.getRoadType().equalsIgnoreCase(WSCalculationConstant.ROAD_TYPE_BM_PREMIX_ROAD))
					roadCuttingChargeBMPrefixRoad = singleRoadCuttingCharge;

				BigDecimal singleUsageTypeCharge = BigDecimal.ZERO;
				if (roadCuttingInfo.getRoadCuttingArea() != null)
					singleUsageTypeCharge = getUsageTypeFee(masterData,
							property.getUsageCategory(),
							roadCuttingInfo.getRoadCuttingArea());

				roadCuttingCharge = roadCuttingCharge.add(singleRoadCuttingCharge);
				usageTypeCharge = usageTypeCharge.add(singleUsageTypeCharge);
			}
		}

		BigDecimal roadPlotCharge = BigDecimal.ZERO;
		if (!WSCalculationConstant.NEW_WATER_CONNECTION.equalsIgnoreCase(
				criteria.getWaterConnection().getApplicationType())
				&& !WSCalculationConstant.MUTATION_WATER_CONNECTION.equalsIgnoreCase(
				criteria.getWaterConnection().getApplicationType())
				&& property.getLandArea() != null) {
			roadPlotCharge = getPlotSizeFee(masterData, property.getLandArea());
		}

		// Aggregate base components total for tax application calculations
		BigDecimal dynamicSlabTotal = dynamicFeeMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal totalCharge = dynamicSlabTotal.add(roadCuttingCharge).add(roadPlotCharge).add(usageTypeCharge).add(infrastructureCharge);
		BigDecimal tax = totalCharge.multiply(taxAndCessPercentage.divide(WSCalculationConstant.HUNDRED));

		List<TaxHeadEstimate> estimates = new ArrayList<>();

		// Build final response list dynamically from calculated entries
		for (Map.Entry<String, BigDecimal> entry : dynamicFeeMap.entrySet()) {
			estimates.add(TaxHeadEstimate.builder()
					.taxHeadCode(entry.getKey())
					.estimateAmount(entry.getValue().setScale(2, RoundingMode.HALF_UP))
					.build());
		}

		// Append legacy transactional charges calculations safely
		if (roadCuttingChargeBerm.compareTo(BigDecimal.ZERO) != 0)
			estimates.add(TaxHeadEstimate.builder().taxHeadCode(WSCalculationConstant.WS_ROAD_CUTTING_CHARGE_BREM)
					.estimateAmount(roadCuttingChargeBerm.setScale(2, RoundingMode.HALF_UP)).build());

		if (roadCuttingChargeBMPrefixRoad.compareTo(BigDecimal.ZERO) != 0)
			estimates.add(TaxHeadEstimate.builder().taxHeadCode(WSCalculationConstant.WS_ROAD_CUTTING_CHARGE_BMPREMIXROAD)
					.estimateAmount(roadCuttingChargeBMPrefixRoad.setScale(2, RoundingMode.HALF_UP)).build());

		if (usageTypeCharge.compareTo(BigDecimal.ZERO) != 0)
			estimates.add(TaxHeadEstimate.builder().taxHeadCode(WSCalculationConstant.WS_ONE_TIME_FEE)
					.estimateAmount(usageTypeCharge.setScale(2, RoundingMode.HALF_UP)).build());

		if (roadPlotCharge.compareTo(BigDecimal.ZERO) != 0)
			estimates.add(TaxHeadEstimate.builder().taxHeadCode(WSCalculationConstant.WS_SECURITY_CHARGE)
					.estimateAmount(roadPlotCharge.setScale(2, RoundingMode.HALF_UP)).build());

		if (infrastructureCharge.compareTo(BigDecimal.ZERO) > 0) {
			estimates.add(TaxHeadEstimate.builder()
					.taxHeadCode(WSCalculationConstant.WS_INFRASTRUCTURE_CHARGE)
					.estimateAmount(infrastructureCharge.setScale(2, RoundingMode.HALF_UP))
					.build());
		}

		if (tax.compareTo(BigDecimal.ZERO) != 0)
			estimates.add(TaxHeadEstimate.builder().taxHeadCode(WSCalculationConstant.WS_TAX_AND_CESS)
					.estimateAmount(tax.setScale(2, RoundingMode.HALF_UP)).build());

		// Calculate K-Number dues from dueVerification if present
		// Feature removed as per user requirement: do not generate demand for K-Number dues during approval.
		/*
		BigDecimal totalDues = BigDecimal.ZERO;
		if (criteria.getWaterConnection().getDueVerification() != null) {
			for (DueVerification due : criteria.getWaterConnection().getDueVerification()) {
				if (due.getDueAmount() != null && !due.getDueAmount().trim().isEmpty()) {
					totalDues = totalDues.add(new BigDecimal(due.getDueAmount()));
				}
			}
		}
		if (totalDues.compareTo(BigDecimal.ZERO) > 0) {
			estimates.add(TaxHeadEstimate.builder()
					.taxHeadCode(WSCalculationConstant.WS_OTHER_CHARGE)
					.estimateAmount(totalDues.setScale(2, RoundingMode.HALF_UP))
					.build());
			log.info("Added K-Number dues to estimates: {}", totalDues);
		}
		*/

		addAdhocPenaltyAndRebate(estimates, criteria.getWaterConnection());

		// Determine payment status based on workflow state
		String appStatus = criteria.getWaterConnection().getApplicationStatus();
		List<String> unpaidInitialStates = Arrays.asList("INITIATED", "PENDING_APPROVAL_FOR_MUTATION", "PENDING_FOR_PAYMENT", "PENDING_FOR_CITIZEN_ACTION");
		boolean isInitialFeePaid = appStatus != null && !unpaidInitialStates.contains(appStatus.toUpperCase());
				
		boolean isFinalFeePaid = "PENDING_FOR_CONNECTION_ACTIVATION".equalsIgnoreCase(appStatus)
				|| "CONNECTION_ACTIVATED".equalsIgnoreCase(appStatus);

		for (TaxHeadEstimate estimate : estimates) {
			if (WSCalculationConstant.WS_OTHER_CHARGE.equals(estimate.getTaxHeadCode())) {
				// K-Number dues are final fees
				estimate.setStatus(isFinalFeePaid ? "PAID" : "UNPAID");
			} else {
				// All standard upfront charges (Application fee, Infrastructure, etc.) are initial fees
				estimate.setStatus(isInitialFeePaid ? "PAID" : "UNPAID");
			}
		}

		return estimates;
	}

	private boolean hasActive12ABCertificate(CalculationCriteria criteria) {
		if (criteria.getWaterConnection() == null || CollectionUtils.isEmpty(criteria.getWaterConnection().getDocuments())) {
			return false;
		}
		return criteria.getWaterConnection().getDocuments().stream().anyMatch(doc -> WSCalculationConstant.SECTION_12AB_CERTIFICATE.equalsIgnoreCase(doc.getDocumentType()) && (doc.getStatus() == null || doc.getStatus() == Status.ACTIVE));
	}

	@SuppressWarnings("unchecked")
	private BigDecimal calculateInfrastructureCharge(CalculationCriteria criteria, Property property, Map<String, Object> masterData, String colonyCategory) {

		JSONArray infrastructureMaster = (JSONArray) masterData.get(WSCalculationConstant.WC_INFRASTRUCTURE_CHARGE_MASTER);

		if (CollectionUtils.isEmpty(infrastructureMaster)) {
			log.warn("Infrastructure Charge MDMS not configured.");
			return BigDecimal.ZERO;
		}

		JSONObject infra = mapper.convertValue(infrastructureMaster.get(0), JSONObject.class);

		if (!Boolean.TRUE.equals(infra.get(WSCalculationConstant.ACTIVE))) {
			log.info("Infrastructure Charge configuration is inactive.");
			return BigDecimal.ZERO;
		}

		BigDecimal plotArea = property.getLandArea() == null ? BigDecimal.ZERO : BigDecimal.valueOf(property.getLandArea());
		BigDecimal minimumPlotArea = infra.get(WSCalculationConstant.MINIMUM_PLOT_AREA) != null ? new BigDecimal(infra.get(WSCalculationConstant.MINIMUM_PLOT_AREA).toString()) : BigDecimal.ZERO;

		// IFC applicable only when plot area > minimum area
		if (plotArea.compareTo(minimumPlotArea) <= 0) {
			log.info("IFC not applicable. Plot Area : {}, Minimum Required : {}",
					plotArea, minimumPlotArea);
			return BigDecimal.ZERO;
		}

		// Average Water Demand
		WaterDemandResult waterDemand = waterDemandCalculator.calculateAverageWaterDemand(property, masterData);

		BigDecimal waterDemandLPD = waterDemand.getTotalWaterDemand();
		
		if (waterDemandLPD == null || waterDemandLPD.compareTo(BigDecimal.ZERO) <= 0) {
			log.warn("Water Demand calculated as ZERO.");
			return BigDecimal.ZERO;
		}

		BigDecimal waterRate = infra.get(WSCalculationConstant.WATER_RATE_PER_LPD) != null ? new BigDecimal(infra.get(WSCalculationConstant.WATER_RATE_PER_LPD).toString()) : BigDecimal.ZERO;

		BigDecimal sewerRate = infra.get(WSCalculationConstant.SEWER_RATE_PER_LPD) != null ? new BigDecimal(infra.get(WSCalculationConstant.SEWER_RATE_PER_LPD).toString()) : BigDecimal.ZERO;

		Integer annualIncrement = infra.get(WSCalculationConstant.ANNUAL_INCREMENT) != null ? Integer.parseInt(infra.get(WSCalculationConstant.ANNUAL_INCREMENT).toString()) : 0;

		LocalDate effectiveDate = LocalDate.of(2026, 4, 1);
		LocalDate today = LocalDate.now();

		if (today.isAfter(effectiveDate) && annualIncrement > 0) {
			int financialYearDifference = today.getYear() - 2026;
			if (today.getMonthValue() < 4) {
				financialYearDifference--;
			}

			if (financialYearDifference > 0) {
				BigDecimal multiplier = BigDecimal.ONE.add(BigDecimal.valueOf(annualIncrement).multiply(BigDecimal.valueOf(financialYearDifference))
						.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

				waterRate = waterRate.multiply(multiplier);
				sewerRate = sewerRate.multiply(multiplier);
			}
		}

		BigDecimal waterIFC = waterDemandLPD.multiply(waterRate);
		BigDecimal sewerIFC = waterDemandLPD.multiply(sewerRate);
		BigDecimal grossIFC = waterIFC.add(sewerIFC);
		BigDecimal rebatePercentage = BigDecimal.ZERO;

		List<Map<String, Object>> rebates = mapper.convertValue(infra.get(WSCalculationConstant.REBATES), new TypeReference<List<Map<String, Object>>>() {});

		if (!CollectionUtils.isEmpty(rebates)) {

			for (Map<String, Object> rebate : rebates) {

				List<String> categories = (List<String>) rebate.get(WSCalculationConstant.CATEGORIES);
				if (CollectionUtils.isEmpty(categories)) {
					continue;
				}
				if (categories.stream().anyMatch(c -> c.equalsIgnoreCase(colonyCategory))) {
					rebatePercentage = new BigDecimal(rebate.get(WSCalculationConstant.REBATE_PERCENTAGE).toString());
					break;
				}
			}
		}

		BigDecimal rebateAmount = grossIFC.multiply(rebatePercentage).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

		BigDecimal netIFC = grossIFC.subtract(rebateAmount).setScale(2, RoundingMode.HALF_UP);
		BigDecimal netIFCAfterColonyRebate = netIFC;

		// ==================== SECTION 12AB / WORSHIP REBATE ====================
		BigDecimal institutionalRebatePercentage = BigDecimal.ZERO;
		BigDecimal institutionalRebateAmount = BigDecimal.ZERO;
		String institutionalRebateReason = null;

		Map<String, Object> instRebateConfig = infra.get(WSCalculationConstant.INSTITUTIONAL_REBATE) != null ? mapper.convertValue(infra.get(WSCalculationConstant.INSTITUTIONAL_REBATE), new TypeReference<Map<String, Object>>() {}) : null;

		if (instRebateConfig != null && hasActive12ABCertificate(criteria)) {
			List<String> eligibleCodes = instRebateConfig.get(WSCalculationConstant.ELIGIBLE_USAGE_CODES) != null ? mapper.convertValue(instRebateConfig.get(WSCalculationConstant.ELIGIBLE_USAGE_CODES), new TypeReference<List<String>>() {}) : Collections.emptyList();

			boolean isPlaceOfWorship = eligibleCodes.stream().anyMatch(c ->
					c.equalsIgnoreCase(extractWaterConnectionUsageType(property)) || (property != null && c.equalsIgnoreCase(property.getUsageCategory())));

			institutionalRebateReason = isPlaceOfWorship ? "Section 12AB Registered Place of Worship" : "Section 12AB Registered Institution";

			institutionalRebatePercentage = new BigDecimal(instRebateConfig.get(WSCalculationConstant.INSTITUTIONAL_REBATE_PERCENTAGE).toString());
			institutionalRebateAmount = netIFC.multiply(institutionalRebatePercentage).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
			netIFC = netIFC.subtract(institutionalRebateAmount).setScale(2, RoundingMode.HALF_UP);
		}
		BigDecimal netIFCAfterInstitutionalRebate = netIFC;

		// ==================== DWELLING UNIT REBATE (Clause 8) ====================
		BigDecimal dwellingRebatePercentage = BigDecimal.ZERO;
		BigDecimal dwellingRebateAmount = BigDecimal.ZERO;
		String dwellingRebateReason = null;

		BigDecimal builtUpArea = waterDemand.getContextVariables() != null
				? waterDemand.getContextVariables().getOrDefault("built_up_area", BigDecimal.ZERO)
				: BigDecimal.ZERO;

		if (builtUpArea.compareTo(BigDecimal.ZERO) > 0 && builtUpArea.compareTo(new BigDecimal("50")) <= 0) {
			dwellingRebateReason = "Dwelling Unit ≤50 sqm Built-up Area";
			dwellingRebatePercentage = new BigDecimal("50");
			dwellingRebateAmount = netIFC.multiply(dwellingRebatePercentage).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
			netIFC = netIFC.subtract(dwellingRebateAmount).setScale(2, RoundingMode.HALF_UP);
		}
		boolean isDwellingRebateEligible = dwellingRebateReason != null;
		BigDecimal netIFCAfterDwellingRebate = netIFC;
        // ================================================================================

		boolean isInstitutionalRebateEligible = institutionalRebateReason != null;

		// ==================== DETAILED BREAKDOWN LOGGING ====================
		log.info("======================================================================");
		log.info("           INFRASTRUCTURE CHARGE BREAKDOWN REPORT                    ");
		log.info("======================================================================");
		log.info("Application No       : {}", criteria.getWaterConnection() != null ? criteria.getWaterConnection().getApplicationNo() : "N/A");
		log.info("Tenant ID            : {}", property.getTenantId());
		log.info("Colony Category      : {}", colonyCategory);
		log.info("Plot Area            : {} Sq.Mtr (Min Required: {})", plotArea, minimumPlotArea);
		log.info("Calculated Water Dem : {} LPD", waterDemandLPD);
		log.info("----------------------------------------------------------------------");
		log.info("Water Rate/LPD       : {} (Base + Multiplier adjustments if active)", waterRate.setScale(2, RoundingMode.HALF_UP));
		log.info("Sewer Rate/LPD       : {}", sewerRate.setScale(2, RoundingMode.HALF_UP));
		log.info("----------------------------------------------------------------------");
		log.info("Water Component IFC  : {}  [Formula: {} * {}]", waterIFC.setScale(2, RoundingMode.HALF_UP), waterDemandLPD, waterRate.setScale(2, RoundingMode.HALF_UP));
		log.info("Sewer Component IFC  : {}  [Formula: {} * {}]", sewerIFC.setScale(2, RoundingMode.HALF_UP), waterDemandLPD, sewerRate.setScale(2, RoundingMode.HALF_UP));
		log.info("Gross Total IFC      : {}  [Formula: Water Component + Sewer Component]", grossIFC.setScale(2, RoundingMode.HALF_UP));
		log.info("----------------------------------------------------------------------");
		log.info("Applicable Rebate    : {}%", rebatePercentage.setScale(2, RoundingMode.HALF_UP));
		log.info("Rebate Concession Amt: {}", rebateAmount.setScale(2, RoundingMode.HALF_UP));
		log.info("Net IFC after Colony Rebate      : {}", netIFCAfterColonyRebate);

		if (isInstitutionalRebateEligible) {
			log.info("----------------------------------------------------------------------");
			log.info("Institutional Rebate : {}% (Reason: {})", institutionalRebatePercentage.setScale(2, RoundingMode.HALF_UP), institutionalRebateReason);
			log.info("Inst. Rebate Amount  : {}", institutionalRebateAmount.setScale(2, RoundingMode.HALF_UP));
		}
		log.info("Net IFC after Institutional Rebate: {}", netIFCAfterInstitutionalRebate);

		if (isDwellingRebateEligible) {
			log.info("Dwelling Rebate     : {}% (Reason: {})", dwellingRebatePercentage.setScale(2, RoundingMode.HALF_UP), dwellingRebateReason);
			log.info("Dwelling Rebate Amt : {}", dwellingRebateAmount.setScale(2, RoundingMode.HALF_UP));
		}
		log.info("Net IFC after Dwelling Rebate     : {}", netIFCAfterDwellingRebate);

		log.info("======================================================================");
		log.info("FINAL PAYABLE NET IFC: {}  [Formula: Gross Total - Rebates - Institutional Rebate]", netIFC);
		log.info("======================================================================");
		// ====================================================================

		try {
		    Map<String, Object> infraBreakdown = new LinkedHashMap<>();
		    infraBreakdown.put("resolvedBuildingType", waterDemandCalculator.resolveUsageCategoryCode(property, masterData));
		    infraBreakdown.put("plotArea", plotArea);
		    infraBreakdown.put("waterDemandLPD", waterDemandLPD);

		    // YAHAN WATER DEMAND KA LOG SPECS INJECT KAREIN
		    Map<String, Object> demandTrace = waterDemandCalculator.traceWaterDemandLogDetails(property, masterData);
		    infraBreakdown.put("waterDemandCalculationTrace", demandTrace);

		    infraBreakdown.put("waterRatePerLPD", waterRate.setScale(2, RoundingMode.HALF_UP));
		    infraBreakdown.put("grossInfrastructureCharge", grossIFC.setScale(2, RoundingMode.HALF_UP));
		    infraBreakdown.put("institutionalRebateApplied", isInstitutionalRebateEligible);
		    infraBreakdown.put("institutionalRebateReason", institutionalRebateReason);
		    infraBreakdown.put("institutionalRebatePercentage", institutionalRebatePercentage.setScale(2, RoundingMode.HALF_UP));
		    infraBreakdown.put("institutionalRebateAmount", institutionalRebateAmount.setScale(2, RoundingMode.HALF_UP));
		    infraBreakdown.put("netInfrastructureCharge", netIFC);

		    if (criteria.getWaterConnection() != null) {
		        Map<String, Object> existingDetails = criteria.getWaterConnection().getAdditionalDetails() != null
		            ? mapper.convertValue(criteria.getWaterConnection().getAdditionalDetails(), new TypeReference<Map<String, Object>>() {})
		            : new LinkedHashMap<>();

		        existingDetails.put("infrastructureChargeDetails", infraBreakdown);
		        criteria.getWaterConnection().setAdditionalDetails(existingDetails);
		    }
		} catch (Exception e) {
		    log.error("Error setting infrastructure breakdown details in calculation context", e);
		}
		
		Map<String, BigDecimal> contextVars = waterDemand.getContextVariables() != null ? waterDemand.getContextVariables() : new HashMap<>();
		PropertyDetail propertyDetail = buildPropertyDetail(property, colonyCategory, contextVars);

		WaterDemandDetail demandDetail = WaterDemandDetail.builder()
		        .matchedNormCode(waterDemand.getMatchedNormCode())
		        .matchedNormName(waterDemand.getMatchedNormName())
		        .formulaUsed(waterDemand.getFormulaUsed())
		        .calculatedOccupancy(waterDemand.getCalculatedOccupancy())
		        .chosenLpcd(waterDemand.getChosenLpcd())
		        .baseDemand(waterDemand.getBaseDemand())
		        .contingencyPercentage(waterDemand.getContingencyPercentage())
		        .totalWaterDemandLPD(waterDemandLPD)
		        .contextVariables(contextVars)
		        .build();

		InfrastructureChargeDetail infraDetail = InfrastructureChargeDetail.builder()
		        .colonyCategory(colonyCategory)
		        .plotArea(plotArea)
		        .minimumPlotArea(minimumPlotArea)
		        .waterRatePerLPD(waterRate.setScale(2, RoundingMode.HALF_UP))
		        .sewerRatePerLPD(sewerRate.setScale(2, RoundingMode.HALF_UP))
		        .waterComponentIFC(waterIFC.setScale(2, RoundingMode.HALF_UP))
		        .sewerComponentIFC(sewerIFC.setScale(2, RoundingMode.HALF_UP))
		        .grossIFC(grossIFC.setScale(2, RoundingMode.HALF_UP))
		        .rebatePercentage(rebatePercentage)
		        .rebateAmount(rebateAmount.setScale(2, RoundingMode.HALF_UP))
				.netIFCAfterColonyRebate(netIFCAfterColonyRebate)
		        .institutionalRebateApplied(isInstitutionalRebateEligible)
		        .institutionalRebateReason(institutionalRebateReason)
		        .institutionalRebatePercentage(institutionalRebatePercentage.setScale(2, RoundingMode.HALF_UP))
		        .institutionalRebateAmount(institutionalRebateAmount.setScale(2, RoundingMode.HALF_UP))
				.netIFCAfterInstitutionalRebate(netIFCAfterInstitutionalRebate)
				.dwellingRebateApplied(isDwellingRebateEligible)
				.dwellingRebateReason(dwellingRebateReason)
				.dwellingRebatePercentage(dwellingRebatePercentage.setScale(2, RoundingMode.HALF_UP))
				.dwellingRebateAmount(dwellingRebateAmount.setScale(2, RoundingMode.HALF_UP))
				.netIFCAfterDwellingRebate(netIFCAfterDwellingRebate)
		        .netIFC(netIFC)
		        .build();

		CalculationDetail calcDetail = CalculationDetail.builder()
		        .propertyDetail(propertyDetail)
		        .waterDemandDetail(demandDetail)
		        .infrastructureChargeDetail(infraDetail)
		        .build();

		criteria.setCalculationDetail(calcDetail);
		return netIFC;
	}


	private PropertyDetail buildPropertyDetail(Property property, String colonyCategory, Map<String, BigDecimal> contextVars) {
	    if (property == null) {
	        return null;
	    }

	    String localityCode = (property.getAddress() != null && property.getAddress().getLocality() != null)
	            ? property.getAddress().getLocality().getCode()
	            : null;

	    // Safely extract waterConnectionUsageType from property.additionalDetails map
	    String waterConnectionUsageType = extractWaterConnectionUsageType(property);

	    return PropertyDetail.builder()
	            .propertyId(property.getPropertyId())
	            .tenantId(property.getTenantId())
	            .propertyType(property.getPropertyType())
	            .usageCategory(property.getUsageCategory())
	            .waterConnectionUsageType(waterConnectionUsageType)
	            .colonyCategory(colonyCategory)
	            .localityCode(localityCode)
	            // Physical & Dimension attributes
	            .landArea(property.getLandArea() != null ? BigDecimal.valueOf(property.getLandArea()) : BigDecimal.ZERO)
	            .superBuiltUpArea(property.getSuperBuiltUpArea() != null ? property.getSuperBuiltUpArea() : BigDecimal.ZERO)
	            .farArea(contextVars.getOrDefault("far_area", BigDecimal.ZERO))
	            .coveredArea(contextVars.getOrDefault("covered_area", BigDecimal.ZERO))
	            // Occupancy context variables used in calculations
	            .numberOfDwellingUnits(contextVars.getOrDefault("total_du", BigDecimal.ZERO))
	            .numberOfBeds(contextVars.getOrDefault("total_beds", BigDecimal.ZERO))
	            .numberOfRooms(contextVars.getOrDefault("total_rooms", BigDecimal.ZERO))
	            .numberOfStudents(contextVars.getOrDefault("total_students", BigDecimal.ZERO))
	            .numberOfStaff(contextVars.getOrDefault("total_staff", BigDecimal.ZERO))
	            .build();
	}
	
	/**
	 * Helper to extract waterConnectionUsageType safely from Property additionalDetails map
	 */
	private String extractWaterConnectionUsageType(Property property) {
	    if (property.getAdditionalDetails() instanceof Map) {
	        Map<String, Object> additionalDetails = (Map<String, Object>) property.getAdditionalDetails();
	        if (additionalDetails != null && additionalDetails.containsKey("waterConnectionUsageType")) {
	            Object val = additionalDetails.get("waterConnectionUsageType");
	            if (val != null && StringUtils.hasText(val.toString())) {
	                return val.toString();
	            }
	        }
	    }
	    // Fallback to primary usage category if missing in additionalDetails
	    return property.getUsageCategory();
	}
	
	private boolean shouldIncludeFee(WaterConnection wc, String feeComponent, String taxHeadCode) {

		String applicationType = wc.getApplicationType();

		if (WSCalculationConstant.NEW_WATER_CONNECTION.equalsIgnoreCase(applicationType)) {
			return WSCalculationConstant.NEW_CONNECTION_FEE.equalsIgnoreCase(feeComponent);
		}

		if (WSCalculationConstant.MODIFY_WATER_CONNECTION.equalsIgnoreCase(applicationType)) {

			return Arrays.asList(
					WSCalculationConstant.MUTATION_FEE,
					WSCalculationConstant.WATER_ADVANCE,
					WSCalculationConstant.REOPENING_FEE
			).contains(feeComponent);
		}

		if (WSCalculationConstant.MUTATION_WATER_CONNECTION.equalsIgnoreCase(applicationType)) {

			return Arrays.asList(
					WSCalculationConstant.MUTATION_FEE,
					WSCalculationConstant.WATER_ADVANCE,
					WSCalculationConstant.MUTATION_TRADE_SECURITY
			).contains(feeComponent);
		}
		return true;
	}
	
	private String normalizeConnectionCategory(String connectionCategory) {

	    if (connectionCategory == null || connectionCategory.trim().isEmpty()) {
	        return connectionCategory;
	    }

	    switch (connectionCategory.trim().toUpperCase()) {
	        case "NON_DOMESTIC":
	            return "COMMERCIAL";

	        case "DOMESTIC":
	            return "DOMESTIC";

	        default:
	            return connectionCategory.trim().toUpperCase();
	    }
	}

	/**
	 * 
	 * @param masterData Master Data Map
	 * @param roadType - Road type
	 * @param roadCuttingArea - Road Cutting Area
	 * @return road cutting charge
	 */
	private BigDecimal getChargeForRoadCutting(Map<String, Object> masterData, String roadType, Float roadCuttingArea) {
		JSONArray roadSlab = (JSONArray) masterData.getOrDefault(WSCalculationConstant.WC_ROADTYPE_MASTER, null);
		BigDecimal charge = BigDecimal.ZERO;
		JSONObject masterSlab = new JSONObject();
		if(roadSlab != null) {
			masterSlab.put("RoadType", roadSlab);
			JSONArray filteredMasters = JsonPath.read(masterSlab, "$.RoadType[?(@.code=='" + roadType + "')]");
			if (CollectionUtils.isEmpty(filteredMasters))
				return BigDecimal.ZERO;
			JSONObject master = mapper.convertValue(filteredMasters.get(0), JSONObject.class);
			charge = new BigDecimal(master.getAsNumber(WSCalculationConstant.UNIT_COST_CONST).toString());
			charge = charge.multiply(
					new BigDecimal(roadCuttingArea == null ? BigDecimal.ZERO.toString() : roadCuttingArea.toString()));
		}
		return charge;
	}
	
	/**
	 * 
	 * @param masterData - Master Data Map
	 * @param plotSize - Plot Size
	 * @return get fee based on plot size
	 */
	private BigDecimal getPlotSizeFee(Map<String, Object> masterData, Double plotSize) {
		BigDecimal charge = BigDecimal.ZERO;
		JSONArray plotSlab = (JSONArray) masterData.getOrDefault(WSCalculationConstant.WC_PLOTSLAB_MASTER, null);
		JSONObject masterSlab = new JSONObject();
		if (plotSlab != null) {
			masterSlab.put("PlotSizeSlab", plotSlab);
			JSONArray filteredMasters = JsonPath.read(masterSlab, "$.PlotSizeSlab[?(@.from <="+ plotSize +"&& @.to > " + plotSize +")]");
			if(CollectionUtils.isEmpty(filteredMasters))
				return charge;
			JSONObject master = mapper.convertValue(filteredMasters.get(0), JSONObject.class);
			charge = new BigDecimal(master.getAsNumber(WSCalculationConstant.UNIT_COST_CONST).toString());
		}
		return charge;
	}
	
	/**
	 * 
	 * @param masterData Master Data Map
	 * @param usageType - Property Usage Type
	 * @param roadCuttingArea Road Cutting Area
	 * @return  returns UsageType Fee
	 */
	private BigDecimal getUsageTypeFee(Map<String, Object> masterData, String usageType, Float roadCuttingArea) {
		BigDecimal charge = BigDecimal.ZERO;
		JSONArray usageSlab = (JSONArray) masterData.getOrDefault(WSCalculationConstant.WC_PROPERTYUSAGETYPE_MASTER, null);
		JSONObject masterSlab = new JSONObject();
		BigDecimal cuttingArea = new BigDecimal(roadCuttingArea.toString());
		if(usageSlab != null) {
			masterSlab.put("PropertyUsageType", usageSlab);
			JSONArray filteredMasters = JsonPath.read(masterSlab, "$.PropertyUsageType[?(@.code=='"+usageType+"')]");
			if(CollectionUtils.isEmpty(filteredMasters))
				return charge;
			JSONObject master = mapper.convertValue(filteredMasters.get(0), JSONObject.class);
			charge = new BigDecimal(master.getAsNumber(WSCalculationConstant.UNIT_COST_CONST).toString());
			charge = charge.multiply(cuttingArea);
		}
		return charge;
	}
	
	/**
	 * Enrich the adhoc penalty and adhoc rebate
	 * @param estimates tax head estimate
	 * @param connection water connection object
	 */
	@SuppressWarnings({ "unchecked"})
	private void addAdhocPenaltyAndRebate(List<TaxHeadEstimate> estimates, WaterConnection connection) {
		if (connection.getAdditionalDetails() != null) {
			HashMap<String, Object> additionalDetails = mapper.convertValue(connection.getAdditionalDetails(),
					HashMap.class);
			if (additionalDetails.getOrDefault(WSCalculationConstant.ADHOC_PENALTY, null) != null) {
				estimates.add(TaxHeadEstimate.builder().taxHeadCode(WSCalculationConstant.WS_ADHOC_PENALTY)
						.estimateAmount(
								new BigDecimal(additionalDetails.get(WSCalculationConstant.ADHOC_PENALTY).toString()))
						.build());
			}
			if (additionalDetails.getOrDefault(WSCalculationConstant.ADHOC_REBATE, null) != null) {
				estimates
						.add(TaxHeadEstimate.builder().taxHeadCode(WSCalculationConstant.WS_ADHOC_REBATE)
								.estimateAmount(new BigDecimal(
										additionalDetails.get(WSCalculationConstant.ADHOC_REBATE).toString()).negate())
								.build());
			}
		}
	}
	
	public Map<String, List> getReconnectionFeeEstimation(CalculationCriteria criteria, RequestInfo requestInfo, Map<String, Object> masterData ) {
		if (StringUtils.isEmpty(criteria.getWaterConnection()) && !StringUtils.isEmpty(criteria.getApplicationNo())) {
			SearchCriteria searchCriteria = new SearchCriteria();
			searchCriteria.setApplicationNumber(criteria.getApplicationNo());
			searchCriteria.setTenantId(criteria.getTenantId());
			WaterConnection waterConnection = calculatorUtil.getWaterConnectionOnApplicationNO(requestInfo, searchCriteria, requestInfo.getUserInfo().getTenantId());
			criteria.setWaterConnection(waterConnection);
		}
		if (StringUtils.isEmpty(criteria.getWaterConnection())) {
			throw new CustomException("WATER_CONNECTION_NOT_FOUND",
					"Water Connection are not present for " + criteria.getApplicationNo() + " Application no");
		}
		List<TaxHeadEstimate> taxHeadEstimates = getTaxHeadForReconnectionFeeEstimationV2(criteria,masterData, requestInfo);
		Map<String, List> estimatesAndBillingSlabs = new HashMap<>();
		estimatesAndBillingSlabs.put("estimates", taxHeadEstimates);
		return estimatesAndBillingSlabs;
	}

	private List<TaxHeadEstimate> getTaxHeadForReconnectionFeeEstimationV2(CalculationCriteria criteria,
			Map<String, Object> masterData, RequestInfo requestInfo) {
		JSONArray feeSlab = (JSONArray) masterData.getOrDefault(WSCalculationConstant.WC_FEESLAB_MASTER, null);
		if (feeSlab == null)
			throw new CustomException("FEE_SLAB_NOT_FOUND", "fee slab master data not found!!"); 
		
		JSONObject feeObj = mapper.convertValue(feeSlab.get(0), JSONObject.class);
		BigDecimal reconnectionCharge = BigDecimal.ZERO;
		
		if (feeObj.get(WSCalculationConstant.RECONNECTION_FEE_CONST) != null) {
			reconnectionCharge = new BigDecimal(feeObj.getAsNumber(WSCalculationConstant.RECONNECTION_FEE_CONST).toString());
		}
		
		List<TaxHeadEstimate> estimates = new ArrayList<>();

		estimates.add(TaxHeadEstimate.builder().taxHeadCode(WSCalculationConstant.WS_RECONNECTION_CHARGE)
				.estimateAmount(reconnectionCharge).build());
		return estimates;

	}
	public CalculationRes estimateCharges(EstimationRequest request) {

	    String tenantId = StringUtils.hasText(request.getTenantId()) ? request.getTenantId() : "dl.djb";
	    
	    Map<String, Object> masterData = masterDataService.loadExemptionMaster(request.getRequestInfo(), tenantId);

	    String usageTypeToValidate = StringUtils.hasText(request.getWaterConnectionUsageType()) 
	            ? request.getWaterConnectionUsageType() 
	            : request.getUsageCategory();

	    String extractedDemandNormCode = validateAndExtractDemandNormCode(masterData, usageTypeToValidate);
	    validateColonyCategory(masterData, request.getColonyCategory());

	    Map<String, Object> additionalDetails = new LinkedHashMap<>();

	    String originalWaterUsageType = StringUtils.hasText(request.getWaterConnectionUsageType()) ? request.getWaterConnectionUsageType() : request.getUsageCategory();

	    additionalDetails.put("waterConnectionUsageType", originalWaterUsageType); 
	    additionalDetails.put("demandNormCode", extractedDemandNormCode); 
	    additionalDetails.put("rawUsageType", request.getUsageCategory());

	    // Categorization Metadata (Required for DTO & downstream services)
	    if (StringUtils.hasText(request.getCategoryType())) {
	        additionalDetails.put("categoryType", request.getCategoryType());    
	    }
	    if (StringUtils.hasText(request.getPropertyCategory())) {
	        additionalDetails.put("propertyCategory", request.getPropertyCategory());
	    }
	    if (request.getNumberOfFloors() != null) {
	        additionalDetails.put("numberOfFloors", request.getNumberOfFloors());
	    }

	    // Physical Attributes & Context Variables
	    if (request.getFarArea() != null) {
	        additionalDetails.put("farArea", request.getFarArea());
	    }
	    if (request.getBuiltUpArea() != null) {
	        additionalDetails.put("builtUpArea", request.getBuiltUpArea());
	    }
	    if (request.getNumberOfDwellingUnits() != null) {
	        additionalDetails.put(WSCalculationConstant.NUMBER_OF_DWELLING_UNITS, request.getNumberOfDwellingUnits());
	    }
	    if (request.getNumberOfStudents() != null) {
	        additionalDetails.put("numberOfStudents", request.getNumberOfStudents());
	    }

	    // Standardized Capacity Keys (Fixed naming conventions: numberOfBeds, numberOfRooms, numberOfStaff)
	    if (request.getNumberOfBeds() != null) {
	        additionalDetails.put("numberOfBeds", request.getNumberOfBeds());
	    }
	    if (request.getNumberOfRooms() != null) {
	        additionalDetails.put("numberOfRooms", request.getNumberOfRooms());
	    }
	    if (request.getNumberOfStaff() != null) {
	        additionalDetails.put("numberOfStaff", request.getNumberOfStaff());
	    }
	    if (request.getServantQuarterArea() != null) {
	        additionalDetails.put("servantQuarterArea", request.getServantQuarterArea());
	    }
	    if (request.getIs12ABCertificate() != null) {
	        additionalDetails.put(WSCalculationConstant.IS_SECTION_12AB, request.getIs12ABCertificate());
	    }

	    Property mockProperty = Property.builder().usageCategory(request.getUsageCategory()) 
	            .propertyType(request.getPropertyType())   
	            .landArea(request.getLandArea())
	            .additionalDetails(additionalDetails)
	            .build();

	    mockProperty.setTenantId(tenantId);

	    WaterConnection mockConnection = new WaterConnection();
	    mockConnection.setTenantId(tenantId);
	    mockConnection.setApplicationType(WSCalculationConstant.NEW_WATER_CONNECTION);
	    mockConnection.setConnectionCategory(request.getCategoryType());
		// Add 12AB certificate document
		if (request.getIs12ABCertificate() != null && request.getIs12ABCertificate()) {
			Document certificate12AB = new Document();
			certificate12AB.setDocumentType(WSCalculationConstant.SECTION_12AB_CERTIFICATE);
			certificate12AB.setStatus(Status.ACTIVE);
			mockConnection.setDocuments(Collections.singletonList(certificate12AB));
		}
	    
	    String connectionType = StringUtils.hasText(request.getConnectionType()) ? request.getConnectionType() : "METERED";
	    mockConnection.setConnectionType(connectionType);

	    CalculationCriteria criteria = CalculationCriteria.builder()
	            .tenantId(tenantId)
	            .waterConnection(mockConnection)
	            .build();

	    String colonyCategory = StringUtils.hasText(request.getColonyCategory()) ? request.getColonyCategory() : "E";
	    
	    BigDecimal infrastructureCharge = calculateInfrastructureCharge(criteria, mockProperty, masterData, colonyCategory);

	    // =========================================================================
	    // Calculate Connection Fee from MDMS ConnectionCharge Master
	    // =========================================================================
	    BigDecimal connectionFee = calculateConnectionFee(masterData, colonyCategory, request.getCategoryType());

	    List<TaxHeadEstimate> estimates = new ArrayList<>();
	    
	    if (infrastructureCharge != null && infrastructureCharge.compareTo(BigDecimal.ZERO) > 0) {
	        estimates.add(TaxHeadEstimate.builder()
	                .taxHeadCode(WSCalculationConstant.WS_INFRASTRUCTURE_CHARGE)
	                .estimateAmount(infrastructureCharge.setScale(2, RoundingMode.HALF_UP))
	                .build());
	    }

	    if (connectionFee != null && connectionFee.compareTo(BigDecimal.ZERO) > 0) {
	        estimates.add(TaxHeadEstimate.builder()
	                .taxHeadCode("WS_CONNECTCHARGE") // WSCalculationConstant.WS_CONNECTCHARGE
	                .estimateAmount(connectionFee.setScale(2, RoundingMode.HALF_UP))
	                .build());
	    }

	    // Total Amount = Infrastructure Charge + Connection Fee
	    BigDecimal totalAmount = infrastructureCharge.add(connectionFee).setScale(2, RoundingMode.HALF_UP);

	    CalculationDetail calcDetail = criteria.getCalculationDetail();
	    if (calcDetail != null) {
	        Map<String, Object> connChargeDetail = new LinkedHashMap<>();
	        connChargeDetail.put("colonyCategory", colonyCategory);
	        connChargeDetail.put("usageType", request.getCategoryType());
	        connChargeDetail.put("connectionCharge", connectionFee);
	    }

	    Calculation calculation = Calculation.builder().tenantId(tenantId).totalAmount(totalAmount).taxHeadEstimates(estimates).calculationDetail(calcDetail).build();

	    return CalculationRes.builder().responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
	            .calculation(Collections.singletonList(calculation))
	            .build();
	}

	/**
	 * Calculates Connection Fee from MDMS FeeSlab master using NEW_CONNECTION_FEE component.
	 */
	private BigDecimal calculateConnectionFee(Map<String, Object> masterData, String colonyCategory, String categoryType) {
	    if (masterData == null || !masterData.containsKey(WSCalculationConstant.WC_FEESLAB_MASTER)) {
	        log.warn("FeeSlab master data missing in MDMS!");
	        return BigDecimal.ZERO;
	    }

	    List<Map<String, Object>> feeSlabs = null;
	    Object feeSlabData = masterData.get(WSCalculationConstant.WC_FEESLAB_MASTER);

	    if (feeSlabData instanceof List) {
	        feeSlabs = (List<Map<String, Object>>) feeSlabData;
	    } else {
	        feeSlabs = mapper.convertValue(feeSlabData, new TypeReference<List<Map<String, Object>>>() {});
	    }

	    if (CollectionUtils.isEmpty(feeSlabs)) {
	        return BigDecimal.ZERO;
	    }

	    String targetConnectionCategory = normalizeUsageTypeForConnectionFee(categoryType);
	    String targetColonyCategory = colonyCategory != null ? colonyCategory.trim().toUpperCase() : "E";

	    for (Map<String, Object> slab : feeSlabs) {
	        if (slab == null) continue;

	        Boolean isActive = slab.get("isActive") != null ? Boolean.parseBoolean(slab.get("isActive").toString()) : true;
	        if (Boolean.FALSE.equals(isActive)) continue;

	        String feeComponent = slab.get("feeComponent") != null ? slab.get("feeComponent").toString() : "";
	        if (!"NEW_CONNECTION_FEE".equalsIgnoreCase(feeComponent)) {
	            continue;
	        }

	        String connectionCategory = slab.get("connectionCategory") != null ? slab.get("connectionCategory").toString() : "";
	        if (!connectionCategory.equalsIgnoreCase(targetConnectionCategory)) {
	            continue;
	        }

	        List<String> colonyCategories = slab.get("colonyCategories") != null 
	                ? mapper.convertValue(slab.get("colonyCategories"), new TypeReference<List<String>>() {}) 
	                : Collections.emptyList();

	        boolean matchesColony = colonyCategories.stream()
	                .anyMatch(cat -> cat.equalsIgnoreCase(targetColonyCategory));

	        if (matchesColony) {
	            if (slab.get("amount") != null) {
	                BigDecimal amount = new BigDecimal(slab.get("amount").toString());
	                log.info("Matched FeeSlab NEW_CONNECTION_FEE: ConnectionCategory={}, Colony={}, Amount={}", connectionCategory, targetColonyCategory, amount);
	                return amount;
	            }
	        }
	    }

	    log.warn("No matching NEW_CONNECTION_FEE found in FeeSlab for ColonyCategory={}, ConnectionCategory={}", targetColonyCategory, targetConnectionCategory);
	    return BigDecimal.ZERO;
	}

	/**
	 * Normalizes NON_DOMESTIC to COMMERCIAL for FeeSlab mapping.
	 */
	private String normalizeUsageTypeForConnectionFee(String categoryType) {
	    if (!StringUtils.hasText(categoryType)) {
	        return "COMMERCIAL"; // Default fallback
	    }
	    String upper = categoryType.trim().toUpperCase();
	    if ("NON_DOMESTIC".equals(upper) || "NON-DOMESTIC".equals(upper) || "COMMERCIAL".equals(upper)) {
	        return "COMMERCIAL";
	    }
	    if ("DOMESTIC".equals(upper)) {
	        return "DOMESTIC";
	    }
	    return upper;
	}

	/**
	 * Validates usage category against MDMS PropertyNewUsageType
	 * and extracts corresponding demandNormCode with fallback.
	 */
	private String validateAndExtractDemandNormCode(Map<String, Object> masterData, String rawUsageType) {
	    if (!StringUtils.hasText(rawUsageType)) {
	        throw new CustomException("INVALID_USAGE_CATEGORY", "Usage Category cannot be empty for charge estimation.");
	    }

	    if (masterData == null || masterData.isEmpty()) {
	        throw new CustomException("MDMS_DATA_ERROR", "Master Data is null or empty.");
	    }

	    // Direct Extraction
	    List<Map<String, Object>> usageTypes = (List<Map<String, Object>>) masterData.get("PropertyNewUsageType");

	    if (CollectionUtils.isEmpty(usageTypes)) {
	        throw new CustomException("MDMS_DATA_EMPTY", "PropertyNewUsageType master data is missing or empty in MDMS.");
	    }

	    try {
	        String fallbackNormCode = null;

	        for (Map<String, Object> usage : usageTypes) {
	            if (usage == null) continue;

	            Boolean isActive = usage.get("active") != null ? Boolean.parseBoolean(usage.get("active").toString()) : true;
	            if (Boolean.FALSE.equals(isActive)) continue;
	            String code = usage.get("code") != null ? usage.get("code").toString() : "";
	            String normCode = usage.get("demandNormCode") != null ? usage.get("demandNormCode").toString() : "";

	            if (rawUsageType.equalsIgnoreCase(code)) {
	                return StringUtils.hasText(normCode) ? normCode : code;
	            }

	            if (rawUsageType.equalsIgnoreCase(normCode)) {
	                fallbackNormCode = normCode;
	            }
	        }

	        if (StringUtils.hasText(fallbackNormCode)) {
	            return fallbackNormCode;
	        }

	    } catch (CustomException ce) {
	        throw ce;
	    } catch (Exception e) {
	        log.error("Error while validating usage category against MDMS masterData", e);
	        throw new CustomException("MDMS_VALIDATION_ERROR", "An error occurred while validating usage category against MDMS: " + e.getMessage());
	    }

	    throw new CustomException("INVALID_USAGE_TYPE", "The provided usageCategory/waterConnectionUsageType [" + rawUsageType + "] is invalid or inactive in MDMS master data.");
	}
	
	/**
	 * Validates Colony Category input format
	 */
	private void validateColonyCategory(Map<String, Object> masterData, String colonyCategory) {
	    if (StringUtils.hasText(colonyCategory)) {
	        List<String> validCategories = Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H");
	        if (!validCategories.contains(colonyCategory.toUpperCase())) {
	            throw new CustomException("INVALID_COLONY_CATEGORY", "Colony Category must be one of A, B, C, D, E, F, G, H.");
	        }
	    }
	}
	
}
