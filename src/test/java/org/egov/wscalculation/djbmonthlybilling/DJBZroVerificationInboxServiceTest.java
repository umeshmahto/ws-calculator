package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.wscalculation.djbmonthlybilling.model.ZroVerification;
import org.egov.wscalculation.djbmonthlybilling.model.ZroVerificationInboxRecord;
import org.egov.wscalculation.djbmonthlybilling.model.enums.ZroStatus;
import org.egov.wscalculation.djbmonthlybilling.repository.ZroVerificationDao;
import org.egov.wscalculation.djbmonthlybilling.service.DJBZroVerificationInboxService;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBZroVerificationSearchRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBZroVerificationSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DJBZroVerificationInboxServiceTest {

    @Mock
    private ZroVerificationDao dao;

    private DJBZroVerificationInboxService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        service = new DJBZroVerificationInboxService(dao);
    }

    @Test
    void shouldReturnOnlyPendingInboxCases() {
        RequestInfo requestInfo = new RequestInfo();
        User user = new User();
        user.setTenantId("dl.djb");
        requestInfo.setUserInfo(user);

        DJBZroVerificationSearchRequest request = new DJBZroVerificationSearchRequest();
        request.setRequestInfo(requestInfo);
        request.setLimit(20);
        request.setOffset(0);

        ZroVerification verification = new ZroVerification();
        verification.setId("zro-1");
        verification.setTenantid("dl.djb");
        verification.setBillingcycleid("cycle-1");
        verification.setConnectionno("WS/DJB/2026-27/000377");
        verification.setConsumption(new BigDecimal("40"));
        verification.setPreviousconsumption(new BigDecimal("20"));
        verification.setStatus(ZroStatus.PENDING);

        ZroVerificationInboxRecord record = new ZroVerificationInboxRecord();
        record.setVerification(verification);
        record.setBillingcycleid("cycle-1");
        record.setActualconsumption(new BigDecimal("40"));
        record.setOnepointfivexflag(true);
        record.setBillingbasis("ACTUAL");

        when(dao.searchPending("dl.djb", null, 20, 0)).thenReturn(Arrays.asList(record));
        when(dao.countPending("dl.djb", null)).thenReturn(1);

        DJBZroVerificationSearchResponse response = service.search(request);

        assertEquals(1, response.getCount());
        assertEquals(1, response.getCases().size());
        assertEquals("cycle-1", response.getCases().get(0).getBillingCycleId());
        assertEquals(new BigDecimal("30.0"), response.getCases().get(0).getThresholdConsumption());
    }
}
