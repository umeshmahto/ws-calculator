package org.egov.wscalculation.djbmonthlybilling.service.master;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.egov.common.contract.request.RequestInfo;
import org.egov.mdms.model.MasterDetail;
import org.egov.mdms.model.MdmsCriteria;
import org.egov.mdms.model.MdmsCriteriaReq;
import org.egov.mdms.model.ModuleDetail;
import org.egov.mdms.model.MdmsResponse;
import org.egov.wscalculation.constants.WSCalculationConstant;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBAdditionalSewerageCharge;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyRebate;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlySewerageRule;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariff;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBReadingQualityCode;
import org.egov.wscalculation.repository.ServiceRequestRepository;
import org.egov.wscalculation.util.CalculatorUtil;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.minidev.json.JSONArray;

@Service
public class DJBMonthlyBillingMasterProvider {

    private final ServiceRequestRepository repository;
    private final CalculatorUtil calculatorUtil;
    private final ObjectMapper mapper;

    public DJBMonthlyBillingMasterProvider(ServiceRequestRepository repository,
            CalculatorUtil calculatorUtil, ObjectMapper mapper) {
        this.repository = repository;
        this.calculatorUtil = calculatorUtil;
        this.mapper = mapper;
    }

    public DJBReadingQualityCode findReadingQualityCode(RequestInfo requestInfo, String tenantId, String code) {
        for (DJBReadingQualityCode master : getReadingQualityCodes(requestInfo, tenantId)) {
            if (master.getCode() != null && master.getCode().equalsIgnoreCase(code)
                    && Boolean.TRUE.equals(master.getActive())) return master;
        }
        throw new IllegalArgumentException("DJB ReadingQualityCode not configured: " + code);
    }

    public List<DJBReadingQualityCode> getReadingQualityCodes(RequestInfo requestInfo, String tenantId) {
        return convert(fetchMaster(requestInfo, tenantId, "DJBReadingQualityCode"), DJBReadingQualityCode.class);
    }

    public DJBMonthlyBillingRule getBillingRule(RequestInfo requestInfo, String tenantId) {
        List<DJBMonthlyBillingRule> rules = convert(fetchMaster(requestInfo, tenantId, "DJBMonthlyBillingRule"),
                DJBMonthlyBillingRule.class);
        if (rules.isEmpty()) throw new IllegalStateException("DJBMonthlyBillingRule master is missing");
        return rules.get(0);
    }

    public List<DJBMonthlyWaterTariff> getWaterTariffs(RequestInfo requestInfo, String tenantId) {
        return convert(fetchMaster(requestInfo, tenantId, "DJBMonthlyWaterTariff"), DJBMonthlyWaterTariff.class);
    }

    public List<DJBMonthlySewerageRule> getSewerageRules(RequestInfo requestInfo, String tenantId) {
        return convert(fetchMaster(requestInfo, tenantId, "DJBMonthlySewerageRule"), DJBMonthlySewerageRule.class);
    }

    public List<DJBMonthlyRebate> getRebates(RequestInfo requestInfo, String tenantId) {
        return convert(fetchMaster(requestInfo, tenantId, "DJBMonthlyRebate"), DJBMonthlyRebate.class);
    }

    public List<DJBAdditionalSewerageCharge> getAdditionalSewerageCharges(RequestInfo requestInfo, String tenantId) {
        return convert(fetchMaster(requestInfo, tenantId, "DJBAdditionalSewerageCharge"), DJBAdditionalSewerageCharge.class);
    }

    private JSONArray fetchMaster(RequestInfo requestInfo, String tenantId, String masterName) {
        List<MasterDetail> details = new ArrayList<>();
        details.add(MasterDetail.builder().name(masterName).build());
        ModuleDetail module = ModuleDetail.builder().moduleName(WSCalculationConstant.WS_TAX_MODULE)
                .masterDetails(details).build();
        MdmsCriteria criteria = MdmsCriteria.builder().tenantId(tenantId)
                .moduleDetails(Collections.singletonList(module)).build();
        MdmsCriteriaReq request = MdmsCriteriaReq.builder().requestInfo(requestInfo)
                .mdmsCriteria(criteria).build();
        Object result = repository.fetchResult(calculatorUtil.getMdmsSearchUrl(), request);
        MdmsResponse response = mapper.convertValue(result, MdmsResponse.class);
        Map<String, JSONArray> masters = response.getMdmsRes().get(WSCalculationConstant.WS_TAX_MODULE);
        if (masters == null || masters.get(masterName) == null)
            throw new IllegalStateException("MDMS master not found: " + masterName);
        return masters.get(masterName);
    }

    private <T> List<T> convert(JSONArray values, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Object value : values) result.add(mapper.convertValue(value, type));
        return result;
    }
}
