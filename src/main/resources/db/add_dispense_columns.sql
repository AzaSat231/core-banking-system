-- Run once if dispense_status / dispense_deadline are missing (or restart Core Banking with ddl-auto=update).
-- psql -h localhost -p 5332 -U dbv1 -d dbv1 -f src/main/resources/db/add_dispense_columns.sql

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS dispense_status VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE';

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS dispense_deadline TIMESTAMP;
