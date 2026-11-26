package org.egov.wscalculation.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.egov.wscalculation.web.models.Property;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class PropertyContextExtractorTest {

    @Test
    void readsDedicatedOpdAreaAndMarksItAsProvided() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("demandNormCode", "C-4");
        details.put("opdArea", new BigDecimal("125.5"));
        Property property = new Property();
        property.setAdditionalDetails(details);

        Map<String, BigDecimal> context = new PropertyContextExtractor(new ObjectMapper())
                .extractContextVariablesForNormsV2(property);

        assertEquals(new BigDecimal("125.5"), context.get("opd_area"));
        assertEquals(BigDecimal.ONE, context.get("opd_area_provided"));
    }
}
