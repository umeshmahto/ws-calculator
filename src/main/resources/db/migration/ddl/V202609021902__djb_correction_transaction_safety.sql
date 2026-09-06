-- automatic correction can reference multiple historical demand/bill IDs.
-- Keep them as TEXT because the combined comma-separated value can exceed VARCHAR(64).
ALTER TABLE IF EXISTS eg_ws_billingcorrection
    ALTER COLUMN olddemandid TYPE text;

ALTER TABLE IF EXISTS eg_ws_billingcorrection
    ALTER COLUMN oldbillid TYPE text;
