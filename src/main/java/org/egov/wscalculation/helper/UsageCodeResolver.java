package org.egov.wscalculation.helper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.egov.wscalculation.constants.WSCalculationConstant;
import org.egov.wscalculation.web.models.Property;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;

@Slf4j
@Component
@RequiredArgsConstructor
public class UsageCodeResolver {

    private final ObjectMapper mapper;
    private final PropertyContextExtractor contextExtractor;

    public String resolveUsageCategoryCode(Property property, Map<String, Object> masterData) {
        if (property == null) {
            return "UNKNOWN";
        }

        Map<String, Object> details = contextExtractor.getPropertyAdditionalDetailsMap(property);

        String rawUsageCode = "";
        if (details.get("waterConnectionUsageType") != null && StringUtils.isNotBlank(details.get("waterConnectionUsageType").toString())) {
            rawUsageCode = details.get("waterConnectionUsageType").toString().trim();
        } else if (details.get("subCategoryCode") != null && StringUtils.isNotBlank(details.get("subCategoryCode").toString())) {
            rawUsageCode = details.get("subCategoryCode").toString().trim();
        } else if (details.get("subUsageCategory") != null && StringUtils.isNotBlank(details.get("subUsageCategory").toString())) {
            rawUsageCode = details.get("subUsageCategory").toString().trim();
        } else if (details.get("usageCategoryDetail") != null && StringUtils.isNotBlank(details.get("usageCategoryDetail").toString())) {
            rawUsageCode = details.get("usageCategoryDetail").toString().trim();
        } else if (StringUtils.isNotBlank(property.getUsageCategory())) {
            rawUsageCode = property.getUsageCategory().trim();
        } else if (StringUtils.isNotBlank(property.getPropertyType())) {
            rawUsageCode = property.getPropertyType().trim();
        }

        if (StringUtils.isBlank(rawUsageCode)) {
            return "UNKNOWN";
        }

        String mappedNormCode = rawUsageCode;
        if (!rawUsageCode.matches("^[A-Z](-[0-9a-zA-Z_]+)?$")) {
            String mdmsCode = lookupDemandNormCodeFromMdms(masterData, rawUsageCode, WSCalculationConstant.WC_PROPERTY_NEW_USAGE_TYPE_MASTER);
            if (StringUtils.isBlank(mdmsCode)) {
                mdmsCode = lookupDemandNormCodeFromMdms(masterData, rawUsageCode, WSCalculationConstant.WC_PROPERTY_TYPE_MASTER);
            }
            if (StringUtils.isNotBlank(mdmsCode)) {
                mappedNormCode = mdmsCode;
            }
        }

        if (mappedNormCode.startsWith("A-2") || mappedNormCode.startsWith("A-4")) {
            BigDecimal area = contextExtractor.getBigDecimalFromMap(details, "carpetArea");
            if (area.compareTo(BigDecimal.ZERO) == 0) {
                area = contextExtractor.getBigDecimalFromMap(details, "builtUpArea");
            }
            if (area.compareTo(BigDecimal.ZERO) == 0 && property.getSuperBuiltUpArea() != null) {
                area = BigDecimal.valueOf(property.getSuperBuiltUpArea().doubleValue());
            }

            boolean isGroupA2 = mappedNormCode.startsWith("A-2");
            if (area.compareTo(BigDecimal.ZERO) > 0) {
                boolean isLigEws = area.compareTo(new BigDecimal("40")) < 0;
                if (isGroupA2) {
                    return isLigEws ? "A-2_LIG_EWS" : "A-2_HIG_MIG";
                } else {
                    return isLigEws ? "A-4_LIG_EWS" : "A-4_HIG_MIG";
                }
            }
        }

        if ("C-1".equalsIgnoreCase(mappedNormCode) || mappedNormCode.startsWith("C-1_")) {
            BigDecimal plotArea = property.getLandArea() != null 
                    ? BigDecimal.valueOf(property.getLandArea()) 
                    : contextExtractor.getBigDecimalFromMap(details, "plotArea");

            if (plotArea.compareTo(BigDecimal.ZERO) > 0 && plotArea.compareTo(new BigDecimal("2500")) < 0) {
                return "C-1_NURSING_HOME";
            }
            return "C-1_HOSPITAL";
        }

        return mappedNormCode;
    }

    @SuppressWarnings("unchecked")
    public String lookupDemandNormCodeFromMdms(Map<String, Object> masterData, String rawCode, String masterKey) {
        if (masterData == null || StringUtils.isBlank(rawCode)) {
            return null;
        }

        Object masterObj = masterData.get(masterKey);
        if (masterObj == null && WSCalculationConstant.WC_PROPERTY_NEW_USAGE_TYPE_MASTER.equalsIgnoreCase(masterKey)) {
            masterObj = masterData.get("PropertyNewUsageType");
        }

        if (masterObj == null) {
            return null;
        }

        List<Map<String, Object>> masterList = new ArrayList<>();
        if (masterObj instanceof JSONArray) {
            masterList = mapper.convertValue(masterObj, new TypeReference<List<Map<String, Object>>>() {});
        } else if (masterObj instanceof List) {
            masterList = (List<Map<String, Object>>) masterObj;
        }

        if (CollectionUtils.isEmpty(masterList)) {
            return null;
        }

        for (Map<String, Object> entry : masterList) {
            String code = String.valueOf(entry.getOrDefault("code", "")).trim();
            Boolean active = entry.get("active") == null || Boolean.parseBoolean(entry.get("active").toString());

            if (active && code.equalsIgnoreCase(rawCode)) {
                Object normCode = entry.get("demandNormCode");
                if (normCode != null && StringUtils.isNotBlank(normCode.toString())) {
                    log.info("Successfully mapped code '{}' -> demandNormCode '{}' using MDMS master '{}'", rawCode, normCode, masterKey);
                    return normCode.toString().trim();
                }
            }
        }

        for (Map<String, Object> entry : masterList) {
            String type = String.valueOf(entry.getOrDefault("type", "")).trim();
            Boolean active = entry.get("active") == null || Boolean.parseBoolean(entry.get("active").toString());

            if (active && type.equalsIgnoreCase(rawCode)) {
                Object normCode = entry.get("demandNormCode");
                if (normCode != null && StringUtils.isNotBlank(normCode.toString())) {
                    log.info("Successfully mapped type '{}' -> demandNormCode '{}' using MDMS master '{}'", rawCode, normCode, masterKey);
                    return normCode.toString().trim();
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> extractWaterDemandNorms(Map<String, Object> masterData) {
        Object normsObj = masterData.get("WaterDemandNorms");
        if (normsObj == null) {
            normsObj = masterData.get("WATER_DEMAND_NORMS");
        }
        if (normsObj == null) {
            normsObj = masterData.get(WSCalculationConstant.WATER_DEMAND_NORMS);
        }

        if (normsObj instanceof JSONArray) {
            return mapper.convertValue(normsObj, new TypeReference<List<Map<String, Object>>>() {});
        } else if (normsObj instanceof List) {
            return (List<Map<String, Object>>) normsObj;
        }

        return new ArrayList<>();
    }

    public Map<String, Object> findMatchingNormConfig(List<Map<String, Object>> norms, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }

        String searchCode = code.toUpperCase(Locale.ROOT);

        for (Map<String, Object> norm : norms) {
            String subCategoryCode = String.valueOf(norm.getOrDefault("subCategoryCode", "")).toUpperCase(Locale.ROOT);
            String normCode = String.valueOf(norm.getOrDefault("code", "")).toUpperCase(Locale.ROOT);

            if (searchCode.equals(subCategoryCode) || searchCode.equals(normCode)) {
                return norm;
            }
        }

        for (Map<String, Object> norm : norms) {
            String parentUsageCode = String.valueOf(norm.getOrDefault("parentUsageCode", "")).toUpperCase(Locale.ROOT);
            if (searchCode.equals(parentUsageCode)) {
                return norm;
            }
        }

        for (Map<String, Object> norm : norms) {
            String subCategoryCode = String.valueOf(norm.getOrDefault("subCategoryCode", "")).toUpperCase(Locale.ROOT);
            if (StringUtils.isNotBlank(subCategoryCode) && (searchCode.startsWith(subCategoryCode) || subCategoryCode.startsWith(searchCode))) {
                return norm;
            }
        }

        return null;
    }
}