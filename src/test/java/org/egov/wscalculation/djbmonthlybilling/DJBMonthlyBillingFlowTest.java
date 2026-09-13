package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyRebate;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlySewerageRule;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariff;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariffSlab;
import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyBillingTestService;
import org.egov.wscalculation.djbmonthlybilling.service.RebateCalculationService;
import org.egov.wscalculation.djbmonthlybilling.service.SewerageCalculationService;
import org.egov.wscalculation.djbmonthlybilling.service.TariffCalculationService;
import org.egov.wscalculation.djbmonthlybilling.service.master.DJBMonthlyBillingMasterProvider;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingTestRequest;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingTestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DJBMonthlyBillingFlowTest {

	private DJBMonthlyBillingTestService service;
	private DJBMonthlyBillingMasterProvider masterProvider;

	private DJBMonthlyWaterTariff domesticTariff;
	private DJBMonthlySewerageRule waterConnectedSewerageRule;

	@BeforeEach
	void setUp() {
		masterProvider = mock(DJBMonthlyBillingMasterProvider.class);

		service = new DJBMonthlyBillingTestService(masterProvider, new TariffCalculationService(),
				new SewerageCalculationService(), new RebateCalculationService());

		domesticTariff = tariff("DOMESTIC", slab(0, 20, "5.27", "146.41"), slab(20, 30, "26.36", "219.62"),
				slab(30, null, "43.93", "292.82"));

		waterConnectedSewerageRule = new DJBMonthlySewerageRule();
		waterConnectedSewerageRule.setCode("WATER_CONNECTED");
		waterConnectedSewerageRule.setPercentage(bd("60"));
		waterConnectedSewerageRule.setActive(true);

		when(masterProvider.getWaterTariffs(any(RequestInfo.class), eq("pb.djb")))
				.thenReturn(Collections.singletonList(domesticTariff));
		when(masterProvider.getSewerageRules(any(RequestInfo.class), eq("pb.djb")))
				.thenReturn(Collections.singletonList(waterConnectedSewerageRule));
		when(masterProvider.getAdditionalSewerageCharges(any(RequestInfo.class), eq("pb.djb")))
				.thenReturn(Collections.emptyList());
		when(masterProvider.getRebates(any(RequestInfo.class), eq("pb.djb")))
				.thenReturn(Collections.<DJBMonthlyRebate>emptyList());
	}

	@Test
	void shouldCalculateNormalDomestic20KlFlow() {
		DJBMonthlyBillingTestResponse response = service
				.calculate(request("CONN-001", "DOMESTIC", "20", "OK", BillingBasis.ACTUAL));

		assertBigDecimalEquals("20.000", response.getConsumption());
		assertEquals("ACTUAL", response.getBillingBasis());

		assertBigDecimalEquals("105.40", response.getWater().getWaterVolumetricCharge());
		assertBigDecimalEquals("146.41", response.getWater().getServiceCharge());
		assertBigDecimalEquals("251.81", response.getWater().getTotalWaterCharge());

		// DJB water-connected sewerage = 60% of water volumetric charge.
		assertBigDecimalEquals("63.24", response.getSewerage().getTotalSewerageCharge());
		assertBigDecimalEquals("315.05", response.getTotalBeforeRebate());
		assertBigDecimalEquals("315.05", response.getNetAmountAfterRebate());
	}

	@Test
	void shouldCalculateProgressiveDomestic25KlFlow() {
		DJBMonthlyBillingTestResponse response = service
				.calculate(request("CONN-002", "DOMESTIC", "25", "OK", BillingBasis.ACTUAL));

		assertBigDecimalEquals("237.20", response.getWater().getWaterVolumetricCharge());
		assertBigDecimalEquals("219.62", response.getWater().getServiceCharge());
		assertBigDecimalEquals("456.82", response.getWater().getTotalWaterCharge());

		assertBigDecimalEquals("142.32", response.getSewerage().getTotalSewerageCharge());
		assertBigDecimalEquals("599.14", response.getTotalBeforeRebate());
		assertBigDecimalEquals("599.14", response.getNetAmountAfterRebate());
	}

	@Test
	void shouldCalculateHighConsumptionDomestic35KlAcrossThreeSlabs() {
		DJBMonthlyBillingTestResponse response = service
				.calculate(request("CONN-003", "DOMESTIC", "35", "OK", BillingBasis.ACTUAL));

		assertBigDecimalEquals("588.65", response.getWater().getWaterVolumetricCharge());
		assertBigDecimalEquals("292.82", response.getWater().getServiceCharge());
		assertBigDecimalEquals("881.47", response.getWater().getTotalWaterCharge());

		assertBigDecimalEquals("353.19", response.getSewerage().getTotalSewerageCharge());
		assertBigDecimalEquals("1234.66", response.getTotalBeforeRebate());
		assertBigDecimalEquals("1234.66", response.getNetAmountAfterRebate());
	}

	@Test
	void shouldRejectNegativeConsumptionBeforeCalculation() {
		DJBMonthlyBillingTestRequest request = request("CONN-004", "DOMESTIC", "-1", "OK", BillingBasis.ACTUAL);

		assertThrows(IllegalArgumentException.class, () -> service.calculate(request));
	}

	@Test
	void shouldRejectMissingReadingQualityCode() {
		DJBMonthlyBillingTestRequest request = request("CONN-005", "DOMESTIC", "20", null, BillingBasis.ACTUAL);

		assertThrows(IllegalArgumentException.class, () -> service.calculate(request));
	}

	private DJBMonthlyBillingTestRequest request(String connectionNo, String tariffCategory, String consumption,
			String readingQualityCode, BillingBasis billingBasis) {

		DJBMonthlyBillingTestRequest request = new DJBMonthlyBillingTestRequest();
		request.setRequestInfo(mock(RequestInfo.class));
		request.setTenantId("pb.djb");
		request.setConnectionNo(connectionNo);
		request.setTariffCategory(tariffCategory);
		request.setConsumption(bd(consumption));
		request.setPreviousConsumption(bd("15"));
		request.setBillingBasis(billingBasis);
		request.setReadingQualityCode(readingQualityCode);
		request.setWaterConnectionAvailable(true);
		request.setSewerConnectionAvailable(true);
		request.setAdditionalWaterSource(false);
		request.setConsumerCategory("INDIVIDUAL_RESIDENCE");
		request.setPropertyUsage("RESIDENTIAL");
		request.setBuiltUpAreaSqm(bd("100"));
		request.setFreeWaterEligibleAmount(BigDecimal.ZERO.setScale(2));
		return request;
	}

	private DJBMonthlyWaterTariff tariff(String category, DJBMonthlyWaterTariffSlab... slabs) {
		DJBMonthlyWaterTariff tariff = new DJBMonthlyWaterTariff();
		tariff.setId(category);
		tariff.setCategory(category);
		tariff.setConnectionType("METERED");
		tariff.setActive(true);
		tariff.setSlabs(Arrays.asList(slabs));
		return tariff;
	}

	private DJBMonthlyWaterTariffSlab slab(int from, Integer to, String rate, String serviceCharge) {

		DJBMonthlyWaterTariffSlab slab = new DJBMonthlyWaterTariffSlab();
		slab.setFrom(BigDecimal.valueOf(from));
		slab.setTo(to == null ? null : BigDecimal.valueOf(to));
		slab.setRatePerKl(bd(rate));
		slab.setServiceCharge(bd(serviceCharge));
		return slab;
	}

	private BigDecimal bd(String value) {
		return new BigDecimal(value);
	}

	private void assertBigDecimalEquals(String expected, BigDecimal actual) {
		assertEquals(0, actual.compareTo(new BigDecimal(expected)), "Expected " + expected + " but was " + actual);
	}

}
