/* tslint:disable */
/* eslint-disable */
// Generated using typescript-generator version 2.22.595 on 2026-09-13 15:00:23.

export namespace Digit {

    interface MeterConnectionRequest {
        RequestInfo: RequestInfo;
        meterReadings: MeterReading;
        meterReadingList: MeterReading[];
    }

    interface MeterReadingResponse {
        ResponseInfo: ResponseInfo;
        meterReadings: MeterReading[];
    }

    interface MeterReadingSearchCriteria {
        empty: boolean;
        tenantId: string;
        connectionNos: string[];
        offset: number;
        locality: string;
        limit: number;
    }

    interface CalculationReq {
        migrationCount: MigrationCount;
        RequestInfo: RequestInfo;
        isconnectionCalculation: boolean;
        isDisconnectionRequest: boolean;
        isReconnectionRequest: boolean;
        CalculationCriteria: CalculationCriteria[];
    }

    interface CalculationRes {
        ResponseInfo: ResponseInfo;
        Calculation: Calculation[];
    }

    interface GetBillCriteria {
        connectionNumber: string;
        amountExpected: number;
        connectionId: string;
        assessmentYear: string;
        tenantId: string;
        billId: string;
        consumerCodes: string[];
        isPaymentCompleted: boolean;
    }

    interface DemandResponse {
        ResponseInfo: ResponseInfo;
        Demands: Demand[];
    }

    interface AdhocTaxReq {
        RequestInfo: RequestInfo;
        demandId: string;
        adhocrebate: number;
        adhocpenalty: number;
        consumerCode: string;
        businessService: string;
    }

    interface RequestInfoWrapper {
        RequestInfo: RequestInfo;
    }

    interface WaterUserStatus {
        id: number;
        uuid: string;
        userName: string;
        password: string;
        salutation: string;
        name: string;
        gender: string;
        mobileNumber: string;
        emailId: string;
        altContactNumber: string;
        pan: string;
        aadhaarNumber: string;
        permanentAddress: string;
        permanentCity: string;
        permanentPinCode: string;
        correspondenceCity: string;
        correspondencePinCode: string;
        correspondenceAddress: string;
        active: boolean;
        dob: number;
        pwdExpiryDate: number;
        locale: string;
        type: string;
        signature: string;
        accountLocked: boolean;
        roles: Role[];
        fatherOrHusbandName: string;
        bloodGroup: string;
        identificationMark: string;
        photo: string;
        createdBy: string;
        createdDate: number;
        lastModifiedBy: string;
        lastModifiedDate: number;
        tenantId: string;
    }

    interface User {
        id: number;
        userName: string;
        name: string;
        type: string;
        mobileNumber: string;
        emailId: string;
        roles: Role[];
        tenantId: string;
        uuid: string;
    }

    interface RequestInfo {
        apiId: string;
        ver: string;
        ts: number;
        action: string;
        did: string;
        key: string;
        msgId: string;
        authToken: string;
        correlationId: string;
        plainAccessRequest: PlainAccessRequest;
        userInfo: User;
    }

    /**
     * This is lightweight meter reading object that can be used as reference by definitions needing meterreading linking.
     */
    interface MeterReading {
        /**
         * Unique Identifier of the meterreading for internal reference.
         */
        id: string;
        /**
         * Formatted billingPeriod
         */
        billingPeriod: string;
        meterStatus: MeterStatusEnum;
        /**
         * DJB reading quality / billing remark
         */
        readingQualityCode: string;
        /**
         * Last Reading
         */
        lastReading: number;
        /**
         * The date of meter last reading date.
         */
        lastReadingDate: number;
        /**
         * Current Reading
         */
        currentReading: number;
        /**
         * The date of meter current reading date.
         */
        currentReadingDate: number;
        /**
         * Formatted billingPeriod
         */
        connectionNo: string;
        consumption: number;
        generateDemand: boolean;
        auditDetails: AuditDetails;
        tenantId: string;
        status: string;
    }

    interface ResponseInfo {
        apiId: string;
        ver: string;
        ts: number;
        resMsgId: string;
        msgId: string;
        status: string;
    }

    interface MigrationCount {
        id: string;
        offset: number;
        limit: number;
        createdTime: number;
        tenantid: string;
        recordCount: number;
        businessService: string;
        message: string;
        auditTopic: string;
        auditTime: number;
    }

    interface CalculationCriteria {
        calculationDetail: CalculationDetail;
        waterConnection: WaterConnection;
        connectionNo: string;
        assessmentYear: string;
        tenantId: string;
        lastReading: number;
        currentReading: number;
        from: number;
        to: number;
        applicationNo: string;
    }

    interface Calculation {
        tenantId: string;
        totalAmount: number;
        charge: number;
        taxAmount: number;
        fee: number;
        exemption: number;
        rebate: number;
        penalty: number;
        taxHeadEstimates: TaxHeadEstimate[];
        applicationNo: string;
        billingSlabIds: string[];
        waterConnection: WaterConnection;
        connectionNo: string;
        calculationDetail: CalculationDetail;
    }

    interface Demand {
        id: string;
        tenantId: string;
        consumerCode: string;
        consumerType: string;
        businessService: string;
        payer: User;
        taxPeriodFrom: number;
        taxPeriodTo: number;
        demandDetails: DemandDetail[];
        auditDetails: AuditDetails;
        billExpiryTime: number;
        additionalDetails: any;
        minimumAmountPayable: number;
        status: DemandStatus;
    }

    interface Role {
        id: number;
        name: string;
        code: string;
        tenantId: string;
    }

    interface PlainAccessRequest {
        recordId: string;
        plainRequestFields: string[];
    }

    interface AuditDetails {
        createdBy: string;
        lastModifiedBy: string;
        createdTime: number;
        lastModifiedTime: number;
    }

    interface CalculationDetail {
        propertyDetail: PropertyDetail;
        waterDemandDetail: WaterDemandDetail;
        infrastructureChargeDetail: InfrastructureChargeDetail;
    }

    /**
     * This is lightweight property object that can be used as reference by definitions needing property linking. Actual Property Object extends this to include more elaborate attributes of the property.
     */
    interface WaterConnection extends Connection {
        /**
         * It is a namespaced master data, defined in MDMS
         */
        waterSource: string;
        /**
         * Unique id of the meter.
         */
        meterId: string;
        /**
         * The date of meter installation date.
         */
        meterInstallationDate: number;
        /**
         * No of proposed Pipe size is citizen input
         */
        proposedPipeSize: number;
        /**
         * No of proposed taps no is citizen input
         */
        proposedTaps: number;
        /**
         * Pipe size for non-metered calulation attribute.
         */
        pipeSize: number;
        /**
         * No of taps for non-metered calculation attribute.
         */
        noOfTaps: number;
        isDisconnectionTemporary: boolean;
        disconnectionReason: string;
        dueVerification: DueVerification[];
    }

    interface TaxHeadEstimate {
        taxHeadCode: string;
        estimateAmount: number;
        category: TaxHeadCategory;
        status: string;
    }

    interface DemandDetail {
        id: string;
        demandId: string;
        taxHeadMasterCode: string;
        taxAmount: number;
        collectionAmount: number;
        auditDetails: AuditDetails;
        tenantId: string;
    }

    interface PropertyDetail {
        propertyId: string;
        tenantId: string;
        propertyType: string;
        usageCategory: string;
        waterConnectionUsageType: string;
        colonyCategory: string;
        localityCode: string;
        landArea: number;
        superBuiltUpArea: number;
        farArea: number;
        coveredArea: number;
        numberOfDwellingUnits: number;
        numberOfBeds: number;
        numberOfRooms: number;
        numberOfStudents: number;
        numberOfStaff: number;
    }

    interface WaterDemandDetail {
        matchedNormCode: string;
        matchedNormName: string;
        formulaUsed: string;
        rawOccupancy: number;
        calculatedOccupancy: number;
        chosenLpcd: number;
        baseDemand: number;
        contingencyPercentage: number;
        totalWaterDemandLPD: number;
        contextVariables: { [index: string]: number };
    }

    interface InfrastructureChargeDetail {
        colonyCategory: string;
        plotArea: number;
        minimumPlotArea: number;
        waterRatePerLPD: number;
        sewerRatePerLPD: number;
        waterComponentIFC: number;
        sewerComponentIFC: number;
        grossIFC: number;
        rebatePercentage: number;
        rebateAmount: number;
        netIFC: number;
        institutionalRebateApplied: boolean;
        institutionalRebateReason: string;
        institutionalRebatePercentage: number;
        institutionalRebateAmount: number;
        dwellingRebateApplied: boolean;
        dwellingRebateReason: string;
        dwellingRebatePercentage: number;
        dwellingRebateAmount: number;
        netIFCAfterColonyRebate: number;
        netIFCAfterInstitutionalRebate: number;
        netIFCAfterDwellingRebate: number;
    }

    interface Document {
        id: string;
        documentType: string;
        fileStoreId: string;
        documentUid: string;
        auditDetails: AuditDetails;
        status: Status;
    }

    interface PlumberInfo {
        /**
         * The id of the user.
         */
        id: string;
        /**
         * The name of the user.
         */
        name: string;
        /**
         * Plumber unique license number.
         */
        licenseNo: string;
        /**
         * MobileNumber of the user.
         */
        mobileNumber: string;
        /**
         * Gender of the user.
         */
        gender: string;
        /**
         * Father or Husband name of the user.
         */
        fatherOrHusbandName: string;
        /**
         * The current address of the owner for correspondence.
         */
        correspondenceAddress: string;
        /**
         * The relationship of gaurdian.
         */
        relationship: string;
        /**
         * Json object to capture any extra information which is not accommodated of model
         */
        additionalDetails: any;
        auditDetails: AuditDetails;
    }

    interface RoadCuttingInfo {
        id: string;
        roadType: string;
        roadCuttingArea: number;
        auditDetails: AuditDetails;
        status: Status;
    }

    /**
     * A Object holds the basic data for a Trade License
     */
    interface ProcessInstance {
        id: string;
        tenantId: string;
        businessService: string;
        businessId: string;
        action: string;
        moduleName: string;
        state: State;
        comment: string;
        documents: Document[];
        assigner: User;
        assignes: User[];
        nextActions: Action[];
        stateSla: number;
        businesssServiceSla: number;
        previousStatus: string;
        entity: any;
        auditDetails: AuditDetails;
    }

    interface OwnerInfo extends WaterUserStatus {
        ownerInfoUuid: string;
        isPrimaryOwner: boolean;
        ownerShipPercentage: number;
        ownerType: string;
        institutionId: string;
        status: Status;
        documents: Document[];
        relationship: Relationship;
    }

    interface DueVerification {
        kno: string;
        fullName: string;
        fullAddress: string;
        dueAmount: string;
        totalAmount: string;
        remarks: string;
    }

    /**
     * This is lightweight property object that can be used as reference by definitions needing property linking. Actual Property Object extends this to include more elaborate attributes of the property.
     */
    interface Connection {
        /**
         * Unique Identifier of the connection for internal reference.
         */
        id: string;
        /**
         * Unique ULB identifier.
         */
        tenantId: string;
        /**
         * UUID of the property.
         */
        propertyId: string;
        /**
         * Formatted application number, which will be generated using ID-Gen at the time .
         */
        applicationNo: string;
        applicationStatus: string;
        status: ConnectionStatus;
        /**
         * Formatted connection number, which will be generated using ID-Gen service after aproval of connection application in case of new application. If the source of data is "DATA_ENTRY" then application status will be considered as "APROVED" application.
         */
        connectionNo: string;
        /**
         * Mandatory if source is "DATA_ENTRY".
         */
        oldConnectionNo: string;
        /**
         * The documents attached by owner for exemption.
         */
        documents: Document[];
        /**
         * The documents attached by owner for exemption.
         */
        plumberInfo: PlumberInfo[];
        /**
         * It is a master data, defined in MDMS. If road cutting is required to established the connection then we need to capture the details of road type.
         */
        roadType: string;
        /**
         * Capture the road cutting area in sqft.
         */
        roadCuttingArea: number;
        /**
         * The road cutting information given by owner
         */
        roadCuttingInfo: RoadCuttingInfo[];
        connectionExecutionDate: number;
        /**
         * It is a master data, defined in MDMS
         */
        connectionCategory: string;
        /**
         * It is a master data, defined in MDMS.
         */
        connectionType: string;
        /**
         * Json object to capture any extra information which is not accommodated of model
         */
        additionalDetails: any;
        auditDetails: AuditDetails;
        processInstance: ProcessInstance;
        applicationType: string;
        dateEffectiveFrom: number;
        /**
         * The connection holder info will enter by employee or citizen
         */
        connectionHolders: OwnerInfo[];
        disconnectionExecutionDate: number;
    }

    /**
     * A Object holds the basic data for a Trade License
     */
    interface State {
        auditDetails: AuditDetails;
        uuid: string;
        tenantId: string;
        businessServiceId: string;
        sla: number;
        state: string;
        applicationStatus: string;
        docUploadRequired: boolean;
        isStartState: boolean;
        isTerminateState: boolean;
        isStateUpdatable: boolean;
        actions: Action[];
    }

    /**
     * A Object holds the basic data for a Trade License
     */
    interface Action {
        auditDetails: AuditDetails;
        uuid: string;
        tenantId: string;
        currentState: string;
        action: string;
        nextState: string;
        roles: string[];
    }

    type DemandStatus = "ACTIVE" | "CANCELLED" | "ADJUSTED";

    type ConnectionStatus = "Active" | "Inactive";

    type MeterStatusEnum = "Working" | "Locked" | "Breakdown" | "No-meter" | "Reset" | "Replacement";

    type TaxHeadCategory = "TAX" | "FEE" | "REBATE" | "EXEMPTION" | "ADVANCE_COLLECTION" | "PENALTY" | "FINES" | "CHARGES" | "WS_RECONNECTION";

    type Status = "ACTIVE" | "INACTIVE" | "INWORKFLOW";

    type Relationship = "FATHER" | "HUSBAND";

}
