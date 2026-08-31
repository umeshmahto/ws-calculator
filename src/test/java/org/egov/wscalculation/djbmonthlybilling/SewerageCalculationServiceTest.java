package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Arrays;

import org.egov.wscalculation.djbmonthlybilling.model.master.DJBAdditionalSewerageCharge;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlySewerageRule;
import org.egov.wscalculation.djbmonthlybilling.service.SewerageCalculationService;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SewerageCalculationServiceTest {

    private SewerageCalculationService service;
    private DJBMonthlySewerageRule waterConnected;
    private DJBMonthlySewerageRule domesticUpTo200;
    private DJBMonthlySewerageRule commercialUpTo500;

    @BeforeEach
    void setUp() {
        service = new SewerageCalculationService();

        waterConnected = regularRule(
                "WATER_CONNECTED", "ALL",
                "PERCENTAGE_OF_WATER_VOLUMETRIC_CHARGE",
                60, null, null, null);

        domesticUpTo200 = regularRule(
                "DOMESTIC_UP_TO_200", "DOMESTIC",
                "FIXED_MONTHLY",
                null, 0.0, 200, 150);

        commercialUpTo500 = regularRule(
                "COMMERCIAL_UP_TO_500", "COMMERCIAL",
                "FIXED_MONTHLY",
                null, 0.0, 500, 1000);
    }

    @Test
    void shouldCalculate60PercentOfWaterVolumetricCharge() {
        SewerageCalculationContext context =
                SewerageCalculationContext.builder()
                        .waterVolumetricCharge(bd("237.20"))
                        .waterConnectionAvailable(true)
                        .sewerConnectionAvailable(true)
                        .build();

        SewerageCalculationResult result =
                service.calculate(context,
                        Arrays.asList(waterConnected),
                        Arrays.asList());

        assertEquals(bd("142.32"), result.getRegularSewerageCharge());
        assertEquals(bd("0.00"), result.getAdditionalSewerageCharge());
        assertEquals(bd("142.32"), result.getTotalSewerageCharge());
    }

    @Test
    void shouldReturnZeroWhenNoSewerConnection() {
        SewerageCalculationContext context =
                SewerageCalculationContext.builder()
                        .waterVolumetricCharge(bd("237.20"))
                        .waterConnectionAvailable(true)
                        .sewerConnectionAvailable(false)
                        .build();

        SewerageCalculationResult result =
                service.calculate(context,
                        Arrays.asList(waterConnected),
                        Arrays.asList());

        assertEquals(bd("0.00"), result.getTotalSewerageCharge());
    }

    @Test
    void shouldCalculateNoWaterDomesticFixedCharge() {
        SewerageCalculationContext context =
                SewerageCalculationContext.builder()
                        .waterConnectionAvailable(false)
                        .sewerConnectionAvailable(true)
                        .consumerCategory("DOMESTIC")
                        .builtUpAreaSqm(bd("150"))
                        .build();

        SewerageCalculationResult result =
                service.calculate(context,
                        Arrays.asList(domesticUpTo200),
                        Arrays.asList());

        assertEquals(bd("150.00"), result.getRegularSewerageCharge());
        assertEquals(bd("150.00"), result.getTotalSewerageCharge());
    }

    @Test
    void shouldCalculateNoWaterCommercialFixedCharge() {
        SewerageCalculationContext context =
                SewerageCalculationContext.builder()
                        .waterConnectionAvailable(false)
                        .sewerConnectionAvailable(true)
                        .consumerCategory("COMMERCIAL")
                        .builtUpAreaSqm(bd("400"))
                        .build();

        SewerageCalculationResult result =
                service.calculate(context,
                        Arrays.asList(commercialUpTo500),
                        Arrays.asList());

        assertEquals(bd("1000.00"), result.getRegularSewerageCharge());
    }

    @Test
    void shouldCalculateHotelAdditionalCharge() {
        SewerageCalculationContext context =
                SewerageCalculationContext.builder()
                        .waterConnectionAvailable(true)
                        .sewerConnectionAvailable(true)
                        .waterVolumetricCharge(bd("100"))
                        .additionalWaterSource(true)
                        .propertyUsage("HOTEL_GUEST_HOUSE")
                        .numberOfRooms(40)
                        .build();

        DJBAdditionalSewerageCharge hotel =
                additionalFixed("HOTEL_0_50",
                        "HOTEL_GUEST_HOUSE", 0, 50, 2000);

        SewerageCalculationResult result =
                service.calculate(context,
                        Arrays.asList(waterConnected),
                        Arrays.asList(hotel));

        assertEquals(bd("60.00"), result.getRegularSewerageCharge());
        assertEquals(bd("2000.00"), result.getAdditionalSewerageCharge());
        assertEquals(bd("2060.00"), result.getTotalSewerageCharge());
    }

    @Test
    void shouldCalculateHotel101RoomsAsOneAdditionalBlock() {
        SewerageCalculationContext context =
                SewerageCalculationContext.builder()
                        .waterConnectionAvailable(true)
                        .sewerConnectionAvailable(true)
                        .waterVolumetricCharge(bd("100"))
                        .additionalWaterSource(true)
                        .propertyUsage("HOTEL_GUEST_HOUSE")
                        .numberOfRooms(101)
                        .build();

        DJBAdditionalSewerageCharge hotel = new DJBAdditionalSewerageCharge();
        hotel.setCode("HOTEL_ABOVE_100");
        hotel.setPropertyUsage("HOTEL_GUEST_HOUSE");
        hotel.setFromRooms(101);
        hotel.setChargeType("BASE_PLUS_PER_BLOCK");
        hotel.setBaseAmount(bd("10000"));
        hotel.setBlockAmount(bd("2500"));
        hotel.setBlockSize(50);
        hotel.setActive(true);

        SewerageCalculationResult result =
                service.calculate(context, Arrays.asList(waterConnected),
                        Arrays.asList(hotel));

        assertEquals(bd("12500.00"), result.getAdditionalSewerageCharge());
    }

    @Test
    void shouldCalculateHotelAdditionalBlockAbove100Rooms() {
        SewerageCalculationContext context =
                SewerageCalculationContext.builder()
                        .waterConnectionAvailable(true)
                        .sewerConnectionAvailable(true)
                        .waterVolumetricCharge(bd("100"))
                        .additionalWaterSource(true)
                        .propertyUsage("HOTEL_GUEST_HOUSE")
                        .numberOfRooms(101)
                        .build();

        DJBAdditionalSewerageCharge hotel =
                new DJBAdditionalSewerageCharge();
        hotel.setCode("HOTEL_ABOVE_100");
        hotel.setPropertyUsage("HOTEL_GUEST_HOUSE");
        hotel.setFromRooms(101);
        hotel.setChargeType("BASE_PLUS_PER_BLOCK");
        hotel.setBaseAmount(bd("10000"));
        hotel.setBlockAmount(bd("2500"));
        hotel.setBlockSize(50);
        hotel.setActive(true);

        SewerageCalculationResult result =
                service.calculate(context,
                        Arrays.asList(waterConnected),
                        Arrays.asList(hotel));

        assertEquals(bd("12500.00"),
                result.getAdditionalSewerageCharge());
    }

    private DJBMonthlySewerageRule regularRule(
            String code,
            String category,
            String chargeType,
            Integer percentage,
            Double minArea,
            Integer maxArea,
            Integer amount) {

        DJBMonthlySewerageRule rule =
                new DJBMonthlySewerageRule();

        rule.setCode(code);
        rule.setCategory(category);
        rule.setChargeType(chargeType);
        rule.setActive(true);

        if (percentage != null) {
            rule.setPercentage(bd(String.valueOf(percentage)));
        }

        if (minArea != null && minArea >= 0) {
            rule.setMinBuiltUpAreaSqm(bd(String.valueOf(minArea)));
        }

        if (maxArea != null) {
            rule.setMaxBuiltUpAreaSqm(bd(String.valueOf(maxArea)));
        }

        if (amount != null) {
            rule.setAmount(bd(String.valueOf(amount)));
        }

        return rule;
    }

    private DJBAdditionalSewerageCharge additionalFixed(
            String code,
            String propertyUsage,
            int fromRooms,
            int toRooms,
            int amount) {

        DJBAdditionalSewerageCharge rule =
                new DJBAdditionalSewerageCharge();

        rule.setCode(code);
        rule.setPropertyUsage(propertyUsage);
        rule.setFromRooms(fromRooms);
        rule.setToRooms(toRooms);
        rule.setChargeType("FIXED_MONTHLY");
        rule.setAmount(bd(String.valueOf(amount)));
        rule.setActive(true);
        return rule;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
