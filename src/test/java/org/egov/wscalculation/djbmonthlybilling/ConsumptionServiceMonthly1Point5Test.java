package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
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

    private ConsumptionService service;
    private DJBMonthlyBillingRule rule;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        service = new ConsumptionService(billingCycleDao);
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

    private Long epoch(String date) {
        return java.time.ZonedDateTime.parse(date + "T00:00:00Z").toInstant().toEpochMilli();
    }
}
