package org.egov.wscalculation.djbmonthlybilling.web.controller;

import java.util.List;

import javax.validation.Valid;

import org.egov.wscalculation.djbmonthlybilling.service.DJBShadowMeterBillingService;
import org.egov.wscalculation.util.ResponseInfoFactory;
import org.egov.wscalculation.web.models.MeterConnectionRequest;
import org.egov.wscalculation.web.models.MeterReading;
import org.egov.wscalculation.web.models.MeterReadingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/djbMeterConnection")
public class DJBMeterReadingTestController {

	private final ResponseInfoFactory responseInfoFactory;
	private final DJBShadowMeterBillingService shadowMeterBillingService;

	public DJBMeterReadingTestController(ResponseInfoFactory responseInfoFactory,DJBShadowMeterBillingService shadowMeterBillingService) {
		this.responseInfoFactory = responseInfoFactory;
		this.shadowMeterBillingService = shadowMeterBillingService;
	}

	/**
	 * DJB-only test endpoint. Difference: legacy immediate demand generation is
	 * intentionally not executed. The DJB billing-cycle calculation runs instead.
	 */
	@RequestMapping(value = "/_create", method = RequestMethod.POST, produces = "application/json")
	public ResponseEntity<MeterReadingResponse> create(@Valid @RequestBody MeterConnectionRequest request) {

		List<MeterReading> meterReadings = shadowMeterBillingService.createAndCalculate(request);

		MeterReadingResponse response = MeterReadingResponse.builder().meterReadings(meterReadings)
				.responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
				.build();

		return new ResponseEntity<>(response, HttpStatus.OK);
	}
}
