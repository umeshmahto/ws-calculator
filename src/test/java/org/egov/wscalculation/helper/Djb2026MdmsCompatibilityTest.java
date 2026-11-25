package org.egov.wscalculation.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.egov.mdms.model.MasterDetail;
import org.egov.mdms.model.MdmsCriteriaReq;
import org.egov.mdms.model.ModuleDetail;
import org.egov.wscalculation.constants.WSCalculationConstant;
import org.egov.wscalculation.util.CalculatorUtil;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the revised DJB masters are requested from MDMS at runtime.
 * No MDMS JSON is bundled in application or test resources. Calculation-value
 * tests use small in-memory objects in their respective unit tests.
 */
class Djb2026MdmsCompatibilityTest {

    @Test
    void estimationRequestFetchesAllThreeRevisedMastersFromMdms() {
        CalculatorUtil calculatorUtil = new CalculatorUtil();
        MdmsCriteriaReq request = calculatorUtil.getEstimationMasterCriteria(null, "dl.djb");

        assertEquals("dl.djb", request.getMdmsCriteria().getTenantId());

        List<ModuleDetail> modules = request.getMdmsCriteria().getModuleDetails();
        Set<String> waterServiceMasters = requestedMasterNames(modules, WSCalculationConstant.WS_TAX_MODULE);
        Set<String> propertyTaxMasters = requestedMasterNames(modules, WSCalculationConstant.PROPERTY_TAX_MODULE);

        assertTrue(waterServiceMasters.contains("WaterDemandNorms2"),
                "WaterDemandNorms2 must be requested from ws-services-calculation MDMS module");
        assertTrue(propertyTaxMasters.contains("PropertyType2"),
                "PropertyType2 must be requested from PropertyTax MDMS module");
        assertTrue(propertyTaxMasters.contains("PropertyCategory"),
                "PropertyCategory must be requested from PropertyTax MDMS module");
    }

    @Test
    void revisedMastersAreNotLoadedFromApplicationClasspath() {
        assertTrue(getClass().getResource("/mdms/djb/revised-2026/PropertyCategory.json") == null,
                "PropertyCategory must be fetched from MDMS, not packaged as an application resource");
        assertTrue(getClass().getResource("/mdms/djb/revised-2026/PropertyType2.json") == null,
                "PropertyType2 must be fetched from MDMS, not packaged as an application resource");
        assertTrue(getClass().getResource("/mdms/djb/revised-2026/WaterDemandNorms2.json") == null,
                "WaterDemandNorms2 must be fetched from MDMS, not packaged as an application resource");
    }

    private Set<String> requestedMasterNames(List<ModuleDetail> modules, String moduleName) {
        Set<String> names = new HashSet<>();
        for (ModuleDetail module : modules) {
            if (moduleName.equals(module.getModuleName()) && module.getMasterDetails() != null) {
                names.addAll(module.getMasterDetails().stream()
                        .map(MasterDetail::getName)
                        .collect(Collectors.toSet()));
            }
        }
        return names;
    }
}
