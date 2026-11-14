package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Arrays;

import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariff;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariffSlab;
import org.egov.wscalculation.djbmonthlybilling.service.TariffCalculationService;
import org.egov.wscalculation.djbmonthlybilling.service.dto.TariffCalculationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TariffCalculationServiceTest {

    private TariffCalculationService service;
    private DJBMonthlyWaterTariff domestic;
    private DJBMonthlyWaterTariff commercial;

    @BeforeEach
    void setUp() {
        service = new TariffCalculationService();
        domestic = tariff("DOMESTIC", slab(0, 20, 5.27, 146.41), slab(20, 30, 26.36, 219.62),
                slab(30, null, 43.93, 292.82));
        commercial = tariff("COMMERCIAL", slab(0, 6, 17.57, 146.41), slab(6, 15, 26.35, 292.82),
                slab(15, 25, 35.14, 585.64), slab(25, 50, 87.85, 1024.87),
                slab(50, 100, 140.56, 1171.28), slab(100, null, 175.69, 1317.69));
    }

    @Test
    void shouldCalculateDomestic20KlAtFirstSlabOnly() {
        TariffCalculationResult result = service.calculate(bd("20"), "DOMESTIC", Arrays.asList(domestic));

        assertEquals(bd("105.40"), result.getWaterVolumetricCharge());
        assertEquals(bd("146.41"), result.getServiceCharge());
        assertEquals(bd("251.81"), result.getTotalWaterCharge());
        assertEquals(1, result.getSlabCharges().size());
        assertEquals(bd("20.000"), result.getSlabCharges().get(0).getUnits());
    }

    @Test
    void shouldProgressivelyAllocateDomestic25KlAcrossFirstTwoSlabs() {
        TariffCalculationResult result = service.calculate(bd("25"), "DOMESTIC", Arrays.asList(domestic));

        assertEquals(bd("237.20"), result.getWaterVolumetricCharge());
        assertEquals(bd("219.62"), result.getServiceCharge());
        assertEquals(bd("456.82"), result.getTotalWaterCharge());
        assertEquals(2, result.getSlabCharges().size());
        assertEquals(bd("20.000"), result.getSlabCharges().get(0).getUnits());
        assertEquals(bd("5.000"), result.getSlabCharges().get(1).getUnits());
        assertEquals(bd("5.27"), result.getSlabCharges().get(0).getRatePerKl());
        assertEquals(bd("26.36"), result.getSlabCharges().get(1).getRatePerKl());
    }

    @Test
    void shouldProgressivelyAllocateDomestic35KlAcrossAllThreeSlabs() {
        TariffCalculationResult result = service.calculate(bd("35"), "DOMESTIC", Arrays.asList(domestic));

        assertEquals(bd("588.65"), result.getWaterVolumetricCharge());
        assertEquals(bd("292.82"), result.getServiceCharge());
        assertEquals(bd("881.47"), result.getTotalWaterCharge());
        assertEquals(3, result.getSlabCharges().size());
        assertEquals(bd("20.000"), result.getSlabCharges().get(0).getUnits());
        assertEquals(bd("10.000"), result.getSlabCharges().get(1).getUnits());
        assertEquals(bd("5.000"), result.getSlabCharges().get(2).getUnits());
    }

    @Test
    void shouldProgressivelyAllocateDomestic39KlAcrossAllThreeSlabs() {
        TariffCalculationResult result = service.calculate(bd("39"), "DOMESTIC", Arrays.asList(domestic));

        assertEquals(bd("764.37"), result.getWaterVolumetricCharge());
        assertEquals(bd("292.82"), result.getServiceCharge());
        assertEquals(bd("1057.19"), result.getTotalWaterCharge());
        assertEquals(3, result.getSlabCharges().size());
        assertEquals(bd("20.000"), result.getSlabCharges().get(0).getUnits());
        assertEquals(bd("10.000"), result.getSlabCharges().get(1).getUnits());
        assertEquals(bd("9.000"), result.getSlabCharges().get(2).getUnits());
    }

    @Test
    void shouldMatchDjbOuccReferenceFor114KlOver81Days() {
        // The OUCC header period is 08-Jun-2026 to 28-Aug-2026: 81 elapsed days.
        BigDecimal periodMonths = bd("81").divide(bd("30"), 10, java.math.RoundingMode.HALF_UP);
        TariffCalculationResult result = service.calculate(bd("114"), "DOMESTIC", Arrays.asList(domestic),
                periodMonths, 2L, epoch("2026-06-08"), epoch("2026-08-28"));

        assertEquals(bd("42.222"), result.getMonthlyConsumption());
        assertEquals(bd("2.7"), result.getBillingPeriodMonths());
        assertEquals(bd("284.58"), result.getSlabCharges().get(0).getCharge());
        assertEquals(bd("54.000"), result.getSlabCharges().get(0).getUnits());
        assertEquals(bd("711.72"), result.getSlabCharges().get(1).getCharge());
        assertEquals(bd("27.000"), result.getSlabCharges().get(1).getUnits());
        assertEquals(bd("1449.69"), result.getSlabCharges().get(2).getCharge());
        assertEquals(bd("33.000"), result.getSlabCharges().get(2).getUnits());
        assertEquals(bd("2445.99"), result.getWaterVolumetricCharge());
        assertEquals(bd("790.61"), result.getServiceCharge());
        assertEquals(bd("3236.60"), result.getTotalWaterCharge());
    }

    @Test
    void shouldCalculate41DayPeriodUsingThirtyDayProration() {
        BigDecimal periodMonths = bd("41").divide(bd("30"), 10, java.math.RoundingMode.HALF_UP);
        TariffCalculationResult result = service.calculate(bd("45"), "DOMESTIC", Arrays.asList(domestic),
                periodMonths, 1L, epoch("2026-04-01"), epoch("2026-05-12"));

        assertEquals(bd("32.927"), result.getMonthlyConsumption());
        assertEquals(bd("27.333"), result.getSlabCharges().get(0).getUnits());
        assertEquals(bd("13.667"), result.getSlabCharges().get(1).getUnits());
        assertEquals(bd("4.000"), result.getSlabCharges().get(2).getUnits());
        assertEquals(bd("680.02"), result.getWaterVolumetricCharge());
        assertEquals(bd("400.19"), result.getServiceCharge());
        assertEquals(bd("1080.21"), result.getTotalWaterCharge());
    }

    @Test
    void shouldApplyMonthlyAverageAsOneTariffMonth() {
        // Average/provisional billingConsumption is already a monthly value.
        TariffCalculationResult result = service.calculate(bd("25"), "DOMESTIC", Arrays.asList(domestic));
        assertEquals(bd("1"), result.getBillingPeriodMonths());
        assertEquals(bd("25.000"), result.getMonthlyConsumption());
        assertEquals(bd("237.20"), result.getWaterVolumetricCharge());
    }

    @Test
    void shouldUseWholeMonthFactorForLegacyThreeMonthCall() {
        TariffCalculationResult result = service.calculate(bd("60"), "DOMESTIC", Arrays.asList(domestic), 3L);

        assertEquals(bd("60.000"), result.getConsumption());
        assertEquals(bd("20.000"), result.getMonthlyConsumption());
        assertEquals(Long.valueOf(3L), result.getBillingMonths());
        assertEquals(bd("3"), result.getBillingPeriodMonths());
        assertEquals(bd("316.20"), result.getWaterVolumetricCharge());
        assertEquals(bd("439.23"), result.getServiceCharge());
        assertEquals(bd("755.43"), result.getTotalWaterCharge());
        assertEquals(1, result.getSlabCharges().size());
        assertEquals(bd("60.000"), result.getSlabCharges().get(0).getUnits());
    }

    @Test
    void shouldAllocateSixMonthsOfConsumptionProgressively() {
        TariffCalculationResult result = service.calculate(bd("180"), "DOMESTIC", Arrays.asList(domestic), 6L);

        assertEquals(bd("30.000"), result.getMonthlyConsumption());
        assertEquals(bd("2214.00"), result.getWaterVolumetricCharge());
        assertEquals(bd("1317.72"), result.getServiceCharge());
        assertEquals(bd("3531.72"), result.getTotalWaterCharge());
        assertEquals(2, result.getSlabCharges().size());
        assertEquals(bd("120.000"), result.getSlabCharges().get(0).getUnits());
        assertEquals(bd("60.000"), result.getSlabCharges().get(1).getUnits());
    }

    @Test
    void shouldNormalizeResidentialToDomestic() {
        TariffCalculationResult result = service.calculate(bd("20"), "residential", Arrays.asList(domestic));
        assertEquals("DOMESTIC", result.getCategory());
    }

    @Test
    void shouldNormalizeBusinessToCommercial() {
        TariffCalculationResult result = service.calculate(bd("10"), "business", Arrays.asList(commercial));
        assertEquals("COMMERCIAL", result.getCategory());
        assertEquals(bd("210.82"), result.getWaterVolumetricCharge());
        assertEquals(bd("292.82"), result.getServiceCharge());
        assertEquals(bd("503.64"), result.getTotalWaterCharge());
    }

    @Test
    void shouldIgnoreInactiveTariff() {
        domestic.setActive(false);
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(bd("20"), "DOMESTIC", Arrays.asList(domestic)));
    }

    @Test
    void shouldRejectNegativeConsumption() {
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(bd("-1"), "DOMESTIC", Arrays.asList(domestic)));
    }

    @Test
    void shouldRejectMissingConsumption() {
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(null, "DOMESTIC", Arrays.asList(domestic)));
    }

    @Test
    void shouldRejectMissingTariff() {
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(bd("20"), "DOMESTIC", Arrays.asList()));
    }

    @Test
    void shouldRejectInvalidSlabRange() {
        DJBMonthlyWaterTariff invalid = tariff("DOMESTIC", slab(0, 20, 5.27, 146.41),
                slab(20, 20, 26.36, 219.62), slab(20, null, 43.93, 292.82));
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(bd("25"), "DOMESTIC", Arrays.asList(invalid)));
    }

    @Test
    void shouldSelectTariffByEffectiveDate() {
        DJBMonthlyWaterTariff oldTariff = tariff("DOMESTIC", slab(0, 20, 5.27, 146.41),
                slab(20, 30, 26.36, 219.62), slab(30, null, 43.93, 292.82));
        oldTariff.setEffectiveFrom("2026-01-01");
        oldTariff.setEffectiveTo("2026-06-30");

        DJBMonthlyWaterTariff newTariff = tariff("DOMESTIC", slab(0, 20, 6.00, 150.00),
                slab(20, 30, 26.36, 219.62), slab(30, null, 43.93, 292.82));
        newTariff.setId("DOMESTIC_NEW");
        newTariff.setEffectiveFrom("2026-07-01");

        TariffCalculationResult result = service.calculate(bd("10"), "DOMESTIC", Arrays.asList(oldTariff, newTariff),
                1L, epoch("2026-08-01"));
        assertEquals("DOMESTIC_NEW", result.getTariffId());
        assertEquals(0, result.getSlabCharges().get(0).getRatePerKl().compareTo(bd("6.00")));
    }


    @Test
    void shouldResolveEffectiveTariffUsingIndiaLocalDateAtMidnight() {
        DJBMonthlyWaterTariff tariff = tariff("DOMESTIC", slab(0, 20, 6.00, 150.00),
                slab(20, 30, 26.36, 219.62), slab(30, null, 43.93, 292.82));
        tariff.setEffectiveFrom("2026-08-01");
        tariff.setEffectiveTo(null);

        // 2026-08-01 00:00 IST is still 2026-07-31 in UTC. DJB effective
        // dates are local dates, so the tariff effective on 1-Aug must apply.
        TariffCalculationResult result = service.calculate(bd("10"), "DOMESTIC",
                Arrays.asList(tariff), 1L, epoch("2026-08-01"));
        assertEquals("DOMESTIC", result.getCategory());
        assertEquals(0, result.getSlabCharges().get(0).getRatePerKl().compareTo(bd("6.00")));
    }

    @Test
    void shouldRejectBillingDateWithNoEffectiveTariff() {
        DJBMonthlyWaterTariff oldTariff = tariff("DOMESTIC", slab(0, 20, 5.27, 146.41),
                slab(20, 30, 26.36, 219.62), slab(30, null, 43.93, 292.82));
        oldTariff.setEffectiveFrom("2026-01-01");
        oldTariff.setEffectiveTo("2026-06-30");
        assertThrows(IllegalArgumentException.class, () -> service.calculate(
                bd("10"), "DOMESTIC", Arrays.asList(oldTariff), 1L, epoch("2026-08-01")));
    }

    @Test
    void shouldRejectNonPositiveBillingPeriodFactor() {
        assertThrows(IllegalArgumentException.class, () -> service.calculate(bd("10"), "DOMESTIC",
                Arrays.asList(domestic), BigDecimal.ZERO, 1L, null, null));
    }

    private Long epoch(String date) {
        return ZonedDateTime.parse(date + "T00:00:00+05:30[Asia/Kolkata]")
                .toInstant().toEpochMilli();
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private DJBMonthlyWaterTariff tariff(String category, DJBMonthlyWaterTariffSlab... slabs) {
        DJBMonthlyWaterTariff tariff = new DJBMonthlyWaterTariff();
        tariff.setId(category);
        tariff.setCategory(category);
        tariff.setConnectionType("METERED");
        tariff.setActive(true);
        tariff.setSlabs(Arrays.asList(slabs));
        return tariff;
    }

    private DJBMonthlyWaterTariffSlab slab(int from, Integer to, double rate, double serviceCharge) {
        DJBMonthlyWaterTariffSlab slab = new DJBMonthlyWaterTariffSlab();
        slab.setFrom(BigDecimal.valueOf(from));
        if (to != null) {
            slab.setTo(BigDecimal.valueOf(to));
        }
        slab.setRatePerKl(BigDecimal.valueOf(rate));
        slab.setServiceCharge(BigDecimal.valueOf(serviceCharge));
        return slab;
    }
}
