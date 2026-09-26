package org.egov.wscalculation.djbmonthlybilling.web.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import javax.validation.Valid;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.service.DJBPenaltyCalculationService;
import org.egov.wscalculation.djbmonthlybilling.service.dto.DJBPenaltyCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.DJBPenaltyCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBPenaltyCalculationRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBPenaltyCalculationResponse;
import org.egov.wscalculation.util.ResponseInfoFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/djb/penalty")
public class DJBPenaltyCalculationController {

	private static final ZoneId DJB_ZONE = ZoneId.of("Asia/Kolkata");

	private final DJBPenaltyCalculationService penaltyCalculationService;
	private final ResponseInfoFactory responseInfoFactory;

	public DJBPenaltyCalculationController(DJBPenaltyCalculationService penaltyCalculationService,
			ResponseInfoFactory responseInfoFactory) {
		this.penaltyCalculationService = penaltyCalculationService;
		this.responseInfoFactory = responseInfoFactory;
	}

	/**
	 * Calculates DJB one-time penalties. This endpoint is deliberately separate
	 * from monthly meter billing because dishonoured-cheque and regularization
	 * penalties are event-driven rather than meter-consumption driven.
	 */
	@PostMapping("/_calculate")
	public ResponseEntity<DJBPenaltyCalculationResponse> calculate(
			@Valid @RequestBody DJBPenaltyCalculationRequest request) {

		validate(request);

		LocalDate regularizationDate = request.getRegularizationDate() == null ? null
				: Instant.ofEpochMilli(request.getRegularizationDate()).atZone(DJB_ZONE).toLocalDate();

		DJBPenaltyCalculationContext context = DJBPenaltyCalculationContext.builder()
				.dishonouredChequeCount(request.getDishonouredChequeCount())
				.regularizationRequired(Boolean.TRUE.equals(request.getRegularizationRequired()))
				.connectionType(request.getConnectionType()).regularizationDate(regularizationDate)
				.misuseWastageOffence(Boolean.TRUE.equals(request.getMisuseWastageOffence()))
				.subsequentMisuseWastageOffence(Boolean.TRUE.equals(request.getSubsequentMisuseWastageOffence()))
				.misuseWastageDays(request.getMisuseWastageDays())
				.misuseWastageFineAmount(request.getMisuseWastageFineAmount()).build();

		DJBPenaltyCalculationResult result = penaltyCalculationService.calculate(context);

		DJBPenaltyCalculationResponse response = DJBPenaltyCalculationResponse.builder()
				.responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
				.tenantId(request.getTenantId()).connectionNo(request.getConnectionNo())
				.totalPenalty(result.getTotalPenalty()).penaltyItems(result.getItems())
				.explanation(result.getExplanation()).build();

		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	private void validate(DJBPenaltyCalculationRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Penalty calculation request is required");
		}

		RequestInfo requestInfo = request.getRequestInfo();
		if (requestInfo == null) {
			throw new IllegalArgumentException("requestInfo is required");
		}

		if (request.getDishonouredChequeCount() != null && request.getDishonouredChequeCount() < 0) {
			throw new IllegalArgumentException("dishonouredChequeCount cannot be negative");
		}

		if (Boolean.TRUE.equals(request.getRegularizationRequired()) && request.getRegularizationDate() == null) {
			throw new IllegalArgumentException("regularizationDate is required when regularizationRequired is true");
		}

		if (Boolean.TRUE.equals(request.getSubsequentMisuseWastageOffence())
				&& !Boolean.TRUE.equals(request.getMisuseWastageOffence())) {
			throw new IllegalArgumentException("subsequentMisuseWastageOffence requires misuseWastageOffence=true");
		}

		if (Boolean.TRUE.equals(request.getMisuseWastageOffence())) {
			boolean subsequent = Boolean.TRUE.equals(request.getSubsequentMisuseWastageOffence());
			if (request.getMisuseWastageFineAmount() == null) {
				throw new IllegalArgumentException("misuseWastageFineAmount is required for a misuse/wastage offence");
			}
			if (request.getMisuseWastageFineAmount().signum() < 0) {
				throw new IllegalArgumentException("misuseWastageFineAmount cannot be negative");
			}
			if (subsequent && (request.getMisuseWastageDays() == null || request.getMisuseWastageDays() <= 0)) {
				throw new IllegalArgumentException(
						"misuseWastageDays must be greater than zero for a subsequent offence");
			}
		}
	}
}
