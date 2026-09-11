package org.egov.wscalculation.djbmonthlybilling.web.controller;

import javax.validation.Valid;

import org.egov.wscalculation.djbmonthlybilling.service.ZroVerificationService;
import org.egov.wscalculation.djbmonthlybilling.service.ZroVerificationService.ZroVerificationResult;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBZroVerificationRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBZroVerificationResponse;
import org.egov.wscalculation.util.ResponseInfoFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/djbZroVerification")
public class DJBZroVerificationController {

	private final ResponseInfoFactory responseInfoFactory;
	private final ZroVerificationService zroVerificationService;

	public DJBZroVerificationController(ResponseInfoFactory responseInfoFactory,
			ZroVerificationService zroVerificationService) {
		this.responseInfoFactory = responseInfoFactory;
		this.zroVerificationService = zroVerificationService;
	}

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
