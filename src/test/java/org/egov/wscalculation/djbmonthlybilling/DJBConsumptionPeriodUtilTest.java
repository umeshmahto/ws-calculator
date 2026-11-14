package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import org.egov.wscalculation.djbmonthlybilling.service.DJBConsumptionPeriodUtil;
import org.junit.jupiter.api.Test;

class DJBConsumptionPeriodUtilTest {

    @Test
    void shouldConvertDjbOucc81ElapsedDaysTo27TariffMonths() {
        Long from = epoch("2026-06-08");
        Long to = epoch("2026-08-28");

        assertEquals(new BigDecimal("81.000000"), DJBConsumptionPeriodUtil.calculateElapsedDays(from, to));
        assertEquals(new BigDecimal("2.7"), DJBConsumptionPeriodUtil.toDjbBillingPeriodMonths(from, to));
    }

    @Test
    void shouldConvert41ElapsedDaysTo13666666667TariffMonths() {
        Long from = epoch("2026-04-01");
        Long to = epoch("2026-05-12");

        assertEquals(new BigDecimal("41.000000"), DJBConsumptionPeriodUtil.calculateElapsedDays(from, to));
        assertEquals(new BigDecimal("1.366666666666666667"), DJBConsumptionPeriodUtil.toDjbBillingPeriodMonths(from, to));
    }

    @Test
    void shouldReturnNullForInvalidOrMissingPeriod() {
        assertNull(DJBConsumptionPeriodUtil.toDjbBillingPeriodMonths(null, epoch("2026-08-28")));
        assertNull(DJBConsumptionPeriodUtil.toDjbBillingPeriodMonths(epoch("2026-08-28"), epoch("2026-08-28")));
        assertNull(DJBConsumptionPeriodUtil.toDjbBillingPeriodMonths(epoch("2026-08-29"), epoch("2026-08-28")));
    }

    private Long epoch(String date) {
        return ZonedDateTime.parse(date + "T00:00:00+05:30[Asia/Kolkata]")
                .toInstant().toEpochMilli();
    }
}
