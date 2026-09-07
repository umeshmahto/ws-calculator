package org.egov.wscalculation.djbmonthlybilling.repository;

import org.springframework.stereotype.Component;

@Component
public class DJBMonthlyBillingQueryBuilder {

    public String findById() {
        return "SELECT * FROM eg_ws_billingcycle WHERE tenantid = ? AND id = ?";
    }

    public String findByConnectionAndPeriod() {
        return "SELECT * FROM eg_ws_billingcycle " +
               "WHERE tenantid = ? AND connectionno = ? " +
               "AND billingperiodfrom = ? AND billingperiodto = ?";
    }

    public String findLatestByConnection() {
        return "SELECT * FROM eg_ws_billingcycle " +
               "WHERE tenantid = ? AND connectionno = ? " +
               "ORDER BY billingperiodto DESC LIMIT 1";
    }

    public String findLatestOkByConnection() {
        return "SELECT * FROM eg_ws_billingcycle " +
               "WHERE tenantid = ? AND connectionno = ? " +
               "AND readingqualitycode = 'OK' " +
               "ORDER BY billingperiodto DESC LIMIT 1";
    }

    public String findPreviousActualCycles() {
        return "SELECT * FROM eg_ws_billingcycle " +
               "WHERE tenantid = ? AND connectionno = ? " +
               "AND billingperiodto < ? " +
               "AND billingbasis IN ('ACTUAL', 'CORRECTED_ACTUAL') " +
               "ORDER BY billingperiodto DESC LIMIT ?";
    }

    public String findCyclesForConnection() {
        return "SELECT * FROM eg_ws_billingcycle " +
               "WHERE tenantid = ? AND connectionno = ? " +
               "AND billingperiodto < ? " +
               "ORDER BY billingperiodto DESC LIMIT ?";
    }

    public String findCyclesForCorrection() {
        return "SELECT * FROM eg_ws_billingcycle " +
               "WHERE tenantid = ? AND connectionno = ? " +
               "AND billingperiodto > ? AND billingperiodto < ? " +
               "AND billingbasis IN ('AVERAGE', 'PROVISIONAL') " +
               "ORDER BY billingperiodto ASC";
    }

    public String insertBillingCycle() {
        return "INSERT INTO eg_ws_billingcycle (" +
                "id, tenantid, connectionno, billingperiodfrom, billingperiodto, " +
                "meterreadingid, readingqualitycode, billingbasis, previousokreading, previousokreadingdate, " +
                "currentreading, currentreadingdate, actualconsumption, averageconsumption, billingconsumption, " +
                "previousconsumption, deviationfactor, onepointfivexflag, averagecyclecount, provisionalcyclecount, " +
                "zrostatus, zroremarks, zroactionby, zroactiondate, calculationid, demandid, billid, " +
                "correctionstatus, status, createdby, createdtime, lastmodifiedby, lastmodifiedtime) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public String updateBillingCycle() {
        return "UPDATE eg_ws_billingcycle SET " +
                "connectionno = ?, billingperiodfrom = ?, billingperiodto = ?, meterreadingid = ?, " +
                "readingqualitycode = ?, billingbasis = ?, previousokreading = ?, previousokreadingdate = ?, " +
                "currentreading = ?, currentreadingdate = ?, actualconsumption = ?, averageconsumption = ?, " +
                "billingconsumption = ?, previousconsumption = ?, deviationfactor = ?, onepointfivexflag = ?, " +
                "averagecyclecount = ?, provisionalcyclecount = ?, zrostatus = ?, zroremarks = ?, zroactionby = ?, " +
                "zroactiondate = ?, calculationid = ?, demandid = ?, billid = ?, correctionstatus = ?, status = ?, " +
                "lastmodifiedby = ?, lastmodifiedtime = ? WHERE tenantid = ? AND id = ?";
    }

    public String findZroById() {
        return "SELECT * FROM eg_ws_zroverification WHERE tenantid = ? AND id = ?";
    }

    public String findZroByBillingCycle() {
        return "SELECT * FROM eg_ws_zroverification WHERE tenantid = ? AND billingcycleid = ?";
    }

    public String insertZro() {
        return "INSERT INTO eg_ws_zroverification (" +
                "id, tenantid, billingcycleid, connectionno, consumption, previousconsumption, deviationfactor, " +
                "status, remarks, actionby, actiondate, createdby, createdtime, lastmodifiedby, lastmodifiedtime) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public String updateZro() {
        return "UPDATE eg_ws_zroverification SET billingcycleid = ?, connectionno = ?, consumption = ?, " +
                "previousconsumption = ?, deviationfactor = ?, status = ?, remarks = ?, actionby = ?, actiondate = ?, " +
                "lastmodifiedby = ?, lastmodifiedtime = ? WHERE tenantid = ? AND id = ?";
    }

    public String findCorrectionById() {
        return "SELECT * FROM eg_ws_billingcorrection WHERE tenantid = ? AND id = ?";
    }

    public String findCorrectionsByConnection() {
        return "SELECT * FROM eg_ws_billingcorrection " +
               "WHERE tenantid = ? AND connectionno = ? ORDER BY createdtime DESC";
    }

    public String insertCorrection() {
        return "INSERT INTO eg_ws_billingcorrection (" +
                "id, tenantid, connectionno, frombillingcycleid, tobillingcycleid, status, reason, " +
                "paidadjustmentamount, appliedpaidadjustmentamount, residualpaidcreditamount, olddemandid, oldbillid, " +
                "correcteddemandid, correctedbillid, createdby, createdtime, lastmodifiedby, lastmodifiedtime) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )";
    }

    public String updateCorrection() {
        return "UPDATE eg_ws_billingcorrection SET connectionno = ?, frombillingcycleid = ?, tobillingcycleid = ?, " +
                "status = ?, reason = ?, paidadjustmentamount = ?, appliedpaidadjustmentamount = ?, residualpaidcreditamount = ?, " +
                "olddemandid = ?, oldbillid = ?, correcteddemandid = ?, correctedbillid = ?, lastmodifiedby = ?, lastmodifiedtime = ? " +
                "WHERE tenantid = ? AND id = ?";
    }

    public String findOpenResidualCreditsForUpdate() {
        return "SELECT * FROM eg_ws_billingcredit " +
               "WHERE tenantid = ? AND connectionno = ? AND status = 'OPEN' " +
               "AND remainingamount > reservedamount " +
               "ORDER BY createdtime ASC, id ASC FOR UPDATE";
    }

    public String findResidualCreditByIdForUpdate() {
        return "SELECT * FROM eg_ws_billingcredit WHERE tenantid = ? AND id = ? FOR UPDATE";
    }

    public String findResidualCreditBySourceCorrection() {
        return "SELECT * FROM eg_ws_billingcredit WHERE tenantid = ? AND sourcecorrectionid = ?";
    }

    public String findResidualCreditAllocationsByBillingCycle() {
        return "SELECT * FROM eg_ws_billingcreditallocation " +
               "WHERE tenantid = ? AND billingcycleid = ? ORDER BY createdtime ASC, id ASC";
    }

    public String insertResidualCredit() {
        return "INSERT INTO eg_ws_billingcredit (" +
               "id, tenantid, connectionno, sourcecorrectionid, originalamount, remainingamount, reservedamount, " +
               "status, createdby, createdtime, lastmodifiedby, lastmodifiedtime) " +
               "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public String updateResidualCredit() {
        return "UPDATE eg_ws_billingcredit SET connectionno = ?, sourcecorrectionid = ?, originalamount = ?, " +
               "remainingamount = ?, reservedamount = ?, status = ?, lastmodifiedby = ?, lastmodifiedtime = ? " +
               "WHERE tenantid = ? AND id = ?";
    }

    public String insertResidualCreditAllocation() {
        return "INSERT INTO eg_ws_billingcreditallocation (" +
               "id, tenantid, creditid, billingcycleid, demandid, billid, appliedamount, status, createdby, createdtime, " +
               "lastmodifiedby, lastmodifiedtime) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public String updateResidualCreditAllocation() {
        return "UPDATE eg_ws_billingcreditallocation SET demandid = ?, billid = ?, appliedamount = ?, status = ?, " +
               "lastmodifiedby = ?, lastmodifiedtime = ? WHERE tenantid = ? AND id = ?";
    }

    public String reserveResidualCredit() {
        return "UPDATE eg_ws_billingcredit SET reservedamount = reservedamount + ?, " +
               "lastmodifiedby = ?, lastmodifiedtime = ? " +
               "WHERE tenantid = ? AND id = ? AND status = 'OPEN' " +
               "AND remainingamount - reservedamount >= ?";
    }

    public String consumeReservedResidualCredit() {
        return "UPDATE eg_ws_billingcredit SET reservedamount = reservedamount - ?, " +
               "remainingamount = remainingamount - ?, " +
               "status = CASE WHEN remainingamount - ? = 0 THEN 'EXHAUSTED' ELSE 'OPEN' END, " +
               "lastmodifiedby = ?, lastmodifiedtime = ? " +
               "WHERE tenantid = ? AND id = ? AND reservedamount >= ? AND remainingamount >= ?";
    }

    public String releaseReservedResidualCredit() {
        return "UPDATE eg_ws_billingcredit SET reservedamount = reservedamount - ?, " +
               "lastmodifiedby = ?, lastmodifiedtime = ? " +
               "WHERE tenantid = ? AND id = ? AND reservedamount >= ?";
    }

   public String findPreviousOkByConnectionBefore() {
        return "SELECT * FROM eg_ws_billingcycle " +
               "WHERE tenantid = ? AND connectionno = ? " +
               "AND readingqualitycode = 'OK' " +
               "AND billingperiodto < ? " +
               "ORDER BY billingperiodto DESC LIMIT 1";
    }
}
