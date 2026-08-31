package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBMonthlyWaterTariff;
import org.egov.wscalculation.djbmonthlybilling.model.master.DJBReadingQualityCode;
import org.junit.jupiter.api.Test;

class MdmsMasterDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeReadingQualityCode() throws Exception {
        String json = "{"
                + "\"code\":\"MLOC\","
                + "\"name\":\"Meter Locked\","
                + "\"billingTreatment\":\"AVERAGE\","
                + "\"active\":true"
                + "}";

        DJBReadingQualityCode value =
                objectMapper.readValue(json, DJBReadingQualityCode.class);

        assertEquals("MLOC", value.getCode());
        assertEquals("AVERAGE", value.getBillingTreatment());
        assertEquals(Boolean.TRUE, value.getActive());
    }

    @Test
    void shouldDeserializeNestedTariffSlabs() throws Exception {
        String json = "{"
                + "\"id\":\"DOMESTIC\","
                + "\"category\":\"DOMESTIC\","
                + "\"connectionType\":\"METERED\","
                + "\"active\":true,"
                + "\"slabs\":["
                + "{"
                + "\"from\":0,"
                + "\"to\":20,"
                + "\"ratePerKl\":5.27,"
                + "\"serviceCharge\":146.41"
                + "}"
                + "]"
                + "}";

        DJBMonthlyWaterTariff value =
                objectMapper.readValue(json, DJBMonthlyWaterTariff.class);

        assertNotNull(value.getSlabs());
        assertEquals(1, value.getSlabs().size());
        assertEquals("5.27",
                value.getSlabs().get(0).getRatePerKl().toPlainString());
    }
}
