package org.egov.wscalculation.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class UsageCodeResolverNorms2Test {

    private UsageCodeResolver resolver;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        resolver = new UsageCodeResolver(mapper, new PropertyContextExtractor(mapper));
    }

    @Test
    void resolvesB3ForIndustrialPropertyUsingOneCanonicalNorm() {
        Map<String, Object> industrialType = propertyType("GeneralIndustrialUnit", "INDUSTRIAL", "B-3");
        Map<String, Object> b3 = norm("B-3", "COMMERCIAL", Arrays.asList("COMMERCIAL", "INDUSTRIAL"));
        Map<String, Object> masterData = masterData(industrialType, b3);

        assertEquals("B-3", resolver.resolveDemandNormCodeFromSelection(
                masterData, "GeneralIndustrialUnit", "Bulk", "Industrial", "Non-Domestic"));
    }

    @Test
    void resolvesB2SubtypeFromPropertyTypeMasterAndKeepsTopLevelCommercial() {
        Map<String, Object> assemblyType = propertyType("BanquetMarriageHall", "COMMERCIAL", "B-2");
        assemblyType.put("demandNormSubType", "ASSEMBLY");
        Map<String, Object> b2 = norm("B-2", "COMMERCIAL", Arrays.asList("COMMERCIAL"));
        Map<String, Object> masterData = masterData(assemblyType, b2);

        assertEquals("B-2", resolver.resolveDemandNormCodeFromSelection(
                masterData, "BanquetMarriageHall", "Bulk", "Commercial", "Non-Domestic"));
        assertEquals("ASSEMBLY", resolver.resolveDemandNormSubType(masterData, "BanquetMarriageHall"));
    }

    @Test
    void requiresAllFourDjbPropertyCategoriesToBeActive() {
        Map<String, Object> type = propertyType("School", "INSTITUTIONAL", "B-1");
        Map<String, Object> norm = norm("B-1", "INSTITUTIONAL", Arrays.asList("INSTITUTIONAL"));
        Map<String, Object> masterData = masterData(type, norm);
        masterData.put("PropertyCategory", Arrays.asList(
                category("Residential", "RESIDENTIAL"),
                category("Commercial", "COMMERCIAL"),
                category("Institutional", "INSTITUTIONAL")));

        assertThrows(IllegalStateException.class, () -> resolver.resolveDemandNormCodeFromSelection(
                masterData, "School", "Bulk", "Institutional", "Non-Domestic"));
    }

    @Test
    void rejectsPropertyCategoryThatDoesNotMatchPropertyType2() {
        Map<String, Object> type = propertyType("BanquetMarriageHall", "COMMERCIAL", "B-2");
        type.put("demandNormSubType", "ASSEMBLY");
        Map<String, Object> masterData = masterData(type, norm("B-2", "COMMERCIAL", Arrays.asList("COMMERCIAL")));

        assertThrows(IllegalStateException.class, () -> resolver.resolveDemandNormCodeFromSelection(
                masterData, "BanquetMarriageHall", "Bulk", "Institutional", "Non-Domestic"));
    }

    @Test
    void rejectsPropertyTypeThatUsesCategoryNotAllowedByNorm() {
        Map<String, Object> type = propertyType("School", "COMMERCIAL", "B-1");
        Map<String, Object> masterData = masterData(type, norm("B-1", "INSTITUTIONAL", Arrays.asList("INSTITUTIONAL")));

        assertThrows(IllegalStateException.class, () -> resolver.resolveDemandNormCodeFromSelection(
                masterData, "School", "Bulk", "Commercial", "Non-Domestic"));
    }

    private Map<String, Object> masterData(Map<String, Object> propertyType, Map<String, Object> norm) {
        Map<String, Object> masterData = new LinkedHashMap<>();
        masterData.put("PropertyType2", Arrays.asList(propertyType));
        masterData.put("PropertyCategory", Arrays.asList(
                category("Residential", "RESIDENTIAL"),
                category("Commercial", "COMMERCIAL"),
                category("Institutional", "INSTITUTIONAL"),
                category("Industrial", "INDUSTRIAL")));
        masterData.put("WaterDemandNorms2", Arrays.asList(norm));
        return masterData;
    }

    private Map<String, Object> propertyType(String code, String propertyCategory, String normCode) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("code", code);
        entry.put("name", code);
        entry.put("propertyCategory", titleCase(propertyCategory));
        entry.put("categoryType", Arrays.asList("Non-Domestic"));
        entry.put("waterConnectionUsageType", "Bulk");
        entry.put("demandNormCode", normCode);
        entry.put("active", true);
        return entry;
    }

    private Map<String, Object> norm(String code, String parent, java.util.List<String> parents) {
        Map<String, Object> norm = new LinkedHashMap<>();
        norm.put("subCategoryCode", code);
        norm.put("name", "Norm " + code);
        norm.put("parentUsageCode", parent);
        norm.put("parentUsageCodes", parents);
        norm.put("isActive", true);
        return norm;
    }

    private Map<String, Object> category(String name, String code) {
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("name", name);
        category.put("code", code);
        category.put("active", true);
        return category;
    }

    private String titleCase(String code) {
        String lower = code.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
