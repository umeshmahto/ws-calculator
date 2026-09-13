CREATE TABLE IF NOT EXISTS eg_ws_billingcalculation (
    id character varying(64) NOT NULL,
    tenantid character varying(64) NOT NULL,
    billingcycleid character varying(64) NOT NULL,
    connectionno character varying(64) NOT NULL,
    engineversion character varying(64) NOT NULL,
    status character varying(32) NOT NULL,
    calculatedtime bigint NOT NULL,
    calculatedby character varying(64),
    snapshotjson jsonb NOT NULL,
    CONSTRAINT pk_eg_ws_billingcalculation PRIMARY KEY (id, tenantid),
    CONSTRAINT uk_eg_ws_billingcalculation_cycle UNIQUE (tenantid, billingcycleid)
);

CREATE INDEX IF NOT EXISTS index_eg_ws_billingcalculation_connection
    ON eg_ws_billingcalculation (tenantid, connectionno, calculatedtime DESC);
