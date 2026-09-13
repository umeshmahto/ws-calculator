package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.config.WSCalculationConfiguration;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingCycleStatus;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.repository.ZroVerificationDao;
import org.egov.wscalculation.djbmonthlybilling.service.ConsumptionService;
import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyDemandService;
import org.egov.wscalculation.djbmonthlybilling.service.RebateCalculationService;
import org.egov.wscalculation.djbmonthlybilling.service.ResidualCreditService;
import org.egov.wscalculation.djbmonthlybilling.service.SewerageCalculationService;
import org.egov.wscalculation.djbmonthlybilling.service.TariffCalculationService;
import org.egov.wscalculation.djbmonthlybilling.service.master.DJBMonthlyBillingMasterProvider;
import org.egov.wscalculation.producer.WSCalculationProducer;
import org.egov.wscalculation.repository.DemandRepository;
import org.egov.wscalculation.repository.ServiceRequestRepository;
import org.egov.wscalculation.util.CalculatorUtil;
import org.egov.wscalculation.util.WSCalculationUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DJBMonthlyDemandServiceRejectedOnePointFiveTest {

    @Mock
    private DJBMonthlyBillingMasterProvider masterProvider;

    @Mock
    private TariffCalculationService tariffCalculationService;

    @Mock
    private ConsumptionService consumptionService;

    @Mock
    private SewerageCalculationService sewerageCalculationService;

    @Mock
    private RebateCalculationService rebateCalculationService;

    @Mock
    private DemandRepository demandRepository;

    @Mock
    private CalculatorUtil calculatorUtil;

    @Mock
    private WSCalculationUtil wsCalculationUtil;

    @Mock
    private WSCalculationConfiguration config;

    @Mock
    private ServiceRequestRepository serviceRequestRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private WSCalculationProducer wsCalculationProducer;

    @Mock
    private ResidualCreditService residualCreditService;

    @Mock
    private ZroVerificationDao zroVerificationDao;

    @Mock
    private WaterBillingCycleDao billingCycleDao;

    @Mock
    private DJBMonthlyBillingRule rule;

    @InjectMocks
    private DJBMonthlyDemandService service;

    private DJBMonthlyDemandService spyService;

    private final RequestInfo requestInfo = new RequestInfo();

    @BeforeEach
    void setUp() {
        spyService = org.mockito.Mockito.spy(service);
    }

    @Test
    void shouldUseHistoricalAverageForFirstRejectedOnePointFiveProvisionalCycle() {
        WaterBillingCycle cycle = flaggedCycle();

        when(masterProvider.getBillingRule(eq(requestInfo), eq(cycle.getTenantid())))
                .thenReturn(rule);
        when(rule.getAverageLookbackMonths()).thenReturn(12);
        when(rule.getProvisionalMaximumCycles()).thenReturn(2);
        when(rule.getMinimumPostAverageConsumptionKl()).thenReturn(Integer.valueOf(25));

        when(consumptionService.calculateHistoricalAverage(
                eq(cycle.getTenantid()),
                eq(cycle.getConnectionno()),
                eq(cycle.getBillingperiodto()),
                eq(12)))
                .thenReturn(new BigDecimal("18"));

        when(billingCycleDao.findCyclesForConnection(
                eq(cycle.getTenantid()),
                eq(cycle.getConnectionno()),
                eq(cycle.getBillingperiodto()),
                eq(24)))
                .thenReturn(Collections.emptyList());

        when(billingCycleDao.update(eq(cycle))).thenReturn(1);

        doReturn(DJBMonthlyDemandService.DemandResult.builder()
                .demandCreated(true)
                .message("test")
                .build())
                .when(spyService).createDemand(eq(requestInfo), eq(cycle));

        DJBMonthlyDemandService.DemandResult result =
                spyService.createRejectedOnePointFiveFallbackDemand(requestInfo, cycle);

        assertEquals(new BigDecimal("18"), cycle.getAverageconsumption());
        assertEquals(new BigDecimal("18"), cycle.getBillingconsumption());
        assertEquals(BillingBasis.PROVISIONAL, cycle.getBillingbasis());
        assertEquals(Integer.valueOf(1), cycle.getProvisionalcyclecount());
        assertEquals(BillingCycleStatus.CALCULATED, cycle.getStatus());

        assertEquals(true, result.isDemandCreated());
        verify(billingCycleDao).update(eq(cycle));
    }

    @Test
    void shouldUseHistoricalAverageForSecondRejectedOnePointFiveProvisionalCycle() {
        WaterBillingCycle cycle = flaggedCycle();

        WaterBillingCycle previous =
                estimatedCycle(BillingBasis.PROVISIONAL);

        when(masterProvider.getBillingRule(eq(requestInfo), eq(cycle.getTenantid())))
                .thenReturn(rule);
        when(rule.getAverageLookbackMonths()).thenReturn(12);
        when(rule.getProvisionalMaximumCycles()).thenReturn(2);
        when(rule.getMinimumPostAverageConsumptionKl()).thenReturn(Integer.valueOf(25));

        when(consumptionService.calculateHistoricalAverage(
                eq(cycle.getTenantid()),
                eq(cycle.getConnectionno()),
                eq(cycle.getBillingperiodto()),
                eq(12)))
                .thenReturn(new BigDecimal("18"));

        when(billingCycleDao.findCyclesForConnection(
                eq(cycle.getTenantid()),
                eq(cycle.getConnectionno()),
                eq(cycle.getBillingperiodto()),
                eq(24)))
                .thenReturn(Collections.singletonList(previous));

        when(billingCycleDao.update(eq(cycle))).thenReturn(1);

        doReturn(DJBMonthlyDemandService.DemandResult.builder()
                .demandCreated(true)
                .build())
                .when(spyService).createDemand(eq(requestInfo), eq(cycle));

        spyService.createRejectedOnePointFiveFallbackDemand(requestInfo, cycle);

        assertEquals(new BigDecimal("18"), cycle.getBillingconsumption());
        assertEquals(Integer.valueOf(2), cycle.getProvisionalcyclecount());
    }

    @Test
    void shouldApplyTwentyFiveKlFloorFromThirdRejectedOnePointFiveProvisionalCycle() {
        WaterBillingCycle cycle = flaggedCycle();

        WaterBillingCycle previous1 =
                estimatedCycle(BillingBasis.PROVISIONAL);
        WaterBillingCycle previous2 =
                estimatedCycle(BillingBasis.PROVISIONAL);

        when(masterProvider.getBillingRule(eq(requestInfo), eq(cycle.getTenantid())))
                .thenReturn(rule);
        when(rule.getAverageLookbackMonths()).thenReturn(12);
        when(rule.getProvisionalMaximumCycles()).thenReturn(2);
        when(rule.getMinimumPostAverageConsumptionKl()).thenReturn(Integer.valueOf(25));

        when(consumptionService.calculateHistoricalAverage(
                eq(cycle.getTenantid()),
                eq(cycle.getConnectionno()),
                eq(cycle.getBillingperiodto()),
                eq(12)))
                .thenReturn(new BigDecimal("18"));

        when(billingCycleDao.findCyclesForConnection(
                eq(cycle.getTenantid()),
                eq(cycle.getConnectionno()),
                eq(cycle.getBillingperiodto()),
                eq(24)))
                .thenReturn(Arrays.asList(previous1, previous2));

        when(billingCycleDao.update(eq(cycle))).thenReturn(1);

        doReturn(DJBMonthlyDemandService.DemandResult.builder()
                .demandCreated(true)
                .build())
                .when(spyService).createDemand(eq(requestInfo), eq(cycle));

        spyService.createRejectedOnePointFiveFallbackDemand(requestInfo, cycle);

        assertEquals(new BigDecimal("25"), cycle.getBillingconsumption());
        assertEquals(Integer.valueOf(3), cycle.getProvisionalcyclecount());
    }

    @Test
    void shouldNotConsumeProvisionalAllowanceFromOlderAverageCycle() {
        WaterBillingCycle cycle = flaggedCycle();

        WaterBillingCycle olderAverage =
                estimatedCycle(BillingBasis.AVERAGE);

        when(masterProvider.getBillingRule(eq(requestInfo), eq(cycle.getTenantid())))
                .thenReturn(rule);
        when(rule.getAverageLookbackMonths()).thenReturn(12);
        when(rule.getProvisionalMaximumCycles()).thenReturn(2);
        when(rule.getMinimumPostAverageConsumptionKl()).thenReturn(Integer.valueOf(25));

        when(consumptionService.calculateHistoricalAverage(
                eq(cycle.getTenantid()),
                eq(cycle.getConnectionno()),
                eq(cycle.getBillingperiodto()),
                eq(12)))
                .thenReturn(new BigDecimal("18"));

        when(billingCycleDao.findCyclesForConnection(
                eq(cycle.getTenantid()),
                eq(cycle.getConnectionno()),
                eq(cycle.getBillingperiodto()),
                eq(24)))
                .thenReturn(Collections.singletonList(olderAverage));

        when(billingCycleDao.update(eq(cycle))).thenReturn(1);

        doReturn(DJBMonthlyDemandService.DemandResult.builder()
                .demandCreated(true)
                .build())
                .when(spyService).createDemand(eq(requestInfo), eq(cycle));

        spyService.createRejectedOnePointFiveFallbackDemand(requestInfo, cycle);

        assertEquals(new BigDecimal("18"), cycle.getBillingconsumption());
        assertEquals(Integer.valueOf(1), cycle.getProvisionalcyclecount());
    }

    @Test
    void shouldRejectFallbackWhenCycleIsNotOnePointFiveFlagged() {
        WaterBillingCycle cycle = flaggedCycle();
        cycle.setOnepointfivexflag(Boolean.FALSE);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> spyService.createRejectedOnePointFiveFallbackDemand(
                        requestInfo, cycle));

        assertEquals(
                "Rejected 1.5x fallback billing is only valid for a DJB 1.5x flagged billing cycle",
                exception.getMessage());
    }

    @Test
    void shouldRejectFallbackWhenHistoricalAverageIsMissing() {
        WaterBillingCycle cycle = flaggedCycle();

        when(masterProvider.getBillingRule(eq(requestInfo), eq(cycle.getTenantid())))
                .thenReturn(rule);
        when(rule.getAverageLookbackMonths()).thenReturn(12);

        when(consumptionService.calculateHistoricalAverage(
                eq(cycle.getTenantid()),
                eq(cycle.getConnectionno()),
                eq(cycle.getBillingperiodto()),
                eq(12)))
                .thenReturn(null);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> spyService.createRejectedOnePointFiveFallbackDemand(
                        requestInfo, cycle));

        assertEquals(
                "No actual consumption history is available for rejected DJB 1.5x fallback billing",
                exception.getMessage());
    }

    private WaterBillingCycle flaggedCycle() {
        WaterBillingCycle cycle = new WaterBillingCycle();
        cycle.setId("cycle-001");
        cycle.setTenantid("dl.djb");
        cycle.setConnectionno("WS/DJB/2026-27/000367");
        cycle.setBillingperiodfrom(1780338600000L);
        cycle.setBillingperiodto(1783017000000L);
        cycle.setActualconsumption(new BigDecimal("40"));
        cycle.setPreviousconsumption(new BigDecimal("20"));
        cycle.setBillingconsumption(new BigDecimal("40"));
        cycle.setOnepointfivexflag(Boolean.TRUE);
        return cycle;
    }

    private WaterBillingCycle estimatedCycle(BillingBasis basis) {
        WaterBillingCycle cycle = new WaterBillingCycle();
        cycle.setId("previous-cycle");
        cycle.setTenantid("dl.djb");
        cycle.setConnectionno("WS/DJB/2026-27/000367");
        cycle.setBillingbasis(basis);
        return cycle;
    }
}
