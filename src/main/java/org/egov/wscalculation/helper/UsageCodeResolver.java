package org.egov.wscalculation.helper;

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

/**
 * Resolves the selected property/usage classification to a WaterDemandNorms2
 * sub-category code. Explicit demandNormCode values always take precedence over
 * connection type labels such as "Normal" and "Bulk".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageCodeResolver {

    private static final String WATER_DEMAND_NORMS_V2 = "WaterDemandNorms2";
    private static final String PROPERTY_TYPE_V2 = "PropertyType2";
    private static final String PROPERTY_CATEGORY_MASTER = "PropertyCategory";

    private final ObjectMapper mapper;
    private final PropertyContextExtractor contextExtractor;

    public String resolveUsageCategoryCode(Property property, Map<String, Object> masterData) {
        if (property == null) {
            throw new IllegalArgumentException("Property is required to resolve the revised water-demand norm.");
        }
        Map<String, Object> details = contextExtractor.getPropertyAdditionalDetailsMap(property);
        String propertyCategory = firstNonBlank(details, "propertyCategory", "propertyUsageCategory");
        String categoryType = firstNonBlank(details, "categoryType");
        String rawUsageType = firstNonBlank(details, "rawUsageType", "usageCategoryDetail", "subUsageCategory");
        String usageCategory = StringUtils.isNotBlank(rawUsageType) ? rawUsageType : property.getUsageCategory();

        // PropertyType2 is the source of truth. Do not let an old persisted
        // demandNormCode override a newly updated PropertyType2 mapping.
        return resolveDemandNormCodeFromSelection(masterData, property.getPropertyType(),
                usageCategory, propertyCategory, categoryType);
    }

    /** Backward-compatible overload for callers that do not provide CategoryType. */
    public String resolveDemandNormCodeFromSelection(Map<String, Object> masterData,
            String propertyTypeCode, String usageCategory, String propertyCategory) {
        return resolveDemandNormCodeFromSelection(masterData, propertyTypeCode, usageCategory, propertyCategory, null);
    }

    /**
     * Resolves norm code strictly from the selected active PropertyType2 record,
     * validates its top-level PropertyCategory against the PropertyCategory master,
     * and validates that the selected category is allowed by the norm's
     * parentUsageCodes. This is what allows one canonical B-3 norm to serve both
     * Commercial and Industrial property records without duplicating B-3.
     */
    public String resolveDemandNormCodeFromSelection(Map<String, Object> masterData,
            String propertyTypeCode, String usageCategory, String propertyCategory, String categoryType) {
        List<Map<String, Object>> activeNorms = getActiveNorms(masterData);
        if (CollectionUtils.isEmpty(activeNorms)) {
            throw new IllegalStateException("WaterDemandNorms2 has no active norm entries. "
                    + "Configure active revised norms A-1, B-1..B-4 and C-1..C-4 in MDMS.");
        }

        Map<String, Object> propertyTypeEntry = findPropertyType2Entry(masterData, propertyTypeCode);
        if (propertyTypeEntry == null && StringUtils.isNotBlank(usageCategory)) {
            propertyTypeEntry = findPropertyType2Entry(masterData, usageCategory);
        }
        if (propertyTypeEntry == null) {
            throw new IllegalStateException("Selected property type '" + StringUtils.defaultIfBlank(propertyTypeCode, usageCategory)
                    + "' was not found in the active PropertyTax.PropertyType2 master.");
        }

        String categoryCode = validatePropertyCategory(masterData, propertyTypeEntry, propertyCategory);
        validateCategoryType(propertyTypeEntry, categoryType);

        String configuredCode = getConfiguredNormCode(propertyTypeEntry);
        if (StringUtils.isBlank(configuredCode)) {
            throw new IllegalStateException("PropertyType2 entry '" + value(propertyTypeEntry, "code")
                    + "' is missing demandNormCode. Every active selectable property type must map to one revised norm.");
        }
        Map<String, Object> matchedNorm = findActiveNorm(activeNorms, configuredCode);
        if (matchedNorm == null) {
            throw new IllegalStateException("PropertyType2 entry '" + value(propertyTypeEntry, "code")
                    + "' maps to unsupported WaterDemandNorms2 code '" + configuredCode + "'.");
        }
        validateNormParentCategory(matchedNorm, categoryCode, propertyTypeEntry);

        String normCode = configuredNormCode(matchedNorm);
        if ("B-2".equalsIgnoreCase(normCode)) {
            String useClass = value(propertyTypeEntry, "demandNormSubType");
            if (!"MERCANTILE".equalsIgnoreCase(useClass) && !"ASSEMBLY".equalsIgnoreCase(useClass)) {
                throw new IllegalStateException("PropertyType2 entry '" + value(propertyTypeEntry, "code")
                        + "' uses B-2 but has no valid demandNormSubType. Set MERCANTILE or ASSEMBLY in MDMS.");
            }
        }
        return normCode;
    }

    /** Returns the B-2 IFC use class configured for the selected property type. */
    public String resolveDemandNormSubType(Map<String, Object> masterData, String propertyTypeCode) {
        Map<String, Object> entry = findPropertyType2Entry(masterData, propertyTypeCode);
        return entry == null ? null : value(entry, "demandNormSubType");
    }

    /** Returns the per-property IFC override, for example ZERO for DDA Flat. */
    public String resolvePropertyIfcOverride(Map<String, Object> masterData, String propertyTypeCode) {
        Map<String, Object> entry = findPropertyType2Entry(masterData, propertyTypeCode);
        return entry == null ? null : value(entry, "ifcOverride");
    }

    /** Returns the active PropertyType2 record for downstream audit/validation. */
    public Map<String, Object> findPropertyType2Record(Map<String, Object> masterData, String propertyTypeCode) {
        return findPropertyType2Entry(masterData, propertyTypeCode);
    }

    private String validatePropertyCategory(Map<String, Object> masterData,
            Map<String, Object> propertyTypeEntry, String requestedPropertyCategory) {
        List<Map<String, Object>> categories = toMapList(
                masterData == null ? null : masterData.get(PROPERTY_CATEGORY_MASTER));
        if (CollectionUtils.isEmpty(categories)) {
            throw new IllegalStateException("PropertyTax.PropertyCategory MDMS master is missing or empty. "
                    + "Expected four active categories: RESIDENTIAL, COMMERCIAL, INSTITUTIONAL and INDUSTRIAL.");
        }
        // The DJB property form has exactly these four top-level categories.
        // Fail fast if the master deployment is incomplete rather than accepting
        // a partial dropdown configuration and resolving norms inconsistently.
        String[] requiredCategoryCodes = { "RESIDENTIAL", "COMMERCIAL", "INSTITUTIONAL", "INDUSTRIAL" };
        for (String requiredCode : requiredCategoryCodes) {
            if (findActivePropertyCategory(categories, requiredCode) == null) {
                throw new IllegalStateException("PropertyTax.PropertyCategory is missing active category code '"
                        + requiredCode + "'. Expected Residential, Commercial, Institutional and Industrial.");
            }
        }

        String configuredCategory = value(propertyTypeEntry, "propertyCategory");
        if (StringUtils.isBlank(configuredCategory)) {
            throw new IllegalStateException("PropertyType2 entry '" + value(propertyTypeEntry, "code")
                    + "' has no propertyCategory.");
        }
        Map<String, Object> configuredMasterCategory = findActivePropertyCategory(categories, configuredCategory);
        if (configuredMasterCategory == null) {
            throw new IllegalStateException("PropertyType2 entry '" + value(propertyTypeEntry, "code")
                    + "' refers to unknown/inactive PropertyCategory '" + configuredCategory + "'.");
        }
        String configuredCode = value(configuredMasterCategory, "code");
        if (StringUtils.isBlank(configuredCode)) {
            throw new IllegalStateException("PropertyCategory entry for '" + configuredCategory + "' has no code.");
        }

        if (StringUtils.isNotBlank(requestedPropertyCategory)) {
            Map<String, Object> requestedMasterCategory = findActivePropertyCategory(categories, requestedPropertyCategory);
            if (requestedMasterCategory == null) {
                throw new IllegalStateException("Selected PropertyCategory '" + requestedPropertyCategory
                        + "' is not present/active in PropertyTax.PropertyCategory MDMS.");
            }
            String requestedCode = value(requestedMasterCategory, "code");
            if (!configuredCode.equalsIgnoreCase(requestedCode)) {
                throw new IllegalStateException("Property category mismatch for PropertyType2 code '"
                        + value(propertyTypeEntry, "code") + "': request supplied '" + requestedPropertyCategory
                        + "' but MDMS PropertyType2 specifies '" + configuredCategory + "'.");
            }
        }
        return configuredCode.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> findActivePropertyCategory(List<Map<String, Object>> categories, String candidate) {
        for (Map<String, Object> category : categories) {
            if (category == null || !isActive(category, "active")) {
                continue;
            }
            if (equalsAnyIgnoreCase(candidate, value(category, "name"), value(category, "code"))) {
                return category;
            }
        }
        return null;
    }

    private void validateCategoryType(Map<String, Object> propertyTypeEntry, String selectedCategoryType) {
        if (StringUtils.isBlank(selectedCategoryType)) {
            return;
        }
        Object configured = propertyTypeEntry.get("categoryType");
        List<String> allowed = new ArrayList<>();
        if (configured instanceof List || configured instanceof JSONArray) {
            List<Object> values = mapper.convertValue(configured, new TypeReference<List<Object>>() { });
            for (Object item : values) {
                if (item != null) allowed.add(item.toString().trim());
            }
        } else if (configured != null) {
            allowed.add(configured.toString().trim());
        }
        String selectedNormalized = normalizeCategoryType(selectedCategoryType);
        for (String candidate : allowed) {
            if (normalizeCategoryType(candidate).equals(selectedNormalized)) {
                return;
            }
        }
        throw new IllegalStateException("Selected categoryType '" + selectedCategoryType
                + "' is not allowed for PropertyType2 code '" + value(propertyTypeEntry, "code")
                + "'. Allowed values: " + allowed);
    }

    private String normalizeCategoryType(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
    }

    private void validateNormParentCategory(Map<String, Object> norm, String selectedCategoryCode,
            Map<String, Object> propertyTypeEntry) {
        List<String> allowed = new ArrayList<>();
        Object configuredParents = norm.get("parentUsageCodes");
        if (configuredParents instanceof List || configuredParents instanceof JSONArray) {
            List<Object> values = mapper.convertValue(configuredParents, new TypeReference<List<Object>>() { });
            for (Object value : values) {
                if (value != null) allowed.add(value.toString().trim().toUpperCase(Locale.ROOT));
            }
        }
        String primaryParent = value(norm, "parentUsageCode");
        if (StringUtils.isNotBlank(primaryParent) && !allowed.contains(primaryParent.toUpperCase(Locale.ROOT))) {
            allowed.add(primaryParent.toUpperCase(Locale.ROOT));
        }
        if (!allowed.contains(selectedCategoryCode.toUpperCase(Locale.ROOT))) {
            throw new IllegalStateException("PropertyType2 code '" + value(propertyTypeEntry, "code")
                    + "' belongs to PropertyCategory '" + selectedCategoryCode + "' but WaterDemandNorms2 code '"
                    + configuredNormCode(norm) + "' allows parentUsageCodes " + allowed + ".");
        }
    }

    /** Returns true only for a code present and active in WaterDemandNorms2. */
    public boolean isActiveWaterDemandNormCode(Map<String, Object> masterData, String code) {
        if (StringUtils.isBlank(code)) {
            return false;
        }
        return findActiveNorm(getActiveNorms(masterData), normalizeDemandNormCode(code)) != null;
    }

    private List<Map<String, Object>> getActiveNorms(Map<String, Object> masterData) {
        List<Map<String, Object>> norms = extractWaterDemandNorms2(masterData);
        List<Map<String, Object>> active = new ArrayList<>();
        for (Map<String, Object> norm : norms) {
            if (norm != null && isActive(norm, "isActive")) {
                active.add(norm);
            }
        }
        return active;
    }

    private Map<String, Object> findActiveNorm(List<Map<String, Object>> norms, String code) {
        if (CollectionUtils.isEmpty(norms) || StringUtils.isBlank(code)) {
            return null;
        }
        String searchCode = normalizeDemandNormCode(code).toUpperCase(Locale.ROOT);
        for (Map<String, Object> norm : norms) {
            String configuredCode = configuredNormCode(norm);
            if (StringUtils.isNotBlank(configuredCode)
                    && searchCode.equals(normalizeDemandNormCode(configuredCode).toUpperCase(Locale.ROOT))) {
                return norm;
            }
        }
        return null;
    }

    private Map<String, Object> findPropertyType2Entry(Map<String, Object> masterData, String candidate) {
        if (masterData == null || StringUtils.isBlank(candidate)) {
            return null;
        }
        Object source = masterData.get(PROPERTY_TYPE_V2);
        for (Map<String, Object> entry : toMapList(source)) {
            if (entry == null || !isActive(entry, "active")) {
                continue;
            }
            if (equalsAnyIgnoreCase(candidate, value(entry, "code"), value(entry, "name"))) {
                return entry;
            }
        }
        return null;
    }

    private String validateConfiguredNormCode(String code, Map<String, Object> propertyTypeEntry,
            List<Map<String, Object>> activeNorms) {
        Map<String, Object> matched = findActiveNorm(activeNorms, code);
        if (matched == null) {
            throw new IllegalStateException("PropertyType2 MDMS entry '" + value(propertyTypeEntry, "code")
                    + "' maps to unsupported WaterDemandNorms2 code '" + code + "'. "
                    + "Use a configured active revised subCategoryCode: A-1, B-1..B-4 or C-1..C-4; "
                    + "legacy codes such as A-4_HIG_MIG are not valid.");
        }
        return configuredNormCode(matched);
    }

    /**
     * Returns a revised norm code only when the PropertyType2 display name is a
     * complete phrase in exactly one active WaterDemandNorms2 label. Singular and
     * plural word forms are supported; partial-token matches are not.
     */
    private String resolveUniqueNormByPropertyLabel(List<Map<String, Object>> norms, String propertyLabel) {
        List<String> propertyWords = normalizedWords(propertyLabel);
        if (propertyWords.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> norm : norms) {
            String normLabel = value(norm, "name");
            List<String> normWords = normalizedWords(normLabel);
            if (containsPhraseWithPluralSupport(normWords, propertyWords)) {
                matches.add(norm);
            }
        }

        if (matches.size() != 1) {
            return null;
        }
        return configuredNormCode(matches.get(0));
    }

    private List<String> normalizedWords(String label) {
        List<String> words = new ArrayList<>();
        if (StringUtils.isBlank(label)) {
            return words;
        }

        // Split camel-case codes before normalizing punctuation and separators.
        String expanded = label.trim().replaceAll("([a-z])([A-Z])", "$1 $2");
        String normalized = expanded.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        if (StringUtils.isBlank(normalized)) {
            return words;
        }
        for (String word : normalized.split("\\s+")) {
            if (StringUtils.isNotBlank(word)) {
                words.add(word);
            }
        }
        return words;
    }

    private boolean containsPhraseWithPluralSupport(List<String> wholeWords, List<String> phraseWords) {
        if (wholeWords == null || phraseWords == null || phraseWords.isEmpty()
                || wholeWords.size() < phraseWords.size()) {
            return false;
        }

        for (int start = 0; start <= wholeWords.size() - phraseWords.size(); start++) {
            boolean matches = true;
            for (int offset = 0; offset < phraseWords.size(); offset++) {
                if (!sameSingularOrPluralWord(phraseWords.get(offset), wholeWords.get(start + offset))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private boolean sameSingularOrPluralWord(String first, String second) {
        if (first.equals(second)) {
            return true;
        }
        if (first.length() > 2 && second.equals(first + "s")) {
            return true;
        }
        return first.length() > 3 && first.endsWith("y")
                && second.equals(first.substring(0, first.length() - 1) + "ies");
    }

    private List<Map<String, Object>> findNormsForParentCategory(List<Map<String, Object>> norms, String category) {
        List<Map<String, Object>> matching = new ArrayList<>();
        String expectedParent = normalizeParentUsageCode(category);
        for (Map<String, Object> norm : norms) {
            String parent = normalizeParentUsageCode(value(norm, "parentUsageCode"));
            if (StringUtils.isNotBlank(expectedParent) && expectedParent.equals(parent)) {
                matching.add(norm);
            }
        }
        return matching;
    }

    private String normalizeParentUsageCode(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        while (normalized.contains("__")) {
            normalized = normalized.replace("__", "_");
        }
        if ("RESIDENTIAL_AREA".equals(normalized)) {
            return "RESIDENTIAL";
        }
        return normalized;
    }

    private String configuredNormCode(Map<String, Object> norm) {
        String code = value(norm, "subCategoryCode");
        if (StringUtils.isBlank(code)) {
            code = value(norm, "code");
        }
        return normalizeDemandNormCode(code);
    }

    private List<String> asCandidates(String... rawCandidates) {
        List<String> candidates = new ArrayList<>();
        if (rawCandidates != null) {
            for (String candidate : rawCandidates) {
                if (StringUtils.isNotBlank(candidate) && !candidates.contains(candidate.trim())) {
                    candidates.add(candidate.trim());
                }
            }
        }
        return candidates;
    }

    /**
     * Looks up an entry in a PropertyNewUsageType/PropertyType master. Both
     * explicit demandNormCode/subCategoryCode mappings are supported. The legacy
     * infrastructureCategory field is intentionally not used here: its meaning
     * is different from the revised WaterDemandNorms2 classification and several
     * property types changed grouping under the revised norms.
     */
    public String lookupDemandNormCodeFromMdms(Map<String, Object> masterData, String rawCode, String masterKey) {
        if (masterData == null || StringUtils.isBlank(rawCode)) {
            return null;
        }
        // WaterDemandNorms2 calculation must never reuse legacy D-J/A-1-style
        // mappings from PropertyNewUsageType or PropertyType. Those masters may
        // still be needed by unrelated legacy workflows, but not by revised IFC.
        if (hasWaterDemandNorms2Master(masterData)
                && !PROPERTY_TYPE_V2.equalsIgnoreCase(masterKey)) {
            return null;
        }

        Object masterObj = masterData.get(masterKey);
        if (masterObj == null && WSCalculationConstant.WC_PROPERTY_NEW_USAGE_TYPE_MASTER.equalsIgnoreCase(masterKey)) {
            masterObj = masterData.get("PropertyNewUsageType");
        }
        if (masterObj == null && WSCalculationConstant.WC_PROPERTY_TYPE_MASTER.equalsIgnoreCase(masterKey)) {
            masterObj = masterData.get(PROPERTY_TYPE_V2);
        }
        if (masterObj == null) {
            return null;
        }

        List<Map<String, Object>> masterList = toMapList(masterObj);
        if (CollectionUtils.isEmpty(masterList)) {
            return null;
        }

        for (Map<String, Object> entry : masterList) {
            if (entry == null || !isActive(entry, "active")) {
                continue;
            }

            String code = value(entry, "code");
            String type = value(entry, "type");
            String name = value(entry, "name");
            if (equalsAnyIgnoreCase(rawCode, code, type, name)) {
                String normCode = getConfiguredNormCode(entry);
                if (StringUtils.isNotBlank(normCode)) {
                    log.debug("Mapped usage '{}' to demand norm '{}' using MDMS master '{}'", rawCode, normCode, masterKey);
                    return normCode;
                }
            }
        }

        // Also accept a user-selected demandNormCode directly if the master keeps
        // demandNormCode separate from the entry's code/type.
        for (Map<String, Object> entry : masterList) {
            if (entry == null || !isActive(entry, "active")) {
                continue;
            }
            String normCode = getConfiguredNormCode(entry);
            if (StringUtils.isNotBlank(normCode) && normCode.equalsIgnoreCase(rawCode.trim())) {
                return normCode;
            }
        }

        return null;
    }

    public boolean hasWaterDemandNorms2Master(Map<String, Object> masterData) {
        return masterData != null && (masterData.containsKey(WATER_DEMAND_NORMS_V2)
                || masterData.containsKey("WATER_DEMAND_NORMS_2"));
    }

    public List<Map<String, Object>> extractWaterDemandNorms2(Map<String, Object> masterData) {
        return extractList(masterData, WATER_DEMAND_NORMS_V2, "WATER_DEMAND_NORMS_2");
    }

    public Map<String, Object> findExactMatchingNormConfig(List<Map<String, Object>> norms, String code) {
        if (CollectionUtils.isEmpty(norms) || StringUtils.isBlank(code)) {
            return null;
        }

        String searchCode = normalizeDemandNormCode(code).toUpperCase(Locale.ROOT);
        for (Map<String, Object> norm : norms) {
            if (norm == null) {
                continue;
            }
            String subCategoryCode = StringUtils.defaultString(normalizeDemandNormCode(value(norm, "subCategoryCode"))).toUpperCase(Locale.ROOT);
            String normCode = StringUtils.defaultString(normalizeDemandNormCode(value(norm, "code"))).toUpperCase(Locale.ROOT);
            if (searchCode.equals(subCategoryCode) || searchCode.equals(normCode)) {
                return norm;
            }
        }
        return null;
    }

    private List<Map<String, Object>> extractList(Map<String, Object> masterData, String... keys) {
        if (masterData == null || masterData.isEmpty()) {
            return new ArrayList<>();
        }
        for (String key : keys) {
            Object value = masterData.get(key);
            if (value != null) {
                List<Map<String, Object>> result = toMapList(value);
                if (!CollectionUtils.isEmpty(result)) {
                    return result;
                }
            }
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(Object source) {
        if (source instanceof JSONArray || source instanceof List) {
            return mapper.convertValue(source, new TypeReference<List<Map<String, Object>>>() { });
        }
        return new ArrayList<>();
    }

    private String getConfiguredNormCode(Map<String, Object> entry) {
        String normCode = value(entry, "demandNormCode");
        if (StringUtils.isNotBlank(normCode)) {
            return normalizeDemandNormCode(normCode);
        }
        normCode = value(entry, "subCategoryCode");
        if (StringUtils.isNotBlank(normCode)) {
            return normalizeDemandNormCode(normCode);
        }
        return null;
    }

    private String normalizeDemandNormCode(String code) {
        if (StringUtils.isBlank(code)) {
            return code;
        }
        String normalized = code.trim();
        String compact = normalized.toUpperCase(Locale.ROOT).replace(" ", "");
        if (compact.matches("[A-Z][0-9]+")) {
            return compact.substring(0, 1) + "-" + compact.substring(1);
        }
        return normalized;
    }

    private boolean isActive(Map<String, Object> entry, String key) {
        Object activeValue = entry.get(key);
        if (activeValue == null) {
            // Some property masters use isActive rather than active.
            activeValue = entry.get("isActive");
        }
        return activeValue == null || Boolean.parseBoolean(activeValue.toString());
    }

    private String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = value(map, key);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String value(Map<String, Object> map, String key) {
        Object value = map != null ? map.get(key) : null;
        return value == null ? null : value.toString().trim();
    }

    private boolean equalsAnyIgnoreCase(String value, String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.isNotBlank(candidate) && value.trim().equalsIgnoreCase(candidate.trim())) {
                return true;
            }
        }
        return false;
    }


}
