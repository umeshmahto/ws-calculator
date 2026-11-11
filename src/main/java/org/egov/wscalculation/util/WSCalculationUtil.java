package org.egov.wscalculation.util;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.egov.wscalculation.config.WSCalculationConfiguration;
import org.egov.wscalculation.constants.WSCalculationConstant;
import org.egov.wscalculation.web.models.*;
import org.egov.wscalculation.repository.ServiceRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.type.TypeReference;

@Component
@Getter
@Slf4j
public class WSCalculationUtil {

	@Autowired
	private WSCalculationConfiguration configurations;

	@Autowired
	private ServiceRequestRepository serviceRequestRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Value("${egov.property.service.host}")
	private String propertyHost;

	@Value("${egov.property.searchendpoint}")
	private String searchPropertyEndPoint;

	private final String tenantId = "tenantId=";
	private final String mobileNumber = "mobileNumber=";
	private final String propertyIds = "propertyIds=";
	private final String uuids = "uuids=";

	/**
	 * Returns the tax head search Url with tenantId and WS service name parameters
	 * 
	 * @param tenantId Tenant Id
	 * @param serviceFieldValue Service Field Value
	 * @return Returns Tax Period Search URL
	 */
	public StringBuilder getTaxPeriodSearchUrl(String tenantId, String serviceFieldValue) {
		return new StringBuilder().append(configurations.getBillingServiceHost())
				.append(configurations.getTaxPeriodSearchEndpoint()).append(WSCalculationConstant.URL_PARAMS_SEPARATER)
				.append(WSCalculationConstant.TENANT_ID_FIELD_FOR_SEARCH_URL).append(tenantId)
				.append(WSCalculationConstant.SEPARATER).append(WSCalculationConstant.SERVICE_FIELD_FOR_SEARCH_URL)
				.append(serviceFieldValue);
	}

	/**
	 * Returns the tax head search Url with tenantId and WS service name parameters
	 * 
	 * @param tenantId TenantId
	 * @param serviceFieldValue Service Field Value
	 * @return Returns the Tax Head Search URL
	 */
	public StringBuilder getTaxHeadSearchUrl(String tenantId, String serviceFieldValue) {

		return new StringBuilder().append(configurations.getBillingServiceHost())
				.append(configurations.getTaxheadsSearchEndpoint()).append(WSCalculationConstant.URL_PARAMS_SEPARATER)
				.append(WSCalculationConstant.TENANT_ID_FIELD_FOR_SEARCH_URL).append(tenantId)
				.append(WSCalculationConstant.SEPARATER).append(WSCalculationConstant.SERVICE_FIELD_FOR_SEARCH_URL)
				.append(serviceFieldValue);
	}

	/**
	 * method to create demand search url with demand criteria
	 *
	 * @param getBillCriteria Bill Criteria to search
	 * @return - Returns Demand Search URL
	 */
	public StringBuilder getDemandSearchUrl(GetBillCriteria getBillCriteria) {

		StringBuilder url;
		if (CollectionUtils.isEmpty(getBillCriteria.getConsumerCodes()))
			url = new StringBuilder().append(configurations.getBillingServiceHost())
					.append(configurations.getDemandSearchEndPoint()).append(WSCalculationConstant.URL_PARAMS_SEPARATER)
					.append(WSCalculationConstant.TENANT_ID_FIELD_FOR_SEARCH_URL).append(getBillCriteria.getTenantId())
					.append(WSCalculationConstant.SEPARATER)
					.append(WSCalculationConstant.CONSUMER_CODE_SEARCH_FIELD_NAME)
					.append(getBillCriteria.getConnectionId())
					.append(WSCalculationConstant.WS_CONSUMER_CODE_SEPARATOR)
					.append(getBillCriteria.getConnectionNumber());

		else {
			 url = new StringBuilder().append(configurations.getBillingServiceHost())
					.append(configurations.getDemandSearchEndPoint()).append(WSCalculationConstant.URL_PARAMS_SEPARATER)
					.append(WSCalculationConstant.TENANT_ID_FIELD_FOR_SEARCH_URL).append(getBillCriteria.getTenantId())
					.append(WSCalculationConstant.SEPARATER)
					.append(WSCalculationConstant.CONSUMER_CODE_SEARCH_FIELD_NAME)
					.append(StringUtils.join(getBillCriteria.getConsumerCodes(), ","));

			 if(getBillCriteria.getIsPaymentCompleted() != null)
				 url.append(WSCalculationConstant.SEPARATER)
				 .append(WSCalculationConstant.PAYMENT_COMPLETED_SEARCH_FIELD_NAME)
				 .append(getBillCriteria.getIsPaymentCompleted());
		}

		return url;
	}

	/**
	 * Returns url for demand update Api
	 *
	 * @return Returns UpdateDemandUrl
	 */
	public StringBuilder getUpdateDemandUrl() {
		return new StringBuilder().append(configurations.getBillingServiceHost())
				.append(configurations.getDemandUpdateEndPoint());
	}

	public DemandDetailAndCollection getLatestDemandDetailByTaxHead(String taxHeadCode,
			List<DemandDetail> demandDetails) {
		List<DemandDetail> details = demandDetails.stream()
				.filter(demandDetail -> demandDetail.getTaxHeadMasterCode().equalsIgnoreCase(taxHeadCode))
				.collect(Collectors.toList());
		if (CollectionUtils.isEmpty(details))
			return null;

		BigDecimal taxAmountForTaxHead = BigDecimal.ZERO;
		BigDecimal collectionAmountForTaxHead = BigDecimal.ZERO;
		DemandDetail latestDemandDetail = null;
		long maxCreatedTime = 0L;

		for (DemandDetail detail : details) {
			taxAmountForTaxHead = taxAmountForTaxHead.add(detail.getTaxAmount());
			collectionAmountForTaxHead = collectionAmountForTaxHead.add(detail.getCollectionAmount());
			if (detail.getAuditDetails().getCreatedTime() > maxCreatedTime) {
				maxCreatedTime = detail.getAuditDetails().getCreatedTime();
				latestDemandDetail = detail;
			}
		}

		return DemandDetailAndCollection.builder().taxHeadCode(taxHeadCode).latestDemandDetail(latestDemandDetail)
				.taxAmountForTaxHead(taxAmountForTaxHead).collectionAmountForTaxHead(collectionAmountForTaxHead)
				.build();

	}

	/**
	 * 
	 * @param waterConnectionRequest
	 *            WaterConnectionRequest containing property
	 * @return List of Property
	 */
	public List<Property> propertySearch(WaterConnectionRequest waterConnectionRequest) {
		PropertyCriteria propertyCriteria = new PropertyCriteria();
		HashSet<String> propertyIds = new HashSet<>();
		propertyIds.add(waterConnectionRequest.getWaterConnection().getPropertyId());
		propertyCriteria.setPropertyIds(propertyIds);
		propertyCriteria.setTenantId(waterConnectionRequest.getWaterConnection().getTenantId());
		Object result = serviceRequestRepository.fetchResult(getPropertyURL(propertyCriteria),
				RequestInfoWrapper.builder().requestInfo(waterConnectionRequest.getRequestInfo()).build());
		List<Property> propertyList = getPropertyDetails(result);
		if (CollectionUtils.isEmpty(propertyList)) {
			throw new CustomException("INCORRECT_PROPERTY_ID", "PROPERTY SEARCH ERROR!");
		}
		return propertyList;
	}


	/**
	 * 
	 * @param waterConnectionRequest
	 *            WaterConnectionRequest containing property
	 * @return List of Property
	 */
	public List<Property> propertySearch(RequestInfo requestInfo, Set<String> propertyIds, String tenantId, Long limit) {

		PropertyCriteria propertyCriteria = PropertyCriteria.builder()
				.propertyIds(propertyIds)
				.tenantId(tenantId)
				.limit(limit)
				.build();

		StringBuilder url = getPropertyURL(propertyCriteria);
		RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder()
				.requestInfo(requestInfo)
				.build();

		Object result = serviceRequestRepository.fetchResult(url, requestInfoWrapper);
		List<Property> propertyList = getPropertyDetails(result);
		return propertyList;
	}
	
	/**
	 * 
	 * @param waterConnectionRequest
	 *            WaterConnectionRequest
	 */
	public Property getProperty(WaterConnectionRequest waterConnectionRequest) {
		Optional<Property> propertyList = propertySearch(waterConnectionRequest).stream().findFirst();
		if (!propertyList.isPresent()) {
			throw new CustomException("INVALID_WATER_CONNECTION_PROPERTY",
					"Water connection cannot be enriched without property");
		}
		Property property = propertyList.get();
		if (isNewConnectionResubmission(waterConnectionRequest.getWaterConnection())) {
			logPropertyCalculationContext(property);
			validateApplicationPropertyContext(waterConnectionRequest.getWaterConnection(), property);
		}
		if (StringUtils.isEmpty(property.getUsageCategory())) {
			throw new CustomException("INVALID_WATER_CONNECTION_PROPERTY_USAGE_TYPE",
					"Water connection cannot be enriched without property usage type");
		}
		return property;
	}

	/** Logs the latest authoritative property values used by water calculations. */
	private void logPropertyCalculationContext(Property property) {
		Map<String, Object> details = new HashMap<>();
		if (property.getAdditionalDetails() != null) {
			try {
				details = objectMapper.convertValue(property.getAdditionalDetails(),
						new TypeReference<Map<String, Object>>() { });
			} catch (IllegalArgumentException ex) {
				log.warn("[WS-PROPERTY-CONTEXT] Could not parse property additionalDetails. propertyId={}", property.getPropertyId());
			}
		}
		log.info("[WS-PROPERTY-CONTEXT] propertyId={}, tenantId={}, landArea={}, superBuiltUpArea={}, builtUpArea={}, plotArea={}, propertyLastModifiedTime={}",
				property.getPropertyId(), property.getTenantId(), property.getLandArea(),
				property.getSuperBuiltUpArea(), details.get("builtUpArea"), details.get("plotArea"),
				property.getAuditDetails() == null ? null : property.getAuditDetails().getLastModifiedTime());
	}

	private boolean isNewConnectionResubmission(WaterConnection connection) {
		return connection != null
				&& WSCalculationConstant.NEW_WATER_CONNECTION.equalsIgnoreCase(connection.getApplicationType())
				&& connection.getProcessInstance() != null
				&& "RESUBMIT_APPLICATION".equalsIgnoreCase(connection.getProcessInstance().getAction());
	}

	/**
	 * For a citizen resubmission, verify that the WS application area fields match
	 * the latest Property Service record. The calculator always calculates from the
	 * Property Service object; this check prevents a stale or client-forged WS copy
	 * from silently disagreeing with the authoritative property.
	 */
	public void validateApplicationPropertyContext(WaterConnection connection, Property property) {
		if (connection == null || property == null || connection.getAdditionalDetails() == null) {
			return;
		}
		Map<String, Object> wsDetails = objectMapper.convertValue(connection.getAdditionalDetails(),
				new TypeReference<Map<String, Object>>() { });
		Map<String, Object> propertyDetails = new HashMap<>();
		if (property.getAdditionalDetails() != null) {
			propertyDetails = objectMapper.convertValue(property.getAdditionalDetails(),
					new TypeReference<Map<String, Object>>() { });
		}

		Object canonicalPlotArea = property.getLandArea() != null
				? property.getLandArea() : firstNonBlank(propertyDetails, "plotArea", "landArea");
		assertPropertyAreaMatches("plotArea", wsDetails.get("plotArea"), canonicalPlotArea,
				connection.getApplicationNo());

		Object canonicalBuiltUpArea = firstNonBlank(propertyDetails, "builtUpArea", "built_up_area",
				"totalBuiltUpArea", "totalBuiltupArea");
		if (canonicalBuiltUpArea == null) {
			canonicalBuiltUpArea = property.getSuperBuiltUpArea();
		}
		assertPropertyAreaMatches("builtUpArea", wsDetails.get("builtUpArea"), canonicalBuiltUpArea,
				connection.getApplicationNo());

		// Detect a Property update racing between the WS validation and calculator call.
		Object snapshotObject = wsDetails.get("propertyCalculationSnapshot");
		if (snapshotObject != null && property.getAuditDetails() != null
				&& property.getAuditDetails().getLastModifiedTime() != null) {
			Map<String, Object> snapshot = objectMapper.convertValue(snapshotObject,
					new TypeReference<Map<String, Object>>() { });
			Object snapshotTime = snapshot.get("propertyLastModifiedTime");
			if (snapshotTime != null && !snapshotTime.toString().equals(
						property.getAuditDetails().getLastModifiedTime().toString())) {
				throw new CustomException("WS_PROPERTY_VERSION_CHANGED",
						"Property details changed while the water application was being recalculated. Refresh and resubmit the application.");
			}
		}
	}

	private Object firstNonBlank(Map<String, Object> details, String... keys) {
		for (String key : keys) {
			Object value = details.get(key);
			if (value != null && !value.toString().trim().isEmpty()) {
				return value;
			}
		}
		return null;
	}

	private void assertPropertyAreaMatches(String field, Object wsValue, Object propertyValue,
			String applicationNo) {
		if (wsValue == null || wsValue.toString().trim().isEmpty()
				|| propertyValue == null || propertyValue.toString().trim().isEmpty()) {
			return;
		}
		try {
			BigDecimal wsArea = new BigDecimal(wsValue.toString().trim());
			BigDecimal propertyArea = new BigDecimal(propertyValue.toString().trim());
			if (wsArea.compareTo(propertyArea) != 0) {
				throw new CustomException("WS_PROPERTY_AREA_NOT_SYNCED",
						"The " + field + " in the water application does not match the latest Property record. Refresh property details before calculation.");
			}
		} catch (NumberFormatException ex) {
			throw new CustomException("WS_PROPERTY_AREA_INVALID",
					"Invalid " + field + " value for water application " + applicationNo + ".");
		}
	}

	/**
	 * 
	 * @param criteria Property Search Criteria
	 * @return property URL
	 */
	private StringBuilder getPropertyURL(PropertyCriteria criteria) {
		StringBuilder url = new StringBuilder(getPropertyURL());
		boolean isanyparametermatch = false;
		url.append("?");
		if (!StringUtils.isEmpty(criteria.getTenantId())) {
			isanyparametermatch = true;
			url.append(tenantId).append(criteria.getTenantId());
		}
		if (!CollectionUtils.isEmpty(criteria.getPropertyIds())) {
			if (isanyparametermatch)
				url.append("&");
			isanyparametermatch = true;
			String propertyIdsString = criteria.getPropertyIds().stream().map(propertyId -> propertyId)
					.collect(Collectors.toSet()).stream().collect(Collectors.joining(","));
			url.append(propertyIds).append(propertyIdsString);
		}
		if (!StringUtils.isEmpty(criteria.getMobileNumber())) {
			if (isanyparametermatch)
				url.append("&");
			isanyparametermatch = true;
			url.append(mobileNumber).append(criteria.getMobileNumber());
		}
		if (!CollectionUtils.isEmpty(criteria.getUuids())) {
			if (isanyparametermatch)
				url.append("&");
			String uuidString = criteria.getUuids().stream().map(uuid -> uuid).collect(Collectors.toSet()).stream()
					.collect(Collectors.joining(","));
			url.append(uuids).append(uuidString);
		}
		if ((criteria.getLimit()) != null) {
			if (isanyparametermatch)
				url.append("&");
			isanyparametermatch = true;
			url.append("limit=").append(criteria.getLimit());
		}
		return url;
	}

	/**
	 * 
	 * @param result
	 *            Response object from property service call
	 * @return List of property
	 */
	private List<Property> getPropertyDetails(Object result) {
		try {
			PropertyResponse propertyResponse = objectMapper.convertValue(result, PropertyResponse.class);
			return propertyResponse.getProperties();
		} catch (Exception ex) {
			throw new CustomException("PARSING_ERROR", "The property json cannot be parsed");
		}
	}

	public StringBuilder getPropertyURL() {
		return new StringBuilder().append(propertyHost).append(searchPropertyEndPoint);
	}
}
