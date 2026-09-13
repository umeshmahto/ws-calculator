package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.config.WSCalculationConfiguration;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.repository.ZroVerificationDao;
import org.egov.wscalculation.djbmonthlybilling.service.ConsumptionService;
import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyBillingCalculationSnapshotService;
import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyDemandService;
import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyDemandService.DemandResult;
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

import com.fasterxml.jackson.databind.ObjectMapper;

class DJBMonthlyDemandServiceValidationTest {

	private DJBMonthlyBillingMasterProvider masterProvider;
	private TariffCalculationService tariffCalculationService;
	private ConsumptionService consumptionService;
	private SewerageCalculationService sewerageCalculationService;
	private RebateCalculationService rebateCalculationService;
	private DemandRepository demandRepository;
	private CalculatorUtil calculatorUtil;
	private WSCalculationUtil wsCalculationUtil;
	private WSCalculationConfiguration config;
	private ServiceRequestRepository serviceRequestRepository;
	private ObjectMapper objectMapper;
	private WSCalculationProducer wsCalculationProducer;
	private ResidualCreditService residualCreditService;
	private ZroVerificationDao zroVerificationDao;
	private WaterBillingCycleDao billingCycleDao;
	private DJBMonthlyBillingCalculationSnapshotService calculationSnapshotService;

	private DJBMonthlyDemandService service;

	@BeforeEach
	void setUp() {
		masterProvider = mock(DJBMonthlyBillingMasterProvider.class);
		tariffCalculationService = mock(TariffCalculationService.class);
		consumptionService = mock(ConsumptionService.class);
		sewerageCalculationService = mock(SewerageCalculationService.class);
		rebateCalculationService = mock(RebateCalculationService.class);
		demandRepository = mock(DemandRepository.class);
		calculatorUtil = mock(CalculatorUtil.class);
		wsCalculationUtil = mock(WSCalculationUtil.class);
		config = mock(WSCalculationConfiguration.class);
		serviceRequestRepository = mock(ServiceRequestRepository.class);
		objectMapper = new ObjectMapper();
		wsCalculationProducer = mock(WSCalculationProducer.class);
		residualCreditService = mock(ResidualCreditService.class);
		zroVerificationDao = mock(ZroVerificationDao.class);
		billingCycleDao = mock(WaterBillingCycleDao.class);
		calculationSnapshotService = mock(DJBMonthlyBillingCalculationSnapshotService.class);

		service = new DJBMonthlyDemandService(masterProvider, tariffCalculationService, consumptionService,
				sewerageCalculationService, rebateCalculationService, demandRepository, calculatorUtil,
				wsCalculationUtil, config, serviceRequestRepository, objectMapper, wsCalculationProducer,
				residualCreditService, zroVerificationDao, billingCycleDao, calculationSnapshotService);
	}

	@Test
	void shouldRejectNullBillingCycle() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> service.createDemand((RequestInfo) null, null));

		assertEquals("Billing cycle is required", ex.getMessage());
		verifyNoInteractions(masterProvider, tariffCalculationService, consumptionService, sewerageCalculationService,
				rebateCalculationService, demandRepository, calculatorUtil, wsCalculationUtil, config,
				serviceRequestRepository, residualCreditService, zroVerificationDao, billingCycleDao, calculationSnapshotService);
	}

	@Test
	void shouldRejectMissingTenantId() {
		WaterBillingCycle cycle = validCycle();
		cycle.setTenantid(null);

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> service.createDemand((RequestInfo) null, cycle));

		assertEquals("Billing cycle tenant and connection are required", ex.getMessage());
		verifyNoInteractions(masterProvider, tariffCalculationService, consumptionService, sewerageCalculationService,
				rebateCalculationService, demandRepository, calculatorUtil, wsCalculationUtil, config,
				serviceRequestRepository, residualCreditService, zroVerificationDao, billingCycleDao, calculationSnapshotService);
	}

	@Test
	void shouldRejectMissingConnectionNo() {
		WaterBillingCycle cycle = validCycle();
		cycle.setConnectionno("  ");

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> service.createDemand((RequestInfo) null, cycle));

		assertEquals("Billing cycle tenant and connection are required", ex.getMessage());
		verifyNoInteractions(masterProvider, tariffCalculationService, consumptionService, sewerageCalculationService,
				rebateCalculationService, demandRepository, calculatorUtil, wsCalculationUtil, config,
				serviceRequestRepository, residualCreditService, zroVerificationDao, billingCycleDao, calculationSnapshotService);
	}

	@Test
	void shouldRejectMissingBillingPeriod() {
		WaterBillingCycle cycle = validCycle();
		cycle.setBillingperiodto(null);

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> service.createDemand((RequestInfo) null, cycle));

		assertEquals("Billing period is required", ex.getMessage());
		verifyNoInteractions(masterProvider, tariffCalculationService, consumptionService, sewerageCalculationService,
				rebateCalculationService, demandRepository, calculatorUtil, wsCalculationUtil, config,
				serviceRequestRepository, residualCreditService, zroVerificationDao, billingCycleDao, calculationSnapshotService);
	}

	@Test
	void shouldNotCreateSecondDemandWhenDemandAlreadyExists() {
		WaterBillingCycle cycle = validCycle();
		cycle.setDemandid("existing-demand-id");
		cycle.setCorrectionstatus(CorrectionStatus.PENDING);

		DemandResult result = service.createDemand((RequestInfo) null, cycle);

		assertEquals(true, result.isDemandCreated());
		assertFalse(result.isZroRequired());
		assertEquals("DJB demand already exists for billing cycle", result.getMessage());
		verifyNoInteractions(masterProvider, tariffCalculationService, consumptionService, sewerageCalculationService,
				rebateCalculationService, demandRepository, calculatorUtil, wsCalculationUtil, config,
				serviceRequestRepository, wsCalculationProducer, zroVerificationDao, billingCycleDao);
	}

	@Test
	void shouldAcceptCompleteCycleAndProceedPastValidation() {
		WaterBillingCycle cycle = validCycle();
		cycle.setDemandid("existing-demand-id");
		cycle.setCorrectionstatus(CorrectionStatus.PENDING);

		assertDoesNotThrow(() -> service.createDemand((RequestInfo) null, cycle));
	}

	private WaterBillingCycle validCycle() {
		WaterBillingCycle cycle = new WaterBillingCycle();
		cycle.setId("cycle-001");
		cycle.setTenantid("pb.amritsar");
		cycle.setConnectionno("WS/DJB/2026-27/000367");
		cycle.setBillingperiodfrom(1780338600000L);
		cycle.setBillingperiodto(1783017000000L);
		cycle.setActualconsumption(new java.math.BigDecimal("10"));
		cycle.setBillingconsumption(new java.math.BigDecimal("10"));
		cycle.setOnepointfivexflag(false);
		cycle.setAveragecyclecount(0);
		cycle.setProvisionalcyclecount(0);
		return cycle;
	}
}
