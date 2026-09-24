-- average/provisional reading-quality codes can legitimately have no
-- current meter value (for example MLOC/PLOC/RDDT/ADF). The reading attempt
-- date remains mandatory for audit and billing-cycle period calculation.
ALTER TABLE eg_ws_meterreading
    ALTER COLUMN currentReading DROP NOT NULL;
