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


    @Test
    void shouldMultiplyNoWaterMonthlySewerageChargeByBillingMonths() {
        SewerageCalculationContext context =
                SewerageCalculationContext.builder()
                        .waterConnectionAvailable(false)
                        .sewerConnectionAvailable(true)
                        .consumerCategory("DOMESTIC")
                        .builtUpAreaSqm(bd("100"))
                        .billingMonths(3)
                        .build();

        DJBMonthlySewerageRule flat = regularRule(
                "NO_WATER_CAT_I_SMALL", "DOMESTIC", "FIXED_MONTHLY", null, 0.0, 200, 150);

        SewerageCalculationResult result = service.calculate(
                context, Arrays.asList(flat), Arrays.asList());

        assertEquals(bd("450.00"), result.getRegularSewerageCharge());
        assertEquals(bd("450.00"), result.getTotalSewerageCharge());
    }

    @Test
    void shouldMultiplyFixedAdditionalSewerageByBillingMonths() {
        SewerageCalculationContext context =
                SewerageCalculationContext.builder()
                        .waterVolumetricCharge(bd("100"))
                        .waterConnectionAvailable(true)
                        .sewerConnectionAvailable(true)
                        .additionalWaterSource(true)
                        .propertyUsage("HOTEL_GUEST_HOUSE")
                        .numberOfRooms(40)
                        .billingMonths(3)
                        .build();

        DJBAdditionalSewerageCharge hotel =
                additionalFixed("HOTEL_0_50", "HOTEL_GUEST_HOUSE", 0, 50, 2000);

        SewerageCalculationResult result = service.calculate(
                context, Arrays.asList(waterConnected), Arrays.asList(hotel));

        assertEquals(bd("60.00"), result.getRegularSewerageCharge());
        assertEquals(bd("6000.00"), result.getAdditionalSewerageCharge());
        assertEquals(bd("6060.00"), result.getTotalSewerageCharge());
    }

    @Test
    void shouldMultiplyBlockBasedAdditionalSewerageByBillingMonths() {
        SewerageCalculationContext context =
                SewerageCalculationContext.builder()
                        .waterVolumetricCharge(bd("100"))
                        .waterConnectionAvailable(true)
                        .sewerConnectionAvailable(true)
                        .additionalWaterSource(true)
                        .propertyUsage("HOTEL_GUEST_HOUSE")
                        .numberOfRooms(101)
                        .billingMonths(4)
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

        SewerageCalculationResult result = service.calculate(
                context, Arrays.asList(waterConnected), Arrays.asList(hotel));

        assertEquals(bd("60.00"), result.getRegularSewerageCharge());
        assertEquals(bd("50000.00"), result.getAdditionalSewerageCharge());
        assertEquals(bd("50060.00"), result.getTotalSewerageCharge());
    }


    @Test
    void shouldProrateMonthlyAdditionalSewerageUsingFractionalThirtyDayFactor() {
        SewerageCalculationContext context = SewerageCalculationContext.builder()
                .waterVolumetricCharge(bd("100"))
                .waterConnectionAvailable(true)
                .sewerConnectionAvailable(true)
                .additionalWaterSource(true)
                .propertyUsage("HOTEL_GUEST_HOUSE")
                .numberOfRooms(40)
                .billingMonths(2)
                .billingPeriodMonths(bd("2.7"))
                .build();
        DJBAdditionalSewerageCharge hotel = additionalFixed("HOTEL_0_50", "HOTEL_GUEST_HOUSE", 0, 50, 2000);
        SewerageCalculationResult result = service.calculate(context, Arrays.asList(waterConnected), Arrays.asList(hotel));
        assertEquals(bd("5400.00"), result.getAdditionalSewerageCharge());
    }

    @Test
    void shouldChargeBanquetHallPerFunctionalSiteNotPerMonth() {
        SewerageCalculationContext context = SewerageCalculationContext.builder()
                .waterConnectionAvailable(true)
                .sewerConnectionAvailable(true)
                .additionalWaterSource(true)
                .propertyUsage("BANQUET_HALL")
                .billingMonths(2)
                .billingPeriodMonths(bd("2.7"))
                .functionalSiteCount(2)
                .build();
        DJBAdditionalSewerageCharge banquet = new DJBAdditionalSewerageCharge();
        banquet.setCode("BANQUET_HALL");
        banquet.setPropertyUsage("BANQUET_HALL");
        banquet.setChargeType("FIXED_MONTHLY");
        banquet.setAmount(bd("10000"));
        banquet.setUnit("FUNCTIONAL_SITE");
        banquet.setActive(true);
        SewerageCalculationResult result = service.calculate(context, Arrays.asList(), Arrays.asList(banquet));
        assertEquals(bd("20000.00"), result.getAdditionalSewerageCharge());
    }

    @Test
    void shouldSelectHigherHospitalBandAtSharedBoundary() {
        SewerageCalculationContext context = SewerageCalculationContext.builder()
                .waterConnectionAvailable(true)
                .sewerConnectionAvailable(true)
                .additionalWaterSource(true)
                .propertyUsage("HOSPITAL")
                .numberOfBeds(25)
                .build();
        DJBAdditionalSewerageCharge first = new DJBAdditionalSewerageCharge();
        first.setCode("HOSPITAL_0_25"); first.setPropertyUsage("HOSPITAL");
        first.setFromBeds(0); first.setToBeds(25); first.setChargeType("FIXED_MONTHLY");
        first.setAmount(bd("1500")); first.setActive(true);
        DJBAdditionalSewerageCharge second = new DJBAdditionalSewerageCharge();
        second.setCode("HOSPITAL_25_50"); second.setPropertyUsage("HOSPITAL");
        second.setFromBeds(25); second.setToBeds(50); second.setChargeType("FIXED_MONTHLY");
        second.setAmount(bd("2000")); second.setActive(true);
        SewerageCalculationResult result = service.calculate(context, Arrays.asList(), Arrays.asList(first, second));
        assertEquals(bd("2000.00"), result.getAdditionalSewerageCharge());
    }


    @Test
    void shouldApplyDomesticNoWaterSewerageAbove200OnlyWhenAreaIsGreaterThan200() {
        DJBMonthlySewerageRule above200 = regularRule(
                "DOMESTIC_ABOVE_200", "DOMESTIC", "FIXED_MONTHLY", null, 200.0, null, 450);
        above200.setMinBuiltUpAreaExclusive(true);

        SewerageCalculationContext exactly200 = SewerageCalculationContext.builder()
                .waterConnectionAvailable(false).sewerConnectionAvailable(true)
                .consumerCategory("DOMESTIC").builtUpAreaSqm(bd("200")).build();
        SewerageCalculationContext above200Context = SewerageCalculationContext.builder()
                .waterConnectionAvailable(false).sewerConnectionAvailable(true)
                .consumerCategory("DOMESTIC").builtUpAreaSqm(bd("200.01")).build();

        SewerageCalculationResult atBoundary = service.calculate(exactly200,
                Arrays.asList(domesticUpTo200, above200), Arrays.asList());
        SewerageCalculationResult aboveBoundary = service.calculate(above200Context,
                Arrays.asList(domesticUpTo200, above200), Arrays.asList());

        assertEquals(bd("150.00"), atBoundary.getRegularSewerageCharge());
        assertEquals(bd("450.00"), aboveBoundary.getRegularSewerageCharge());
    }

    @Test
    void shouldSupportExplicitFixedPerSiteBanquetChargeType() {
        SewerageCalculationContext context = SewerageCalculationContext.builder()
                .waterConnectionAvailable(true).sewerConnectionAvailable(true)
                .additionalWaterSource(true).propertyUsage("BANQUET_HALL")
                .functionalSiteCount(2).billingPeriodMonths(bd("2.7")).build();
        DJBAdditionalSewerageCharge banquet = new DJBAdditionalSewerageCharge();
        banquet.setCode("BANQUET_HALL");
        banquet.setPropertyUsage("BANQUET_HALL");
        banquet.setChargeType("FIXED_PER_SITE");
        banquet.setUnit("FUNCTIONAL_SITE");
        banquet.setAmount(bd("10000"));
        banquet.setActive(true);

        SewerageCalculationResult result = service.calculate(context,
                Arrays.asList(waterConnected), Arrays.asList(banquet));

        assertEquals(bd("20000.00"), result.getAdditionalSewerageCharge());
    }

    @Test
    void shouldRejectBanquetChargeWhenFunctionalSiteCountIsMissing() {
        SewerageCalculationContext context = SewerageCalculationContext.builder()
                .waterConnectionAvailable(true).sewerConnectionAvailable(true)
                .additionalWaterSource(true).propertyUsage("BANQUET_HALL")
                .billingPeriodMonths(bd("2.7")).build();
        DJBAdditionalSewerageCharge banquet = new DJBAdditionalSewerageCharge();
        banquet.setCode("BANQUET_HALL");
        banquet.setPropertyUsage("BANQUET_HALL");
        banquet.setChargeType("FIXED_PER_SITE");
        banquet.setUnit("FUNCTIONAL_SITE");
        banquet.setAmount(bd("10000"));
        banquet.setActive(true);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.calculate(context, Arrays.asList(waterConnected), Arrays.asList(banquet)));
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
