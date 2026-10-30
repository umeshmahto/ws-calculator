package org.egov.wscalculation.djbmonthlybilling.web.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DJBMonthlyBillingSystemContext {

    private String sourceSystem;
    // Internal enrichment is retained for building the compact DJB projection, not serialized.
    @JsonIgnore
    private ConnectionDetails connection;
    @JsonIgnore
    private PropertyDetails property;
    @JsonIgnore
    private RegisterDetails registerDetails;

    @Builder.Default
    @JsonIgnore
    private List<MeterReadingHistory> meterReadingHistory = Collections.emptyList();

    @Builder.Default
    @JsonIgnore
    private List<BillingCycleHistory> billingCycleHistory = Collections.emptyList();

    private DjbRegisterDetails djbRegister;

    @Builder.Default
    @JsonIgnore
    private List<String> warnings = new ArrayList<String>();

    @JsonIgnore
    private boolean meterReadingHistoryComplete;
    @JsonIgnore
    private boolean billingCycleHistoryComplete;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionDetails {
        private String id;
        private String tenantId;
        private String connectionNo;
        private String legacyAccountId;
        private String djbAccountId;
        private String applicationNo;
        private String applicationStatus;
        private String connectionStatus;
        private String propertyId;
        private String connectionCategory;
        private String connectionType;
        private String waterSource;
        private String meterId;
        private String meterNumber;
        private String meterModel;
        private Long meterInstallationDate;
        private Long connectionExecutionDate;
        private Long dateEffectiveFrom;
        private Long disconnectionExecutionDate;
        private String zoneLocation;
        private String locality;
        private String meterReaderKey;
        private String billingRoundId;
        private String saStatusFlag;
        private String currentReaderRemarkCode;
        private String currentNewAverageReading;
        private Double pipeSize;
        private Integer noOfTaps;
        private String consumerName;
        private String phone;
        private String email;
        private String consumerAddress;
        private Long createdTime;
        private Long lastModifiedTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PropertyDetails {
        private String id;
        private String propertyId;
        private String accountId;
        private String oldPropertyId;
        private String status;
        private String propertyType;
        private String ownershipCategory;
        private String usageCategory;
        private Long noOfFloors;
        private Double landArea;
        private BigDecimal superBuiltUpArea;
        private AddressDetails address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressDetails {
        private String doorNo;
        private String plotNo;
        private String buildingName;
        private String street;
        private String landmark;
        private String localityCode;
        private String localityName;
        private String city;
        private String district;
        private String region;
        private String state;
        private String country;
        private String pincode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterDetails {
        private Long billingPeriodFrom;
        private Long billingPeriodTo;
        private String readingQualityCode;
        private String billingBasis;
        private String billingCycleStatus;
        private BigDecimal previousConsumption;
        private BigDecimal averageConsumption;
        private BigDecimal billingConsumption;
        private BigDecimal previousRegisterReading;
        private Long previousRegisterReadingDate;
        private String previousRegisterReadingQualityCode;
        private String previousRegisterMeterStatus;
        private BigDecimal currentRegisterReading;
        private Long currentRegisterReadingDate;
        private String currentRegisterReadingQualityCode;
        private String currentRegisterMeterStatus;
        private ReadingPoint lastKnownOkReading;
        private Long currentBillGeneratedDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadingPoint {
        private BigDecimal reading;
        private Long readingDate;
        private String readingQualityCode;
        private String meterStatus;
        private String source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MeterReadingHistory {
        private String id;
        private String billingPeriod;
        private String meterStatus;
        private String readingQualityCode;
        private BigDecimal lastReading;
        private Long lastReadingDate;
        private BigDecimal currentReading;
        private Long currentReadingDate;
        private BigDecimal consumption;
        private String billingCycleId;
        private String billingCycleStatus;
        private Long createdTime;
        private Long lastModifiedTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillingCycleHistory {
        private String id;
        private Long billingPeriodFrom;
        private Long billingPeriodTo;
        private String meterReadingId;
        private String readingQualityCode;
        private String billingBasis;
        private BigDecimal previousOkReading;
        private Long previousOkReadingDate;
        private BigDecimal currentReading;
        private Long currentReadingDate;
        private BigDecimal actualConsumption;
        private BigDecimal averageConsumption;
        private BigDecimal billingConsumption;
        private BigDecimal previousConsumption;
        private BigDecimal deviationFactor;
        private Boolean onePointFiveXFlag;
        private Integer averageCycleCount;
        private Integer provisionalCycleCount;
        private String zroStatus;
        private String zroRemarks;
        private String correctionStatus;
        private String status;
        private String calculationId;
        private String demandId;
        private String billId;
        private Long createdTime;
        private Long lastModifiedTime;
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DjbRegisterDetails {
        private ConsumerRegister consumer;
        private TariffClassification classification;
        private MeterRegister meter;
        // Reading and consumption details are exposed once under billingStatement.reading.
        @JsonIgnore
        private ReadingRegister previousReading;
        @JsonIgnore
        private ReadingRegister actualReading;
        @JsonIgnore
        private ReadingRegister previousOkReading;
        @JsonIgnore
        private ConsumptionRegister consumption;
        @JsonIgnore
        private BillingRoundRegister billingRound;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConsumerRegister {
        private String accountId;
        private String name;
        private String address;
        private String phone;
        private String zoneLocation;
        private String meterReaderKey;
        private String serviceAccountStatusFlag;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TariffClassification {
        private String propertyUsageType;
        @JsonIgnore
        private String sourcePropertyUsageType;
        @JsonIgnore
        private String upyogTariffCategory;
        private String djbCategoryCode;
        private String djbCategoryName;
        private String mappingStatus;
        @JsonIgnore
        private String mappingSource;
        @JsonIgnore
        private String ruleReference;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MeterRegister {
        private String meterNumber;
        private String meterModel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReadingRegister {
        private Long readingDate;
        private BigDecimal readingKl;
        private String readingQualityCode;
        private String meterStatus;
        private String readType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConsumptionRegister {
        private BigDecimal previousConsumption;
        private BigDecimal legacyPreviousConsumption;
        private BigDecimal averageReading;
        private BigDecimal billingConsumption;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BillingRoundRegister {
        private String billingRoundId;
        private Long previousBillGeneratedAt;
        private Long currentBillGeneratedAt;
        private String readerRemarkCode;
        private BigDecimal regularReading;
        private BigDecimal newAverageReading;
    }


}
