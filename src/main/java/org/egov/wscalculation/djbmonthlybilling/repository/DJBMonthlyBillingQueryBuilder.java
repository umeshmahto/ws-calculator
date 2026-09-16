package org.egov.wscalculation.djbmonthlybilling.repository;

import org.springframework.stereotype.Component;

@Component
public class DJBMonthlyBillingQueryBuilder {

	public String findById() {
		return "SELECT * FROM eg_ws_billingcycle WHERE tenantid = ? AND id = ?";
	}

	public String findByConnectionAndPeriod() {
		return "SELECT * FROM eg_ws_billingcycle " + "WHERE tenantid = ? AND connectionno = ? "
				+ "AND billingperiodfrom = ? AND billingperiodto = ?";
	}

	public String findOverlappingCycles() {
		return "SELECT * FROM eg_ws_billingcycle " + "WHERE tenantid = ? AND connectionno = ? "
				+ "AND billingperiodfrom < ? AND billingperiodto > ? " + "ORDER BY billingperiodfrom ASC";
	}

	/**
	 * Database-level per-connection transaction lock. Uses PostgreSQL advisory
	 * locking; no new table or schema object is required. The lock lives only for
	 * the current transaction and serializes billing-cycle reservation for a given
	 * tenant+connection across application instances.
	 */
	public String lockConnectionForBilling() {
		return "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))";
	}

	public String findLatestByConnection() {
		return "SELECT * FROM eg_ws_billingcycle " + "WHERE tenantid = ? AND connectionno = ? "
				+ "ORDER BY billingperiodto DESC LIMIT 1";
	}

	public String findLatestOkByConnection() {
		return "SELECT * FROM eg_ws_billingcycle " + "WHERE tenantid = ? AND connectionno = ? "
				+ "AND readingqualitycode = 'OK' " + "ORDER BY billingperiodto DESC LIMIT 1";
	}

	public String findPreviousActualCycles() {
		return "SELECT * FROM eg_ws_billingcycle " + "WHERE tenantid = ? AND connectionno = ? "
				+ "AND billingperiodto < ? " + "AND billingbasis IN ('ACTUAL', 'CORRECTED_ACTUAL') "
				+ "ORDER BY billingperiodto DESC LIMIT ?";
	}

	public String findCyclesForConnection() {
		return "SELECT * FROM eg_ws_billingcycle " + "WHERE tenantid = ? AND connectionno = ? "
				+ "AND billingperiodto < ? " + "ORDER BY billingperiodto DESC LIMIT ?";
	}

	public String findCyclesForCorrection() {
		return "SELECT * FROM eg_ws_billingcycle " + "WHERE tenantid = ? AND connectionno = ? "
				+ "AND billingperiodto > ? AND billingperiodto < ? " + "AND billingbasis IN ('AVERAGE', 'PROVISIONAL') "
				+ "ORDER BY billingperiodto ASC";
	}

	public String insertBillingCycle() {
		return "INSERT INTO eg_ws_billingcycle (" + "id, tenantid, connectionno, billingperiodfrom, billingperiodto, "
				+ "meterreadingid, readingqualitycode, billingbasis, previousokreading, previousokreadingdate, "
				+ "currentreading, currentreadingdate, actualconsumption, averageconsumption, billingconsumption, "
				+ "previousconsumption, deviationfactor, onepointfivexflag, averagecyclecount, provisionalcyclecount, "
				+ "zrostatus, zroremarks, zroactionby, zroactiondate, calculationid, demandid, billid, "
				+ "correctionstatus, status, createdby, createdtime, lastmodifiedby, lastmodifiedtime) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	}

	public String updateBillingCycle() {
		return "UPDATE eg_ws_billingcycle SET "
				+ "connectionno = ?, billingperiodfrom = ?, billingperiodto = ?, meterreadingid = ?, "
				+ "readingqualitycode = ?, billingbasis = ?, previousokreading = ?, previousokreadingdate = ?, "
				+ "currentreading = ?, currentreadingdate = ?, actualconsumption = ?, averageconsumption = ?, "
				+ "billingconsumption = ?, previousconsumption = ?, deviationfactor = ?, onepointfivexflag = ?, "
				+ "averagecyclecount = ?, provisionalcyclecount = ?, zrostatus = ?, zroremarks = ?, zroactionby = ?, "
				+ "zroactiondate = ?, calculationid = ?, demandid = ?, billid = ?, correctionstatus = ?, status = ?, "
				+ "lastmodifiedby = ?, lastmodifiedtime = ? WHERE tenantid = ? AND id = ?";
	}

	public String findZroById() {
		return "SELECT * FROM eg_ws_zroverification WHERE tenantid = ? AND id = ?";
	}

	public String findZroByBillingCycle() {
		return "SELECT * FROM eg_ws_zroverification WHERE tenantid = ? AND billingcycleid = ?";
	}

	public String searchPendingZro() {
		return searchPendingZroBase() + " ORDER BY z.createdtime ASC, z.id ASC LIMIT ? OFFSET ?";
	}

	public String searchPendingZroByConnection() {
		return searchPendingZroBase() + " AND z.connectionno = ? ORDER BY z.createdtime ASC, z.id ASC LIMIT ? OFFSET ?";
	}

	public String countPendingZro() {
		return countPendingZroBase();
	}

	public String countPendingZroByConnection() {
		return countPendingZroBase() + " AND z.connectionno = ?";
	}

	private String searchPendingZroBase() {
		return "SELECT "
				+ "z.id AS zro_id, z.tenantid AS zro_tenantid, z.billingcycleid AS zro_billingcycleid, "
				+ "z.connectionno AS zro_connectionno, z.consumption AS zro_consumption, "
				+ "z.previousconsumption AS zro_previousconsumption, z.deviationfactor AS zro_deviationfactor, "
				+ "z.status AS zro_status, z.remarks AS zro_remarks, z.actionby AS zro_actionby, "
				+ "z.actiondate AS zro_actiondate, z.createdby AS zro_createdby, z.createdtime AS zro_createdtime, "
				+ "z.lastmodifiedby AS zro_lastmodifiedby, z.lastmodifiedtime AS zro_lastmodifiedtime, "
				+ "c.id AS cycle_id, c.billingperiodfrom, c.billingperiodto, c.previousokreading, c.currentreading, "
				+ "c.readingqualitycode, c.billingbasis, c.actualconsumption, c.onepointfivexflag, "
				+ "c.status AS cycle_status, c.correctionstatus, c.calculationid, c.demandid, c.billid "
				+ "FROM eg_ws_zroverification z INNER JOIN eg_ws_billingcycle c "
				+ "ON c.tenantid = z.tenantid AND c.id = z.billingcycleid "
				+ "WHERE z.tenantid = ? AND z.status = 'PENDING' AND c.onepointfivexflag = TRUE "
				+ "AND c.demandid IS NULL AND c.billid IS NULL";
	}

	private String countPendingZroBase() {
		return "SELECT COUNT(1) FROM eg_ws_zroverification z INNER JOIN eg_ws_billingcycle c "
				+ "ON c.tenantid = z.tenantid AND c.id = z.billingcycleid "
				+ "WHERE z.tenantid = ? AND z.status = 'PENDING' AND c.onepointfivexflag = TRUE "
				+ "AND c.demandid IS NULL AND c.billid IS NULL";
	}

	public String insertZro() {
		return "INSERT INTO eg_ws_zroverification ("
				+ "id, tenantid, billingcycleid, connectionno, consumption, previousconsumption, deviationfactor, "
				+ "status, remarks, actionby, actiondate, createdby, createdtime, lastmodifiedby, lastmodifiedtime) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	}

	public String updateZro() {
		return "UPDATE eg_ws_zroverification SET billingcycleid = ?, connectionno = ?, consumption = ?, "
				+ "previousconsumption = ?, deviationfactor = ?, status = ?, remarks = ?, actionby = ?, actiondate = ?, "
				+ "lastmodifiedby = ?, lastmodifiedtime = ? WHERE tenantid = ? AND id = ?";
	}

	public String findCorrectionById() {
		return "SELECT * FROM eg_ws_billingcorrection WHERE tenantid = ? AND id = ?";
	}

	public String findCorrectionsByConnection() {
		return "SELECT * FROM eg_ws_billingcorrection "
				+ "WHERE tenantid = ? AND connectionno = ? ORDER BY createdtime DESC";
	}

	public String insertCorrection() {
		return "INSERT INTO eg_ws_billingcorrection ("
				+ "id, tenantid, connectionno, frombillingcycleid, tobillingcycleid, status, reason, "
				+ "paidadjustmentamount, appliedpaidadjustmentamount, residualpaidcreditamount, olddemandid, oldbillid, "
				+ "correcteddemandid, correctedbillid, createdby, createdtime, lastmodifiedby, lastmodifiedtime) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )";
	}

	public String updateCorrection() {
		return "UPDATE eg_ws_billingcorrection SET connectionno = ?, frombillingcycleid = ?, tobillingcycleid = ?, "
				+ "status = ?, reason = ?, paidadjustmentamount = ?, appliedpaidadjustmentamount = ?, residualpaidcreditamount = ?, "
				+ "olddemandid = ?, oldbillid = ?, correcteddemandid = ?, correctedbillid = ?, lastmodifiedby = ?, lastmodifiedtime = ? "
				+ "WHERE tenantid = ? AND id = ?";
	}

	public String findOpenResidualCreditsForUpdate() {
		return "SELECT * FROM eg_ws_billingcredit " + "WHERE tenantid = ? AND connectionno = ? AND status = 'OPEN' "
				+ "AND remainingamount > reservedamount " + "ORDER BY createdtime ASC, id ASC FOR UPDATE";
	}

	public String findResidualCreditByIdForUpdate() {
		return "SELECT * FROM eg_ws_billingcredit WHERE tenantid = ? AND id = ? FOR UPDATE";
	}

	public String findResidualCreditBySourceCorrection() {
		return "SELECT * FROM eg_ws_billingcredit WHERE tenantid = ? AND sourcecorrectionid = ?";
	}

	public String findResidualCreditAllocationsByBillingCycle() {
		return "SELECT * FROM eg_ws_billingcreditallocation "
				+ "WHERE tenantid = ? AND billingcycleid = ? ORDER BY createdtime ASC, id ASC";
	}

	public String insertResidualCredit() {
		return "INSERT INTO eg_ws_billingcredit ("
				+ "id, tenantid, connectionno, sourcecorrectionid, originalamount, remainingamount, reservedamount, "
				+ "status, createdby, createdtime, lastmodifiedby, lastmodifiedtime) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	}

	public String updateResidualCredit() {
		return "UPDATE eg_ws_billingcredit SET connectionno = ?, sourcecorrectionid = ?, originalamount = ?, "
				+ "remainingamount = ?, reservedamount = ?, status = ?, lastmodifiedby = ?, lastmodifiedtime = ? "
				+ "WHERE tenantid = ? AND id = ?";
	}

	public String insertResidualCreditAllocation() {
		return "INSERT INTO eg_ws_billingcreditallocation ("
				+ "id, tenantid, creditid, billingcycleid, demandid, billid, appliedamount, status, createdby, createdtime, "
				+ "lastmodifiedby, lastmodifiedtime) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	}

	public String updateResidualCreditAllocation() {
		return "UPDATE eg_ws_billingcreditallocation SET demandid = ?, billid = ?, appliedamount = ?, status = ?, "
				+ "lastmodifiedby = ?, lastmodifiedtime = ? WHERE tenantid = ? AND id = ?";
	}

	public String reserveResidualCredit() {
		return "UPDATE eg_ws_billingcredit SET reservedamount = reservedamount + ?, "
				+ "lastmodifiedby = ?, lastmodifiedtime = ? " + "WHERE tenantid = ? AND id = ? AND status = 'OPEN' "
				+ "AND remainingamount - reservedamount >= ?";
	}

	public String consumeReservedResidualCredit() {
		return "UPDATE eg_ws_billingcredit SET reservedamount = reservedamount - ?, "
				+ "remainingamount = remainingamount - ?, "
				+ "status = CASE WHEN remainingamount - ? = 0 THEN 'EXHAUSTED' ELSE 'OPEN' END, "
				+ "lastmodifiedby = ?, lastmodifiedtime = ? "
				+ "WHERE tenantid = ? AND id = ? AND reservedamount >= ? AND remainingamount >= ?";
	}

	public String releaseReservedResidualCredit() {
		return "UPDATE eg_ws_billingcredit SET reservedamount = reservedamount - ?, "
				+ "lastmodifiedby = ?, lastmodifiedtime = ? " + "WHERE tenantid = ? AND id = ? AND reservedamount >= ?";
	}

	public String findPreviousOkByConnectionBefore() {
		return "SELECT * FROM eg_ws_billingcycle " + "WHERE tenantid = ? AND connectionno = ? "
				+ "AND readingqualitycode = 'OK' " + "AND billingperiodto < ? "
				+ "ORDER BY billingperiodto DESC LIMIT 1";
	}

	public String findBillingCalculationById() {
		return "SELECT * FROM eg_ws_billingcalculation WHERE tenantid = ? AND id = ?";
	}

	public String findBillingCalculationByCycle() {
		return "SELECT * FROM eg_ws_billingcalculation WHERE tenantid = ? AND billingcycleid = ? ORDER BY calculatedtime DESC LIMIT 1";
	}

	public String updateBillingCalculationStatus() {
		return "UPDATE eg_ws_billingcalculation SET status = ? WHERE tenantid = ? AND id = ?";
	}

	public String updateBillingCalculationSnapshot() {
		return "UPDATE eg_ws_billingcalculation SET status = ?, calculatedtime = ?, calculatedby = ?, snapshotjson = ? WHERE tenantid = ? AND id = ?";
	}

	public String insertBillingCalculation() {
		return "INSERT INTO eg_ws_billingcalculation (id, tenantid, billingcycleid, connectionno, engineversion, status, calculatedtime, calculatedby, snapshotjson) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)";
	}

}
