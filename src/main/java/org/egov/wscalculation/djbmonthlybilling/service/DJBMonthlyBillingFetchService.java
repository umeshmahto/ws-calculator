package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.constants.WSCalculationConstant;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBAdditionalSewerageCharge;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyRebate;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlySewerageRule;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariff;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.TariffCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.master.DJBMonthlyBillingMasterProvider;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingFetchRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingFetchResponse;
import org.egov.wscalculation.repository.ServiceRequestRepository;
import org.egov.wscalculation.util.CalculatorUtil;
import org.egov.wscalculation.util.WSCalculationUtil;
import org.egov.wscalculation.web.models.Property;
import org.egov.wscalculation.web.models.RequestInfoWrapper;
import org.egov.wscalculation.web.models.WaterConnection;
import org.egov.wscalculation.web.models.WaterConnectionRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DJBMonthlyBillingFetchService {

    private static final int MONEY_SCALE = 2;

    private final WaterBillingCycleDao billingCycleDao;
    private final DJBMonthlyBillingMasterProvider masterProvider;
    private final TariffCalculationService tariffCalculationService;
    private final SewerageCalculationService sewerageCalculationService;
    private final RebateCalculationService rebateCalculationService;
    private final CalculatorUtil calculatorUtil;
    private final WSCalculationUtil wsCalculationUtil;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ObjectMapper objectMapper;
    private final String billingServiceHost;
    private final String billSearchEndpoint;

    public DJBMonthlyBillingFetchService(
            WaterBillingCycleDao billingCycleDao,
            DJBMonthlyBillingMasterProvider masterProvider,
            TariffCalculationService tariffCalculationService,
            SewerageCalculationService sewerageCalculationService,
            RebateCalculationService rebateCalculationService,
            CalculatorUtil calculatorUtil,
            WSCalculationUtil wsCalculationUtil,
            ServiceRequestRepository serviceRequestRepository,
            ObjectMapper objectMapper,
            @Value("${egov.billingservice.host}") String billingServiceHost,
            @Value("${egov.bill.search.endpoint}") String billSearchEndpoint) {
        this.billingCycleDao = billingCycleDao;
        this.masterProvider = masterProvider;
        this.tariffCalculationService = tariffCalculationService;
        this.sewerageCalculationService = sewerageCalculationService;
        this.rebateCalculationService = rebateCalculationService;
        this.calculatorUtil = calculatorUtil;
        this.wsCalculationUtil = wsCalculationUtil;
        this.serviceRequestRepository = serviceRequestRepository;
        this.objectMapper = objectMapper;
        this.billingServiceHost = billingServiceHost;
        this.billSearchEndpoint = billSearchEndpoint;
    }

    public DJBMonthlyBillingFetchResponse fetch(DJBMonthlyBillingFetchRequest request) {
        validate(request);

        RequestInfo requestInfo = request.getRequestInfo();
        String tenantId = request.getTenantId();
        String connectionNo = request.getConnectionNo();

        WaterBillingCycle cycle = StringUtils.hasText(request.getBillingCycleId())
                ? billingCycleDao.findById(tenantId, request.getBillingCycleId())
                : billingCycleDao.findLatestByConnection(tenantId, connectionNo);

        if (cycle == null) {
            throw new IllegalStateException("Billing cycle not found for connection " + connectionNo
                    + (StringUtils.hasText(request.getBillingCycleId()) ? " and billingCycleId " + request.getBillingCycleId() : ""));
        }

        if (!connectionNo.equalsIgnoreCase(cycle.getConnectionno())) {
            throw new IllegalArgumentException("Billing cycle does not belong to connection " + connectionNo);
        }

        WaterConnection connection = loadWaterConnection(requestInfo, connectionNo, tenantId);
        Property property = wsCalculationUtil.getProperty(
                WaterConnectionRequest.builder().requestInfo(requestInfo).waterConnection(connection).build());

        String tariffCategory = resolveTariffCategory(connection, property);
        BigDecimal billingConsumption = cycle.getBillingconsumption();
        if (billingConsumption == null) {
            throw new IllegalStateException("Billing consumption is not available for billing cycle " + cycle.getId());
        }

        List<DJBMonthlyWaterTariff> tariffs = masterProvider.getWaterTariffs(requestInfo, tenantId);
        TariffCalculationResult water = tariffCalculationService.calculate(billingConsumption, tariffCategory, tariffs);

        List<DJBMonthlySewerageRule> sewerageRules = masterProvider.getSewerageRules(requestInfo, tenantId);
        List<DJBAdditionalSewerageCharge> additionalSewerageRules = masterProvider
                .getAdditionalSewerageCharges(requestInfo, tenantId);

        SewerageCalculationContext sewerageContext = SewerageCalculationContext.builder()
                .waterVolumetricCharge(water.getWaterVolumetricCharge())
                .waterConnectionAvailable(true)
                .sewerConnectionAvailable(true)
                .additionalWaterSource(isAdditionalWaterSource(connection))
                .consumerCategory(tariffCategory)
                .propertyUsage(property.getUsageCategory())
                .builtUpAreaSqm(property.getSuperBuiltUpArea())
                .build();

        SewerageCalculationResult sewerage = sewerageCalculationService.calculate(sewerageContext,
                sewerageRules, additionalSewerageRules);

        BigDecimal totalBeforeRebate = water.getTotalWaterCharge().add(sewerage.getTotalSewerageCharge())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        List<DJBMonthlyRebate> rebates = masterProvider.getRebates(requestInfo, tenantId);
        RebateCalculationContext rebateContext = RebateCalculationContext.builder()
                .consumption(billingConsumption)
                .billingBasis(cycle.getBillingbasis())
                .readingQualityCode(cycle.getReadingqualitycode())
                .consumerType(tariffCategory)
                .propertyCategory(tariffCategory)
                .connectionType(tariffCategory)
                .bulkConnection(false)
                .propertyAreaSqm(property.getSuperBuiltUpArea())
                .functionalRwh(false)
                .functionalWastewaterRecycling(false)
                .totalBillBeforeRebate(totalBeforeRebate)
                .freeWaterEligibleAmount(BigDecimal.ZERO)
                .build();

        RebateCalculationResult rebate = rebateCalculationService.calculate(rebateContext, rebates);
        BigDecimal netAmount = totalBeforeRebate.subtract(rebate.getTotalRebate())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // Fetch the exact persisted bill by billId. This avoids returning the
        // complete bill chain for the consumer and lets us isolate the single
        // bill-detail belonging to this billing cycle.
        Map<String, Object> rawBillResponse = fetchBillSearch(requestInfo, tenantId, cycle.getBillid());
        Map<String, Object> matchedBill = findBillById(rawBillResponse, cycle.getBillid());
        Map<String, Object> bill = filterBillForCycle(matchedBill, cycle);
        BigDecimal finalBillAmount = extractCycleBillAmount(bill, cycle.getId());

        DJBMonthlyBillingFetchResponse.BillingCycleSummary cycleSummary =
                DJBMonthlyBillingFetchResponse.BillingCycleSummary.builder()
                .id(cycle.getId())
                .billingPeriodFrom(String.valueOf(cycle.getBillingperiodfrom()))
                .billingPeriodTo(String.valueOf(cycle.getBillingperiodto()))
                .meterReadingId(cycle.getMeterreadingid())
                .readingQualityCode(cycle.getReadingqualitycode())
                .billingBasis(cycle.getBillingbasis() == null ? null : cycle.getBillingbasis().toString())
                .previousReading(cycle.getPreviousokreading())
                .currentReading(cycle.getCurrentreading())
                .actualConsumption(cycle.getActualconsumption())
                .averageConsumption(cycle.getAverageconsumption())
                .billingConsumption(cycle.getBillingconsumption())
                .previousConsumption(cycle.getPreviousconsumption())
                .deviationFactor(cycle.getDeviationfactor())
                .onePointFiveXFlag(cycle.getOnepointfivexflag())
                .averageCycleCount(cycle.getAveragecyclecount())
                .provisionalCycleCount(cycle.getProvisionalcyclecount())
                .zroStatus(cycle.getZrostatus() == null ? null : cycle.getZrostatus().toString())
                .zroRemarks(cycle.getZroremarks())
                .demandId(cycle.getDemandid())
                .billId(cycle.getBillid())
                .correctionStatus(cycle.getCorrectionstatus() == null ? null : cycle.getCorrectionstatus().toString())
                .status(cycle.getStatus() == null ? null : cycle.getStatus().toString())
                .build();

        List<DJBMonthlyBillingFetchResponse.SlabSummary> slabs = water.getSlabCharges() == null ? null
                : water.getSlabCharges().stream()
                    .map(slab -> DJBMonthlyBillingFetchResponse.SlabSummary.builder()
                        .from(slab.getFrom())
                        .to(slab.getTo())
                        .units(slab.getUnits())
                        .ratePerKl(slab.getRatePerKl())
                        .charge(slab.getCharge())
                        .build())
                    .collect(java.util.stream.Collectors.toList());

        DJBMonthlyBillingFetchResponse.BillingCalculationSummary calculationSummary =
                DJBMonthlyBillingFetchResponse.BillingCalculationSummary.builder()
                .tariffCategory(tariffCategory)
                .unit("KL")
                .billableConsumption(billingConsumption)
                .waterVolumetricCharge(water.getWaterVolumetricCharge())
                .serviceCharge(water.getServiceCharge())
                .totalWaterCharge(water.getTotalWaterCharge())
                .slabs(slabs)
                .regularSewerageCharge(sewerage.getRegularSewerageCharge())
                .additionalSewerageCharge(sewerage.getAdditionalSewerageCharge())
                .totalSewerageCharge(sewerage.getTotalSewerageCharge())
                .totalBeforeRebate(totalBeforeRebate)
                .rebateAmount(rebate.getTotalRebate())
                .netAmount(netAmount)
                .build();

        DJBMonthlyBillingFetchResponse.BillSummary billSummary = buildBillSummary(bill, cycle);

        return DJBMonthlyBillingFetchResponse.builder()
                .tenantId(tenantId)
                .connectionNo(connectionNo)
                .billingCycle(cycleSummary)
                .calculation(calculationSummary)
                .bill(billSummary)
                .finalBillAmount(finalBillAmount)
                .build();
    }

    private DJBMonthlyBillingFetchResponse.BillSummary buildBillSummary(
            Map<String, Object> bill, WaterBillingCycle cycle) {
        if (bill == null) {
            return null;
        }

        Map<String, Object> cycleDetail = findCycleBillDetail(bill, cycle);
        if (cycleDetail == null) {
            return null;
        }

        Map<String, Object> additionalDetails = asMap(getIgnoreCase(cycleDetail, "additionalDetails"));

        return DJBMonthlyBillingFetchResponse.BillSummary.builder()
                .billId(String.valueOf(getIgnoreCase(bill, "id")))
                .billNumber((String) getIgnoreCase(bill, "billNumber"))
                .billStatus((String) getIgnoreCase(bill, "status"))
                .demandId((String) getIgnoreCase(cycleDetail, "demandId"))
                .billingCycleId(cycle.getId())
                .netAmount(toBigDecimal(additionalDetails == null ? null
                        : getIgnoreCase(additionalDetails, "netAmount")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findCycleBillDetail(Map<String, Object> bill, WaterBillingCycle cycle) {
        Object detailsObject = getIgnoreCase(bill, "billDetails");
        if (!(detailsObject instanceof List)) {
            return null;
        }
        for (Object detailObject : (List<?>) detailsObject) {
            if (detailObject instanceof Map && matchesBillingCycle((Map<String, Object>) detailObject, cycle)) {
                return (Map<String, Object>) detailObject;
            }
        }
        return null;
    }

    private Map<String, Object> fetchBillSearch(RequestInfo requestInfo, String tenantId, String billId) {
        if (!StringUtils.hasText(billId)) {
            return null;
        }

        // BillSearchCriteria supports billId directly. Use the exact persisted
        StringBuilder url = new StringBuilder(billingServiceHost).append(billSearchEndpoint)
                .append(WSCalculationConstant.URL_PARAMS_SEPARATER)
                .append(WSCalculationConstant.TENANT_ID_FIELD_FOR_SEARCH_URL).append(tenantId)
                .append(WSCalculationConstant.SEPARATER)
                .append("billId=").append(billId);

        Object result = serviceRequestRepository.fetchResult(url,RequestInfoWrapper.builder().requestInfo(requestInfo).build());

        if (result == null) {
            return null;
        }

        try {
            return objectMapper.convertValue(result, Map.class);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to parse billing-service bill search response", ex);
        }
    }

    private Map<String, Object> findBillById(Map<String, Object> response, String billId) {
        if (response == null || !StringUtils.hasText(billId)) {
            return null;
        }

        JsonNode root = objectMapper.valueToTree(response);
        JsonNode billNode = findNodeIgnoreCase(root, "bill");
        if (billNode == null) {
            billNode = findNodeIgnoreCase(root, "bills");
        }
        if (billNode == null) {
            return null;
        }

        if (billNode.isArray()) {
            for (JsonNode item : billNode) {
                if (item.isObject() && billId.equals(item.path("id").asText())) {
                    return objectMapper.convertValue(item, Map.class);
                }
            }
        } else if (billNode.isObject() && billId.equals(billNode.path("id").asText())) {
            return objectMapper.convertValue(billNode, Map.class);
        }

        return null;
    }

    private BigDecimal extractCycleBillAmount(Map<String, Object> bill, String billingCycleId) {
        if (bill == null) {
            return null;
        }

        Object detailsObject = getIgnoreCase(bill, "billDetails");
        if (detailsObject instanceof List) {
            for (Object detailObject : (List<?>) detailsObject) {
                if (!(detailObject instanceof Map)) {
                    continue;
                }

                Map<String, Object> detail = (Map<String, Object>) detailObject;
                Map<String, Object> additionalDetails = asMap(getIgnoreCase(detail, "additionalDetails"));
                Object cycleValue = additionalDetails == null ? null
                        : getIgnoreCase(additionalDetails, "djbBillingCycleId");

                if (billingCycleId.equals(String.valueOf(cycleValue))) {
                    BigDecimal netAmount = toBigDecimal(getIgnoreCase(additionalDetails, "netAmount"));
                    if (netAmount != null) {
                        return netAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                    }

                    BigDecimal amount = toBigDecimal(getIgnoreCase(detail, "amount"));
                    if (amount != null) {
                        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                    }
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> filterBillForCycle(Map<String, Object> bill, WaterBillingCycle cycle) {
        if (bill == null) {
            return null;
        }

        Map<String, Object> filtered = new java.util.LinkedHashMap<>(bill);
        Object detailsObject = getIgnoreCase(bill, "billDetails");
        if (!(detailsObject instanceof List)) {
            return filtered;
        }

        List<Map<String, Object>> matchingDetails = new java.util.ArrayList<>();
        for (Object detailObject : (List<?>) detailsObject) {
            if (!(detailObject instanceof Map)) {
                continue;
            }
            Map<String, Object> detail = (Map<String, Object>) detailObject;
            if (matchesBillingCycle(detail, cycle)) {
                matchingDetails.add(detail);
            }
        }

        String key = findActualKey(bill, "billDetails");
        filtered.put(key == null ? "billDetails" : key, matchingDetails);
        return filtered;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> filterBillResponseForCycle(Map<String, Object> response, Map<String, Object> filteredBill) {
        if (response == null || filteredBill == null) {
            return response;
        }

        JsonNode root = objectMapper.valueToTree(response);
        JsonNode billNode = findNodeIgnoreCase(root, "bill");
        if (billNode == null) {
            billNode = findNodeIgnoreCase(root, "bills");
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (root != null && root.isObject()) {
            result = objectMapper.convertValue(root, Map.class);
        }

        if (billNode != null && billNode.isArray()) {
            String key = findActualKey(result, "Bill");
            if (key == null) {
                key = findActualKey(result, "bill");
            }
            if (key == null) {
                key = findActualKey(result, "bills");
            }
            java.util.List<Map<String, Object>> oneBill = new java.util.ArrayList<>();
            oneBill.add(filteredBill);
            result.put(key == null ? "Bill" : key, oneBill);
        } else {
            String key = findActualKey(result, "Bill");
            if (key == null) {
                key = findActualKey(result, "bill");
            }
            if (key == null) {
                key = "Bill";
            }
            result.put(key, filteredBill);
        }

        return result;
    }

    private boolean matchesBillingCycle(Map<String, Object> detail, WaterBillingCycle cycle) {
        Map<String, Object> additionalDetails = asMap(getIgnoreCase(detail, "additionalDetails"));
        if (additionalDetails != null) {
            Object cycleId = getIgnoreCase(additionalDetails, "djbBillingCycleId");
            if (cycle.getId() != null && cycle.getId().equals(String.valueOf(cycleId))) {
                return true;
            }
        }

        Object demandId = getIgnoreCase(detail, "demandId");
        return cycle.getDemandid() != null && cycle.getDemandid().equals(String.valueOf(demandId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private String findActualKey(Map<String, Object> map, String desiredKey) {
        if (map == null) {
            return null;
        }
        for (String key : map.keySet()) {
            if (key != null && key.equalsIgnoreCase(desiredKey)) {
                return key;
            }
        }
        return null;
    }

    private Object getIgnoreCase(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return new BigDecimal((String) value);
            } catch (NumberFormatException ignored) {
                return null;
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
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getKey().equalsIgnoreCase(fieldName)) {
                    return field.getValue();
                }
                JsonNode nested = findNodeIgnoreCase(field.getValue(), fieldName);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode nested = findNodeIgnoreCase(child, fieldName);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private WaterConnection loadWaterConnection(RequestInfo requestInfo, String connectionNo, String tenantId) {
        List<WaterConnection> connections = calculatorUtil.getWaterConnection(requestInfo, connectionNo, tenantId);
        if (connections == null || connections.isEmpty()) {
            throw new IllegalStateException("Water connection not found: " + connectionNo);
        }
        return calculatorUtil.getWaterConnectionObject(connections);
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
        if (normalized.contains("COMMERCIAL") || normalized.contains("NON_DOMESTIC")
                || normalized.contains("CAT_II") || normalized.contains("BUSINESS")) {
            return "COMMERCIAL";
        }
        throw new IllegalStateException("Unsupported DJB tariff category: " + candidate);
    }

    private boolean isAdditionalWaterSource(WaterConnection connection) {
        String source = connection.getWaterSource();
        return source != null && (source.toUpperCase().contains("BORE")
                || source.toUpperCase().contains("BOREWELL"));
    }

    private void validate(DJBMonthlyBillingFetchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (!StringUtils.hasText(request.getTenantId())) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (!StringUtils.hasText(request.getConnectionNo())) {
            throw new IllegalArgumentException("connectionNo is required");
        }
        if (request.getRequestInfo() == null) {
            throw new IllegalArgumentException("requestInfo is required");
        }
    }
}
