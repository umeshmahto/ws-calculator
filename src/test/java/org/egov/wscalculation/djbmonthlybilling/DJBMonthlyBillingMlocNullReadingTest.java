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
import org.egov.wscalculation.djbmonthlybilling.repository.WaterConnectionActivationDao;
import org.egov.wscalculation.djbmonthlybilling.service.ConsumptionService;
import org.egov.wscalculation.djbmonthlybilling.service.dto.BillingBasisDecision;
import org.egov.wscalculation.djbmonthlybilling.service.dto.ConsumptionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DJBMonthlyBillingMlocNullReadingTest {

    private static final String TENANT_ID = "dl.djb";
    private static final String CONNECTION_NO = "WS/DJB/MLOC-1";
    private static final long CURRENT_PERIOD_FROM = 1767292200000L;
    private static final long CURRENT_PERIOD_TO = 1769884200000L;
    // 12 calendar months before CURRENT_PERIOD_TO, aligned with ConsumptionService UTC lookback calculation.
    private static final long LOOKBACK_FROM = 1738281600000L;

    @Mock
    private WaterBillingCycleDao billingCycleDao;

    @Mock
    private WaterConnectionActivationDao activationDao;

    private ConsumptionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        service = new ConsumptionService(billingCycleDao, activationDao);
    }

    @Test
    void shouldCalculateAverageBillingWhenMlocHasNoCurrentReading() {
        WaterBillingCycle cycle = new WaterBillingCycle();
        cycle.setBillingperiodfrom(CURRENT_PERIOD_FROM);
        cycle.setBillingperiodto(CURRENT_PERIOD_TO);
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
		actual.setBillingperiodfrom(1764599400000L);
		actual.setBillingperiodto(CURRENT_PERIOD_FROM);
        actual.setBillingconsumption(new BigDecimal("18"));

        when(billingCycleDao.findPreviousActualCyclesWithinPeriod(
                TENANT_ID, CONNECTION_NO, LOOKBACK_FROM, CURRENT_PERIOD_TO, 48))
                .thenReturn(Collections.singletonList(actual));

        ConsumptionResult result = service.calculate(
                TENANT_ID, CONNECTION_NO, cycle, decision, rule);

        assertNull(result.getActualConsumption());
        assertEquals(new BigDecimal("18.000"), result.getAverageConsumption());
        assertEquals(new BigDecimal("18.000"), result.getBillingConsumption());
        assertFalse(result.isOnePointFiveX());
    }
}
