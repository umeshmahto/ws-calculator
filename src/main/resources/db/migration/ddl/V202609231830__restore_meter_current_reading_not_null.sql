-- Restore the legacy/non-null persistence contract after DJB average-billing
-- readings were temporarily allowed to persist NULL currentReading.

ALTER TABLE eg_ws_meterreading
    ALTER COLUMN currentReading SET NOT NULL;
