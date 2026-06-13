-- One-time fix for customers soft-deleted before releaseUniqueFields was added.
-- Run against Core Banking Postgres if re-registration fails with "Email already exists"
-- for an address that was deleted via DELETE /customers/{id}.
--
-- Example:
--   psql -h localhost -p 5332 -U dbv1 -d dbv1 -f src/main/resources/db/fix_soft_deleted_customer_uniques.sql

UPDATE customers
SET email = LEFT(email, 220) || '.deleted.legacy'
WHERE deleted_at IS NOT NULL
  AND email NOT LIKE '%.deleted.%';

UPDATE customers
SET national_id = LEFT(national_id, 220) || '.deleted.legacy'
WHERE deleted_at IS NOT NULL
  AND national_id NOT LIKE '%.deleted.%';

UPDATE customers
SET phone_number = LEFT(phone_number, 220) || '.deleted.legacy'
WHERE deleted_at IS NOT NULL
  AND phone_number NOT LIKE '%.deleted.%';
