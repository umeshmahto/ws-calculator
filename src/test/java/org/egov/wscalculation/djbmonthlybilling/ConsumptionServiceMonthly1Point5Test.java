package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;

import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterConnectionActivationDao;
import org.egov.wscalculation.djbmonthlybilling.service.ConsumptionService;
import org.egov.wscalculation.djbmonthlybilling.service.dto.BillingBasisDecision;
import org.egov.wscalculation.djbmonthlybilling.service.dto.ConsumptionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ConsumptionServiceMonthly1Point5Test {

    @Mock
    private WaterBillingCycleDao billingCycleDao;

    @Mock
    private WaterConnectionActivationDao activationDao;

    private ConsumptionService service;
    private DJBMonthlyBillingRule rule;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        service = new ConsumptionService(billingCycleDao, activationDao);
        rule = new DJBMonthlyBillingRule();
        rule.setHighConsumptionMultiplier(1.5d);
        rule.setHighConsumptionThresholdKl(20);
    }

    @Test
    void shouldUseMonthlyEquivalentForMultiMonth1Point5Check() {
        WaterBillingCycle current = new WaterBillingCycle();
        current.setBillingperiodfrom(epoch("2026-05-31"));
        current.setBillingperiodto(epoch("2026-08-31"));
        current.setPreviousokreading(new BigDecimal("100"));
        current.setCurrentreading(new BigDecimal("196"));

        BillingBasisDecision decision = BillingBasisDecision.builder()
                .previousConsumption(new BigDecimal("34"))
                .billingBasis(BillingBasis.ACTUAL)
                .build();

        ConsumptionResult result = service.calculate("dl.djb", "WS/DJB/1", current, decision, rule);

        assertEquals(new BigDecimal("96"), result.getActualConsumption());
        assertEquals(new BigDecimal("31.304348"), result.getMonthlyConsumption());
        assertEquals(new BigDecimal("0.920716"), result.getDeviationFactor());
        assertFalse(result.isOnePointFiveX());
    }

    @Test
    void shouldNormalizeFromLastOkReadingWhenAverageCyclesAreIntervening() {
        WaterBillingCycle current = new WaterBillingCycle();
        current.setBillingperiodfrom(epoch("2026-08-01"));
        current.setBillingperiodto(epoch("2026-09-01"));
        current.setPreviousokreading(new BigDecimal("30"));
        current.setPreviousokreadingdate(epoch("2026-06-01"));
        current.setCurrentreading(new BigDecimal("75"));

        BillingBasisDecision decision = BillingBasisDecision.builder()
                .previousConsumption(new BigDecimal("17"))
                .billingBasis(BillingBasis.ACTUAL)
                .build();

        ConsumptionResult result = service.calculate("dl.djb", "WS/DJB/2026-27/000388", current, decision, rule);

        assertEquals(new BigDecimal("45"), result.getActualConsumption());
        assertEquals(new BigDecimal("14.673913"), result.getMonthlyConsumption());
        assertEquals(new BigDecimal("0.863171"), result.getDeviationFactor());
        assertFalse(result.isOnePointFiveX());
    }

    @Test
    void shouldNotSendBelow20MonthlyConsumptionToZroEvenWhenRawPeriodLooksHigh() {
        WaterBillingCycle current = new WaterBillingCycle();
        current.setBillingperiodfrom(epoch("2026-06-01"));
        current.setBillingperiodto(epoch("2026-08-31"));
        current.setPreviousokreading(new BigDecimal("100"));
        current.setCurrentreading(new BigDecimal("154"));

        BillingBasisDecision decision = BillingBasisDecision.builder()
                .previousConsumption(new BigDecimal("10"))
                .billingBasis(BillingBasis.ACTUAL)
                .build();

        ConsumptionResult result = service.calculate("dl.djb", "WS/DJB/2", current, decision, rule);

        assertEquals(new BigDecimal("17.802198"), result.getMonthlyConsumption());
        assertFalse(result.isOnePointFiveX());
    }

    @Test
    void shouldEvaluateExactly20MonthlyAgainstThe1Point5Rule() {
        WaterBillingCycle current = new WaterBillingCycle();
        current.setBillingperiodfrom(epoch("2026-07-01"));
        current.setBillingperiodto(epoch("2026-08-30"));
        current.setPreviousokreading(new BigDecimal("100"));
        current.setCurrentreading(new BigDecimal("140"));

        BillingBasisDecision decision = BillingBasisDecision.builder()
                .previousConsumption(new BigDecimal("10"))
                .billingBasis(BillingBasis.ACTUAL)
                .build();

        ConsumptionResult result = service.calculate("dl.djb", "WS/DJB/3", current, decision, rule);

        assertEquals(new BigDecimal("20.000000"), result.getMonthlyConsumption());
        assertTrue(result.isOnePointFiveX());
    }


    @Test
    void shouldEvaluateSubDayPeriodUsingMonthlyEquivalent() {
        WaterBillingCycle current = new WaterBillingCycle();
        current.setBillingperiodfrom(epoch("2026-08-10") + 0L);
        current.setBillingperiodto(epoch("2026-08-10") + (12L * 60L * 60L * 1000L));
        current.setPreviousokreading(new BigDecimal("100"));
        current.setCurrentreading(new BigDecimal("102"));

        BillingBasisDecision decision = BillingBasisDecision.builder()
                .previousConsumption(new BigDecimal("10"))
                .billingBasis(BillingBasis.ACTUAL)
                .build();

        ConsumptionResult result = service.calculate("dl.djb", "WS/DJB/4", current, decision, rule);

        assertEquals(new BigDecimal("120.000000"), result.getMonthlyConsumption());
        assertTrue(result.isOnePointFiveX());
    }

    @Test
    void shouldNotTrigger15xWhenRawMultiMonthConsumptionIsHighButMonthlyEquivalentIsNormal() {
        WaterBillingCycle current = new WaterBillingCycle();
        current.setBillingperiodfrom(epoch("2026-03-01"));
        current.setBillingperiodto(epoch("2026-04-30"));
        current.setPreviousokreading(new BigDecimal("100"));
        current.setCurrentreading(new BigDecimal("160"));

        BillingBasisDecision decision = BillingBasisDecision.builder()
                .previousConsumption(new BigDecimal("35"))
                .billingBasis(BillingBasis.ACTUAL)
                .build();

        ConsumptionResult result = service.calculate("dl.djb", "WS/DJB/1", current, decision, rule);

        // 60 KL over roughly two months is ~30 KL/month. It must be compared
        // against 35 KL/month, not against the raw 60 KL meter delta.
        assertEquals(new BigDecimal("30.000000"), result.getMonthlyConsumption());
        assertFalse(result.isOnePointFiveX());
    }

    @Test
    void shouldTrigger15xUsingMonthlyEquivalentWhenThreeMonthRawDeltaCrossesThreshold() {
        WaterBillingCycle current = new WaterBillingCycle();
        current.setBillingperiodfrom(epoch("2026-01-01"));
        current.setBillingperiodto(epoch("2026-04-01"));
        current.setPreviousokreading(new BigDecimal("100"));
        current.setCurrentreading(new BigDecimal("160"));

        BillingBasisDecision decision = BillingBasisDecision.builder()
                .previousConsumption(new BigDecimal("13"))
                .billingBasis(BillingBasis.ACTUAL)
                .build();

        ConsumptionResult result = service.calculate("dl.djb", "WS/DJB/2", current, decision, rule);

        // 60 KL over three months = 20 KL/month. 20 > 1.5 * 13 and also meets
        // the DJB minimum 20 KL/month gate.
        assertEquals(new BigDecimal("20.000000"), result.getMonthlyConsumption());
        assertTrue(result.isOnePointFiveX());
    }

    @Test
    void shouldWeightHistoricalAverageByObservedDaysInsteadOfCycleCount() {
        WaterBillingCycle current = new WaterBillingCycle();
        current.setBillingperiodfrom(epoch("2026-04-01"));
        current.setBillingperiodto(epoch("2026-05-01"));
        current.setCurrentreading(null);

        DJBMonthlyBillingRule averageRule = new DJBMonthlyBillingRule();
        averageRule.setAverageLookbackMonths(12);
        averageRule.setAverageMaximumCycles(2);
        averageRule.setProvisionalMaximumCycles(2);
        averageRule.setMinimumPostAverageConsumptionKl(25);

        BillingBasisDecision decision = BillingBasisDecision.builder()
                .billingBasis(BillingBasis.AVERAGE)
                .averageCycleCount(1)
                .provisionalCycleCount(0)
                .build();

        WaterBillingCycle threeMonthCycle = new WaterBillingCycle();
        threeMonthCycle.setBillingperiodfrom(epoch("2025-06-01"));
        threeMonthCycle.setBillingperiodto(epoch("2025-09-01"));
        threeMonthCycle.setBillingconsumption(new BigDecimal("60"));

        WaterBillingCycle oneMonthCycle = new WaterBillingCycle();
        oneMonthCycle.setBillingperiodfrom(epoch("2025-09-01"));
        oneMonthCycle.setBillingperiodto(epoch("2025-10-01"));
        oneMonthCycle.setBillingconsumption(new BigDecimal("10"));

        when(billingCycleDao.findPreviousActualCyclesWithinPeriod("dl.djb", "WS/DJB/HISTORY",
                epoch("2025-05-01"), current.getBillingperiodto(), 48))
                .thenReturn(Arrays.asList(threeMonthCycle, oneMonthCycle));

        ConsumptionResult result = service.calculate("dl.djb", "WS/DJB/HISTORY", current, decision, averageRule);

        // (60 KL / 3 months + 10 KL / 1 month) / 2 observed monthly periods
        // is NOT a valid average. The correct duration-weighted average is
        // (60 + 10) / 4 months = 17.5 KL/month.
        assertEquals(new BigDecimal("17.500"), result.getAverageConsumption());
        assertEquals(new BigDecimal("17.500"), result.getBillingConsumption());
    }

    @Test
    void shouldUseMaximumAvailableConsumptionForFirstTwoAverageBillsOfNewConnection() {
        WaterBillingCycle current = new WaterBillingCycle();
        current.setBillingperiodto(epoch("2026-06-01"));

        DJBMonthlyBillingRule averageRule = new DJBMonthlyBillingRule();
        averageRule.setAverageLookbackMonths(12);
        averageRule.setAverageMaximumCycles(2);
        averageRule.setProvisionalMaximumCycles(2);
        averageRule.setMinimumPostAverageConsumptionKl(25);

        BillingBasisDecision decision = BillingBasisDecision.builder()
                .billingBasis(BillingBasis.AVERAGE)
                .averageCycleCount(1)
                .provisionalCycleCount(0)
                .build();

        WaterBillingCycle lower = new WaterBillingCycle();
        lower.setBillingperiodfrom(epoch("2026-03-01"));
        lower.setBillingperiodto(epoch("2026-04-01"));
        lower.setBillingconsumption(new BigDecimal("18"));

        WaterBillingCycle higher = new WaterBillingCycle();
        higher.setBillingperiodfrom(epoch("2026-04-01"));
        higher.setBillingperiodto(epoch("2026-05-01"));
        higher.setBillingconsumption(new BigDecimal("24"));

        when(activationDao.findActivationDate("dl.djb", "WS/DJB/NEW-1"))
                .thenReturn(epoch("2026-01-15"));
        when(billingCycleDao.findPreviousActualCyclesWithinPeriod("dl.djb", "WS/DJB/NEW-1",
                epoch("2025-06-01"), current.getBillingperiodto(), 48))
                .thenReturn(Arrays.asList(higher, lower));

        ConsumptionResult result = service.calculate("dl.djb", "WS/DJB/NEW-1", current, decision, averageRule);

        // Standard history average is 21 KL/month, but DJB Table 24 requires
        // maximum available consumption for a connection active for less than one year.
        assertEquals(new BigDecimal("24.000"), result.getAverageConsumption());
        assertEquals(new BigDecimal("24.000"), result.getBillingConsumption());
    }

    @Test
    void shouldUseHistoricalAverageWhenConnectionIsAtLeastOneYearOld() {
        WaterBillingCycle current = new WaterBillingCycle();
        current.setBillingperiodto(epoch("2026-06-01"));

        DJBMonthlyBillingRule averageRule = new DJBMonthlyBillingRule();
        averageRule.setAverageLookbackMonths(12);
        averageRule.setAverageMaximumCycles(2);
        averageRule.setProvisionalMaximumCycles(2);
        averageRule.setMinimumPostAverageConsumptionKl(25);

        BillingBasisDecision decision = BillingBasisDecision.builder()
                .billingBasis(BillingBasis.AVERAGE)
                .averageCycleCount(1)
                .provisionalCycleCount(0)
                .build();

        WaterBillingCycle lower = new WaterBillingCycle();
        lower.setBillingperiodfrom(epoch("2026-03-01"));
        lower.setBillingperiodto(epoch("2026-04-01"));
        lower.setBillingconsumption(new BigDecimal("18"));

        WaterBillingCycle higher = new WaterBillingCycle();
        higher.setBillingperiodfrom(epoch("2026-04-01"));
        higher.setBillingperiodto(epoch("2026-05-01"));
        higher.setBillingconsumption(new BigDecimal("24"));

        when(activationDao.findActivationDate("dl.djb", "WS/DJB/OLD-1"))
                .thenReturn(epoch("2025-01-15"));
        when(billingCycleDao.findPreviousActualCyclesWithinPeriod("dl.djb", "WS/DJB/OLD-1",
                epoch("2025-06-01"), current.getBillingperiodto(), 48))
                .thenReturn(Arrays.asList(higher, lower));

        ConsumptionResult result = service.calculate("dl.djb", "WS/DJB/OLD-1", current, decision, averageRule);

        assertEquals(new BigDecimal("21.000"), result.getAverageConsumption());
        assertEquals(new BigDecimal("21.000"), result.getBillingConsumption());
    }

    @Test
    void shouldNotUseNewConnectionMaximumAfterFirstTwoAverageBills() {
        WaterBillingCycle current = new WaterBillingCycle();
        current.setBillingperiodto(epoch("2026-06-01"));

        DJBMonthlyBillingRule averageRule = new DJBMonthlyBillingRule();
        averageRule.setAverageLookbackMonths(12);
        averageRule.setAverageMaximumCycles(2);
        averageRule.setProvisionalMaximumCycles(2);
        averageRule.setMinimumPostAverageConsumptionKl(25);

        BillingBasisDecision decision = BillingBasisDecision.builder()
                .billingBasis(BillingBasis.AVERAGE)
                .averageCycleCount(3)
                .provisionalCycleCount(0)
                .build();

        WaterBillingCycle lower = new WaterBillingCycle();
        lower.setBillingperiodfrom(epoch("2026-03-01"));
        lower.setBillingperiodto(epoch("2026-04-01"));
        lower.setBillingconsumption(new BigDecimal("18"));

        WaterBillingCycle higher = new WaterBillingCycle();
        higher.setBillingperiodfrom(epoch("2026-04-01"));
        higher.setBillingperiodto(epoch("2026-05-01"));
        higher.setBillingconsumption(new BigDecimal("24"));

        when(activationDao.findActivationDate("dl.djb", "WS/DJB/NEW-2"))
                .thenReturn(epoch("2026-01-15"));
        when(billingCycleDao.findPreviousActualCyclesWithinPeriod("dl.djb", "WS/DJB/NEW-2",
                epoch("2025-06-01"), current.getBillingperiodto(), 48))
                .thenReturn(Arrays.asList(higher, lower));

        ConsumptionResult result = service.calculate("dl.djb", "WS/DJB/NEW-2", current, decision, averageRule);

        // After the first two average cycles the 25 KL/month floor applies.
        assertEquals(new BigDecimal("21.000"), result.getAverageConsumption());
        assertEquals(new BigDecimal("25.000"), result.getBillingConsumption());
    }

    @Test
    void shouldIgnoreActualCyclesOutsideStrictTwelveMonthWindow() {
        WaterBillingCycle current = new WaterBillingCycle();
        current.setBillingperiodto(epoch("2026-06-01"));

        DJBMonthlyBillingRule averageRule = new DJBMonthlyBillingRule();
        averageRule.setAverageLookbackMonths(12);
        averageRule.setAverageMaximumCycles(2);
        averageRule.setProvisionalMaximumCycles(2);
        averageRule.setMinimumPostAverageConsumptionKl(25);

        BillingBasisDecision decision = BillingBasisDecision.builder()
                .billingBasis(BillingBasis.AVERAGE)
                .averageCycleCount(1)
                .build();

        WaterBillingCycle recent = new WaterBillingCycle();
        recent.setBillingperiodfrom(epoch("2025-06-01"));
        recent.setBillingperiodto(epoch("2025-07-01"));
        recent.setBillingconsumption(new BigDecimal("24"));

        when(billingCycleDao.findPreviousActualCyclesWithinPeriod("dl.djb", "WS/DJB/WINDOW",
                epoch("2025-06-01"), current.getBillingperiodto(), 48))
                .thenReturn(Arrays.asList(recent));

        ConsumptionResult result = service.calculate("dl.djb", "WS/DJB/WINDOW", current, decision, averageRule);
        assertEquals(new BigDecimal("24.000"), result.getAverageConsumption());
    }

    private Long epoch(String date) {
        return java.time.ZonedDateTime.parse(date + "T00:00:00Z").toInstant().toEpochMilli();
    }
}
