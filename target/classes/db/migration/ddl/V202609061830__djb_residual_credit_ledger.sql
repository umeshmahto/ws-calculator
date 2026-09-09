-- Durable ledger for excess paid amount left after an automatic DJB correction.

CREATE TABLE IF NOT EXISTS eg_ws_billingcredit (
    id character varying(64) NOT NULL,
    tenantid character varying(64) NOT NULL,
    connectionno character varying(128) NOT NULL,
    sourcecorrectionid character varying(64) NOT NULL,
    originalamount numeric(18,2) NOT NULL,
    remainingamount numeric(18,2) NOT NULL,
    reservedamount numeric(18,2) NOT NULL DEFAULT 0,
    status character varying(32) NOT NULL,
    createdby character varying(256),
    createdtime bigint,
    lastmodifiedby character varying(256),
    lastmodifiedtime bigint,
    CONSTRAINT pk_eg_ws_billingcredit PRIMARY KEY (id, tenantid),
    CONSTRAINT uk_eg_ws_billingcredit_source UNIQUE (tenantid, sourcecorrectionid),
    CONSTRAINT ck_eg_ws_billingcredit_amounts CHECK (
        originalamount >= 0 AND remainingamount >= 0 AND reservedamount >= 0 AND reservedamount <= remainingamount
    )
);

CREATE INDEX IF NOT EXISTS idx_eg_ws_billingcredit_connection
    ON eg_ws_billingcredit (tenantid, connectionno, status, createdtime);

CREATE TABLE IF NOT EXISTS eg_ws_billingcreditallocation (
    id character varying(64) NOT NULL,
    tenantid character varying(64) NOT NULL,
    creditid character varying(64) NOT NULL,
    billingcycleid character varying(64) NOT NULL,
    demandid character varying(64),
    billid character varying(64),
    appliedamount numeric(18,2) NOT NULL,
    status character varying(32) NOT NULL,
    createdby character varying(256),
    createdtime bigint,
    lastmodifiedby character varying(256),
    lastmodifiedtime bigint,
    CONSTRAINT pk_eg_ws_billingcreditallocation PRIMARY KEY (id, tenantid),
    CONSTRAINT uk_eg_ws_billingcreditallocation_cycle UNIQUE (tenantid, creditid, billingcycleid),
    CONSTRAINT ck_eg_ws_billingcreditallocation_amount CHECK (appliedamount > 0)
);

CREATE INDEX IF NOT EXISTS idx_eg_ws_billingcreditallocation_cycle
    ON eg_ws_billingcreditallocation (tenantid, billingcycleid, status);

CREATE INDEX IF NOT EXISTS idx_eg_ws_billingcreditallocation_credit
    ON eg_ws_billingcreditallocation (tenantid, creditid, status);

-- Backfill residual credits produced by corrections completed before this ledger was deployed.
-- Deterministic ids avoid requiring a database-specific UUID extension.
INSERT INTO eg_ws_billingcredit (
    id, tenantid, connectionno, sourcecorrectionid, originalamount, remainingamount, reservedamount,
    status, createdby, createdtime, lastmodifiedby, lastmodifiedtime
)
SELECT
    md5('DJB_RESIDUAL_CREDIT:' || c.id),
    c.tenantid,
    c.connectionno,
    c.id,
    c.residualpaidcreditamount,
    c.residualpaidcreditamount,
    0,
    'OPEN',
    c.createdby,
    c.createdtime,
    c.lastmodifiedby,
    c.lastmodifiedtime
FROM eg_ws_billingcorrection c
WHERE c.status = 'COMPLETED'
  AND COALESCE(c.residualpaidcreditamount, 0) > 0
ON CONFLICT (tenantid, sourcecorrectionid) DO NOTHING;
