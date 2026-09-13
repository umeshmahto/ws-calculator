package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.config.WSCalculationConfiguration;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingCycleStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ZroStatus;
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
import org.egov.wscalculation.web.models.Property;
import org.egov.wscalculation.web.models.WaterConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DJBMonthlyDemandServiceZroTest {

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

	@InjectMocks
	private DJBMonthlyDemandService service;

	@Test
	void shouldBlockDemandForDomesticOnePointFiveXWhileZroIsPending() {
		String tenantId = "dl.djb";
		String connectionNo = "WS/DJB/2026-27/000367";

		RequestInfo requestInfo = new RequestInfo();

		WaterConnection connection = new WaterConnection();
		connection.setConnectionNo(connectionNo);
		connection.setConnectionCategory("DOMESTIC");

		Property property = new Property();
		property.setUsageCategory("RESIDENTIAL");

		WaterBillingCycle cycle = validCycle(tenantId, connectionNo);
		cycle.setOnepointfivexflag(Boolean.TRUE);
		cycle.setZrostatus(ZroStatus.PENDING);
		cycle.setActualconsumption(new BigDecimal("23"));
		cycle.setPreviousconsumption(new BigDecimal("15"));

		when(calculatorUtil.getWaterConnection(eq(requestInfo), eq(connectionNo), eq(tenantId)))
				.thenReturn(Collections.singletonList(connection));

		when(calculatorUtil.getWaterConnectionObject(anyList())).thenReturn(connection);

		when(wsCalculationUtil.getProperty(any())).thenReturn(property);

		when(zroVerificationDao.findByBillingCycle(eq(tenantId), eq(cycle.getId()))).thenReturn(null);

		when(billingCycleDao.update(eq(cycle))).thenReturn(1);

		DJBMonthlyDemandService.DemandResult result = service.createDemand(requestInfo, cycle);

		assertNotNull(result);
		assertFalse(result.isDemandCreated());
		assertTrue(result.isZroRequired());

		assertEquals("Demand not generated because DJB 1.5x ZRO verification is required", result.getMessage());

		assertEquals(ZroStatus.PENDING, cycle.getZrostatus());
		assertEquals(BillingCycleStatus.CALCULATED, cycle.getStatus());

		verify(zroVerificationDao).save(any());
		verify(billingCycleDao).update(eq(cycle));

		verify(demandRepository, never()).saveDemand(any(), anyList(), any());
	}

	@Test
	void shouldReturnExistingDemandWithoutCreatingAnotherDemand() {
		String tenantId = "dl.djb";
		String connectionNo = "WS/DJB/2026-27/000367";

		RequestInfo requestInfo = new RequestInfo();

		WaterBillingCycle cycle = validCycle(tenantId, connectionNo);
		cycle.setDemandid("existing-demand-001");
		cycle.setCorrectionstatus(CorrectionStatus.PENDING);

		DJBMonthlyDemandService.DemandResult result = service.createDemand(requestInfo, cycle);

		assertNotNull(result);
		assertTrue(result.isDemandCreated());
		assertFalse(result.isZroRequired());

		assertEquals("DJB demand already exists for billing cycle", result.getMessage());

		verify(demandRepository, never()).saveDemand(any(), anyList(), any());

		verify(calculatorUtil, never()).getWaterConnection(any(), any(String.class), any(String.class));
	}

	@Test
	void shouldRejectDemandWhenBillingConsumptionIsMissing() {
		String tenantId = "dl.djb";
		String connectionNo = "WS/DJB/2026-27/000367";

		RequestInfo requestInfo = new RequestInfo();

		WaterBillingCycle cycle = validCycle(tenantId, connectionNo);
		cycle.setBillingconsumption(null);

		try {
			service.createDemand(requestInfo, cycle);
		} catch (IllegalStateException ex) {
			assertEquals("Billing consumption is required before demand generation", ex.getMessage());
			return;
		}

		throw new AssertionError("Expected IllegalStateException for missing billing consumption");
	}

	private WaterBillingCycle validCycle(String tenantId, String connectionNo) {

		WaterBillingCycle cycle = new WaterBillingCycle();
		cycle.setId("cycle-001");
		cycle.setTenantid(tenantId);
		cycle.setConnectionno(connectionNo);
		cycle.setBillingperiodfrom(1780338600000L);
		cycle.setBillingperiodto(1783017000000L);
		cycle.setBillingconsumption(new BigDecimal("23"));
		cycle.setOnepointfivexflag(Boolean.FALSE);
		cycle.setZrostatus(null);
		return cycle;
	}
}
