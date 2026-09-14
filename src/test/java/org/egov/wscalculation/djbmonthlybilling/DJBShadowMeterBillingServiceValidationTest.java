package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyBillingRule;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBReadingQualityCode;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.service.CorrectionService;
import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyBillingService;
import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyDemandService;
import org.egov.wscalculation.djbmonthlybilling.service.DJBShadowMeterBillingService;
import org.egov.wscalculation.djbmonthlybilling.service.master.DJBMonthlyBillingMasterProvider;
import org.egov.wscalculation.repository.WSCalculationDao;
import org.egov.wscalculation.service.EnrichmentService;
import org.egov.wscalculation.web.models.MeterReading;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class DJBShadowMeterBillingServiceValidationTest {

	@Mock
	private WSCalculationDao wsCalculationDao;
	@Mock
	private EnrichmentService enrichmentService;
	@Mock
	private DJBMonthlyBillingMasterProvider masterProvider;
	@Mock
	private DJBMonthlyBillingService monthlyBillingService;
	@Mock
	private WaterBillingCycleDao billingCycleDao;
	@Mock
	private CorrectionService correctionService;
	@Mock
	private DJBMonthlyDemandService demandService;
	@Mock
	private PlatformTransactionManager transactionManager;

	@InjectMocks
	private DJBShadowMeterBillingService service;

	@Test
	void shouldRejectOverlappingBillingPeriod() {
		MeterReading reading = validReading(2000L, 3000L, 50d, 60d);
		WaterBillingCycle latest = cycle("LATEST", 1000L, 2500L);
		when(masterProvider.getBillingRule(any(), eq("dl.djb"))).thenReturn(new DJBMonthlyBillingRule());
		when(masterProvider.findReadingQualityCode(any(), eq("dl.djb"), eq("OK")))
				.thenReturn(new DJBReadingQualityCode());
		when(billingCycleDao.findByConnectionAndPeriod("dl.djb", reading.getConnectionNo(), 2000L, 3000L))
				.thenReturn(null);
		when(billingCycleDao.findLatestByConnection("dl.djb", reading.getConnectionNo())).thenReturn(latest);

		assertThrows(IllegalStateException.class, () -> service.processDjbBilling(reading, new RequestInfo()));
	}

	@Test
	void shouldRejectBillingPeriodGap() {
		MeterReading reading = validReading(3000L, 4000L, 60d, 70d);
		WaterBillingCycle latest = cycle("LATEST", 1000L, 2000L);
		when(masterProvider.getBillingRule(any(), eq("dl.djb"))).thenReturn(new DJBMonthlyBillingRule());
		when(masterProvider.findReadingQualityCode(any(), eq("dl.djb"), eq("OK")))
				.thenReturn(new DJBReadingQualityCode());
		when(billingCycleDao.findByConnectionAndPeriod("dl.djb", reading.getConnectionNo(), 3000L, 4000L))
				.thenReturn(null);
		when(billingCycleDao.findLatestByConnection("dl.djb", reading.getConnectionNo())).thenReturn(latest);

		assertThrows(IllegalStateException.class, () -> service.processDjbBilling(reading, new RequestInfo()));
	}

	@Test
	void shouldRejectReadingRollback() {
		MeterReading reading = validReading(3000L, 2500L, 60d, 70d);
		when(masterProvider.getBillingRule(any(), eq("dl.djb"))).thenReturn(new DJBMonthlyBillingRule());
		when(masterProvider.findReadingQualityCode(any(), eq("dl.djb"), eq("OK")))
				.thenReturn(new DJBReadingQualityCode());

		assertThrows(IllegalArgumentException.class, () -> service.processDjbBilling(reading, new RequestInfo()));
	}

	@Test
	void shouldRejectMeterReadingRollback() {
		MeterReading reading = validReading(3000L, 4000L, 70d, 60d);
		when(masterProvider.getBillingRule(any(), eq("dl.djb"))).thenReturn(new DJBMonthlyBillingRule());
		when(masterProvider.findReadingQualityCode(any(), eq("dl.djb"), eq("OK")))
				.thenReturn(new DJBReadingQualityCode());

		assertThrows(IllegalArgumentException.class, () -> service.processDjbBilling(reading, new RequestInfo()));
	}

	private MeterReading validReading(Long lastDate, Long currentDate, Double lastReading, Double currentReading) {
		MeterReading reading = new MeterReading();
		reading.setId("reading-001");
		reading.setTenantId("dl.djb");
		reading.setConnectionNo("WS/DJB/2026-27/000367");
		reading.setLastReadingDate(lastDate);
		reading.setCurrentReadingDate(currentDate);
		reading.setLastReading(lastReading);
		reading.setCurrentReading(currentReading);
		reading.setReadingQualityCode("OK");
		return reading;
	}

	private WaterBillingCycle cycle(String id, Long from, Long to) {
		WaterBillingCycle cycle = new WaterBillingCycle();
		cycle.setId(id);
		cycle.setTenantid("dl.djb");
		cycle.setConnectionno("WS/DJB/2026-27/000367");
		cycle.setBillingperiodfrom(from);
		cycle.setBillingperiodto(to);
		return cycle;
	}
}
