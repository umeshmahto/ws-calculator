package org.egov.wscalculation.djbmonthlybilling.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.egov.common.contract.request.RequestInfo;
import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDao;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingStatement;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext.AddressDetails;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext.BillingCycleHistory;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext.ConnectionDetails;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext.BillingRoundRegister;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext.ConsumptionRegister;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext.ConsumerRegister;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext.DjbRegisterDetails;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext.MeterRegister;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext.ReadingRegister;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext.TariffClassification;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext.MeterReadingHistory;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext.PropertyDetails;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext.ReadingPoint;
import org.egov.wscalculation.djbmonthlybilling.web.model.DJBMonthlyBillingSystemContext.RegisterDetails;
import org.egov.wscalculation.repository.WSCalculationDao;
import org.egov.wscalculation.util.CalculatorUtil;
import org.egov.wscalculation.web.models.Address;
import org.egov.wscalculation.web.models.AuditDetails;
import org.egov.wscalculation.web.models.MeterReading;
import org.egov.wscalculation.web.models.OwnerInfo;
import org.egov.wscalculation.web.models.Property;
import org.egov.wscalculation.web.models.WaterConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds read-only context for the DJB-style details response from current
 * UPYOG records. This service does not import or persist spreadsheet data.
 */
@Service
public class DJBMonthlyBillingSystemContextService {

    private static final Logger log = LoggerFactory.getLogger(DJBMonthlyBillingSystemContextService.class);
    private static final int BILLING_HISTORY_PAGE_SIZE = 100;
    private static final int MAX_BILLING_HISTORY_ROWS = 2000;

    private final WaterBillingCycleDao billingCycleDao;
    private final CalculatorUtil calculatorUtil;
    private final WSCalculationDao wsCalculationDao;
    private final ObjectMapper objectMapper;

    public DJBMonthlyBillingSystemContextService(WaterBillingCycleDao billingCycleDao,
            CalculatorUtil calculatorUtil, WSCalculationDao wsCalculationDao, ObjectMapper objectMapper) {
        this.billingCycleDao = billingCycleDao;
        this.calculatorUtil = calculatorUtil;
        this.wsCalculationDao = wsCalculationDao;
        this.objectMapper = objectMapper;
    }

	public DJBMonthlyBillingSystemContext build(RequestInfo requestInfo, String tenantId,
			String connectionNo, WaterBillingCycle cycle, DJBMonthlyBillingStatement statement) {
		List<String> warnings = new ArrayList<String>();
		WaterConnection waterConnection = null;
		Property property = null;
		List<MeterReading> readings = Collections.emptyList();
		List<WaterBillingCycle> cycles = new ArrayList<WaterBillingCycle>();
		boolean meterHistoryComplete = true;
		boolean cycleHistoryComplete = true;

		try {
			List<WaterConnection> connections = calculatorUtil.getWaterConnection(requestInfo, connectionNo, tenantId);
			waterConnection = selectCurrentConnection(connections, tenantId, connectionNo);
			if (waterConnection == null) {
				warnings.add("CONNECTION_DETAILS_NOT_FOUND");
			}
		} catch (RuntimeException ex) {
			warnings.add("CONNECTION_DETAILS_UNAVAILABLE");
			log.warn("Unable to enrich DJB billing statement with water-connection details for {}: {}",
					connectionNo, ex.getMessage());
		}

		if (waterConnection != null && StringUtils.hasText(waterConnection.getPropertyId())) {
			try {
				property = calculatorUtil.getProperty(requestInfo, tenantId, waterConnection.getPropertyId());
				if (property == null) {
					warnings.add("PROPERTY_DETAILS_NOT_FOUND");
				}
			} catch (RuntimeException ex) {
				warnings.add("PROPERTY_DETAILS_UNAVAILABLE");
				log.warn("Unable to enrich DJB billing statement with property details for {}: {}",
						connectionNo, ex.getMessage());
			}
		}

		try {
			List<MeterReading> loaded = wsCalculationDao.searchMeterReadingHistory(tenantId, connectionNo);
			readings = loaded == null ? Collections.<MeterReading>emptyList() : loaded;
		} catch (RuntimeException ex) {
			meterHistoryComplete = false;
			warnings.add("METER_READING_HISTORY_UNAVAILABLE");
			log.warn("Unable to load meter-reading history for {}: {}", connectionNo, ex.getMessage());
		}

		try {
			long before = Long.MAX_VALUE;
			while (cycles.size() < MAX_BILLING_HISTORY_ROWS) {
				int limit = Math.min(BILLING_HISTORY_PAGE_SIZE, MAX_BILLING_HISTORY_ROWS - cycles.size());
				List<WaterBillingCycle> page = billingCycleDao.findCyclesForConnection(tenantId, connectionNo, before,
						limit);
				if (page == null || page.isEmpty()) {
					break;
				}
				cycles.addAll(page);
				if (page.size() < limit) {
					break;
				}
				Long oldestPeriodTo = null;
				for (WaterBillingCycle item : page) {
					if (item != null && item.getBillingperiodto() != null
							&& (oldestPeriodTo == null || item.getBillingperiodto() < oldestPeriodTo)) {
						oldestPeriodTo = item.getBillingperiodto();
					}
				}
				if (oldestPeriodTo == null || oldestPeriodTo >= before) {
					cycleHistoryComplete = false;
					warnings.add("BILLING_CYCLE_HISTORY_PAGINATION_STOPPED");
					break;
				}
				before = oldestPeriodTo;
			}
			if (cycles.size() >= MAX_BILLING_HISTORY_ROWS) {
				cycleHistoryComplete = false;
				warnings.add("BILLING_CYCLE_HISTORY_LIMIT_REACHED");
			}
		} catch (RuntimeException ex) {
			cycleHistoryComplete = false;
			warnings.add("BILLING_CYCLE_HISTORY_UNAVAILABLE");
			log.warn("Unable to load billing-cycle history for {}: {}", connectionNo, ex.getMessage());
		}

		MeterReading selectedReading = findSelectedMeterReading(readings, cycle);
		MeterReading previousReading = findPreviousReadingRecord(readings, selectedReading);
		MeterReading lastOkReading = findPreviousOkReading(readings, cycle, selectedReading);
		WaterBillingCycle previousOkCycle = findPreviousOkCycle(cycles, cycle);
		// Reading-level details belong to the billing statement; avoid duplicating them in systemData.
		enrichStatementReading(statement, cycle, selectedReading, previousReading, lastOkReading, previousOkCycle);
		boolean includeConsumerContact = isEmployeeRequest(requestInfo);
		if (!includeConsumerContact) {
			warnings.add("CONSUMER_CONTACT_DETAILS_REDACTED_FOR_NON_EMPLOYEE");
		}
		ConnectionDetails connectionDetails = buildConnectionDetails(waterConnection, property, includeConsumerContact);
		PropertyDetails propertyDetails = buildPropertyDetails(property, includeConsumerContact);
		RegisterDetails registerDetails = buildRegisterDetails(cycle, selectedReading, previousReading,
				lastOkReading, statement);
		List<MeterReadingHistory> meterHistory = new ArrayList<MeterReadingHistory>();
		for (MeterReading item : readings) {
			meterHistory.add(toMeterReadingHistory(item));
		}
		List<BillingCycleHistory> cycleHistory = new ArrayList<BillingCycleHistory>();
		for (WaterBillingCycle item : cycles) {
			cycleHistory.add(toBillingCycleHistory(item));
		}
		DjbRegisterDetails djbRegister = buildDjbRegister(connectionDetails, propertyDetails, cycle,
				selectedReading, previousReading, lastOkReading, previousOkCycle, statement, includeConsumerContact);

		if (!meterHistoryComplete || !cycleHistoryComplete) {
			warnings.add("HISTORY_DATA_MAY_BE_INCOMPLETE");
		}
		return DJBMonthlyBillingSystemContext.builder().sourceSystem("UPYOG_EXISTING_RECORDS")
				.connection(connectionDetails).property(propertyDetails).registerDetails(registerDetails)
				.meterReadingHistory(meterHistory).billingCycleHistory(cycleHistory)
				.djbRegister(djbRegister).warnings(warnings)
				.meterReadingHistoryComplete(meterHistoryComplete)
				.billingCycleHistoryComplete(cycleHistoryComplete).build();
	}

	private WaterConnection selectCurrentConnection(List<WaterConnection> connections, String tenantId, String connectionNo) {
		if (connections == null || connections.isEmpty()) {
			return null;
		}
		List<WaterConnection> sorted = new ArrayList<WaterConnection>();
		for (WaterConnection candidate : connections) {
			if (candidate != null && tenantId.equalsIgnoreCase(candidate.getTenantId())
					&& connectionNo.equalsIgnoreCase(candidate.getConnectionNo())) {
				sorted.add(candidate);
			}
		}
		if (sorted.isEmpty()) {
			return null;
		}
		Collections.sort(sorted, new Comparator<WaterConnection>() {
			@Override
			public int compare(WaterConnection left, WaterConnection right) {
				long leftTime = auditLastModified(left);
				long rightTime = auditLastModified(right);
				return Long.compare(leftTime, rightTime);
			}
		});
		WaterConnection latest = sorted.get(sorted.size() - 1);
		if (sorted.size() > 1 && "MODIFY_WATER_CONNECTION".equalsIgnoreCase(latest.getApplicationType())
				&& latest.getDateEffectiveFrom() != null && latest.getDateEffectiveFrom() > System.currentTimeMillis()) {
			return sorted.get(sorted.size() - 2);
		}
		return latest;
	}

	private long auditLastModified(WaterConnection connection) {
		if (connection == null || connection.getAuditDetails() == null
				|| connection.getAuditDetails().getLastModifiedTime() == null) {
			return Long.MIN_VALUE;
		}
		return connection.getAuditDetails().getLastModifiedTime();
	}

	private ConnectionDetails buildConnectionDetails(WaterConnection connection, Property property, boolean includeConsumerContact) {
		if (connection == null) {
			return null;
		}
		Object details = connection.getAdditionalDetails();
		OwnerInfo primaryOwner = primaryOwner(connection.getConnectionHolders());
		if (primaryOwner == null && property != null) {
			primaryOwner = primaryOwner(property.getOwners());
		}
		String address = property == null ? null : formatAddress(property.getAddress(), connection.getTenantId());
		if (!StringUtils.hasText(address) && primaryOwner != null) {
			address = primaryOwner.getCorrespondenceAddress();
		}
		if (!includeConsumerContact) {
			address = null;
		}
		AuditDetails audit = connection.getAuditDetails();
		Object propertyAdditionalDetails = property == null ? null : property.getAdditionalDetails();
		String zoneLocation = firstNonBlank(readAdditionalDetail(details, "zoneLocation", "mcdZoneName", "zoneName"),
				readAdditionalDetail(propertyAdditionalDetails, "zoneLocation", "mcdZoneName", "zoneName"));
		String mrKey = firstNonBlank(readAdditionalDetail(details, "mrKey", "meterReaderKey", "meterReaderId"),
				readAdditionalDetail(propertyAdditionalDetails, "mrKey", "meterReaderKey", "meterReaderId"));
		String billRoundId = firstNonBlank(readAdditionalDetail(details, "billRoundId", "billingRoundId"),
				readAdditionalDetail(propertyAdditionalDetails, "billRoundId", "billingRoundId"));
		String saStatusFlag = firstNonBlank(readAdditionalDetail(details, "saStatusFlag", "sa_status_flg"),
				readAdditionalDetail(propertyAdditionalDetails, "saStatusFlag", "sa_status_flg"));
		String meterModel = firstNonBlank(readAdditionalDetail(details, "meterModel", "meter_model"),
				readAdditionalDetail(propertyAdditionalDetails, "meterModel", "meter_model"));
		String meterNumber = firstNonBlank(readAdditionalDetail(details, "meterNumber", "meterNo", "mtrNumber", "meterSerialNumber"),
				readAdditionalDetail(propertyAdditionalDetails, "meterNumber", "meterNo", "mtrNumber", "meterSerialNumber"));
		String currentReaderRemarkCode = firstNonBlank(readAdditionalDetail(details, "currReaderRemCd", "currentReaderRemarkCode"),
				readAdditionalDetail(propertyAdditionalDetails, "currReaderRemCd", "currentReaderRemarkCode"));
		String currentNewAverageReading = firstNonBlank(readAdditionalDetail(details, "currNewAvgRead", "currentNewAverageReading"),
				readAdditionalDetail(propertyAdditionalDetails, "currNewAvgRead", "currentNewAverageReading"));
		String djbAccountId = firstNonBlank(
				readAdditionalDetail(details, "djbAccountId", "djbAcctId", "acctId", "ACCTID"),
				readAdditionalDetail(propertyAdditionalDetails, "djbAccountId", "djbAcctId", "acctId", "ACCTID"));
		return ConnectionDetails.builder().id(connection.getId()).tenantId(connection.getTenantId())
				.connectionNo(connection.getConnectionNo()).legacyAccountId(connection.getOldConnectionNo())
				.djbAccountId(djbAccountId)
				.applicationNo(connection.getApplicationNo()).applicationStatus(connection.getApplicationStatus())
				.connectionStatus(connection.getStatus() == null ? null : connection.getStatus().toString())
				.propertyId(connection.getPropertyId()).connectionCategory(connection.getConnectionCategory())
				.connectionType(connection.getConnectionType()).waterSource(connection.getWaterSource())
				.meterId(connection.getMeterId())
				.meterNumber(meterNumber)
				.meterModel(meterModel)
				.meterInstallationDate(connection.getMeterInstallationDate())
				.connectionExecutionDate(connection.getConnectionExecutionDate())
				.dateEffectiveFrom(connection.getDateEffectiveFrom())
				.disconnectionExecutionDate(connection.getDisconnectionExecutionDate())
				.zoneLocation(zoneLocation)
				.locality(firstNonBlank(readAdditionalDetail(details, "locality", "localityName"),
						propertyLocality(property)))
				.meterReaderKey(mrKey).billingRoundId(billRoundId).saStatusFlag(saStatusFlag)
				.currentReaderRemarkCode(currentReaderRemarkCode).currentNewAverageReading(currentNewAverageReading)
				.pipeSize(connection.getPipeSize()).noOfTaps(connection.getNoOfTaps())
				.consumerName(!includeConsumerContact || primaryOwner == null ? null : primaryOwner.getName())
				.phone(!includeConsumerContact || primaryOwner == null ? null : primaryOwner.getMobileNumber())
				.email(!includeConsumerContact || primaryOwner == null ? null : primaryOwner.getEmailId()).consumerAddress(address)
				.createdTime(audit == null ? null : audit.getCreatedTime())
				.lastModifiedTime(audit == null ? null : audit.getLastModifiedTime()).build();
	}

	private PropertyDetails buildPropertyDetails(Property property, boolean includeConsumerContact) {
		if (property == null) {
			return null;
		}
		return PropertyDetails.builder().id(property.getId()).propertyId(property.getPropertyId())
				.accountId(property.getAccountId()).oldPropertyId(property.getOldPropertyId())
				.status(property.getStatus() == null ? null : property.getStatus().toString())
				.propertyType(property.getPropertyType()).ownershipCategory(property.getOwnershipCategory())
				.usageCategory(property.getUsageCategory()).noOfFloors(property.getNoOfFloors())
				.landArea(property.getLandArea()).superBuiltUpArea(property.getSuperBuiltUpArea())
				.address(includeConsumerContact ? buildAddressDetails(property.getAddress(), property.getTenantId()) : null).build();
	}

	private AddressDetails buildAddressDetails(Address address, String tenantId) {
		if (address == null) {
			return null;
		}
		return AddressDetails.builder().doorNo(address.getDoorNo()).plotNo(address.getPlotNo())
				.buildingName(address.getBuildingName()).street(address.getStreet()).landmark(address.getLandmark())
				.localityCode(address.getLocality() == null ? null : address.getLocality().getCode())
				.localityName(address.getLocality() == null ? null : address.getLocality().getName())
				.city(isTenantIdValue(address.getCity(), tenantId) ? null : address.getCity()).district(address.getDistrict()).region(address.getRegion())
				.state(address.getState()).country(address.getCountry()).pincode(address.getPincode()).build();
	}

	/**
	 * Exposes the immediate previous meter reading in the reading block and, only
	 * when a non-OK/LOC reading makes it relevant, the latest earlier OK reading.
	 * This enriches the response object only; it does not change saved billing data.
	 */
	private void enrichStatementReading(DJBMonthlyBillingStatement statement, WaterBillingCycle cycle,
			MeterReading selectedReading, MeterReading previousReadingRecord, MeterReading lastOkReading,
			WaterBillingCycle previousOkCycle) {
		if (statement == null || statement.getReading() == null || cycle == null) {
			return;
		}

		DJBMonthlyBillingStatement.Reading reading = statement.getReading();
		if (selectedReading != null) {
			BigDecimal immediatePreviousValue = decimal(selectedReading.getLastReading());
			Long immediatePreviousDate = validTimestamp(selectedReading.getLastReadingDate());
			if (immediatePreviousValue != null) {
				reading.setPreviousReading(immediatePreviousValue);
			}
			if (immediatePreviousDate != null) {
				reading.setPreviousReadingDate(immediatePreviousDate);
			}
			BigDecimal currentValue = decimal(selectedReading.getCurrentReading());
			Long currentDate = validTimestamp(selectedReading.getCurrentReadingDate());
			if (currentValue != null) {
				reading.setCurrentReading(currentValue);
			}
			if (currentDate != null) {
				reading.setCurrentReadingDate(currentDate);
			}
		}

		String currentRqc = firstNonBlank(cycle.getReadingqualitycode(),
				selectedReading == null ? null : selectedReading.getReadingQualityCode());
		String previousRqc = previousReadingRecord == null ? null : previousReadingRecord.getReadingQualityCode();
		boolean currentIsNonOk = StringUtils.hasText(currentRqc) && !"OK".equalsIgnoreCase(currentRqc);
		boolean previousIsNonOk = StringUtils.hasText(previousRqc) && !"OK".equalsIgnoreCase(previousRqc);

		boolean previousIsKnownOk = previousReadingRecord != null && "OK".equalsIgnoreCase(previousRqc);
		// A non-OK current reading (for example MLOC/PLOC) still needs its OK baseline,
		// even when the immediately previous reading is already OK. Do not suppress it
		// merely because that baseline is also represented by previousReading.
		if (!currentIsNonOk && !previousIsNonOk) {
			reading.setPreviousOkReading(null);
			return;
		}

		// Prefer the immediate previous record when it is explicitly marked OK. This avoids
		// depending on history ordering/cutoff rules for the MLOC/PLOC reading whose immediate predecessor is OK.
		BigDecimal okValue = previousIsKnownOk ? decimal(previousReadingRecord.getCurrentReading()) : null;
		Long okDate = previousIsKnownOk ? validTimestamp(previousReadingRecord.getCurrentReadingDate()) : null;
		if (okValue == null && lastOkReading != null) {
			okValue = decimal(lastOkReading.getCurrentReading());
		}
		if (okDate == null && lastOkReading != null) {
			okDate = validTimestamp(lastOkReading.getCurrentReadingDate());
		}
		if (okValue == null && previousOkCycle != null) {
			okValue = previousOkCycle.getCurrentreading();
		}
		if (okDate == null && previousOkCycle != null) {
			okDate = validTimestamp(previousOkCycle.getCurrentreadingdate());
			if (okDate == null) {
				okDate = validTimestamp(previousOkCycle.getBillingperiodto());
			}
		}
		if (okValue == null) {
			okValue = cycle.getPreviousokreading();
		}
		if (okDate == null) {
			okDate = validTimestamp(cycle.getPreviousokreadingdate());
		}

		if (okValue == null && okDate == null) {
			reading.setPreviousOkReading(null);
		} else {
			reading.setPreviousOkReading(DJBMonthlyBillingStatement.PreviousOkReading.builder()
					.reading(okValue).readingDate(okDate).readingQualityCode("OK").build());
		}
	}

	private RegisterDetails buildRegisterDetails(WaterBillingCycle cycle, MeterReading selectedReading,
			MeterReading previousReadingRecord, MeterReading lastOkReading, DJBMonthlyBillingStatement statement) {
		BigDecimal previousReading = selectedReading == null ? null : decimal(selectedReading.getLastReading());
		Long previousReadingDate = selectedReading == null ? null : validTimestamp(selectedReading.getLastReadingDate());
		BigDecimal currentReading = selectedReading == null ? null : decimal(selectedReading.getCurrentReading());
		if (currentReading == null) {
			currentReading = cycle.getCurrentreading();
		}
		Long currentReadingDate = selectedReading == null ? null : validTimestamp(selectedReading.getCurrentReadingDate());
		if (currentReadingDate == null) {
			currentReadingDate = validTimestamp(cycle.getCurrentreadingdate());
		}
		return RegisterDetails.builder().billingPeriodFrom(cycle.getBillingperiodfrom())
				.billingPeriodTo(cycle.getBillingperiodto()).readingQualityCode(cycle.getReadingqualitycode())
				.billingBasis(cycle.getBillingbasis() == null ? null : cycle.getBillingbasis().toString())
				.billingCycleStatus(cycle.getStatus() == null ? null : cycle.getStatus().toString())
				.previousConsumption(cycle.getPreviousconsumption()).averageConsumption(cycle.getAverageconsumption())
				.billingConsumption(cycle.getBillingconsumption()).previousRegisterReading(previousReading)
				.previousRegisterReadingDate(previousReadingDate)
				.previousRegisterReadingQualityCode(previousReadingRecord == null ? null
						: previousReadingRecord.getReadingQualityCode())
				.previousRegisterMeterStatus(previousReadingRecord == null || previousReadingRecord.getMeterStatus() == null
						? null : previousReadingRecord.getMeterStatus().toString())
				.currentRegisterReading(currentReading).currentRegisterReadingDate(currentReadingDate)
				.currentRegisterReadingQualityCode(cycle.getReadingqualitycode())
				.currentRegisterMeterStatus(selectedReading == null || selectedReading.getMeterStatus() == null ? null
						: selectedReading.getMeterStatus().toString())
				.lastKnownOkReading(toReadingPoint(lastOkReading, "LATEST_OK_READING"))
				.currentBillGeneratedDate(statement.getBill() == null ? null : statement.getBill().getGeneratedAt())
				.build();
	}

	private DjbRegisterDetails buildDjbRegister(ConnectionDetails connection, PropertyDetails property,
			WaterBillingCycle cycle, MeterReading selectedReading, MeterReading previousReadingRecord,
			MeterReading lastOkReading, WaterBillingCycle previousOkCycle,
			DJBMonthlyBillingStatement statement, boolean includeConsumerContact) {
		String accountId = connection == null ? null : connection.getDjbAccountId();
		String consumerName = connection == null ? null : connection.getConsumerName();
		String consumerAddress = connection == null ? null : connection.getConsumerAddress();
		String phone = connection == null ? null : connection.getPhone();
		String zoneLocation = connection == null ? null : connection.getZoneLocation();
		String meterReaderKey = connection == null ? null : connection.getMeterReaderKey();
		String serviceAccountStatusFlag = connection == null ? null : connection.getSaStatusFlag();
		String meterNumber = connection == null ? null : connection.getMeterNumber();
		String meterModel = connection == null ? null : connection.getMeterModel();
		if (!includeConsumerContact) {
			consumerName = null;
			consumerAddress = null;
			phone = null;
		}

		BigDecimal previousValue = selectedReading == null ? cycle.getPreviousokreading()
				: decimal(selectedReading.getLastReading());
		Long previousDate = selectedReading == null ? validTimestamp(cycle.getPreviousokreadingdate())
				: validTimestamp(selectedReading.getLastReadingDate());
		BigDecimal actualValue = selectedReading == null ? cycle.getCurrentreading()
				: decimal(selectedReading.getCurrentReading());
		Long actualDate = selectedReading == null ? validTimestamp(cycle.getCurrentreadingdate())
				: validTimestamp(selectedReading.getCurrentReadingDate());
		String previousReadingStatus = previousReadingRecord == null ? null
				: previousReadingRecord.getReadingQualityCode();
		String previousMeterStatus = previousReadingRecord == null || previousReadingRecord.getMeterStatus() == null
				? null : previousReadingRecord.getMeterStatus().toString();
		String actualReadingStatus = firstNonBlank(cycle.getReadingqualitycode(),
				selectedReading == null ? null : selectedReading.getReadingQualityCode());
		String actualMeterStatus = selectedReading == null || selectedReading.getMeterStatus() == null
				? null : selectedReading.getMeterStatus().toString();

		String propertyUsage = statement != null && statement.getConsumer() != null
				? statement.getConsumer().getPropertyUsage() : null;
		if (!StringUtils.hasText(propertyUsage) && property != null) {
			propertyUsage = property.getUsageCategory();
		}
		String tariffCategory = statement != null && statement.getConsumer() != null
				? statement.getConsumer().getTariffCategory() : null;
		String tariffSource = StringUtils.hasText(tariffCategory) ? "billingStatement.consumer.tariffCategory" : null;
		if (!StringUtils.hasText(tariffCategory) && statement != null && statement.getCharges() != null
				&& statement.getCharges().getWater() != null) {
			tariffCategory = statement.getCharges().getWater().getCategory();
			tariffSource = StringUtils.hasText(tariffCategory) ? "billingStatement.charges.water.category" : null;
		}
		if (!StringUtils.hasText(tariffCategory) && connection != null) {
			tariffCategory = connection.getConnectionCategory();
			tariffSource = StringUtils.hasText(tariffCategory) ? "systemData.connection.connectionCategory" : null;
		}
		if (!StringUtils.hasText(tariffCategory)) {
			tariffCategory = propertyUsage;
			tariffSource = StringUtils.hasText(tariffCategory) ? "systemData.property.usageCategory" : null;
		}
		TariffClassification classification = resolveDjbTariffClassification(propertyUsage, tariffCategory, tariffSource);
		MeterRegister djbMeter = StringUtils.hasText(meterNumber) || StringUtils.hasText(meterModel)
				? MeterRegister.builder().meterNumber(meterNumber).meterModel(meterModel).build() : null;

		return DjbRegisterDetails.builder()
				.consumer(ConsumerRegister.builder().accountId(accountId).name(consumerName).address(consumerAddress)
						.phone(phone).zoneLocation(zoneLocation).meterReaderKey(meterReaderKey)
						.serviceAccountStatusFlag(serviceAccountStatusFlag).build())
				.classification(classification)
				.meter(djbMeter)
				.previousReading(ReadingRegister.builder().readingDate(previousDate).readingKl(previousValue)
						.readingQualityCode(previousReadingStatus).meterStatus(previousMeterStatus).readType(null).build())
				.actualReading(ReadingRegister.builder().readingDate(actualDate).readingKl(actualValue)
						.readingQualityCode(actualReadingStatus).meterStatus(actualMeterStatus).readType(null).build())
				.previousOkReading(buildPreviousOkReading(lastOkReading, previousOkCycle))
				.consumption(ConsumptionRegister.builder()
						.previousConsumption(cycle.getPreviousconsumption())
						.legacyPreviousConsumption(null)
						.averageReading(cycle.getAverageconsumption())
						.billingConsumption(cycle.getBillingconsumption()).build())
				.billingRound(BillingRoundRegister.builder()
						.billingRoundId(connection == null ? null : connection.getBillingRoundId())
						.previousBillGeneratedAt(null)
						.currentBillGeneratedAt(statement == null || statement.getBill() == null
								? null : statement.getBill().getGeneratedAt())
						.readerRemarkCode(connection == null ? null : connection.getCurrentReaderRemarkCode())
						.regularReading(actualValue)
						.newAverageReading(parseDecimal(connection == null ? null : connection.getCurrentNewAverageReading()))
						.build())
				.build();
	}

	private TariffClassification resolveDjbTariffClassification(String propertyUsage, String tariffCategory,
			String mappingSource) {
		String categoryCode = djbCategoryCode(tariffCategory);
		String categoryName = "I".equals(categoryCode) ? "Domestic"
				: ("II".equals(categoryCode) ? "Non-Domestic" : null);
		String canonicalPropertyUsage = categoryName;
		return TariffClassification.builder().propertyUsageType(canonicalPropertyUsage)
				.sourcePropertyUsageType(propertyUsage)
				.upyogTariffCategory(tariffCategory).djbCategoryCode(categoryCode).djbCategoryName(categoryName)
				.mappingStatus(categoryCode == null ? "UNMAPPED" : "MAPPED")
				.mappingSource(categoryCode == null ? null : mappingSource)
				.ruleReference("DJB Compilation of Charges, Fee and Tariff, Table 1: Revised Water Tariff")
				.build();
	}

	/** Maps legacy/system aliases to DJB's two business groups: Domestic and Non-Domestic. */
	private String djbCategoryCode(String category) {
		if (!StringUtils.hasText(category)) {
			return null;
		}
		String normalized = category.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
		if ("DOMESTIC".equals(normalized) || "I".equals(normalized) || "CATEGORYI".equals(normalized)
				|| "DOMESTICCONSUMER".equals(normalized)) {
			return "I";
		}
		// COMMERCIAL is retained as a legacy UPYOG/MDMS alias for the DJB Non-Domestic group.
		if ("COMMERCIAL".equals(normalized) || "NONDOMESTIC".equals(normalized)
				|| "NONDOMESTICCONSUMER".equals(normalized) || "BUSINESS".equals(normalized)
				|| "II".equals(normalized) || "CATEGORYII".equals(normalized)) {
			return "II";
		}
		return null;
	}

	private MeterReading findSelectedMeterReading(List<MeterReading> readings, WaterBillingCycle cycle) {
		if (readings == null || cycle == null) {
			return null;
		}
		if (StringUtils.hasText(cycle.getMeterreadingid())) {
			for (MeterReading reading : readings) {
				if (cycle.getMeterreadingid().equals(reading.getId())) {
					return reading;
				}
			}
		}
		for (MeterReading reading : readings) {
			if (StringUtils.hasText(cycle.getId()) && cycle.getId().equals(reading.getBillingCycleId())) {
				return reading;
			}
		}
		return null;
	}

	private MeterReading findPreviousReadingRecord(List<MeterReading> readings, MeterReading selected) {
		if (readings == null || selected == null || validTimestamp(selected.getLastReadingDate()) == null) {
			return null;
		}
		Long previousReadingDate = validTimestamp(selected.getLastReadingDate());
		for (MeterReading reading : readings) {
			if (reading == null || validTimestamp(reading.getCurrentReadingDate()) == null
					|| !previousReadingDate.equals(validTimestamp(reading.getCurrentReadingDate()))
					|| reading.getCurrentReading() == null || selected.getLastReading() == null) {
				continue;
			}
			if (BigDecimal.valueOf(reading.getCurrentReading()).compareTo(BigDecimal.valueOf(selected.getLastReading())) == 0) {
				return reading;
			}
		}
		return null;
	}

	private MeterReading findPreviousOkReading(List<MeterReading> readings, WaterBillingCycle cycle,
			MeterReading selectedReading) {
		if (readings == null || cycle == null) {
			return null;
		}
		Long cutoff = selectedReading == null ? null : validTimestamp(selectedReading.getCurrentReadingDate());
		if (cutoff == null) cutoff = validTimestamp(cycle.getCurrentreadingdate());
		if (cutoff == null) cutoff = validTimestamp(cycle.getBillingperiodto());
		if (cutoff == null) return null;

		MeterReading latest = null;
		Long latestDate = null;
		Long latestCreatedTime = null;
		for (MeterReading reading : readings) {
			if (reading == null || reading.getCurrentReading() == null
					|| !"OK".equalsIgnoreCase(reading.getReadingQualityCode())) {
				continue;
			}
			Long readingDate = validTimestamp(reading.getCurrentReadingDate());
			if (readingDate == null || readingDate >= cutoff) {
				continue; // Strictly before this cycle: never treat the selected OK reading as its own previous-OK reading.
			}
			if (selectedReading != null && StringUtils.hasText(selectedReading.getId())
					&& selectedReading.getId().equals(reading.getId())) {
				continue;
			}
			Long createdTime = reading.getAuditDetails() == null ? null
					: reading.getAuditDetails().getCreatedTime();
			if (latest == null || readingDate > latestDate
					|| (readingDate.equals(latestDate) && compareNullableLong(createdTime, latestCreatedTime) > 0)) {
				latest = reading;
				latestDate = readingDate;
				latestCreatedTime = createdTime;
			}
		}
		return latest;
	}

	private int compareNullableLong(Long left, Long right) {
		if (left == null) return right == null ? 0 : -1;
		if (right == null) return 1;
		return left.compareTo(right);
	}

	private WaterBillingCycle findPreviousOkCycle(List<WaterBillingCycle> cycles, WaterBillingCycle currentCycle) {
		if (cycles == null || currentCycle == null) return null;
		Long cutoff = validTimestamp(currentCycle.getBillingperiodfrom());
		if (cutoff == null) cutoff = validTimestamp(currentCycle.getCurrentreadingdate());
		if (cutoff == null) cutoff = validTimestamp(currentCycle.getBillingperiodto());
		if (cutoff == null) return null;

		WaterBillingCycle latest = null;
		Long latestDate = null;
		for (WaterBillingCycle candidate : cycles) {
			if (candidate == null || !"OK".equalsIgnoreCase(candidate.getReadingqualitycode())
					|| candidate.getCurrentreading() == null) {
				continue;
			}
			if (StringUtils.hasText(currentCycle.getId()) && currentCycle.getId().equals(candidate.getId())) {
				continue;
			}
			Long readingDate = validTimestamp(candidate.getCurrentreadingdate());
			if (readingDate == null) readingDate = validTimestamp(candidate.getBillingperiodto());
			if (readingDate == null || readingDate > cutoff) continue;
			if (readingDate.equals(cutoff) && candidate.getBillingperiodto() != null
					&& currentCycle.getBillingperiodfrom() != null
					&& candidate.getBillingperiodto() > currentCycle.getBillingperiodfrom()) {
				continue;
			}
			if (latest == null || readingDate > latestDate) {
				latest = candidate;
				latestDate = readingDate;
			}
		}
		return latest;
	}

	private ReadingRegister buildPreviousOkReading(MeterReading lastOkReading, WaterBillingCycle previousOkCycle) {
		if (lastOkReading != null) {
			return ReadingRegister.builder()
					.readingDate(validTimestamp(lastOkReading.getCurrentReadingDate()))
					.readingKl(decimal(lastOkReading.getCurrentReading()))
					.readingQualityCode(lastOkReading.getReadingQualityCode())
					.meterStatus(lastOkReading.getMeterStatus() == null ? null : lastOkReading.getMeterStatus().toString())
					.build();
		}
		if (previousOkCycle != null) {
			Long date = validTimestamp(previousOkCycle.getCurrentreadingdate());
			if (date == null) date = validTimestamp(previousOkCycle.getBillingperiodto());
			return ReadingRegister.builder().readingDate(date).readingKl(previousOkCycle.getCurrentreading())
					.readingQualityCode(previousOkCycle.getReadingqualitycode()).build();
		}
		return null;
	}

	private ReadingPoint toReadingPoint(MeterReading reading, String source) {
		if (reading == null) {
			return null;
		}
		return ReadingPoint.builder().reading(decimal(reading.getCurrentReading()))
				.readingDate(validTimestamp(reading.getCurrentReadingDate())).readingQualityCode(reading.getReadingQualityCode())
				.meterStatus(reading.getMeterStatus() == null ? null : reading.getMeterStatus().toString()).source(source).build();
	}

	private MeterReadingHistory toMeterReadingHistory(MeterReading reading) {
		AuditDetails audit = reading.getAuditDetails();
		return MeterReadingHistory.builder().id(reading.getId()).billingPeriod(reading.getBillingPeriod())
				.meterStatus(reading.getMeterStatus() == null ? null : reading.getMeterStatus().toString())
				.readingQualityCode(reading.getReadingQualityCode()).lastReading(decimal(reading.getLastReading()))
				.lastReadingDate(validTimestamp(reading.getLastReadingDate())).currentReading(decimal(reading.getCurrentReading()))
				.currentReadingDate(validTimestamp(reading.getCurrentReadingDate())).consumption(decimal(reading.getConsumption()))
				.billingCycleId(reading.getBillingCycleId()).billingCycleStatus(reading.getStatus())
				.createdTime(audit == null ? null : audit.getCreatedTime())
				.lastModifiedTime(audit == null ? null : audit.getLastModifiedTime()).build();
	}

	private BillingCycleHistory toBillingCycleHistory(WaterBillingCycle cycle) {
		return BillingCycleHistory.builder().id(cycle.getId()).billingPeriodFrom(cycle.getBillingperiodfrom())
				.billingPeriodTo(cycle.getBillingperiodto()).meterReadingId(cycle.getMeterreadingid())
				.readingQualityCode(cycle.getReadingqualitycode())
				.billingBasis(cycle.getBillingbasis() == null ? null : cycle.getBillingbasis().toString())
				.previousOkReading(cycle.getPreviousokreading()).previousOkReadingDate(cycle.getPreviousokreadingdate())
				.currentReading(cycle.getCurrentreading()).currentReadingDate(cycle.getCurrentreadingdate())
				.actualConsumption(cycle.getActualconsumption()).averageConsumption(cycle.getAverageconsumption())
				.billingConsumption(cycle.getBillingconsumption()).previousConsumption(cycle.getPreviousconsumption())
				.deviationFactor(cycle.getDeviationfactor()).onePointFiveXFlag(cycle.getOnepointfivexflag())
				.averageCycleCount(cycle.getAveragecyclecount()).provisionalCycleCount(cycle.getProvisionalcyclecount())
				.zroStatus(cycle.getZrostatus() == null ? null : cycle.getZrostatus().toString())
				.zroRemarks(cycle.getZroremarks())
				.correctionStatus(cycle.getCorrectionstatus() == null ? null : cycle.getCorrectionstatus().toString())
				.status(cycle.getStatus() == null ? null : cycle.getStatus().toString())
				.calculationId(cycle.getCalculationid()).demandId(cycle.getDemandid()).billId(cycle.getBillid())
				.createdTime(cycle.getCreatedtime()).lastModifiedTime(cycle.getLastmodifiedtime()).build();
	}

	private OwnerInfo primaryOwner(List<OwnerInfo> owners) {
		if (owners == null || owners.isEmpty()) {
			return null;
		}
		for (OwnerInfo owner : owners) {
			if (owner != null && Boolean.TRUE.equals(owner.getIsPrimaryOwner())) {
				return owner;
			}
		}
		for (OwnerInfo owner : owners) {
			if (owner != null) {
				return owner;
			}
		}
		return null;
	}

	private String propertyLocality(Property property) {
		if (property == null || property.getAddress() == null || property.getAddress().getLocality() == null) {
			return null;
		}
		return firstNonBlank(property.getAddress().getLocality().getName(), property.getAddress().getLocality().getCode());
	}

	private String formatAddress(Address address, String tenantId) {
		if (address == null) {
			return null;
		}
		List<String> parts = new ArrayList<String>();
		appendAddressPart(parts, address.getDoorNo());
		appendAddressPart(parts, address.getPlotNo());
		appendAddressPart(parts, address.getBuildingName());
		appendAddressPart(parts, address.getStreet());
		appendAddressPart(parts, address.getLandmark());
		appendAddressPart(parts, address.getLocality() == null ? null : address.getLocality().getName());
		if (!isTenantIdValue(address.getCity(), tenantId)) {
			appendAddressPart(parts, address.getCity());
		}
		appendAddressPart(parts, address.getDistrict());
		appendAddressPart(parts, address.getState());
		appendAddressPart(parts, address.getPincode());
		return parts.isEmpty() ? null : String.join(", ", parts);
	}

	private boolean isTenantIdValue(String value, String tenantId) {
		return StringUtils.hasText(value) && StringUtils.hasText(tenantId)
				&& value.trim().equalsIgnoreCase(tenantId.trim());
	}

	private void appendAddressPart(List<String> parts, String value) {
		if (StringUtils.hasText(value)) {
			parts.add(value.trim());
		}
	}

	private String firstNonBlank(String first, String second) {
		return StringUtils.hasText(first) ? first : (StringUtils.hasText(second) ? second : null);
	}

	private String readAdditionalDetail(Object additionalDetails, String... keys) {
		if (additionalDetails == null || keys == null) {
			return null;
		}
		JsonNode root;
		try {
			root = objectMapper.valueToTree(additionalDetails);
		} catch (IllegalArgumentException ex) {
			return null;
		}
		if (root == null || !root.isObject()) {
			return null;
		}
		for (String key : keys) {
			java.util.Iterator<Map.Entry<String, JsonNode>> iterator = root.fields();
			while (iterator.hasNext()) {
				Map.Entry<String, JsonNode> entry = iterator.next();
				if (entry.getKey().equalsIgnoreCase(key) && !entry.getValue().isNull()) {
					String text = entry.getValue().asText();
					if (StringUtils.hasText(text)) {
						return text.trim();
					}
				}
			}
		}
		return null;
	}

	private boolean isEmployeeRequest(RequestInfo requestInfo) {
		return requestInfo != null && requestInfo.getUserInfo() != null
				&& "EMPLOYEE".equalsIgnoreCase(requestInfo.getUserInfo().getType());
	}

	private Long validTimestamp(Long value) {
		return value == null || value.longValue() <= 0L ? null : value;
	}

	private BigDecimal parseDecimal(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return new BigDecimal(value.trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private BigDecimal decimal(Double value) {
		return value == null ? null : BigDecimal.valueOf(value);
	}


}
