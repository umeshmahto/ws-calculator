package org.egov.wscalculation.djbmonthlybilling.web.controller;

import javax.validation.Valid;

import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyBillingTestService;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingTestRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingTestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/djb/monthly-billing")
public class DJBMonthlyBillingTestController {

	private final DJBMonthlyBillingTestService testService;

	public DJBMonthlyBillingTestController(DJBMonthlyBillingTestService testService) {
		this.testService = testService;
	}

	@PostMapping("/_test")
	public ResponseEntity<DJBMonthlyBillingTestResponse> calculate(
			@Valid @RequestBody DJBMonthlyBillingTestRequest request) {

		return new ResponseEntity<>(testService.calculate(request), HttpStatus.OK);
	}
}
