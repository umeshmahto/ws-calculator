package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Arrays;
import java.util.List;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.model.enums.BillingBasis;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.service.DJBMonthlyBillingSystemContextService;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingStatement;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext;
import org.egov.wscalculation.repository.WSCalculationDao;
import org.egov.wscalculation.util.CalculatorUtil;
import org.egov.wscalculation.web.models.WaterConnection;
import org.egov.wscalculation.web.models.MeterReading;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DJBMonthlyBillingSystemContextServiceTest {

    private static final String TENANT_ID = "dl.djb";
    private static final String CONNECTION_NO = "WS/DJB/2026-27/000576";

    @Mock
    private WaterBillingCycleDao billingCycleDao;

    @Mock
    private CalculatorUtil calculatorUtil;

    @Mock
    private WSCalculationDao wsCalculationDao;

    @Test
    void shouldMapCommercialAliasToDjbNonDomesticCategoryTwoWithoutImportingLegacyData() throws Exception {
        DJBMonthlyBillingSystemContext result = buildContext("COMMERCIAL", "COMMERCIAL");

        assertEquals("II", result.getDjbRegister().getClassification().getDjbCategoryCode());
        assertEquals("Non-Domestic", result.getDjbRegister().getClassification().getDjbCategoryName());
        assertEquals("Non-Domestic", result.getDjbRegister().getClassification().getPropertyUsageType());
        assertEquals("COMMERCIAL", result.getDjbRegister().getClassification().getSourcePropertyUsageType());
        assertEquals("COMMERCIAL", result.getDjbRegister().getClassification().getUpyogTariffCategory());
        assertEquals("MAPPED", result.getDjbRegister().getClassification().getMappingStatus());
        assertNull(result.getDjbRegister().getConsumer().getAccountId());
        String json = new ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains("\"dataQuality\""));
        assertFalse(json.contains("\"fieldGaps\""));
        assertFalse(json.contains("\"relatedMappings\""));
    }

    @Test
    void shouldMapExplicitNonDomesticAliasToDjbCategoryTwo() throws Exception {
        DJBMonthlyBillingSystemContext result = buildContext("NON-DOMESTIC", "NON-DOMESTIC");

        assertEquals("II", result.getDjbRegister().getClassification().getDjbCategoryCode());
        assertEquals("Non-Domestic", result.getDjbRegister().getClassification().getDjbCategoryName());
        assertEquals("Non-Domestic", result.getDjbRegister().getClassification().getPropertyUsageType());
    }

    @Test
    void shouldMapDomesticToDjbCategoryOne() throws Exception {
        DJBMonthlyBillingSystemContext result = buildContext("DOMESTIC", "DOMESTIC");

        assertEquals("I", result.getDjbRegister().getClassification().getDjbCategoryCode());
        assertEquals("Domestic", result.getDjbRegister().getClassification().getDjbCategoryName());
        assertEquals("Domestic", result.getDjbRegister().getClassification().getPropertyUsageType());
    }

    @Test
    void shouldExposePreviousOkReadingSeparatelyWhenCurrentAndPreviousCyclesAreLoc() throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        WaterConnection connection = new WaterConnection();
        connection.setTenantId(TENANT_ID);
        connection.setConnectionNo(CONNECTION_NO);

        when(calculatorUtil.getWaterConnection(eq(requestInfo), eq(CONNECTION_NO), eq(TENANT_ID)))
                .thenReturn(Collections.singletonList(connection));

        MeterReading previousOkReading = MeterReading.builder().id("MR-OK")
                .tenantId(TENANT_ID).connectionNo(CONNECTION_NO)
                .lastReading(50.0).lastReadingDate(50L)
                .currentReading(80.0).currentReadingDate(100L)
                .readingQualityCode("OK").meterStatus(MeterReading.MeterStatusEnum.WORKING).build();
        MeterReading previousLocReading = MeterReading.builder().id("MR-MLOC")
                .tenantId(TENANT_ID).connectionNo(CONNECTION_NO)
                .lastReading(80.0).lastReadingDate(100L)
                .currentReading(80.0).currentReadingDate(150L)
                .readingQualityCode("MLOC").meterStatus(MeterReading.MeterStatusEnum.LOCKED).build();
        MeterReading currentPlocReading = MeterReading.builder().id("MR-PLOC")
                .tenantId(TENANT_ID).connectionNo(CONNECTION_NO)
                .lastReading(80.0).lastReadingDate(150L)
                .currentReading(80.0).currentReadingDate(200L)
                .readingQualityCode("PLOC").meterStatus(MeterReading.MeterStatusEnum.LOCKED).build();
        when(wsCalculationDao.searchMeterReadingHistory(TENANT_ID, CONNECTION_NO))
                .thenReturn(Arrays.asList(currentPlocReading, previousLocReading, previousOkReading));

        WaterBillingCycle previousOkCycle = new WaterBillingCycle();
        previousOkCycle.setId("CYCLE-OK");
        previousOkCycle.setTenantid(TENANT_ID);
        previousOkCycle.setConnectionno(CONNECTION_NO);
        previousOkCycle.setBillingperiodfrom(50L);
        previousOkCycle.setBillingperiodto(100L);
        previousOkCycle.setCurrentreading(new java.math.BigDecimal("80"));
        previousOkCycle.setCurrentreadingdate(100L);
        previousOkCycle.setReadingqualitycode("OK");

        WaterBillingCycle previousLocCycle = new WaterBillingCycle();
        previousLocCycle.setId("CYCLE-MLOC");
        previousLocCycle.setTenantid(TENANT_ID);
        previousLocCycle.setConnectionno(CONNECTION_NO);
        previousLocCycle.setBillingperiodfrom(100L);
        previousLocCycle.setBillingperiodto(150L);
        previousLocCycle.setCurrentreading(new java.math.BigDecimal("80"));
        previousLocCycle.setCurrentreadingdate(150L);
        previousLocCycle.setReadingqualitycode("MLOC");

        WaterBillingCycle currentCycle = new WaterBillingCycle();
        currentCycle.setId("CYCLE-PLOC");
        currentCycle.setTenantid(TENANT_ID);
        currentCycle.setConnectionno(CONNECTION_NO);
        currentCycle.setMeterreadingid("MR-PLOC");
        currentCycle.setBillingperiodfrom(150L);
        currentCycle.setBillingperiodto(200L);
        currentCycle.setCurrentreading(new java.math.BigDecimal("80"));
        currentCycle.setCurrentreadingdate(200L);
        currentCycle.setPreviousokreading(new java.math.BigDecimal("80"));
        currentCycle.setPreviousokreadingdate(100L);
        currentCycle.setReadingqualitycode("PLOC");

        when(billingCycleDao.findCyclesForConnection(eq(TENANT_ID), eq(CONNECTION_NO), anyLong(), anyInt()))
                .thenReturn(Arrays.asList(previousLocCycle, previousOkCycle));

        DJBMonthlyBillingSystemContextService service = new DJBMonthlyBillingSystemContextService(
                billingCycleDao, calculatorUtil, wsCalculationDao, new ObjectMapper());
        DJBMonthlyBillingStatement statement = DJBMonthlyBillingStatement.builder()
                .consumer(DJBMonthlyBillingStatement.Consumer.builder()
                        .tenantId(TENANT_ID).connectionNo(CONNECTION_NO)
                        .tariffCategory("DOMESTIC").propertyUsage("DOMESTIC").build())
                .reading(DJBMonthlyBillingStatement.Reading.builder()
                        .previousReading(new java.math.BigDecimal("80"))
                        .previousReadingDate(100L)
                        .currentReading(new java.math.BigDecimal("80"))
                        .currentReadingDate(200L)
                        .previousConsumption(new java.math.BigDecimal("26.1"))
                        .billingConsumption(new java.math.BigDecimal("27"))
                        .unit("KL").build())
                .build();

        DJBMonthlyBillingSystemContext result = service.build(
                requestInfo, TENANT_ID, CONNECTION_NO, currentCycle, statement);

        assertEquals("MLOC", result.getDjbRegister().getPreviousReading().getReadingQualityCode());
        assertEquals(Long.valueOf(150L), result.getDjbRegister().getPreviousReading().getReadingDate());
        assertEquals("OK", result.getDjbRegister().getPreviousOkReading().getReadingQualityCode());
        assertEquals(new java.math.BigDecimal("80.0"), result.getDjbRegister().getPreviousOkReading().getReadingKl());
        assertEquals(Long.valueOf(100L), result.getDjbRegister().getPreviousOkReading().getReadingDate());

        // The API places reading data once under billingStatement.reading, not in systemData.
        assertEquals(new java.math.BigDecimal("80.0"), statement.getReading().getPreviousReading());
        assertEquals(Long.valueOf(150L), statement.getReading().getPreviousReadingDate());
        assertNotNull(statement.getReading().getPreviousOkReading());
        assertEquals(new java.math.BigDecimal("80.0"), statement.getReading().getPreviousOkReading().getReading());
        assertEquals(Long.valueOf(100L), statement.getReading().getPreviousOkReading().getReadingDate());
        assertEquals("OK", statement.getReading().getPreviousOkReading().getReadingQualityCode());

        ObjectMapper mapper = new ObjectMapper();
        String contextJson = mapper.writeValueAsString(result);
        assertFalse(contextJson.contains("\"dataQuality\""));
        assertFalse(contextJson.contains("\"fieldGaps\""));
        assertFalse(contextJson.contains("\"relatedMappings\""));
        assertFalse(contextJson.contains("\"meterReadingHistory\""));
        assertFalse(contextJson.contains("\"billingCycleHistory\""));
        assertFalse(contextJson.contains("\"registerDetails\""));
        assertFalse(contextJson.contains("\"previousOkReading\""));
        String statementJson = mapper.writeValueAsString(statement);
        assertTrue(statementJson.contains("\"previousOkReading\""));
    }

    @Test
    void shouldExposePreviousOkReadingWhenCurrentAverageBillHasImmediatePreviousOkReading() throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        WaterConnection connection = new WaterConnection();
        connection.setTenantId(TENANT_ID);
        connection.setConnectionNo(CONNECTION_NO);
        when(calculatorUtil.getWaterConnection(eq(requestInfo), eq(CONNECTION_NO), eq(TENANT_ID)))
                .thenReturn(Collections.singletonList(connection));

        MeterReading previousOkReading = MeterReading.builder().id("MR-OK-AVERAGE")
                .tenantId(TENANT_ID).connectionNo(CONNECTION_NO)
                .lastReading(60.0).lastReadingDate(50L)
                .currentReading(80.0).currentReadingDate(100L)
                .readingQualityCode("OK").meterStatus(MeterReading.MeterStatusEnum.WORKING).build();
        MeterReading currentMlocReading = MeterReading.builder().id("MR-MLOC-AVERAGE")
                .tenantId(TENANT_ID).connectionNo(CONNECTION_NO)
                .lastReading(80.0).lastReadingDate(100L)
                .currentReading(80.0).currentReadingDate(150L)
                .readingQualityCode("MLOC").meterStatus(MeterReading.MeterStatusEnum.LOCKED).build();
        when(wsCalculationDao.searchMeterReadingHistory(TENANT_ID, CONNECTION_NO))
                .thenReturn(Arrays.asList(currentMlocReading, previousOkReading));

        WaterBillingCycle previousOkCycle = new WaterBillingCycle();
        previousOkCycle.setId("CYCLE-OK-AVERAGE");
        previousOkCycle.setTenantid(TENANT_ID);
        previousOkCycle.setConnectionno(CONNECTION_NO);
        previousOkCycle.setBillingperiodfrom(50L);
        previousOkCycle.setBillingperiodto(100L);
        previousOkCycle.setCurrentreading(new java.math.BigDecimal("80"));
        previousOkCycle.setCurrentreadingdate(100L);
        previousOkCycle.setReadingqualitycode("OK");

        WaterBillingCycle currentCycle = new WaterBillingCycle();
        currentCycle.setId("CYCLE-MLOC-AVERAGE");
        currentCycle.setTenantid(TENANT_ID);
        currentCycle.setConnectionno(CONNECTION_NO);
        currentCycle.setMeterreadingid("MR-MLOC-AVERAGE");
        currentCycle.setBillingperiodfrom(100L);
        currentCycle.setBillingperiodto(150L);
        currentCycle.setCurrentreading(new java.math.BigDecimal("80"));
        currentCycle.setCurrentreadingdate(150L);
        currentCycle.setReadingqualitycode("MLOC");
        currentCycle.setBillingbasis(BillingBasis.AVERAGE);
        currentCycle.setPreviousokreading(new java.math.BigDecimal("80"));
        currentCycle.setPreviousokreadingdate(100L);

        when(billingCycleDao.findCyclesForConnection(eq(TENANT_ID), eq(CONNECTION_NO), anyLong(), anyInt()))
                .thenReturn(Collections.singletonList(previousOkCycle));

        DJBMonthlyBillingSystemContextService service = new DJBMonthlyBillingSystemContextService(
                billingCycleDao, calculatorUtil, wsCalculationDao, new ObjectMapper());
        DJBMonthlyBillingStatement statement = DJBMonthlyBillingStatement.builder()
                .consumer(DJBMonthlyBillingStatement.Consumer.builder()
                        .tenantId(TENANT_ID).connectionNo(CONNECTION_NO).tariffCategory("DOMESTIC")
                        .propertyUsage("Residential").build())
                .reading(DJBMonthlyBillingStatement.Reading.builder()
                        .previousReading(new java.math.BigDecimal("80"))
                        .previousReadingDate(100L)
                        .currentReading(new java.math.BigDecimal("80"))
                        .currentReadingDate(150L)
                        .billingConsumption(new java.math.BigDecimal("27"))
                        .averageConsumption(new java.math.BigDecimal("27")).unit("KL").build())
                .build();

        service.build(requestInfo, TENANT_ID, CONNECTION_NO, currentCycle, statement);

        assertNotNull(statement.getReading().getPreviousOkReading());
        assertEquals(new java.math.BigDecimal("80.0"), statement.getReading().getPreviousOkReading().getReading());
        assertEquals(Long.valueOf(100L), statement.getReading().getPreviousOkReading().getReadingDate());
        assertEquals("OK", statement.getReading().getPreviousOkReading().getReadingQualityCode());
    }

    private DJBMonthlyBillingSystemContext buildContext(String tariffCategory, String propertyUsage) throws Exception {
        WaterConnection connection = new WaterConnection();
        connection.setTenantId(TENANT_ID);
        connection.setConnectionNo(CONNECTION_NO);
        connection.setConnectionCategory(null);
        connection.setPropertyId(null);

        RequestInfo requestInfo = new RequestInfo();
        when(calculatorUtil.getWaterConnection(eq(requestInfo), eq(CONNECTION_NO), eq(TENANT_ID)))
                .thenReturn(Collections.singletonList(connection));
        when(wsCalculationDao.searchMeterReadingHistory(TENANT_ID, CONNECTION_NO))
                .thenReturn(Collections.emptyList());
        when(billingCycleDao.findCyclesForConnection(eq(TENANT_ID), eq(CONNECTION_NO), anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());

        DJBMonthlyBillingSystemContextService service = new DJBMonthlyBillingSystemContextService(
                billingCycleDao, calculatorUtil, wsCalculationDao, new ObjectMapper());
        WaterBillingCycle cycle = new WaterBillingCycle();
        cycle.setId("CYCLE-001");
        cycle.setTenantid(TENANT_ID);
        cycle.setConnectionno(CONNECTION_NO);

        DJBMonthlyBillingStatement statement = DJBMonthlyBillingStatement.builder()
                .consumer(DJBMonthlyBillingStatement.Consumer.builder()
                        .tenantId(TENANT_ID)
                        .connectionNo(CONNECTION_NO)
                        .tariffCategory(tariffCategory)
                        .propertyUsage(propertyUsage)
                        .build())
                .build();

        return service.build(requestInfo, TENANT_ID, CONNECTION_NO, cycle, statement);
    }
}
