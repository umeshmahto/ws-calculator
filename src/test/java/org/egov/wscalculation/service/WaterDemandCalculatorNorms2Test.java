package org.egov.wscalculation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.egov.wscalculation.helper.ExpressionEvaluator;
import org.egov.wscalculation.helper.PropertyContextExtractor;
import org.egov.wscalculation.helper.UsageCodeResolver;
import org.egov.wscalculation.service.strategy.GroupDemandStrategy;
import org.egov.wscalculation.service.strategy.WaterDemandStrategyFactory;
import org.egov.wscalculation.service.strategy.impl.GroupADemandStrategy;
import org.egov.wscalculation.service.strategy.impl.GroupBDemandStrategy;
import org.egov.wscalculation.service.strategy.impl.GroupCDemandStrategy;
import org.egov.wscalculation.web.models.Property;
import org.egov.wscalculation.web.models.WaterDemandResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class WaterDemandCalculatorNorms2Test {

    private WaterDemandCalculator calculator;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        PropertyContextExtractor contextExtractor = new PropertyContextExtractor(mapper);
        UsageCodeResolver usageCodeResolver = new UsageCodeResolver(mapper, contextExtractor);
        ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
        List<GroupDemandStrategy> strategies = Arrays.<GroupDemandStrategy>asList(
                new GroupADemandStrategy(expressionEvaluator),
                new GroupBDemandStrategy(expressionEvaluator),
                new GroupCDemandStrategy(expressionEvaluator));
        calculator = new WaterDemandCalculator(contextExtractor, expressionEvaluator, usageCodeResolver,
                new WaterDemandStrategyFactory(strategies));
    }

    @Test
    void residentialNormUsesTotalWaterAlways() {
        Map<String, Object> propertyDetails = details("A-1");
        // Deliberately conflict with the old sibling field: questionCode must win.
        propertyDetails.put("numberOfDwellingUnits", new BigDecimal("99"));
        propertyDetails.put("questions", Arrays.asList(questionAnswer("numberOfDwellingUnits", "2")));
        WaterDemandResult result = calculate(property("IndependentResidentialHouse", propertyDetails),
                Arrays.asList(norm("A-1", "RESIDENTIAL", "total_du * 5", 225, 180, 45, "TOTAL_WATER_ALWAYS")));

        assertEquals(new BigDecimal("10"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("225"), result.getChosenLpcd());
        assertEquals(new BigDecimal("2250.00"), result.getTotalWaterDemand());
        assertFalse(result.getPotableThresholdApplied());
    }

    @Test
    void institutionalNormAppliesPotableLpcdOnlyAfterThreshold() {
        Map<String, Object> propertyDetails = details("B-1");
        propertyDetails.put("builtUpArea", new BigDecimal("1200"));
        propertyDetails.put("questions", Arrays.asList(
                questionAnswer("totalStrengthIncludingStaffAndStudents", "250")));
        WaterDemandResult result = calculate(property("School", propertyDetails),
                Arrays.asList(norm("B-1", "INSTITUTIONAL",
                        "MAX(built_up_area / 4.0, total_strength_including_staff_and_students)",
                        45, 15, 30, "POTABLE_ONLY_IF_OVER_12500")));

        // 1200 / 4 = 300, which is higher than the total strength of 250.
        assertEquals(new BigDecimal("300"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("15"), result.getChosenLpcd());
        assertEquals(new BigDecimal("4500.00"), result.getTotalWaterDemand());
        assertTrue(result.getPotableThresholdApplied());
    }

    @Test
    void b1NormUsesAreaBasedOccupancyWhenStrengthIsNotCollected() {
        Map<String, Object> propertyDetails = details("B-1");
        propertyDetails.put("builtUpArea", new BigDecimal("800"));
        // B-1 has a required strength question. A zero answer leaves the area
        // formula as the larger occupancy basis in this test.
        propertyDetails.put("questions", Arrays.asList(
                questionAnswer("totalStrengthIncludingStaffAndStudents", "0")));
        WaterDemandResult result = calculate(property("DestituteHome", propertyDetails),
                Arrays.asList(norm("B-1", "INSTITUTIONAL",
                        "MAX(built_up_area / 4.0, total_strength_including_staff_and_students)",
                        45, 15, 30, "POTABLE_ONLY_IF_OVER_12500")));

        assertEquals(new BigDecimal("200"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("45"), result.getChosenLpcd());
        assertEquals(new BigDecimal("9000.00"), result.getTotalWaterDemand());
        assertFalse(result.getPotableThresholdApplied());
    }

    @Test
    void categoryB2UsesBuiltUpAreaDividedByFourPointFive() {
        Map<String, Object> propertyDetails = details("B-2");
        propertyDetails.put("builtUpArea", new BigDecimal("90"));
        WaterDemandResult result = calculate(property("StationaryShop", propertyDetails),
                Arrays.asList(norm("B-2", "COMMERCIAL", "built_up_area / 4.5",
                        45, 15, 30, "POTABLE_ONLY_IF_OVER_12500")));

        assertEquals(new BigDecimal("20"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("45"), result.getChosenLpcd());
        assertEquals(new BigDecimal("900.00"), result.getTotalWaterDemand());
        assertFalse(result.getPotableThresholdApplied());
    }

    @Test
    void categoryB3UsesBuiltUpAreaDividedByTen() {
        Map<String, Object> propertyDetails = details("B-3");
        propertyDetails.put("builtUpArea", new BigDecimal("1000"));
        WaterDemandResult result = calculate(property("Office", propertyDetails),
                Arrays.asList(norm("B-3", "COMMERCIAL", "built_up_area / 10.0",
                        45, 15, 30, "POTABLE_ONLY_IF_OVER_12500")));

        assertEquals(new BigDecimal("100"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("45"), result.getChosenLpcd());
        assertEquals(new BigDecimal("4500.00"), result.getTotalWaterDemand());
    }

    @Test
    void categoryB4UsesBuiltUpAreaDividedByThirty() {
        Map<String, Object> propertyDetails = details("B-4");
        propertyDetails.put("builtUpArea", new BigDecimal("3000"));
        WaterDemandResult result = calculate(property("WarehouseGodown", propertyDetails),
                Arrays.asList(norm("B-4", "COMMERCIAL", "built_up_area / 30.0",
                        45, 15, 30, "POTABLE_ONLY_IF_OVER_12500")));

        assertEquals(new BigDecimal("100"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("45"), result.getChosenLpcd());
        assertEquals(new BigDecimal("4500.00"), result.getTotalWaterDemand());
    }

    @Test
    void categoryB3SupportsIndustrialParentCategory() {
        Map<String, Object> propertyDetails = details("B-3");
        propertyDetails.put("builtUpArea", new BigDecimal("1000"));
        Map<String, Object> b3 = norm("B-3", "COMMERCIAL", "built_up_area / 10.0",
                45, 15, 30, "POTABLE_ONLY_IF_OVER_12500");
        b3.put("parentUsageCodes", Arrays.asList("COMMERCIAL", "INDUSTRIAL"));
        propertyDetails.put("propertyCategory", "Industrial");
        WaterDemandResult result = calculate(property("GeneralIndustrialUnit", propertyDetails), Arrays.asList(b3));
        assertEquals(new BigDecimal("100"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("4500.00"), result.getTotalWaterDemand());
    }

    @Test
    void b2AssemblyUsesTotalDemandAlwaysWhenConfiguredByUseClass() {
        Map<String, Object> propertyDetails = details("B-2");
        propertyDetails.put("builtUpArea", new BigDecimal("400"));
        propertyDetails.put("demandNormSubType", "ASSEMBLY");
        Map<String, Object> b2 = norm("B-2", "COMMERCIAL", "built_up_area / 4.5",
                45, 15, 30, "CONDITIONAL_BY_USE_CLASS");
        Map<String, Object> basisByUse = new LinkedHashMap<>();
        basisByUse.put("MERCANTILE", "POTABLE_ONLY_IF_OVER_12500");
        basisByUse.put("ASSEMBLY", "TOTAL_WATER_ALWAYS");
        b2.put("ifcCalculationBasisByUseClass", basisByUse);
        WaterDemandResult result = calculate(property("AssemblyHall", propertyDetails), Arrays.asList(b2));
        assertEquals(new BigDecimal("89"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("45"), result.getChosenLpcd());
        assertFalse(result.getPotableThresholdApplied());
    }

    @Test
    void categoryC2UsesSpecifiedBedsWhenAvailable() {
        Map<String, Object> propertyDetails = details("C-2");
        propertyDetails.put("questions", Arrays.asList(
                questionAnswer("numberOfBeds", "50"), questionAnswer("numberOfRooms", "20")));
        propertyDetails.put("builtUpArea", new BigDecimal("1000"));
        WaterDemandResult result = calculate(property("Hotel", propertyDetails),
                Arrays.asList(normWithFormula("C-2", "COMMERCIAL",
                        "IF(sanctioned_beds_is_specified, sanctioned_beds, MAX(total_rooms * 2, built_up_area / 12.5))",
                        320, 275, 45, "POTABLE_ONLY_IF_OVER_12500")));

        assertEquals(new BigDecimal("50"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("275"), result.getChosenLpcd());
        assertEquals(new BigDecimal("13750.00"), result.getTotalWaterDemand());
        assertTrue(result.getPotableThresholdApplied());
    }

    @Test
    void categoryC2FallsBackToHigherOfRoomsAndBuiltUpAreaWhenBedsAreNotSpecified() {
        Map<String, Object> propertyDetails = details("C-2");
        propertyDetails.put("questions", Arrays.asList(questionAnswer("numberOfRooms", "40")));
        propertyDetails.put("builtUpArea", new BigDecimal("1250"));
        WaterDemandResult result = calculate(property("Hotel", propertyDetails),
                Arrays.asList(normWithFormula("C-2", "COMMERCIAL",
                        "IF(sanctioned_beds_is_specified, sanctioned_beds, MAX(total_rooms * 2, built_up_area / 12.5))",
                        320, 275, 45, "POTABLE_ONLY_IF_OVER_12500")));

        // 40 rooms imply 80 beds, while 1,250 sqm imply 100 beds; use 100.
        assertEquals(new BigDecimal("100"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("275"), result.getChosenLpcd());
        assertEquals(new BigDecimal("27500.00"), result.getTotalWaterDemand());
        assertTrue(result.getPotableThresholdApplied());
    }

    @Test
    void categoryC1UsesHigherOfSanctionedBedsAndAreaBasedBeds() {
        Map<String, Object> propertyDetails = details("C-1");
        propertyDetails.put("questions", Arrays.asList(questionAnswer("numberOfBeds", "10")));
        propertyDetails.put("builtUpArea", new BigDecimal("1500"));
        WaterDemandResult result = calculate(property("GovernmentHostel", propertyDetails),
                Arrays.asList(norm("C-1", "COMMERCIAL", "MAX(sanctioned_beds, built_up_area / 7.5)",
                        180, 135, 45, "POTABLE_ONLY_IF_OVER_12500")));

        assertEquals(new BigDecimal("200"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("135"), result.getChosenLpcd());
        assertEquals(new BigDecimal("27000.00"), result.getTotalWaterDemand());
        assertTrue(result.getPotableThresholdApplied());
    }

    @Test
    void categoryC3UsesBuiltUpAreaDividedByOnePointFive() {
        Map<String, Object> propertyDetails = details("C-3");
        propertyDetails.put("builtUpArea", new BigDecimal("150"));
        WaterDemandResult result = calculate(property("Restaurant", propertyDetails),
                Arrays.asList(norm("C-3", "COMMERCIAL", "built_up_area / 1.5",
                        70, 15, 55, "POTABLE_ONLY_IF_OVER_12500")));

        assertEquals(new BigDecimal("100"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("70"), result.getChosenLpcd());
        assertEquals(new BigDecimal("7000.00"), result.getTotalWaterDemand());
        assertFalse(result.getPotableThresholdApplied());
    }

    @Test
    void hospitalNormEvaluatesTotalAndPotableLpcdFormulas() {
        Map<String, Object> propertyDetails = details("C-4");
        propertyDetails.put("questions", Arrays.asList(questionAnswer("numberOfBeds", "30")));
        propertyDetails.put("builtUpArea", new BigDecimal("1000"));
        propertyDetails.put("plotArea", new BigDecimal("3000"));
        WaterDemandResult result = calculate(property("Hospital", propertyDetails),
                Arrays.asList(hospitalNorm()));

        assertEquals(new BigDecimal("30"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("340"), result.getStandardTotalLpcd());
        assertEquals(new BigDecimal("250"), result.getPotableLpcd());
        assertEquals(new BigDecimal("250"), result.getChosenLpcd());
        assertEquals(new BigDecimal("18150.00"), result.getTotalWaterDemand());
        assertTrue(result.getPotableThresholdApplied());
    }

    @Test
    void hospitalNormSwitchesToPotableLpcdAboveOneHundredBedsAndThreshold() {
        Map<String, Object> propertyDetails = details("C-4");
        propertyDetails.put("questions", Arrays.asList(questionAnswer("numberOfBeds", "101")));
        propertyDetails.put("builtUpArea", new BigDecimal("1000"));
        propertyDetails.put("plotArea", new BigDecimal("3000"));
        WaterDemandResult result = calculate(property("Hospital", propertyDetails),
                Arrays.asList(hospitalNorm()));

        assertEquals(new BigDecimal("101"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("450"), result.getStandardTotalLpcd());
        assertEquals(new BigDecimal("360"), result.getPotableLpcd());
        assertEquals(new BigDecimal("360"), result.getChosenLpcd());
        assertEquals(new BigDecimal("73326.00"), result.getTotalWaterDemand());
        assertTrue(result.getPotableThresholdApplied());
    }

    @Test
    void nursingHomeUsesOneBedPerSixtySquareMetresAsMinimum() {
        Map<String, Object> propertyDetails = details("C-4");
        propertyDetails.put("questions", Arrays.asList(questionAnswer("numberOfBeds", "10")));
        propertyDetails.put("builtUpArea", new BigDecimal("1200"));
        propertyDetails.put("plotArea", new BigDecimal("2000"));
        WaterDemandResult result = calculate(property("NursingHome", propertyDetails),
                Arrays.asList(hospitalNorm()));
        // Nursing home minimum is 1 bed / 60 sqm: 1,200 / 60 = 20 beds.
        assertEquals(new BigDecimal("20"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("10"), result.getContingencyPercentage());
        assertEquals(new BigDecimal("6000"), result.getOpdPotableDemand());
    }

    @Test
    void ddaFlatPropertyIfcOverrideIsZeroWithoutChangingOccupancyNorm() {
        Map<String, Object> propertyDetails = details("A-1");
        propertyDetails.put("questions", Arrays.asList(questionAnswer("numberOfDwellingUnits", "2")));
        propertyDetails.put("ifcOverride", "ZERO");
        WaterDemandResult result = calculate(property("DDAFlat", propertyDetails),
                Arrays.asList(norm("A-1", "RESIDENTIAL", "total_du * 5", 225, 180, 45, "TOTAL_WATER_ALWAYS")));

        assertEquals(new BigDecimal("10"), result.getCalculatedOccupancy());
        assertEquals(new BigDecimal("0"), result.getTotalWaterDemand());
        assertEquals("ZERO", result.getCalculationBasisApplied());
        assertEquals("Unit test zero IFC override.", result.getIfcOverrideReason());
    }

    @Test
    void legacyGroupDNormIsRejectedInsteadOfUsingStaleStrategy() {
        Map<String, Object> legacyAssemblyNorm = norm("D-1", "ASSEMBLY", "built_up_area / 1.5",
                15, 5, 10, "POTABLE_ONLY_IF_OVER_12500");
        Map<String, Object> propertyDetails = details("D-1");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> calculate(property("AssemblyHall", propertyDetails), Arrays.asList(legacyAssemblyNorm)));

        assertTrue(exception.getMessage().contains("Supported revised norms are A-1, B-1..B-4 and C-1..C-4"));
    }

    @Test
    void missingRevisedNormMasterFailsClosedEvenIfLegacyMasterExists() {
        Map<String, Object> masterData = new HashMap<>();
        masterData.put("WaterDemandNorms", Arrays.asList(
                norm("D-1", "ASSEMBLY", "built_up_area / 1.5", 15, 5, 10, "TOTAL_WATER_ALWAYS")));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> calculator.calculateAverageWaterDemand(property("AssemblyHall", details("D-1")), masterData));

        assertTrue(exception.getMessage().contains("WaterDemandNorms2 master is missing"));
    }

    private WaterDemandResult calculate(Property property, List<Map<String, Object>> norms) {
        Map<String, Object> masterData = new HashMap<>();
        masterData.put("WaterDemandNorms2", norms);

        Map<String, Object> selectedDetails = property.getAdditionalDetails() instanceof Map
                ? (Map<String, Object>) property.getAdditionalDetails() : new HashMap<String, Object>();
        String normCode = selectedDetails.get("demandNormCode").toString();
        Map<String, Object> matchedNorm = norms.stream()
                .filter(n -> normCode.equalsIgnoreCase(String.valueOf(n.get("subCategoryCode"))))
                .findFirst().orElseThrow(IllegalStateException::new);
        String propertyCategory = selectedDetails.get("propertyCategory") == null
                ? String.valueOf(matchedNorm.get("parentUsageCode"))
                : String.valueOf(selectedDetails.get("propertyCategory"));
        Map<String, Object> propertyTypeEntry = new LinkedHashMap<>();
        propertyTypeEntry.put("name", property.getPropertyType());
        propertyTypeEntry.put("code", property.getPropertyType());
        propertyTypeEntry.put("propertyCategory", propertyCategory);
        propertyTypeEntry.put("categoryType", Arrays.asList(
                "RESIDENTIAL".equalsIgnoreCase(propertyCategory) ? "Domestic" : "Non-Domestic"));
        propertyTypeEntry.put("demandNormCode", normCode);
        propertyTypeEntry.put("active", true);
        List<String> configuredQuestionCodes = new java.util.ArrayList<>();
        Object rawQuestionAnswers = selectedDetails.get("questions");
        if (rawQuestionAnswers instanceof Iterable<?>) {
            for (Object rawQuestionAnswer : (Iterable<?>) rawQuestionAnswers) {
                if (rawQuestionAnswer instanceof Map<?, ?>) {
                    Object code = ((Map<?, ?>) rawQuestionAnswer).get("questionCode");
                    if (code != null) configuredQuestionCodes.add(code.toString());
                }
            }
        }
        propertyTypeEntry.put("questions", configuredQuestionCodes);
        if (selectedDetails.get("demandNormSubType") != null) {
            propertyTypeEntry.put("demandNormSubType", selectedDetails.get("demandNormSubType"));
        } else if ("B-2".equals(normCode)) {
            propertyTypeEntry.put("demandNormSubType", "MERCANTILE");
        }
        if (selectedDetails.get("ifcOverride") != null) {
            propertyTypeEntry.put("ifcOverride", selectedDetails.get("ifcOverride"));
            propertyTypeEntry.put("ifcOverrideReason", "Unit test zero IFC override.");
        }
        masterData.put("PropertyType2", Arrays.asList(propertyTypeEntry));

        List<Map<String, Object>> categories = new java.util.ArrayList<>();
        categories.add(category("Residential", "RESIDENTIAL"));
        categories.add(category("Commercial", "COMMERCIAL"));
        categories.add(category("Institutional", "INSTITUTIONAL"));
        categories.add(category("Industrial", "INDUSTRIAL"));
        masterData.put("PropertyCategory", categories);
        return calculator.calculateAverageWaterDemand(property, masterData);
    }

    private Map<String, Object> category(String name, String code) {
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("name", name);
        category.put("code", code);
        category.put("active", true);
        return category;
    }

    private Property property(String propertyType, Map<String, Object> details) {
        Property property = new Property();
        property.setPropertyType(propertyType);
        property.setAdditionalDetails(details);
        return property;
    }

    private Map<String, Object> details(String normCode) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("demandNormCode", normCode);
        return details;
    }

    private Map<String, Object> questionAnswer(String questionCode, String answer) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("questionCode", questionCode);
        question.put("answer", answer);
        return question;
    }

    private Map<String, Object> norm(String code, String parent, String occupancyFormula,
            int totalLpcd, int potableLpcd, int nonPotableLpcd, String ifcBasis) {
        Map<String, Object> norm = normWithFormula(code, parent, occupancyFormula,
                totalLpcd, potableLpcd, nonPotableLpcd, ifcBasis);
        norm.put("totalLpcd", new BigDecimal(String.valueOf(totalLpcd)));
        norm.put("potableLpcd", new BigDecimal(String.valueOf(potableLpcd)));
        norm.put("nonPotableLpcd", new BigDecimal(String.valueOf(nonPotableLpcd)));
        norm.remove("totalLpcdFormula");
        norm.remove("potableLpcdFormula");
        return norm;
    }

    private Map<String, Object> normWithFormula(String code, String parent, String occupancyFormula,
            int totalLpcd, int potableLpcd, int nonPotableLpcd, String ifcBasis) {
        Map<String, Object> norm = new LinkedHashMap<>();
        norm.put("subCategoryCode", code);
        norm.put("parentUsageCode", parent);
        norm.put("name", "Norm " + code);
        norm.put("minOccupancyFormula", occupancyFormula);
        norm.put("totalLpcd", new BigDecimal(String.valueOf(totalLpcd)));
        norm.put("potableLpcd", new BigDecimal(String.valueOf(potableLpcd)));
        norm.put("nonPotableLpcd", new BigDecimal(String.valueOf(nonPotableLpcd)));
        norm.put("contingencyPercentage", BigDecimal.ZERO);
        norm.put("ifcCalculationBasis", ifcBasis);
        norm.put("ifcThresholdLpd", new BigDecimal("12500"));
        norm.put("parentUsageCodes", Arrays.asList(parent));
        norm.put("isActive", true);
        return norm;
    }

    private Map<String, Object> hospitalNorm() {
        Map<String, Object> norm = new LinkedHashMap<>();
        norm.put("subCategoryCode", "C-4");
        norm.put("parentUsageCode", "INSTITUTIONAL");
        norm.put("name", "Nursing Homes / Hospitals / Sanatoria");
        norm.put("minOccupancyFormula", "sanctioned_beds");
        norm.put("totalLpcd", null);
        norm.put("potableLpcd", null);
        norm.put("nonPotableLpcd", new BigDecimal("90"));
        norm.put("totalLpcdFormula", "IF(effective_beds <= 100, 340, 450)");
        norm.put("potableLpcdFormula", "IF(effective_beds <= 100, 250, 360)");
        norm.put("contingencyPercentage", new BigDecimal("10"));
        norm.put("contingencyBasis", "TOTAL_CALCULATED_WATER_DEMAND_INCLUDING_OPD");
        norm.put("ifcCalculationBasis", "POTABLE_ONLY_IF_OVER_12500");
        norm.put("ifcThresholdLpd", new BigDecimal("12500"));
        norm.put("parentUsageCodes", Arrays.asList("INSTITUTIONAL"));
        Map<String, Object> plotRules = new LinkedHashMap<>();
        plotRules.put("HOSPITAL", "plot_area >= 2500");
        plotRules.put("NURSING_HOME", "plot_area <= 2500");
        norm.put("facilityPlotAreaRules", plotRules);
        norm.put("isActive", true);
        return norm;
    }
}
