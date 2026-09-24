package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;

import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.service.ConsumptionService;
import org.egov.wscalculation.djbmonthlybilling.service.dto.BillingBasisDecision;
import org.egov.wscalculation.djbmonthlybilling.service.dto.ConsumptionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DJBMonthlyBillingMlocNullReadingTest {

    @Mock
    private WaterBillingCycleDao billingCycleDao;

    private ConsumptionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        service = new ConsumptionService(billingCycleDao);
    }

    @Test
    void shouldCalculateAverageBillingWhenMlocHasNoCurrentReading() {
        WaterBillingCycle cycle = new WaterBillingCycle();
        cycle.setBillingperiodfrom(1767292200000L);
        cycle.setBillingperiodto(1769884200000L);
        cycle.setCurrentreading(null);
        cycle.setPreviousokreading(new BigDecimal("30"));

        DJBMonthlyBillingRule rule = new DJBMonthlyBillingRule();
        rule.setAverageLookbackMonths(12);
        rule.setAverageMaximumCycles(2);
        rule.setProvisionalMaximumCycles(2);
        rule.setMinimumPostAverageConsumptionKl(25);

        BillingBasisDecision decision = BillingBasisDecision.builder()
                .billingBasis(BillingBasis.AVERAGE)
                .averageCycleCount(1)
                .provisionalCycleCount(0)
                .build();

        WaterBillingCycle actual = new WaterBillingCycle();
		actual.setBillingperiodfrom(1769884200000L - (30L * 24L * 60L * 60L * 1000L));
		actual.setBillingperiodto(1769884200000L);
        actual.setBillingconsumption(new BigDecimal("18"));

        when(billingCycleDao.findPreviousActualCycles(
				"dl.djb", "WS/DJB/MLOC-1", 1769884200000L, 48))
                .thenReturn(Collections.singletonList(actual));

        ConsumptionResult result = service.calculate(
                "dl.djb", "WS/DJB/MLOC-1", cycle, decision, rule);

        assertNull(result.getActualConsumption());
        assertEquals(new BigDecimal("18.000"), result.getAverageConsumption());
        assertEquals(new BigDecimal("18.000"), result.getBillingConsumption());
        assertFalse(result.isOnePointFiveX());
    }
}
