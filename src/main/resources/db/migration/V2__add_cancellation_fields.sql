ALTER TABLE orders ADD COLUMN cancelled_at        TIMESTAMP;
ALTER TABLE orders ADD COLUMN cancelled_by        VARCHAR(255);
ALTER TABLE orders ADD COLUMN cancellation_reason VARCHAR(100);
