-- Run once if fingerprint_slot_id is missing (or restart Core Banking with ddl-auto=update).
-- psql -h localhost -p 5332 -U dbv1 -d dbv1 -f src/main/resources/db/add_fingerprint_slot_id.sql

ALTER TABLE accounts ADD COLUMN IF NOT EXISTS fingerprint_slot_id INTEGER;
