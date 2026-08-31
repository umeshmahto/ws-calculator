package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Arrays;

import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyRebate;
import org.egov.wscalculation.djbmonthlybilling.service.RebateCalculationService;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.RebateCalculationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RebateCalculationServiceTest {

    private RebateCalculationService service;

    @BeforeEach
    void setUp() {
        service = new RebateCalculationService();
    }

    @Test
    void shouldApply100PercentFreeWaterFor20KlOnOkBasis() {
        RebateCalculationContext context =
                RebateCalculationContext.builder()
                        .consumption(bd("20"))
                        .billingBasis(BillingBasis.ACTUAL)
                        .readingQualityCode("OK")
                        .consumerType("INDIVIDUAL_RESIDENCE")
                        .propertyCategory("CAT-I")
                        .connectionType("DOMESTIC")
                        .bulkConnection(false)
                        .freeWaterEligibleAmount(bd("251.81"))
                        .build();

        DJBMonthlyRebate rule =
                freeWaterRule();

        RebateCalculationResult result =
                service.calculate(context, Arrays.asList(rule));

        assertEquals(bd("251.81"), result.getTotalRebate());
        assertEquals(1, result.getRebateItems().size());
    }

    @Test
    void shouldNotApplyFreeWaterRebateForAverageBilling() {
        RebateCalculationContext context =
                RebateCalculationContext.builder()
                        .consumption(bd("20"))
                        .billingBasis(BillingBasis.AVERAGE)
                        .readingQualityCode("MLOC")
                        .freeWaterEligibleAmount(bd("251.81"))
                        .build();

        RebateCalculationResult result =
                service.calculate(context,
                        Arrays.asList(freeWaterRule()));

        assertEquals(bd("0.00"), result.getTotalRebate());
    }

    @Test
    void shouldNotApplyFreeWaterRebateAbove20Kl() {
        RebateCalculationContext context =
                RebateCalculationContext.builder()
                        .consumption(bd("20.001"))
                        .billingBasis(BillingBasis.ACTUAL)
                        .readingQualityCode("OK")
                        .freeWaterEligibleAmount(bd("500"))
                        .build();

        RebateCalculationResult result =
                service.calculate(context,
                        Arrays.asList(freeWaterRule()));

        assertEquals(bd("0.00"), result.getTotalRebate());
    }

    @Test
    void shouldApply10PercentRwhRebate() {
        RebateCalculationContext context =
                RebateCalculationContext.builder()
                        .propertyAreaSqm(bd("200"))
                        .functionalRwh(true)
                        .functionalWastewaterRecycling(false)
                        .totalBillBeforeRebate(bd("1000"))
                        .build();

        DJBMonthlyRebate rwh =
                rebate("RWH_10", "RWH Rebate",
                        10, 100, true, false);

        RebateCalculationResult result =
                service.calculate(context,
                        Arrays.asList(rwh));

        assertEquals(bd("100.00"), result.getTotalRebate());
    }

    @Test
    void shouldPrefer15PercentRwhCombinedRuleOver10Percent() {
        RebateCalculationContext context =
                RebateCalculationContext.builder()
                        .propertyAreaSqm(bd("500"))
                        .functionalRwh(true)
                        .functionalWastewaterRecycling(true)
                        .totalBillBeforeRebate(bd("1000"))
                        .build();

        DJBMonthlyRebate rwh10 =
                rebate("RWH_10", "RWH Rebate",
                        10, 100, true, false);

        DJBMonthlyRebate rwh15 =
                rebate("RWH_WATER_RECYCLING_15",
                        "RWH + Recycling",
                        15, 500, true, true);

        RebateCalculationResult result =
                service.calculate(context,
                        Arrays.asList(rwh10, rwh15));

        assertEquals(bd("150.00"), result.getTotalRebate());
        assertEquals("RWH_WATER_RECYCLING_15",
                result.getRebateItems().get(0).getCode());
    }

    private DJBMonthlyRebate freeWaterRule() {
        DJBMonthlyRebate rule = new DJBMonthlyRebate();
        rule.setCode("FREE_WATER_20KL");
        rule.setName("Free Water Up To 20 KL");
        rule.setRebateType("PERCENTAGE");
        rule.setRate(bd("100"));
        rule.setMaxConsumptionKl(bd("20"));
        rule.setConsumerType("INDIVIDUAL_RESIDENCE");
        rule.setPropertyCategory("CAT-I");
        rule.setEligibleConnectionType("DOMESTIC");
        rule.setBulkApplicable(false);
        rule.setActive(true);
        return rule;
    }

    private DJBMonthlyRebate rebate(
            String code,
            String name,
            int rate,
            int minArea,
            boolean requiresRwh,
            boolean requiresRecycling) {

        DJBMonthlyRebate rule = new DJBMonthlyRebate();
        rule.setCode(code);
        rule.setName(name);
        rule.setRebateType("PERCENTAGE");
        rule.setRate(bd(String.valueOf(rate)));
        rule.setMinPropertyAreaSqm(bd(String.valueOf(minArea)));
        rule.setRequiresFunctionalRwh(requiresRwh);
        rule.setRequiresFunctionalWastewaterRecycling(requiresRecycling);
        rule.setActive(true);
        return rule;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
