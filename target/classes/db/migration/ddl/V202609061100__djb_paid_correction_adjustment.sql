-- Stores the total amount already collected against superseded DJB average/provisional demands.
-- This value is used to create a negative correction adjustment on the new OK-to-OK demand.
ALTER TABLE IF EXISTS eg_ws_billingcorrection
    ADD COLUMN IF NOT EXISTS paidadjustmentamount numeric(18,2);

    
ALTER TABLE IF EXISTS eg_ws_billingcorrection
    ADD COLUMN IF NOT EXISTS appliedpaidadjustmentamount numeric(18,2);

ALTER TABLE IF EXISTS eg_ws_billingcorrection
    ADD COLUMN IF NOT EXISTS residualpaidcreditamount numeric(18,2);