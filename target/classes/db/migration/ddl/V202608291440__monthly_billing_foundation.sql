
CREATE TABLE IF NOT EXISTS eg_ws_billingcycle (
        id character varying(64) NOT NULL,
        tenantid character varying(64) NOT NULL,
        connectionno character varying(64) NOT NULL,
        billingperiodfrom bigint NOT NULL,
        billingperiodto bigint NOT NULL,
        meterreadingid character varying(64),
        readingqualitycode character varying(64),
        billingbasis character varying(64),
        previousokreading decimal,
        previousokreadingdate bigint,
        currentreading decimal,
        currentreadingdate bigint,
        actualconsumption decimal,
        averageconsumption decimal,
        billingconsumption decimal,
        previousconsumption decimal,
        deviationfactor decimal,
        onepointfivexflag boolean NOT NULL DEFAULT false,
        averagecyclecount integer NOT NULL DEFAULT 0,
        provisionalcyclecount integer NOT NULL DEFAULT 0,
        zrostatus character varying(32),
        zroremarks character varying(1024),
        zroactionby character varying(64),
        zroactiondate bigint,
        calculationid character varying(64),
        demandid character varying(64),
        billid character varying(64),
        correctionstatus character varying(32),
        status character varying(32),
        createdby character varying(64),
        createdtime bigint,
        lastmodifiedby character varying(64),
        lastmodifiedtime bigint,
        CONSTRAINT pk_eg_ws_billingcycle PRIMARY KEY (id, tenantid),
        CONSTRAINT uk_eg_ws_billingcycle_period UNIQUE (
            tenantid,
            connectionno,
            billingperiodfrom,
            billingperiodto
        )
    );

CREATE INDEX IF NOT EXISTS index_eg_ws_billingcycle_connection ON eg_ws_billingcycle (tenantid, connectionno, billingperiodto DESC);

CREATE INDEX IF NOT EXISTS index_eg_ws_billingcycle_basis ON eg_ws_billingcycle (
    tenantid,
    connectionno,
    billingbasis,
    billingperiodto DESC
);

CREATE INDEX IF NOT EXISTS index_eg_ws_billingcycle_rqc ON eg_ws_billingcycle (
    tenantid,
    connectionno,
    readingqualitycode,
    billingperiodto DESC
);

CREATE TABLE IF NOT EXISTS eg_ws_zroverification (
        id character varying(64) NOT NULL,
        tenantid character varying(64) NOT NULL,
        billingcycleid character varying(64) NOT NULL,
        connectionno character varying(64) NOT NULL,
        consumption decimal,
        previousconsumption decimal,
        deviationfactor decimal,
        status character varying(32) NOT NULL,
        remarks character varying(1024),
        actionby character varying(64),
        actiondate bigint,
        createdby character varying(64),
        createdtime bigint,
        lastmodifiedby character varying(64),
        lastmodifiedtime bigint,
        CONSTRAINT pk_eg_ws_zroverification PRIMARY KEY (id, tenantid),
        CONSTRAINT uk_eg_ws_zroverification_cycle UNIQUE (tenantid, billingcycleid)
    );

CREATE INDEX IF NOT EXISTS index_eg_ws_zroverification_cycle ON eg_ws_zroverification (tenantid, billingcycleid);

CREATE INDEX IF NOT EXISTS index_eg_ws_zroverification_connection ON eg_ws_zroverification (tenantid, connectionno);

CREATE INDEX IF NOT EXISTS index_eg_ws_zroverification_status ON eg_ws_zroverification (tenantid, status);

CREATE TABLE IF NOT EXISTS eg_ws_billingcorrection (
        id character varying(64) NOT NULL,
        tenantid character varying(64) NOT NULL,
        connectionno character varying(64) NOT NULL,
        frombillingcycleid character varying(64),
        tobillingcycleid character varying(64),
        status character varying(32) NOT NULL,
        reason character varying(1024),
        olddemandid character varying(64),
        oldbillid character varying(64),
        correcteddemandid character varying(64),
        correctedbillid character varying(64),
        createdby character varying(64),
        createdtime bigint,
        lastmodifiedby character varying(64),
        lastmodifiedtime bigint,
        CONSTRAINT pk_eg_ws_billingcorrection PRIMARY KEY (id, tenantid)
    );

CREATE INDEX IF NOT EXISTS index_eg_ws_billingcorrection_connection ON eg_ws_billingcorrection (tenantid, connectionno, createdtime DESC);

CREATE INDEX IF NOT EXISTS index_eg_ws_billingcorrection_from_cycle ON eg_ws_billingcorrection (tenantid, frombillingcycleid);

CREATE INDEX IF NOT EXISTS index_eg_ws_billingcorrection_to_cycle ON eg_ws_billingcorrection (tenantid, tobillingcycleid);