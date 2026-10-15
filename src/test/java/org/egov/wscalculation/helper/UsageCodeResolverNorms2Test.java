package org.egov.wscalculation.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void usesExplicitDemandNormCodeFromPropertyType2() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("code", "Clinic");
        entry.put("name", "Clinic");
        entry.put("infrastructureCategory", "Category C3");
        entry.put("demandNormCode", "B-3");
        entry.put("active", true);

        Map<String, Object> masterData = new LinkedHashMap<>();
        masterData.put("PropertyType2", Arrays.asList(entry));

        assertEquals("B-3", resolver.lookupDemandNormCodeFromMdms(masterData, "Clinic", "PropertyType2"));
    }

    @Test
    void doesNotInferRevisedNormFromLegacyInfrastructureCategory() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("code", "Clinic");
        entry.put("name", "Clinic");
        entry.put("infrastructureCategory", "Category C3");
        entry.put("active", true);

        Map<String, Object> masterData = new LinkedHashMap<>();
        masterData.put("PropertyType2", Arrays.asList(entry));

        assertNull(resolver.lookupDemandNormCodeFromMdms(masterData, "Clinic", "PropertyType2"));
    }

    @Test
    void resolvesDestituteHomeToB1FromUniqueRevisedNormLabel() {
        Map<String, Object> propertyType = new LinkedHashMap<>();
        propertyType.put("code", "DestituteHome");
        propertyType.put("name", "Destitute Home");
        propertyType.put("propertyCategory", "Institutional");
        propertyType.put("infrastructureCategory", "Category B1");
        propertyType.put("active", true);

        Map<String, Object> b1 = norm("B-1",
                "Colleges / Schools / Destitute Homes / Charitable Homes / Universities", "INSTITUTIONAL");
        Map<String, Object> b2 = norm("B-2", "Mercantile / Assembly / Petrol Pumps / Laundry", "INSTITUTIONAL");
        Map<String, Object> b3 = norm("B-3", "Offices / Banks / Police / Laboratories / Clinics Without IPD or Beds", "INSTITUTIONAL");
        Map<String, Object> b4 = norm("B-4", "Storage / Multilevel Parking / Cremation or Burial Ground", "INSTITUTIONAL");

        Map<String, Object> masterData = new LinkedHashMap<>();
        masterData.put("PropertyType2", Arrays.asList(propertyType));
        masterData.put("WaterDemandNorms2", Arrays.asList(b1, b2, b3, b4));

        assertEquals("B-1", resolver.resolveDemandNormCodeFromSelection(
                masterData, "DestituteHome", "Institutional", "Institutional"));
    }

    @Test
    void doesNotGuessWhenPropertyLabelMatchesMultipleRevisedNorms() {
        Map<String, Object> propertyType = new LinkedHashMap<>();
        propertyType.put("code", "Hotel");
        propertyType.put("name", "Hotel");
        propertyType.put("propertyCategory", "Commercial");
        propertyType.put("active", true);

        Map<String, Object> c1 = norm("C-1", "Hotels / Hostels / Dormitories / Lodging", "COMMERCIAL");
        Map<String, Object> c2 = norm("C-2", "Hotels (3-Star and Above) / Resorts / Banquet Halls / Malls", "COMMERCIAL");
        Map<String, Object> masterData = new LinkedHashMap<>();
        masterData.put("PropertyType2", Arrays.asList(propertyType));
        masterData.put("WaterDemandNorms2", Arrays.asList(c1, c2));

        assertThrows(IllegalStateException.class, () -> resolver.resolveDemandNormCodeFromSelection(
                masterData, "Hotel", "Commercial", "Commercial"));
    }

    private Map<String, Object> norm(String code, String name, String parentUsageCode) {
        Map<String, Object> norm = new LinkedHashMap<>();
        norm.put("subCategoryCode", code);
        norm.put("name", name);
        norm.put("parentUsageCode", parentUsageCode);
        norm.put("isActive", true);
        return norm;
    }

}
