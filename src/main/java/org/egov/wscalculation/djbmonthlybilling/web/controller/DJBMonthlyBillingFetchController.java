package org.egov.wscalculation.djbmonthlybilling.web.controller;

import javax.validation.Valid;

import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyBillingFetchService;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingFetchRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingFetchResponse;
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

	public DJBMonthlyBillingFetchController(DJBMonthlyBillingFetchService fetchService) {
		this.fetchService = fetchService;
	}

	/**
	 * Read-only endpoint. It does not create meter readings, billing cycles,
	 * demands or bills. It loads the persisted DJB billing cycle and reconstructs
	 * the tariff/sewerage/rebate breakdown for inspection, then performs a bill
	 * search against billing-service.
	 */
	@PostMapping("/_fetch")
	public ResponseEntity<DJBMonthlyBillingFetchResponse> fetch(@Valid @RequestBody DJBMonthlyBillingFetchRequest request) {

		return new ResponseEntity<>(fetchService.fetch(request), HttpStatus.OK);
	}
}
