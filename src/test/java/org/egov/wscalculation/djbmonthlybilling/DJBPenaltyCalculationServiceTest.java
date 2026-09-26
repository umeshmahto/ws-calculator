package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.egov.wscalculation.djbmonthlybilling.service.DJBPenaltyCalculationService;
import org.egov.wscalculation.djbmonthlybilling.service.dto.DJBPenaltyCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.DJBPenaltyCalculationResult;
import org.junit.jupiter.api.Test;

class DJBPenaltyCalculationServiceTest {

    private final DJBPenaltyCalculationService service = new DJBPenaltyCalculationService();

    @Test
    void shouldCalculateTwoHundredPerDishonouredCheque() {
        DJBPenaltyCalculationResult result = service.calculate(DJBPenaltyCalculationContext.builder()
                .dishonouredChequeCount(3)
                .build());

        assertEquals(new BigDecimal("600.00"), result.getTotalPenalty());
        assertEquals(1, result.getItems().size());
        assertEquals("DJB_DISHONOURED_CHEQUE_PENALTY", result.getItems().get(0).getCode());
    }

    @Test
    void shouldCalculateStandardRegularizationPenaltyOutsideSchemePeriod() {
        DJBPenaltyCalculationResult domestic = service.calculate(DJBPenaltyCalculationContext.builder()
                .regularizationRequired(true)
                .connectionType("DOMESTIC")
                .regularizationDate(LocalDate.of(2026, 9, 1))
                .build());

        DJBPenaltyCalculationResult nonDomestic = service.calculate(DJBPenaltyCalculationContext.builder()
                .regularizationRequired(true)
                .connectionType("COMMERCIAL")
                .regularizationDate(LocalDate.of(2026, 9, 1))
                .build());

        assertEquals(new BigDecimal("3000.00"), domestic.getTotalPenalty());
        assertEquals(new BigDecimal("3000.00"), nonDomestic.getTotalPenalty());
    }

    @Test
    void shouldApplySchemePeriodRegularizationPenaltyByConnectionType() {
        DJBPenaltyCalculationResult domestic = service.calculate(DJBPenaltyCalculationContext.builder()
                .regularizationRequired(true)
                .connectionType("DOMESTIC")
                .regularizationDate(LocalDate.of(2025, 10, 14))
                .build());

        DJBPenaltyCalculationResult nonDomestic = service.calculate(DJBPenaltyCalculationContext.builder()
                .regularizationRequired(true)
                .connectionType("NON_DOMESTIC")
                .regularizationDate(LocalDate.of(2026, 1, 31))
                .build());

        assertEquals(new BigDecimal("1000.00"), domestic.getTotalPenalty());
        assertEquals(new BigDecimal("5000.00"), nonDomestic.getTotalPenalty());
    }

    @Test
    void shouldCombineBothPenaltyEvents() {
        DJBPenaltyCalculationResult result = service.calculate(DJBPenaltyCalculationContext.builder()
                .dishonouredChequeCount(2)
                .regularizationRequired(true)
                .connectionType("DOMESTIC")
                .regularizationDate(LocalDate.of(2026, 9, 1))
                .build());

        assertEquals(new BigDecimal("3400.00"), result.getTotalPenalty());
        assertEquals(2, result.getItems().size());
    }

    @Test
    void shouldCalculateFirstOffenceMisuseWastageFineWithinStatutoryCap() {
        DJBPenaltyCalculationResult result = service.calculate(DJBPenaltyCalculationContext.builder()
                .misuseWastageOffence(true)
                .subsequentMisuseWastageOffence(false)
                .misuseWastageFineAmount(new BigDecimal("1500"))
                .build());

        assertEquals(new BigDecimal("1500.00"), result.getTotalPenalty());
        assertEquals("DJB_MISUSE_WASTAGE_FIRST_OFFENCE_PENALTY", result.getItems().get(0).getCode());
    }

    @Test
    void shouldCalculateSubsequentMisuseWastageDailyFine() {
        DJBPenaltyCalculationResult result = service.calculate(DJBPenaltyCalculationContext.builder()
                .misuseWastageOffence(true)
                .subsequentMisuseWastageOffence(true)
                .misuseWastageDays(3)
                .misuseWastageFineAmount(new BigDecimal("500"))
                .build());

        assertEquals(new BigDecimal("1500.00"), result.getTotalPenalty());
    }

    @Test
    void shouldRejectMisuseWastageFineAboveStatutoryCap() {
        assertThrows(IllegalArgumentException.class, () -> service.calculate(
                DJBPenaltyCalculationContext.builder()
                        .misuseWastageOffence(true)
                        .misuseWastageFineAmount(new BigDecimal("2000.01"))
                        .build()));
    }

    @Test
    void shouldRejectMissingRegularizationDate() {
        assertThrows(IllegalArgumentException.class, () -> service.calculate(
                DJBPenaltyCalculationContext.builder()
                        .regularizationRequired(true)
                        .connectionType("DOMESTIC")
                        .build()));
    }

    @Test
    void shouldRejectNegativeChequeCount() {
        assertThrows(IllegalArgumentException.class, () -> service.calculate(
                DJBPenaltyCalculationContext.builder()
                        .dishonouredChequeCount(-1)
                        .build()));
    }
}
