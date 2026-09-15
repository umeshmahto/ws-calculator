package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.BillingCorrection;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.enums.CorrectionStatus;
import org.egov.wscalculation.djbmonthlybilling.repository.BillingCorrectionDao;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.service.CorrectionService;
import org.egov.wscalculation.djbmonthlybilling.service.ResidualCreditService;
import org.egov.wscalculation.djbmonthlybilling.service.dto.CorrectionPlan;
import org.egov.wscalculation.repository.DemandRepository;
import org.egov.wscalculation.service.DemandService;
import org.egov.wscalculation.web.models.Demand;
import org.egov.wscalculation.web.models.DemandDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CorrectionServiceTest {

	@Mock
	private WaterBillingCycleDao billingCycleDao;

	@Mock
	private BillingCorrectionDao billingCorrectionDao;

	@Mock
	private DemandRepository demandRepository;

	@Mock
	private DemandService demandService;

	@Mock
	private ResidualCreditService residualCreditService;

	@InjectMocks
	private CorrectionService service;

	@Test
	void shouldBuildCorrectionPlanForInterveningAverageAndProvisionalCycles() {
		String tenantId = "dl.djb";
		String connectionNo = "WS/DJB/2026-27/000367";

		WaterBillingCycle previousOk = cycle("ok-1", BillingBasis.ACTUAL, 100L, 200L);
		previousOk.setCurrentreading(new BigDecimal("100"));

		WaterBillingCycle average = cycle("avg-1", BillingBasis.AVERAGE, 200L, 300L);
		WaterBillingCycle provisional = cycle("prov-1", BillingBasis.PROVISIONAL, 300L, 400L);
		WaterBillingCycle actual = cycle("actual-1", BillingBasis.ACTUAL, 400L, 500L);

		WaterBillingCycle currentOk = cycle("ok-2", BillingBasis.ACTUAL, 500L, 600L);
		currentOk.setCurrentreading(new BigDecimal("160"));
		currentOk.setReadingqualitycode("OK");

		when(billingCycleDao.findPreviousOkByConnectionBefore(eq(tenantId), eq(connectionNo),
				eq(currentOk.getBillingperiodto()))).thenReturn(previousOk);

		when(billingCycleDao.findCyclesForCorrection(eq(tenantId), eq(connectionNo),
				eq(previousOk.getBillingperiodto()), eq(currentOk.getBillingperiodto())))
				.thenReturn(Arrays.asList(average, provisional, actual));

		CorrectionPlan plan = service.buildCorrectionPlan(tenantId, currentOk);

		assertTrue(plan.isCorrectionRequired());
		assertEquals("ok-1", plan.getPreviousOkBillingCycleId());
		assertEquals("ok-2", plan.getCurrentOkBillingCycleId());
		assertEquals(new BigDecimal("60"), plan.getCorrectedConsumption());
		assertEquals(2, plan.getCyclesToCorrect().size());
		assertEquals("avg-1", plan.getCyclesToCorrect().get(0).getId());
		assertEquals("prov-1", plan.getCyclesToCorrect().get(1).getId());
	}

	@Test
	void shouldCapturePaidAdjustmentFromInterveningEstimatedDemand() {
		String tenantId = "dl.djb";
		String connectionNo = "WS/DJB/2026-27/000367";

		WaterBillingCycle previousOk = cycle("ok-1", BillingBasis.ACTUAL, 100L, 200L);
		previousOk.setCurrentreading(new BigDecimal("100"));

		WaterBillingCycle provisional = cycle("prov-1", BillingBasis.PROVISIONAL, 200L, 300L);
		provisional.setDemandid("demand-prov-1");

		WaterBillingCycle currentOk = cycle("ok-2", BillingBasis.ACTUAL, 300L, 400L);
		currentOk.setCurrentreading(new BigDecimal("150"));
		currentOk.setReadingqualitycode("OK");

		Demand demand = new Demand();
		demand.setId("demand-prov-1");
		DemandDetail detail = new DemandDetail();
		detail.setCollectionAmount(new BigDecimal("75.50"));
		demand.setDemandDetails(Collections.singletonList(detail));

		BillingCorrection existing = null;

		when(billingCycleDao.findPreviousOkByConnectionBefore(eq(tenantId), eq(connectionNo),
				eq(currentOk.getBillingperiodto()))).thenReturn(previousOk);

		when(billingCycleDao.findCyclesForCorrection(eq(tenantId), eq(connectionNo),
				eq(previousOk.getBillingperiodto()), eq(currentOk.getBillingperiodto())))
				.thenReturn(Collections.singletonList(provisional));

		when(demandService.searchDemand(eq(tenantId), eq(Collections.singleton(connectionNo)),
				eq(provisional.getBillingperiodfrom()), eq(provisional.getBillingperiodto()), any(RequestInfo.class),
				eq(null), eq(false), eq(false))).thenReturn(Collections.singletonList(demand));

		when(billingCorrectionDao.findByConnection(eq(tenantId), eq(connectionNo)))
				.thenReturn(existing == null ? Collections.emptyList() : Collections.singletonList(existing));

		RequestInfo requestInfo = new RequestInfo();

		CorrectionService.CorrectionPlanResult result = service.processAutomaticCorrection(requestInfo, tenantId,
				currentOk, "SYSTEM", 12345L);

		assertTrue(result.isCorrectionRequired());
		assertEquals(new BigDecimal("75.50"), result.getPlan().getPaidAdjustmentAmount());
		assertEquals(new BigDecimal("50"), result.getPlan().getCorrectedConsumption());
		assertEquals(CorrectionStatus.PENDING, result.getCorrection().getStatus());

		verify(billingCorrectionDao).save(any(BillingCorrection.class));
	}

	private WaterBillingCycle cycle(String id, BillingBasis basis, long from, long to) {
		WaterBillingCycle cycle = new WaterBillingCycle();
		cycle.setId(id);
		cycle.setTenantid("dl.djb");
		cycle.setConnectionno("WS/DJB/2026-27/000367");
		cycle.setBillingperiodfrom(from);
		cycle.setBillingperiodto(to);
		cycle.setBillingbasis(basis);
		return cycle;
	}
}
