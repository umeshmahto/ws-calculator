package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Arrays;

import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariff;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariffSlab;
import org.egov.wscalculation.djbmonthlybilling.service.TariffCalculationService;
import org.egov.wscalculation.djbmonthlybilling.service.dto.TariffCalculationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TariffCalculationServiceTest {

	private TariffCalculationService service;
	private DJBMonthlyWaterTariff domestic;
	private DJBMonthlyWaterTariff commercial;

	@BeforeEach
	void setUp() {
		service = new TariffCalculationService();

		domestic = tariff("DOMESTIC", slab(0, 20, 5.27, 146.41), slab(20, 30, 26.36, 219.62),
				slab(30, null, 43.93, 292.82));

		commercial = tariff("COMMERCIAL", slab(0, 6, 17.57, 146.41), slab(6, 15, 26.35, 292.82),
				slab(15, 25, 35.14, 585.64), slab(25, 50, 87.85, 1024.87), slab(50, 100, 140.56, 1171.28),
				slab(100, null, 175.69, 1317.69));
	}

	@Test
	void shouldCalculateDomestic20Kl() {
		TariffCalculationResult result = service.calculate(bd("20"), "DOMESTIC", Arrays.asList(domestic));

		assertEquals(bd("105.40"), result.getWaterVolumetricCharge());
		assertEquals(bd("146.41"), result.getServiceCharge());
		assertEquals(bd("251.81"), result.getTotalWaterCharge());
		assertEquals(1, result.getSlabCharges().size());
	}

	@Test
	void shouldCalculateDomestic25KlUsingApplicableSlab() {
		TariffCalculationResult result = service.calculate(bd("25"), "DOMESTIC", Arrays.asList(domestic));

		assertEquals(bd("659.00"), result.getWaterVolumetricCharge());
		assertEquals(bd("219.62"), result.getServiceCharge());
		assertEquals(bd("878.62"), result.getTotalWaterCharge());
		assertEquals(1, result.getSlabCharges().size());

		assertEquals(bd("25.000"), result.getSlabCharges().get(0).getUnits());
		assertEquals(bd("26.36"), result.getSlabCharges().get(0).getRatePerKl());
		assertEquals(bd("659.00"), result.getSlabCharges().get(0).getCharge());
	}

	@Test
	void shouldCalculateDomestic35KlUsingApplicableSlab() {
		TariffCalculationResult result = service.calculate(bd("35"), "DOMESTIC", Arrays.asList(domestic));

		BigDecimal expectedVolumetric = bd("1537.55");
		BigDecimal expectedTotal = bd("1830.37");

		assertEquals(expectedVolumetric, result.getWaterVolumetricCharge());
		assertEquals(bd("292.82"), result.getServiceCharge());
		assertEquals(expectedTotal, result.getTotalWaterCharge());
		assertEquals(1, result.getSlabCharges().size());
	}

	@Test
	void shouldCalculateDomestic39KlUsingSingleApplicableSlab() {
		TariffCalculationResult result = service.calculate(bd("39"), "DOMESTIC", Arrays.asList(domestic));

		assertEquals(bd("1713.27"), result.getWaterVolumetricCharge());
		assertEquals(bd("292.82"), result.getServiceCharge());
		assertEquals(bd("2006.09"), result.getTotalWaterCharge());
		assertEquals(1, result.getSlabCharges().size());
		assertEquals(bd("39.000"), result.getSlabCharges().get(0).getUnits());
		assertEquals(bd("43.93"), result.getSlabCharges().get(0).getRatePerKl());
	}

	@Test
	void shouldNormalizeResidentialToDomestic() {
		TariffCalculationResult result = service.calculate(bd("20"), "residential", Arrays.asList(domestic));

		assertEquals("DOMESTIC", result.getCategory());
	}

	@Test
	void shouldNormalizeBusinessToCommercial() {
		TariffCalculationResult result = service.calculate(bd("10"), "business", Arrays.asList(commercial));

		assertEquals("COMMERCIAL", result.getCategory());
		assertEquals(bd("556.32"), result.getTotalWaterCharge());
	}

	@Test
	void shouldIgnoreInactiveTariff() {
		domestic.setActive(false);

		assertThrows(IllegalArgumentException.class,
				() -> service.calculate(bd("20"), "DOMESTIC", Arrays.asList(domestic)));
	}

	@Test
	void shouldRejectNegativeConsumption() {
		assertThrows(IllegalArgumentException.class,
				() -> service.calculate(bd("-1"), "DOMESTIC", Arrays.asList(domestic)));
	}

	@Test
	void shouldRejectMissingConsumption() {
		assertThrows(IllegalArgumentException.class,
				() -> service.calculate(null, "DOMESTIC", Arrays.asList(domestic)));
	}

	@Test
	void shouldRejectMissingTariff() {
		assertThrows(IllegalArgumentException.class, () -> service.calculate(bd("20"), "DOMESTIC", Arrays.asList()));
	}

	@Test
	void shouldRejectInvalidSlabRange() {
		DJBMonthlyWaterTariff invalid = tariff("DOMESTIC", slab(20, 20, 5.27, 146.41));

		assertThrows(IllegalArgumentException.class,
				() -> service.calculate(bd("25"), "DOMESTIC", Arrays.asList(invalid)));
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

	private DJBMonthlyWaterTariffSlab slab(int from, Integer to, double rate, double serviceCharge) {

		DJBMonthlyWaterTariffSlab slab = new DJBMonthlyWaterTariffSlab();

		slab.setFrom(BigDecimal.valueOf(from));

		if (to != null) {
			slab.setTo(BigDecimal.valueOf(to));
		}

		slab.setRatePerKl(BigDecimal.valueOf(rate));
		slab.setServiceCharge(BigDecimal.valueOf(serviceCharge));
		return slab;
	}

	private BigDecimal bd(String value) {
		return new BigDecimal(value);
	}
}
