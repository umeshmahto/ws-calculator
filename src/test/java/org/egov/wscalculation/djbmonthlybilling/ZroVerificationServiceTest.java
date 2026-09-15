package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.ZroVerification;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingCycleStatus;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ZroStatus;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.repository.ZroVerificationDao;
import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyBillingCalculationSnapshotService;
import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyDemandService;
import org.egov.wscalculation.djbmonthlybilling.service.DJBShadowMeterBillingService;
import org.egov.wscalculation.djbmonthlybilling.service.ZroVerificationService;
import org.egov.wscalculation.web.models.Demand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZroVerificationServiceTest {

	@Mock
	private WaterBillingCycleDao billingCycleDao;

	@Mock
	private ZroVerificationDao zroVerificationDao;

	@Mock
	private DJBMonthlyDemandService demandService;

	@Mock
	private DJBShadowMeterBillingService djbShadowMeterBillingService;

	@Mock
	private DJBMonthlyBillingCalculationSnapshotService calculationSnapshotService;

	@InjectMocks
	private ZroVerificationService service;

	@Test
	void shouldApproveOnePointFiveXAndHandOffToBilling() {
		String tenantId = "dl.djb";
		String cycleId = "cycle-zro-approve";
		WaterBillingCycle cycle = flaggedCycle(tenantId, cycleId);

		ZroVerification verification = pendingVerification(cycle);

		when(billingCycleDao.findById(eq(tenantId), eq(cycleId))).thenReturn(cycle);
		when(zroVerificationDao.findByBillingCycle(eq(tenantId), eq(cycleId))).thenReturn(verification);
		when(zroVerificationDao.update(eq(verification))).thenReturn(1);
		when(billingCycleDao.update(eq(cycle))).thenReturn(1);

		RequestInfo requestInfo = requestInfo(tenantId, "zro-user");

		ZroVerificationService.ZroVerificationResult result = service.update(requestInfo, cycleId, "APPROVE",
				"Consumption verified");

		assertNotNull(result);
		assertEquals(ZroStatus.APPROVED, verification.getStatus());
		assertEquals(ZroStatus.APPROVED, cycle.getZrostatus());
		assertEquals(BillingCycleStatus.CALCULATED, cycle.getStatus());

		verify(calculationSnapshotService).updateStatus(eq(tenantId), eq(cycle.getCalculationid()), eq("ZRO_APPROVED"));
		verify(djbShadowMeterBillingService).processCalculatedCycleAfterDecision(eq(requestInfo), eq(cycle));
	}

	@Test
	void shouldRejectOnePointFiveXAndCreateFallbackProvisionalBill() {
		String tenantId = "dl.djb";
		String cycleId = "cycle-zro-reject";
		WaterBillingCycle cycle = flaggedCycle(tenantId, cycleId);

		ZroVerification verification = pendingVerification(cycle);

		Demand fallbackDemand = new Demand();
		fallbackDemand.setId("fallback-demand-001");

		DJBMonthlyDemandService.DemandResult fallbackResult = DJBMonthlyDemandService.DemandResult.builder()
				.demandCreated(true).demand(fallbackDemand).billId("fallback-bill-001").build();

		when(billingCycleDao.findById(eq(tenantId), eq(cycleId))).thenReturn(cycle);
		when(zroVerificationDao.findByBillingCycle(eq(tenantId), eq(cycleId))).thenReturn(verification);
		when(zroVerificationDao.update(eq(verification))).thenReturn(1);
		when(billingCycleDao.update(eq(cycle))).thenReturn(1);
		RequestInfo requestInfo = requestInfo(tenantId, "zro-user");
		when(demandService.createRejectedOnePointFiveFallbackDemand(eq(requestInfo), eq(cycle)))
				.thenReturn(fallbackResult);

		ZroVerificationService.ZroVerificationResult result = service.update(requestInfo, cycleId, "REJECT",
				"Consumption not justified");

		assertNotNull(result);
		assertEquals(ZroStatus.REJECTED, verification.getStatus());
		assertEquals(ZroStatus.REJECTED, cycle.getZrostatus());
		assertEquals(BillingBasis.PROVISIONAL, cycle.getBillingbasis());
		assertEquals("fallback-demand-001", cycle.getDemandid());
		assertEquals("fallback-bill-001", cycle.getBillid());
		assertEquals(BillingCycleStatus.BILL_GENERATED, cycle.getStatus());

		verify(calculationSnapshotService).updateStatus(eq(tenantId), eq(cycle.getCalculationid()), eq("ZRO_REJECTED"));
		verify(demandService).createRejectedOnePointFiveFallbackDemand(eq(requestInfo), eq(cycle));
	}

	private WaterBillingCycle flaggedCycle(String tenantId, String cycleId) {
		WaterBillingCycle cycle = new WaterBillingCycle();
		cycle.setId(cycleId);
		cycle.setTenantid(tenantId);
		cycle.setConnectionno("WS/DJB/2026-27/000367");
		cycle.setBillingperiodfrom(1780338600000L);
		cycle.setBillingperiodto(1783017000000L);
		cycle.setActualconsumption(new BigDecimal("40"));
		cycle.setPreviousconsumption(new BigDecimal("20"));
		cycle.setBillingconsumption(new BigDecimal("40"));
		cycle.setBillingbasis(BillingBasis.ACTUAL);
		cycle.setOnepointfivexflag(Boolean.TRUE);
		cycle.setZrostatus(ZroStatus.PENDING);
		cycle.setCalculationid("calc-" + cycleId);
		return cycle;
	}

	private ZroVerification pendingVerification(WaterBillingCycle cycle) {
		ZroVerification verification = new ZroVerification();
		verification.setId("verification-" + cycle.getId());
		verification.setTenantid(cycle.getTenantid());
		verification.setBillingcycleid(cycle.getId());
		verification.setConnectionno(cycle.getConnectionno());
		verification.setConsumption(cycle.getActualconsumption());
		verification.setPreviousconsumption(cycle.getPreviousconsumption());
		verification.setStatus(ZroStatus.PENDING);
		return verification;
	}

	private RequestInfo requestInfo(String tenantId, String uuid) {
		RequestInfo requestInfo = new RequestInfo();
		User userInfo = new User();
		userInfo.setTenantId(tenantId);
		userInfo.setUuid(uuid);
		requestInfo.setUserInfo(userInfo);
		return requestInfo;
	}
}
