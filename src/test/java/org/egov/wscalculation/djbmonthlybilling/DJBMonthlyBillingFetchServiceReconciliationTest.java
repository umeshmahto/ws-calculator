package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyBillingCalculationSnapshotService;
import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyBillingFetchService;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingFetchRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingStatement;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingStatementResponse;
import org.egov.wscalculation.repository.ServiceRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DJBMonthlyBillingFetchServiceReconciliationTest {

    @Mock
    private WaterBillingCycleDao billingCycleDao;

    @Mock
    private DJBMonthlyBillingCalculationSnapshotService snapshotService;

    @Mock
    private ServiceRequestRepository serviceRequestRepository;

    @Test
    void shouldExposeNotGeneratedReconciliationWhenBillDoesNotExist() {
        DJBMonthlyBillingFetchService service = new DJBMonthlyBillingFetchService(
                billingCycleDao,
                snapshotService,
                serviceRequestRepository,
                new ObjectMapper(),
                "http://billing-service",
                "/bill/v2/_search");

        WaterBillingCycle cycle = new WaterBillingCycle();
        cycle.setId("CYCLE-001");
        cycle.setTenantid("dl.djb");
        cycle.setConnectionno("WS/DJB/2026-27/000367");
        cycle.setCalculationid("CALC-001");
        cycle.setDemandid(null);
        cycle.setBillid(null);
        cycle.setStatus(org.egov.wscalculation.djbmonthlybilling.model.enums.BillingCycleStatus.CALCULATED);

        DJBMonthlyBillingStatement statement = statementWithNetAmount(new BigDecimal("230.73"));

        when(billingCycleDao.findLatestByConnection(eq("dl.djb"), eq(cycle.getConnectionno())))
                .thenReturn(cycle);
        when(snapshotService.read(eq("dl.djb"), eq("CALC-001"))).thenReturn(statement);

        DJBMonthlyBillingFetchRequest request = new DJBMonthlyBillingFetchRequest();
        request.setTenantId("dl.djb");
        request.setConnectionNo(cycle.getConnectionno());
        request.setRequestInfo(new RequestInfo());

        DJBMonthlyBillingStatementResponse response = service.fetch(request);

        assertEquals("NOT_GENERATED", response.getBillingStatement().getReconciliation().getStatus());
        assertEquals(new BigDecimal("230.73"),
                response.getBillingStatement().getReconciliation().getCalculatedAmount());
    }

    private DJBMonthlyBillingStatement statementWithNetAmount(BigDecimal amount) {
        return DJBMonthlyBillingStatement.builder()
                .charges(DJBMonthlyBillingStatement.Charges.builder().netAmount(amount).build())
                .build();
    }
}
