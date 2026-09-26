package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.egov.wscalculation.djbmonthlybilling.service.dto.DJBPenaltyCalculationContext;
import org.egov.wscalculation.djbmonthlybilling.service.dto.DJBPenaltyCalculationResult;
import org.egov.wscalculation.djbmonthlybilling.service.dto.DJBPenaltyItem;
import org.springframework.stereotype.Service;

/**
 * Calculates DJB one-time penalties that are owned by the calculator domain.
 *
 * Current DJB source rules:
 * - Dishonoured cheque: Rs. 200 per dishonoured cheque.
 * - Unauthorized-connection regularization penalty:
 *   - Outside the scheme period: Rs. 3,000 for both domestic and non-domestic.
 *   - During 14-Oct-2025 through 31-Aug-2026 scheme period:
 *       Domestic: Rs. 1,000
 *       Non-domestic: Rs. 5,000
 *
 * RWH penalty is intentionally not calculated because the current DJB source
 * states that the RWH penalty is presently waived.
 */
@Service
public class DJBPenaltyCalculationService {

    public static final String DISHONOURED_CHEQUE_PENALTY =
            "DJB_DISHONOURED_CHEQUE_PENALTY";
    public static final String UNAUTHORIZED_REGULARIZATION_PENALTY =
            "DJB_UNAUTHORIZED_REGULARIZATION_PENALTY";

    private static final BigDecimal DISHONOURED_CHEQUE_AMOUNT = new BigDecimal("200.00");
    private static final BigDecimal REGULARIZATION_STANDARD_AMOUNT = new BigDecimal("3000.00");
    private static final BigDecimal REGULARIZATION_SCHEME_DOMESTIC_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal REGULARIZATION_SCHEME_NON_DOMESTIC_AMOUNT = new BigDecimal("5000.00");
    private static final BigDecimal MISUSE_WASTAGE_FIRST_OFFENCE_MAX = new BigDecimal("2000.00");
    private static final BigDecimal MISUSE_WASTAGE_SUBSEQUENT_DAILY_MAX = new BigDecimal("500.00");

    private static final LocalDate REGULARIZATION_SCHEME_FROM = LocalDate.of(2025, 10, 14);
    private static final LocalDate REGULARIZATION_SCHEME_TO = LocalDate.of(2026, 8, 31);

    private static final int MONEY_SCALE = 2;

    public DJBPenaltyCalculationResult calculate(DJBPenaltyCalculationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("DJB penalty calculation context is required");
        }

        List<DJBPenaltyItem> items = new ArrayList<>();
        addDishonouredChequePenalty(context, items);
        addRegularizationPenalty(context, items);
        addMisuseWastagePenalty(context, items);

        BigDecimal total = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        for (DJBPenaltyItem item : items) {
            total = total.add(item.getAmount()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        String explanation = items.isEmpty()
                ? "No DJB calculator-side penalty applicable"
                : items.stream().map(DJBPenaltyItem::getBasis).collect(Collectors.joining("; "));

        return DJBPenaltyCalculationResult.builder()
                .totalPenalty(total)
                .items(items)
                .explanation(explanation)
                .build();
    }

    private void addDishonouredChequePenalty(DJBPenaltyCalculationContext context,
            List<DJBPenaltyItem> items) {

        Integer count = context.getDishonouredChequeCount();
        if (count == null || count == 0) {
            return;
        }
        if (count < 0) {
            throw new IllegalArgumentException("Dishonoured cheque count cannot be negative");
        }

        BigDecimal amount = DISHONOURED_CHEQUE_AMOUNT
                .multiply(BigDecimal.valueOf(count))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        items.add(DJBPenaltyItem.builder()
                .code(DISHONOURED_CHEQUE_PENALTY)
                .name("Dishonoured Cheque Penalty")
                .rateOrUnitAmount(DISHONOURED_CHEQUE_AMOUNT)
                .quantity(count)
                .amount(amount)
                .basis("Rs. 200 per dishonoured cheque")
                .build());
    }

    private void addMisuseWastagePenalty(DJBPenaltyCalculationContext context,
            List<DJBPenaltyItem> items) {
        if (!context.isMisuseWastageOffence()) {
            return;
        }

        BigDecimal fine = context.getMisuseWastageFineAmount();
        if (fine == null || fine.signum() < 0) {
            throw new IllegalArgumentException("misuseWastageFineAmount is required and cannot be negative");
        }

        if (context.isSubsequentMisuseWastageOffence()) {
            Integer days = context.getMisuseWastageDays();
            if (days == null || days <= 0) {
                throw new IllegalArgumentException("misuseWastageDays must be greater than zero for a subsequent offence");
            }
            if (fine.compareTo(MISUSE_WASTAGE_SUBSEQUENT_DAILY_MAX) > 0) {
                throw new IllegalArgumentException(
                        "Subsequent misuse/wastage daily fine cannot exceed Rs. 500");
            }

            BigDecimal amount = fine.multiply(BigDecimal.valueOf(days))
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            items.add(DJBPenaltyItem.builder()
                    .code("DJB_MISUSE_WASTAGE_SUBSEQUENT_PENALTY")
                    .name("Misuse/Wastage Water Subsequent-Offence Penalty")
                    .rateOrUnitAmount(fine.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                    .quantity(days)
                    .amount(amount)
                    .basis("Daily fine up to Rs. 500 for subsequent misuse/wastage offence; " + days + " day(s)")
                    .build());
            return;
        }

        if (fine.compareTo(MISUSE_WASTAGE_FIRST_OFFENCE_MAX) > 0) {
            throw new IllegalArgumentException(
                    "First misuse/wastage offence fine cannot exceed Rs. 2000");
        }

        items.add(DJBPenaltyItem.builder()
                .code("DJB_MISUSE_WASTAGE_FIRST_OFFENCE_PENALTY")
                .name("Misuse/Wastage Water First-Offence Penalty")
                .rateOrUnitAmount(fine.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .quantity(1)
                .amount(fine.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .basis("Fine up to Rs. 2000 for first misuse/wastage offence")
                .build());
    }

    private void addRegularizationPenalty(DJBPenaltyCalculationContext context,
            List<DJBPenaltyItem> items) {

        if (!context.isRegularizationRequired()) {
            return;
        }

        if (context.getRegularizationDate() == null) {
            throw new IllegalArgumentException("Regularization date is required when regularization is applicable");
        }

        String connectionType = normalizeConnectionType(context.getConnectionType());
        BigDecimal amount = resolveRegularizationPenalty(connectionType, context.getRegularizationDate());
        boolean schemePeriod = isWithinRegularizationScheme(context.getRegularizationDate());

        String basis = schemePeriod
                ? "Regularization scheme penalty for " + connectionType
                : "Standard unauthorized-connection regularization penalty for " + connectionType;

        items.add(DJBPenaltyItem.builder()
                .code(UNAUTHORIZED_REGULARIZATION_PENALTY)
                .name("Unauthorized Connection Regularization Penalty")
                .rateOrUnitAmount(amount)
                .quantity(1)
                .amount(amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .basis(basis)
                .build());
    }

    private BigDecimal resolveRegularizationPenalty(String connectionType, LocalDate date) {
        if (isWithinRegularizationScheme(date)) {
            if ("DOMESTIC".equals(connectionType)) {
                return REGULARIZATION_SCHEME_DOMESTIC_AMOUNT;
            }
            return REGULARIZATION_SCHEME_NON_DOMESTIC_AMOUNT;
        }
        return REGULARIZATION_STANDARD_AMOUNT;
    }

    private boolean isWithinRegularizationScheme(LocalDate date) {
        return !date.isBefore(REGULARIZATION_SCHEME_FROM)
                && !date.isAfter(REGULARIZATION_SCHEME_TO);
    }

    private String normalizeConnectionType(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Connection type is required when regularization is applicable");
        }

        String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ENGLISH);

        if ("DOMESTIC".equals(normalized)
                || "CAT_I".equals(normalized)
                || "RESIDENTIAL".equals(normalized)) {
            return "DOMESTIC";
        }

        if ("NON_DOMESTIC".equals(normalized)
                || "NONDOMESTIC".equals(normalized)
                || "COMMERCIAL".equals(normalized)
                || "CAT_II".equals(normalized)) {
            return "NON_DOMESTIC";
        }

        throw new IllegalArgumentException("Unsupported DJB connection type for regularization: " + value);
    }
}
