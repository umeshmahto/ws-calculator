package org.egov.wscalculation.djbmonthlybilling.web.controller;

import javax.validation.Valid;

import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyBillingFetchService;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingFetchRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingStatementResponse;
import org.egov.wscalculation.util.ResponseInfoFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/djb/monthly-billing")
public class DJBMonthlyBillingFetchController {

    private final DJBMonthlyBillingFetchService fetchService;
    private final ResponseInfoFactory responseInfoFactory;

    public DJBMonthlyBillingFetchController(
            DJBMonthlyBillingFetchService fetchService,
            ResponseInfoFactory responseInfoFactory) {
        this.fetchService = fetchService;
        this.responseInfoFactory = responseInfoFactory;
    }

    /**
     * Read-only production billing-statement endpoint.
     * The calculation section is loaded from the immutable persisted calculation
     * snapshot used to generate the demand/bill; the billing-service is queried
     * only to enrich and reconcile the final bill details.
     */
    @PostMapping("/_fetch")
    public ResponseEntity<DJBMonthlyBillingStatementResponse> fetch(
            @Valid @RequestBody DJBMonthlyBillingFetchRequest request) {

        DJBMonthlyBillingStatementResponse serviceResponse = fetchService.fetch(request);
        serviceResponse.setResponseInfo(
                responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true));

        return new ResponseEntity<>(serviceResponse, HttpStatus.OK);
    }
}
