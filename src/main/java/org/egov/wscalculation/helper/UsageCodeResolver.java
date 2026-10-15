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

    private final ObjectMapper mapper;
    private final PropertyContextExtractor contextExtractor;

    public String resolveUsageCategoryCode(Property property, Map<String, Object> masterData) {
        if (property == null) {
            return "UNKNOWN";
        }

        Map<String, Object> details = contextExtractor.getPropertyAdditionalDetailsMap(property);

        // A persisted code is trusted only when it is present and active in the
        // revised WaterDemandNorms2 master. This prevents an old code such as
        // A-4_HIG_MIG from leaking into the new A/B/C calculation path.
        String explicitCode = firstNonBlank(details, "demandNormCode", "waterDemandNormCode",
                "subCategoryCode", "waterDemandSubCategoryCode");
        if (StringUtils.isNotBlank(explicitCode)) {
            String normalized = normalizeDemandNormCode(explicitCode);
            if (isActiveWaterDemandNormCode(masterData, normalized)) {
                log.debug("Using active WaterDemandNorms2 code '{}' for property '{}'",
                        normalized, property.getPropertyId());
                return normalized;
            }
            log.warn("Ignoring stale/unsupported persisted water-demand code '{}' for property '{}'; "
                    + "resolving against WaterDemandNorms2 and PropertyType2 instead.",
                    normalized, property.getPropertyId());
        }

        String propertyCategory = firstNonBlank(details, "propertyCategory", "propertyUsageCategory");
        String rawUsageType = firstNonBlank(details, "rawUsageType", "usageCategoryDetail", "subUsageCategory");
        String usageCategory = StringUtils.isNotBlank(rawUsageType) ? rawUsageType : property.getUsageCategory();

        String resolved = resolveDemandNormCodeFromSelection(masterData, property.getPropertyType(),
                usageCategory, propertyCategory);
        return StringUtils.isBlank(resolved) ? "UNKNOWN" : resolved;
    }

    /**
     * Resolve a selected property to the revised WaterDemandNorms2 code.
     *
     * Resolution order is deliberately restricted to the revised contract:
     * 1) an exact active code in WaterDemandNorms2;
     * 2) explicit demandNormCode/subCategoryCode on PropertyTax.PropertyType2;
     * 3) a unique norm for PropertyType2.propertyCategory/parentUsageCode.
     *
     * The third rule is intentionally allowed only when the parent category has
     * exactly one active norm (currently Residential -> A). Institutional and
     * Commercial have multiple subcategories and therefore require explicit
     * PropertyType2 mappings. Legacy PropertyNewUsageType/PropertyType demand
     * codes are never consulted for WaterDemandNorms2.
     */
    public String resolveDemandNormCodeFromSelection(Map<String, Object> masterData,
            String propertyTypeCode, String usageCategory, String propertyCategory) {
        List<Map<String, Object>> activeNorms = getActiveNorms(masterData);
        if (CollectionUtils.isEmpty(activeNorms)) {
            throw new IllegalStateException("WaterDemandNorms2 has no active norm entries. "
                    + "Configure active revised norms A, B-1..B-4 and C-1..C-4 in MDMS.");
        }

        // A caller may supply the revised subCategoryCode directly. Only codes
        // actually configured as active in WaterDemandNorms2 are accepted.
        for (String candidate : asCandidates(propertyTypeCode, usageCategory)) {
            String normalizedCandidate = normalizeDemandNormCode(candidate);
            Map<String, Object> activeNorm = findActiveNorm(activeNorms, normalizedCandidate);
            if (activeNorm != null) {
                return configuredNormCode(activeNorm);
            }
        }

        Map<String, Object> propertyTypeEntry = findPropertyType2Entry(masterData, propertyTypeCode);
        if (propertyTypeEntry == null) {
            propertyTypeEntry = findPropertyType2Entry(masterData, usageCategory);
        }

        // Do not infer a norm from a broad category if the selected property type
        // itself is unknown to PropertyType2. This protects against typo/obsolete
        // UI codes being silently charged under a plausible parent category.
        if (propertyTypeEntry == null && StringUtils.isNotBlank(propertyTypeCode)) {
            return null;
        }

        String categoryFromMaster = value(propertyTypeEntry, "propertyCategory");
        if (StringUtils.isNotBlank(propertyCategory) && StringUtils.isNotBlank(categoryFromMaster)
                && !propertyCategory.trim().equalsIgnoreCase(categoryFromMaster.trim())) {
            throw new IllegalStateException("Property category mismatch for PropertyType2 code '"
                    + value(propertyTypeEntry, "code") + "': request supplied '" + propertyCategory
                    + "' but MDMS PropertyType2 specifies '" + categoryFromMaster + "'.");
        }

        if (propertyTypeEntry != null) {
            String configuredCode = getConfiguredNormCode(propertyTypeEntry);
            if (StringUtils.isNotBlank(configuredCode)) {
                return validateConfiguredNormCode(configuredCode, propertyTypeEntry, activeNorms);
            }

            // PropertyType2.infrastructureCategory (for example, "Category B1")
            // is the older infrastructure/IFC classification, not a dependable
            // one-to-one mapping to the revised water-demand subcategories. Instead,
            // use the selected property's display name only when it appears as a
            // complete, unambiguous phrase in exactly one active WaterDemandNorms2
            // label. E.g. "Destitute Home" uniquely matches B-1's "Destitute Homes".
            // Ambiguous labels such as "Hotel" still require explicit MDMS mapping.
            String normFromLabel = resolveUniqueNormByPropertyLabel(activeNorms,
                    value(propertyTypeEntry, "name"));
            if (StringUtils.isNotBlank(normFromLabel)) {
                log.debug("Resolved property type '{}' to WaterDemandNorms2 code '{}' by unique norm label match",
                        value(propertyTypeEntry, "code"), normFromLabel);
                return normFromLabel;
            }
        }

        String effectiveCategory = StringUtils.isNotBlank(categoryFromMaster)
                ? categoryFromMaster : propertyCategory;
        if (StringUtils.isNotBlank(effectiveCategory)) {
            List<Map<String, Object>> categoryNorms = findNormsForParentCategory(activeNorms, effectiveCategory);
            if (categoryNorms.size() == 1) {
                return configuredNormCode(categoryNorms.get(0));
            }
            if (categoryNorms.size() > 1) {
                List<String> codes = new ArrayList<>();
                for (Map<String, Object> norm : categoryNorms) {
                    codes.add(configuredNormCode(norm));
                }
                throw new IllegalStateException("Property type '" + StringUtils.defaultIfBlank(propertyTypeCode, usageCategory)
                        + "' belongs to category '" + effectiveCategory + "', which matches multiple revised norms "
                        + codes + ". Configure an explicit demandNormCode on its active PropertyType2 MDMS entry.");
            }
        }

        return null;
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
                    + "Use a configured active revised subCategoryCode: A, B-1..B-4 or C-1..C-4; "
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
