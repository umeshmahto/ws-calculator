package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.config.WSCalculationConfiguration;
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
class DJBMonthlyDemandServiceBillFetchTest {

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
	private WSCalculationProducer wsCalculationProducer;

	@Mock
	private ResidualCreditService residualCreditService;

	@Mock
	private ZroVerificationDao zroVerificationDao;

	@Mock
	private WaterBillingCycleDao billingCycleDao;

	@Mock
	private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

	@InjectMocks
	private DJBMonthlyDemandService service;

	private final ObjectMapper realMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		/*
		 * Keep the production service dependency mocked, but make JSON conversion
		 * behave like the real ObjectMapper.
		 */
		org.mockito.Mockito.lenient().when(objectMapper.valueToTree(any()))
				.thenAnswer(invocation -> realMapper.valueToTree(invocation.getArgument(0)));
	}

	@Test
	void shouldExtractBillIdFromBillObject() {
		RequestInfo requestInfo = new RequestInfo();

		Map<String, Object> bill = new HashMap<>();
		bill.put("id", "BILL-001");

		Map<String, Object> response = new HashMap<>();
		response.put("bill", bill);

		StringBuilder url = new StringBuilder("http://billing-service/bill/v2/_fetchbill");

		when(calculatorUtil.getFetchBillURL(eq("dl.djb"), eq("WS/DJB/2026-27/000367"))).thenReturn(url);

		when(serviceRequestRepository.fetchResult(eq(url),
				any(org.egov.wscalculation.web.models.RequestInfoWrapper.class))).thenReturn(response);

		String billId = service.fetchBillForExistingDemand(requestInfo, "dl.djb", "WS/DJB/2026-27/000367");

		assertEquals("BILL-001", billId);

		verify(wsCalculationProducer).push(eq(null), any(Map.class));
	}

	@Test
	void shouldExtractFirstBillIdFromBillsArray() {
		RequestInfo requestInfo = new RequestInfo();

		Map<String, Object> firstBill = new HashMap<>();
		firstBill.put("id", "BILL-ARRAY-001");

		Map<String, Object> secondBill = new HashMap<>();
		secondBill.put("id", "BILL-ARRAY-002");

		Map<String, Object> response = new HashMap<>();
		response.put("bills", Arrays.asList(firstBill, secondBill));

		StringBuilder url = new StringBuilder("http://billing-service/bill/v2/_fetchbill");

		when(calculatorUtil.getFetchBillURL(eq("dl.djb"), eq("WS/DJB/2026-27/000367"))).thenReturn(url);

		when(serviceRequestRepository.fetchResult(eq(url),
				any(org.egov.wscalculation.web.models.RequestInfoWrapper.class))).thenReturn(response);

		String billId = service.fetchBillForExistingDemand(requestInfo, "dl.djb", "WS/DJB/2026-27/000367");

		assertEquals("BILL-ARRAY-001", billId);
	}

	@Test
	void shouldRejectNullBillResponse() {
		RequestInfo requestInfo = new RequestInfo();

		StringBuilder url = new StringBuilder("http://billing-service/bill/v2/_fetchbill");

		when(calculatorUtil.getFetchBillURL(eq("dl.djb"), eq("WS/DJB/2026-27/000367"))).thenReturn(url);

		when(serviceRequestRepository.fetchResult(eq(url),
				any(org.egov.wscalculation.web.models.RequestInfoWrapper.class))).thenReturn(null);

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> service.fetchBillForExistingDemand(requestInfo, "dl.djb", "WS/DJB/2026-27/000367"));

		assertEquals("Billing-service returned null bill response for WS/DJB/2026-27/000367", exception.getMessage());

		verify(wsCalculationProducer, never()).push(any(), any(Map.class));
	}

	@Test
	void shouldRejectBillResponseWithoutBillId() {
		RequestInfo requestInfo = new RequestInfo();

		Map<String, Object> bill = new HashMap<>();
		bill.put("billNumber", "BILL-NO-ID");

		Map<String, Object> response = new HashMap<>();
		response.put("bill", bill);

		StringBuilder url = new StringBuilder("http://billing-service/bill/v2/_fetchbill");

		when(calculatorUtil.getFetchBillURL(eq("dl.djb"), eq("WS/DJB/2026-27/000367"))).thenReturn(url);

		when(serviceRequestRepository.fetchResult(eq(url),
				any(org.egov.wscalculation.web.models.RequestInfoWrapper.class))).thenReturn(response);

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> service.fetchBillForExistingDemand(requestInfo, "dl.djb", "WS/DJB/2026-27/000367"));

		assertEquals("Billing-service returned bill response without bill id for WS/DJB/2026-27/000367",
				exception.getMessage());

		verify(wsCalculationProducer).push(eq(null), any(Map.class));
	}

	@Test
	void shouldRejectMissingTenantIdOrConnectionNo() {
		RequestInfo requestInfo = new RequestInfo();

		assertThrows(IllegalArgumentException.class,
				() -> service.fetchBillForExistingDemand(requestInfo, null, "WS/DJB/2026-27/000367"));

		assertThrows(IllegalArgumentException.class,
				() -> service.fetchBillForExistingDemand(requestInfo, "dl.djb", null));

		verify(calculatorUtil, never()).getFetchBillURL(any(), any());
	}
}
