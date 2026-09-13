package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.constants.WSCalculationConstant;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingFetchRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingStatement;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingStatementResponse;
import org.egov.wscalculation.repository.ServiceRequestRepository;
import org.egov.wscalculation.web.models.RequestInfoWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DJBMonthlyBillingFetchService {

    private static final int MONEY_SCALE = 2;

    private final WaterBillingCycleDao billingCycleDao;
    private final DJBMonthlyBillingCalculationSnapshotService snapshotService;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ObjectMapper objectMapper;
    private final String billingServiceHost;
    private final String billSearchEndpoint;

    public DJBMonthlyBillingFetchService(
            WaterBillingCycleDao billingCycleDao,
            DJBMonthlyBillingCalculationSnapshotService snapshotService,
            ServiceRequestRepository serviceRequestRepository,
            ObjectMapper objectMapper,
            @Value("${egov.billingservice.host}") String billingServiceHost,
            @Value("${egov.bill.search.endpoint}") String billSearchEndpoint) {
        this.billingCycleDao = billingCycleDao;
        this.snapshotService = snapshotService;
        this.serviceRequestRepository = serviceRequestRepository;
        this.objectMapper = objectMapper;
        this.billingServiceHost = billingServiceHost;
        this.billSearchEndpoint = billSearchEndpoint;
    }

    public DJBMonthlyBillingStatementResponse fetch(DJBMonthlyBillingFetchRequest request) {
        validate(request);

        RequestInfo requestInfo = request.getRequestInfo();
        String tenantId = request.getTenantId();
        String connectionNo = request.getConnectionNo();

        WaterBillingCycle cycle = StringUtils.hasText(request.getBillingCycleId())
                ? billingCycleDao.findById(tenantId, request.getBillingCycleId())
                : billingCycleDao.findLatestByConnection(tenantId, connectionNo);

        if (cycle == null) {
            throw new IllegalStateException("Billing cycle not found for connection " + connectionNo
                    + (StringUtils.hasText(request.getBillingCycleId())
                            ? " and billingCycleId " + request.getBillingCycleId() : ""));
        }

        if (!tenantId.equalsIgnoreCase(cycle.getTenantid())) {
            throw new IllegalArgumentException("Billing cycle does not belong to tenant " + tenantId);
        }

        if (!connectionNo.equalsIgnoreCase(cycle.getConnectionno())) {
            throw new IllegalArgumentException("Billing cycle does not belong to connection " + connectionNo);
        }

        if (!StringUtils.hasText(cycle.getCalculationid())) {
            throw new IllegalStateException(
                    "Billing calculation snapshot is not available for billing cycle " + cycle.getId());
        }

        DJBMonthlyBillingStatement statement = snapshotService.read(tenantId, cycle.getCalculationid());
        if (statement == null) {
            throw new IllegalStateException(
                    "Billing calculation snapshot not found for billing cycle " + cycle.getId());
        }

        refreshCurrentCycleState(cycle, statement);
        mergeBillDetails(requestInfo, tenantId, cycle, statement);
        return DJBMonthlyBillingStatementResponse.builder()
                .billingStatement(statement)
                .build();
    }


    private void refreshCurrentCycleState(WaterBillingCycle cycle, DJBMonthlyBillingStatement statement) {
        if (statement.getBillingCycle() == null) {
            statement.setBillingCycle(DJBMonthlyBillingStatement.BillingCycle.builder().build());
        }
        DJBMonthlyBillingStatement.BillingCycle summary = statement.getBillingCycle();
        summary.setStatus(cycle.getStatus() == null ? null : cycle.getStatus().toString());
        summary.setCorrectionStatus(cycle.getCorrectionstatus() == null ? null : cycle.getCorrectionstatus().toString());
        summary.setZroStatus(cycle.getZrostatus() == null ? null : cycle.getZrostatus().toString());
        summary.setZroRemarks(cycle.getZroremarks());
        summary.setAverageCycleCount(cycle.getAveragecyclecount());
        summary.setProvisionalCycleCount(cycle.getProvisionalcyclecount());
        if (statement.getDemand() == null) {
            statement.setDemand(DJBMonthlyBillingStatement.DemandSummary.builder()
                    .created(StringUtils.hasText(cycle.getDemandid()))
                    .id(cycle.getDemandid())
                    .amount(statement.getCharges() == null ? null : statement.getCharges().getNetAmount())
                    .build());
        } else {
            statement.getDemand().setId(cycle.getDemandid() == null ? statement.getDemand().getId() : cycle.getDemandid());
            statement.getDemand().setCreated(StringUtils.hasText(cycle.getDemandid()));
            statement.getDemand().setAmount(statement.getCharges() == null ? null : statement.getCharges().getNetAmount());
        }
        if (statement.getBill() == null) {
            statement.setBill(DJBMonthlyBillingStatement.BillSummary.builder()
                    .generated(StringUtils.hasText(cycle.getBillid()))
                    .id(cycle.getBillid())
                    .demandId(cycle.getDemandid())
                    .calculatedAmount(statement.getCharges() == null ? null : statement.getCharges().getNetAmount())
                    .build());
        }
    }

    private void mergeBillDetails(RequestInfo requestInfo, String tenantId,
            WaterBillingCycle cycle, DJBMonthlyBillingStatement statement) {

        if (!StringUtils.hasText(cycle.getBillid())) {
            if (statement.getBill() != null) {
                statement.getBill().setGenerated(false);
                statement.getBill().setReconciliationStatus("NOT_GENERATED");
            }
            statement.setReconciliation(DJBMonthlyBillingStatement.Reconciliation.builder()
                    .calculatedAmount(statement.getCharges() == null ? null : statement.getCharges().getNetAmount())
                    .status("NOT_GENERATED").build());
            return;
        }

        Map<String, Object> rawResponse = fetchBillSearch(requestInfo, tenantId, cycle.getBillid());
        Map<String, Object> matchedBill = findBillById(rawResponse, cycle.getBillid());
        Map<String, Object> cycleDetail = findCycleBillDetail(matchedBill, cycle.getId());

        if (statement.getBill() == null) {
            statement.setBill(DJBMonthlyBillingStatement.BillSummary.builder()
                    .generated(true)
                    .id(cycle.getBillid())
                    .build());
        }

        statement.getBill().setGenerated(true);
        statement.getBill().setId(cycle.getBillid());
        statement.getBill().setDemandId(cycle.getDemandid());
        if (matchedBill == null) {
            statement.getBill().setReconciliationStatus("BILL_REFERENCE_NOT_FOUND");
            statement.setReconciliation(DJBMonthlyBillingStatement.Reconciliation.builder()
                    .calculatedAmount(statement.getCharges() == null ? null : statement.getCharges().getNetAmount())
                    .status("BILL_REFERENCE_NOT_FOUND").build());
            return;
        }
        statement.getBill().setNumber(asString(getIgnoreCase(matchedBill, "billNumber")));
        statement.getBill().setStatus(asString(getIgnoreCase(matchedBill, "status")));

        BigDecimal actualAmount = extractCycleBillAmount(matchedBill, cycle.getId(), cycleDetail);
        statement.getBill().setAmount(actualAmount);
        statement.getBill().setCalculatedAmount(statement.getCharges() == null ? null : statement.getCharges().getNetAmount());

        BigDecimal calculated = statement.getBill().getCalculatedAmount();
        if (actualAmount == null || calculated == null) {
            statement.getBill().setDifference(null);
            statement.getBill().setReconciliationStatus("NOT_RECONCILED");
            statement.setReconciliation(DJBMonthlyBillingStatement.Reconciliation.builder()
                    .calculatedAmount(calculated).billedAmount(actualAmount).status("NOT_RECONCILED").build());
            return;
        }

        BigDecimal difference = actualAmount.subtract(calculated).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        statement.getBill().setDifference(difference);
        String reconciliationStatus = difference.signum() == 0 ? "MATCHED" : "MISMATCH";
        statement.getBill().setReconciliationStatus(reconciliationStatus);
        statement.setReconciliation(DJBMonthlyBillingStatement.Reconciliation.builder()
                .calculatedAmount(calculated).billedAmount(actualAmount).difference(difference)
                .status(reconciliationStatus).build());
    }

    private Map<String, Object> fetchBillSearch(RequestInfo requestInfo, String tenantId, String billId) {
        StringBuilder url = new StringBuilder(billingServiceHost).append(billSearchEndpoint)
                .append(WSCalculationConstant.URL_PARAMS_SEPARATER)
                .append(WSCalculationConstant.TENANT_ID_FIELD_FOR_SEARCH_URL).append(tenantId)
                .append(WSCalculationConstant.SEPARATER)
                .append("billId=").append(billId);

        Object result = serviceRequestRepository.fetchResult(url,
                RequestInfoWrapper.builder().requestInfo(requestInfo).build());

        if (result == null) {
            throw new IllegalStateException("Billing-service returned null bill response for " + billId);
        }

        try {
            return objectMapper.convertValue(result, Map.class);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to parse billing-service bill search response for " + billId, ex);
        }
    }

    private Map<String, Object> findBillById(Map<String, Object> response, String billId) {
        if (response == null) {
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
        }

        if (billNode.isObject() && billId.equals(billNode.path("id").asText())) {
            return objectMapper.convertValue(billNode, Map.class);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findCycleBillDetail(Map<String, Object> bill, String billingCycleId) {
        if (bill == null) {
            return null;
        }

        Object detailsObject = getIgnoreCase(bill, "billDetails");
        if (!(detailsObject instanceof List)) {
            return null;
        }

        for (Object item : (List<?>) detailsObject) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> detail = (Map<String, Object>) item;
            Map<String, Object> additionalDetails = asMap(getIgnoreCase(detail, "additionalDetails"));
            Object cycleValue = additionalDetails == null ? null
                    : getIgnoreCase(additionalDetails, "djbBillingCycleId");
            if (billingCycleId.equals(String.valueOf(cycleValue))) {
                return detail;
            }
        }
        return null;
    }

    private BigDecimal extractCycleBillAmount(Map<String, Object> bill,
            String billingCycleId, Map<String, Object> cycleDetail) {
        if (cycleDetail != null) {
            Map<String, Object> additionalDetails = asMap(getIgnoreCase(cycleDetail, "additionalDetails"));
            BigDecimal netAmount = toBigDecimal(additionalDetails == null ? null
                    : getIgnoreCase(additionalDetails, "netAmount"));
            if (netAmount != null) {
                return netAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            }
            BigDecimal amount = toBigDecimal(getIgnoreCase(cycleDetail, "amount"));
            if (amount != null) {
                return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            }
        }

        if (bill != null) {
            BigDecimal totalAmount = toBigDecimal(getIgnoreCase(bill, "totalAmount"));
            if (totalAmount != null) {
                return totalAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private Object getIgnoreCase(Map<String, Object> map, String fieldName) {
        if (map == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(fieldName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
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
