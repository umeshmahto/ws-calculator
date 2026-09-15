package org.egov.wscalculation.djbmonthlybilling.web.controller;

import javax.validation.Valid;

import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.ZroVerification;
import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyBillingFetchService;
import org.egov.wscalculation.djbmonthlybilling.service.DJBZroVerificationInboxService;
import org.egov.wscalculation.djbmonthlybilling.service.ZroVerificationService;
import org.egov.wscalculation.djbmonthlybilling.service.ZroVerificationService.ZroVerificationResult;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.repository.ZroVerificationDao;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingStatementResponse;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBZroVerificationFetchRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBZroVerificationFetchResponse;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBZroVerificationRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBZroVerificationResponse;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBZroVerificationSearchRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBZroVerificationSearchResponse;
import org.egov.wscalculation.util.ResponseInfoFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/zro-verification")
public class DJBZroVerificationController {

    private final ResponseInfoFactory responseInfoFactory;
    private final ZroVerificationService zroVerificationService;
    private final DJBZroVerificationInboxService inboxService;
    private final DJBMonthlyBillingFetchService billingFetchService;
    private final WaterBillingCycleDao billingCycleDao;
    private final ZroVerificationDao zroVerificationDao;

    public DJBZroVerificationController(ResponseInfoFactory responseInfoFactory,
            ZroVerificationService zroVerificationService,
            DJBZroVerificationInboxService inboxService,
            DJBMonthlyBillingFetchService billingFetchService,
            WaterBillingCycleDao billingCycleDao,
            ZroVerificationDao zroVerificationDao) {
        this.responseInfoFactory = responseInfoFactory;
        this.zroVerificationService = zroVerificationService;
        this.inboxService = inboxService;
        this.billingFetchService = billingFetchService;
        this.billingCycleDao = billingCycleDao;
        this.zroVerificationDao = zroVerificationDao;
    }

    /**
     * ZRO inbox. Returns only cases that are waiting for a ZRO decision.
     */
    @RequestMapping(value = "/_search", method = RequestMethod.POST, produces = "application/json")
    public ResponseEntity<DJBZroVerificationSearchResponse> search(
            @Valid @RequestBody DJBZroVerificationSearchRequest request) {
        DJBZroVerificationSearchResponse response = inboxService.search(request);
        response.setResponseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true));
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * ZRO detail screen. Loads the immutable billing calculation snapshot together
     * with the current billing-cycle/ZRO state. No calculation or billing side effect.
     */
    @RequestMapping(value = "/_fetch", method = RequestMethod.POST, produces = "application/json")
    public ResponseEntity<DJBZroVerificationFetchResponse> fetch(
            @Valid @RequestBody DJBZroVerificationFetchRequest request) {
        String tenantId = request.getRequestInfo().getUserInfo().getTenantId();
        WaterBillingCycle cycle = billingCycleDao.findById(tenantId, request.getBillingCycleId());
        if (cycle == null) {
            throw new IllegalArgumentException("Billing cycle not found: " + request.getBillingCycleId());
        }
        ZroVerification verification = zroVerificationDao.findByBillingCycle(tenantId, request.getBillingCycleId());
        if (verification == null) {
            throw new IllegalArgumentException("ZRO verification not found for billing cycle: "
                    + request.getBillingCycleId());
        }

        org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingFetchRequest billingRequest =
                new org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingFetchRequest();
        billingRequest.setRequestInfo(request.getRequestInfo());
        billingRequest.setTenantId(tenantId);
        billingRequest.setConnectionNo(cycle.getConnectionno());
        billingRequest.setBillingCycleId(cycle.getId());
        DJBMonthlyBillingStatementResponse statementResponse = billingFetchService.fetch(billingRequest);

        DJBZroVerificationFetchResponse response = DJBZroVerificationFetchResponse.builder()
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .verification(verification)
                .billingCycle(cycle)
                .billingStatement(statementResponse.getBillingStatement())
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * ZRO decision. APPROVE generates the actual bill; REJECT switches to the
     * documented provisional fallback path.
     */
    @RequestMapping(value = "/_update", method = RequestMethod.POST, produces = "application/json")
    public ResponseEntity<DJBZroVerificationResponse> update(@Valid @RequestBody DJBZroVerificationRequest request) {

        ZroVerificationResult result = zroVerificationService.update(request.getRequestInfo(),
                request.getBillingCycleId(), request.getAction(), request.getRemarks());

        DJBZroVerificationResponse response = DJBZroVerificationResponse.builder()
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .verification(result.getVerification()).billingCycle(result.getBillingCycle())
                .demandId(result.getDemandId()).billId(result.getBillId()).message(result.getMessage()).build();

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
