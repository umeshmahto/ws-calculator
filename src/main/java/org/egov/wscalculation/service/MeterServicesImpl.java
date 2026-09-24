package org.egov.wscalculation.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.repository.WSCalculationDao;
import org.egov.wscalculation.validator.WSCalculationValidator;
import org.egov.wscalculation.validator.WSCalculationWorkflowValidator;
import org.egov.wscalculation.djbmonthlybilling.service.DJBShadowMeterBillingService;
import org.egov.wscalculation.web.models.CalculationCriteria;
import org.egov.wscalculation.web.models.CalculationReq;
import org.egov.wscalculation.web.models.MeterConnectionRequest;
import org.egov.wscalculation.web.models.MeterReading;
import org.egov.wscalculation.web.models.MeterReadingSearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MeterServicesImpl implements MeterService {

	@Autowired
	private WSCalculationDao wSCalculationDao;

	@Autowired
	private WSCalculationValidator wsCalculationValidator;
	
	@Autowired
	private WSCalculationService wSCalculationService;
	
	@Autowired
	private EstimationService estimationService;

	@Autowired
	private DJBShadowMeterBillingService djbShadowMeterBillingService;

	private EnrichmentService enrichmentService;
	
	@Autowired
	private WSCalculationWorkflowValidator wsCalulationWorkflowValidator;

	@Autowired
	public MeterServicesImpl(EnrichmentService enrichmentService) {
		this.enrichmentService = enrichmentService;
	}

	/**
	 * 
	 * @param meterConnectionRequest MeterConnectionRequest contains meter reading connection to be created
	 * @return List of MeterReading after create
	 */

	@Override
	public List<MeterReading> createMeterReading(MeterConnectionRequest meterConnectionRequest) {
		Boolean genratedemand = true;
		List<MeterReading> meterReadingsList = new ArrayList<MeterReading>();
		if(meterConnectionRequest.getMeterReading().getGenerateDemand()){
			if ("dl.djb".equalsIgnoreCase(meterConnectionRequest.getMeterReading().getTenantId())) {
				djbShadowMeterBillingService.resolveAndApplyLastValidReading(meterConnectionRequest.getMeterReading());
				djbShadowMeterBillingService.normalizeAverageReadingForPersistence(
						meterConnectionRequest.getMeterReading(), meterConnectionRequest.getRequestInfo());
			}
			wsCalulationWorkflowValidator.applicationValidation(meterConnectionRequest.getRequestInfo(),meterConnectionRequest.getMeterReading().getTenantId(),meterConnectionRequest.getMeterReading().getConnectionNo(),genratedemand);
			wsCalculationValidator.validateMeterReading(meterConnectionRequest.getRequestInfo(),meterConnectionRequest.getMeterReading(), true);
		}
		enrichmentService.enrichMeterReadingRequest(meterConnectionRequest.getRequestInfo(),meterConnectionRequest.getMeterReading());

		if (meterConnectionRequest.getMeterReading().getGenerateDemand()
				&& "dl.djb".equalsIgnoreCase(meterConnectionRequest.getMeterReading().getTenantId())) {
			djbShadowMeterBillingService.prepareBillingCycleReservation(
						meterConnectionRequest.getMeterReading(),
						meterConnectionRequest.getRequestInfo());
		}

		meterReadingsList.add(meterConnectionRequest.getMeterReading());
		wSCalculationDao.saveMeterReading(meterConnectionRequest);
		if (meterConnectionRequest.getMeterReading().getGenerateDemand()) {
			if ("dl.djb".equalsIgnoreCase(meterConnectionRequest.getMeterReading().getTenantId())) {

				org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle billingCycle =
						djbShadowMeterBillingService.processDjbBilling(
								meterConnectionRequest.getMeterReading(),
								meterConnectionRequest.getRequestInfo());
				if (billingCycle != null) {
					meterConnectionRequest.getMeterReading().setBillingCycleId(billingCycle.getId());
				}
			} else {
				generateDemandForMeterReading(meterReadingsList, meterConnectionRequest.getRequestInfo());
			}
		}
		return meterReadingsList;
	}
	
	@Override
	public List<MeterReading> createMeterReadingBulk(MeterConnectionRequest meterConnectionRequest) {
		Boolean genratedemand = true;
		List<MeterReading> meterReadingsList = new ArrayList<MeterReading>();
		List<MeterReading> meterReadingOutput=new ArrayList<MeterReading>();
		Boolean applicationValid = false,readingValid=false;
		String status=null;
		for(MeterReading mr:meterConnectionRequest.getMeterReadingList()) {
		if(mr.getGenerateDemand()){
			if ("dl.djb".equalsIgnoreCase(mr.getTenantId())) {
				djbShadowMeterBillingService.resolveAndApplyLastValidReading(mr);
				djbShadowMeterBillingService.normalizeAverageReadingForPersistence(
						mr, meterConnectionRequest.getRequestInfo());
			}
			applicationValid=wsCalulationWorkflowValidator.applicationValidationBulk(meterConnectionRequest.getRequestInfo(),mr,genratedemand);
			readingValid=wsCalculationValidator.validateMeterReadingBulk(meterConnectionRequest.getRequestInfo(),mr, true);
		}
		
		log.info("applicationValid ="+applicationValid);
		log.info("readingValid ="+readingValid);

		if(applicationValid && readingValid) {
			if (mr.getGenerateDemand() && "dl.djb".equalsIgnoreCase(mr.getTenantId())) {
				/*
				 * Keep the DJB bulk path on the same billing engine as the single-reading
				 * flow. The legacy bulk path bypassed DJB billing-cycle reservation,
				 * average/1.5x handling and correction orchestration entirely.
				 */
				MeterConnectionRequest djbRequest = MeterConnectionRequest.builder()
						.requestInfo(meterConnectionRequest.getRequestInfo()).meterReading(mr).build();
				djbShadowMeterBillingService.createAndCalculate(djbRequest);
				meterReadingsList.add(mr);
			} else {
				enrichmentService.enrichMeterReadingRequest(meterConnectionRequest.getRequestInfo(),mr);
				meterReadingsList.add(mr);
				meterConnectionRequest.setMeterReading(mr);
				wSCalculationDao.saveMeterReading(meterConnectionRequest);
				if (mr.getGenerateDemand()) {
					generateDemandForMeterReading(meterReadingsList, meterConnectionRequest.getRequestInfo());
				}
			}
		if(mr.getStatus()==null) mr.setStatus("Meter Reading entered successfully");
		}
		meterReadingOutput.add(mr);

		}
		return meterReadingOutput;
	}

	private void generateDemandForMeterReading(List<MeterReading> meterReadingsList, RequestInfo requestInfo) {
		List<CalculationCriteria> criteriaList = new ArrayList<>();
		meterReadingsList.forEach(reading -> {
			CalculationCriteria criteria = new CalculationCriteria();
			criteria.setTenantId(reading.getTenantId());
			criteria.setAssessmentYear(estimationService.getAssessmentYear());
			criteria.setCurrentReading(reading.getCurrentReading());
			criteria.setLastReading(reading.getLastReading());
			criteria.setConnectionNo(reading.getConnectionNo());
			criteria.setFrom(reading.getLastReadingDate());
			criteria.setTo(reading.getCurrentReadingDate());
			criteriaList.add(criteria);
		});
		CalculationReq calculationRequest = CalculationReq.builder().requestInfo(requestInfo)
				.calculationCriteria(criteriaList).isconnectionCalculation(true).build();
		wSCalculationService.getCalculation(calculationRequest);
	}
	
	/**
	 * 
	 * @param criteria
	 *            MeterConnectionSearchCriteria contains meter reading
	 *            connection criteria to be searched for in the meter
	 *            connection table
	 * @return List of MeterReading after search
	 */
	@Override
	public List<MeterReading> searchMeterReadings(MeterReadingSearchCriteria criteria, RequestInfo requestInfo) {
		return wSCalculationDao.searchMeterReadings(criteria);
	}
}
