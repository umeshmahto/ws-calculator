package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.DJBMonthlyBillingCalculation;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.repository.DJBMonthlyBillingCalculationDao;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyBillingCalculationSnapshotService;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.SewerageCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.TariffCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingStatement;
import org.egov.wscalculation.web.models.Property;
import org.egov.wscalculation.web.models.WaterConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DJBMonthlyBillingCalculationSnapshotServiceTest {

    @Mock
    private DJBMonthlyBillingCalculationDao calculationDao;

    @Mock
    private WaterBillingCycleDao billingCycleDao;

    private DJBMonthlyBillingCalculationSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new DJBMonthlyBillingCalculationSnapshotService(
                calculationDao,
                billingCycleDao,
                new ObjectMapper());
    }

    @Test
    void shouldPersistConfiguredMinimumFromBillingMasterInsteadOfHardcoded25() throws Exception {
        WaterBillingCycle cycle = validCycle();
        cycle.setBillingbasis(BillingBasis.PROVISIONAL);
        cycle.setBillingconsumption(new BigDecimal("30"));
        cycle.setAverageconsumption(new BigDecimal("18"));
        cycle.setProvisionalcyclecount(3);

        DJBMonthlyBillingRule rule = new DJBMonthlyBillingRule();
        rule.setCode("DJB_MONTHLY_RULE");
        rule.setMinimumPostAverageConsumptionKl(27);
        rule.setAverageMaximumCycles(2);
        rule.setProvisionalMaximumCycles(2);

        TariffCalculationResult water = TariffCalculationResult.builder()
                .tariffId("DOMESTIC")
                .category("DOMESTIC")
                .consumption(new BigDecimal("30"))
                .waterVolumetricCharge(new BigDecimal("100"))
                .serviceCharge(new BigDecimal("200"))
                .totalWaterCharge(new BigDecimal("300"))
                .slabCharges(Collections.emptyList())
                .build();

        SewerageCalculationResult sewerage = SewerageCalculationResult.builder()
                .regularSewerageCharge(new BigDecimal("60"))
                .additionalSewerageCharge(BigDecimal.ZERO)
                .totalSewerageCharge(new BigDecimal("60"))
                .regularRuleCode("SEWER_60_PERCENT")
                .build();

        RebateCalculationResult rebate = RebateCalculationResult.builder()
                .totalRebate(BigDecimal.ZERO)
                .rebateItems(Collections.emptyList())
                .explanation("No rebate")
                .build();

        when(calculationDao.save(any(DJBMonthlyBillingCalculation.class))).thenReturn(1);

        String calculationId = service.persist(
                new RequestInfo(), cycle, new WaterConnection(), new Property(),
                water, sewerage, rebate, rule,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                Collections.<String>emptyList(), new BigDecimal("360"));

        assertEquals(36, calculationId.length());

        ArgumentCaptor<DJBMonthlyBillingCalculation> captor =
                ArgumentCaptor.forClass(DJBMonthlyBillingCalculation.class);
        verify(calculationDao).save(captor.capture());

        DJBMonthlyBillingCalculation saved = captor.getValue();
        DJBMonthlyBillingStatement statement =
                new ObjectMapper().readValue(saved.getSnapshotjson(), DJBMonthlyBillingStatement.class);

        assertEquals(new BigDecimal("27"),
                statement.getBillingDecision().getMinimumBillingConsumption());
        assertEquals(Integer.valueOf(2),
                statement.getBillingDecision().getConfiguredAverageMaximumCycles());
        assertEquals(Integer.valueOf(2),
                statement.getBillingDecision().getConfiguredProvisionalMaximumCycles());
        assertEquals("DJB_MONTHLY_RULE",
                statement.getBillingDecision().getBillingRuleCode());
    }

    @Test
    void shouldExposeCorrectionSupersededCyclesAndCalculatedAmounts() {
        WaterBillingCycle current = validCycle();
        current.setBillingbasis(BillingBasis.CORRECTED_ACTUAL);
        current.setCorrectionstatus(org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus.COMPLETED);
        current.setPreviousokreading(new BigDecimal("20"));
        current.setPreviousokreadingdate(1000L);
        current.setCurrentreading(new BigDecimal("58"));
        current.setCurrentreadingdate(2000L);
        current.setActualconsumption(new BigDecimal("38"));
        current.setBillingconsumption(new BigDecimal("38"));

        WaterBillingCycle previous = validCycle();
        previous.setId("CYCLE-AVG");
        previous.setBillingbasis(BillingBasis.AVERAGE);
        previous.setBillingconsumption(new BigDecimal("6"));
        previous.setCalculationid("CALC-AVG");
        previous.setDemandid("DEMAND-AVG");
        previous.setBillid("BILL-AVG");

        DJBMonthlyBillingCalculation previousCalculation = DJBMonthlyBillingCalculation.builder()
                .id("CALC-AVG")
                .tenantid("dl.djb")
                .billingcycleid("CYCLE-AVG")
                .snapshotjson("{\"charges\":{\"netAmount\":100.00}}")
                .build();

        when(calculationDao.findById("dl.djb", "CALC-AVG")).thenReturn(previousCalculation);
        when(billingCycleDao.findCyclesForCorrection("dl.djb", current.getConnectionno(), 1000L, current.getBillingperiodto()))
                .thenReturn(Collections.singletonList(previous));
        when(billingCycleDao.findPreviousOkByConnectionBefore("dl.djb", current.getConnectionno(), current.getBillingperiodto()))
                .thenReturn(null);
        when(calculationDao.save(any(DJBMonthlyBillingCalculation.class))).thenReturn(1);

        TariffCalculationResult water = TariffCalculationResult.builder()
                .tariffId("DOMESTIC").category("DOMESTIC").consumption(new BigDecimal("38"))
                .waterVolumetricCharge(new BigDecimal("200")).serviceCharge(new BigDecimal("200"))
                .totalWaterCharge(new BigDecimal("400")).slabCharges(Collections.emptyList()).build();
        SewerageCalculationResult sewerage = SewerageCalculationResult.builder()
                .regularSewerageCharge(new BigDecimal("120")).additionalSewerageCharge(BigDecimal.ZERO)
                .totalSewerageCharge(new BigDecimal("120")).build();
        RebateCalculationResult rebate = RebateCalculationResult.builder()
                .totalRebate(BigDecimal.ZERO).rebateItems(Collections.emptyList()).build();

        String id = service.persist(new RequestInfo(), current, new WaterConnection(), new Property(),
                water, sewerage, rebate, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                Collections.<String>emptyList(), new BigDecimal("520"));

        verify(calculationDao).save(any(DJBMonthlyBillingCalculation.class));
        verify(calculationDao).findById("dl.djb", "CALC-AVG");
        assertEquals(36, id.length());
    }

    private WaterBillingCycle validCycle() {
        WaterBillingCycle cycle = new WaterBillingCycle();
        cycle.setId("CYCLE-001");
        cycle.setTenantid("dl.djb");
        cycle.setConnectionno("WS/DJB/2026-27/000367");
        cycle.setBillingperiodfrom(1000L);
        cycle.setBillingperiodto(2000L);
        cycle.setReadingqualitycode("OK");
        cycle.setStatus(org.egov.wscalculation.djbmonthlybilling.model.enums.BillingCycleStatus.CALCULATED);
        return cycle;
    }
}
