-- Step 1: Drop old tables (with CASCADE to drop foreign keys)
DROP TABLE IF EXISTS tc_ai_receipts_batch_items CASCADE;
DROP TABLE IF EXISTS tc_ai_receipts_batches CASCADE;
DROP TABLE IF EXISTS tc_maintenance_logs CASCADE;
DROP TABLE IF EXISTS tc_expenses CASCADE;

-- Step 2: Remove Liquibase changelog entries for these tables
DELETE FROM databasechangelog
WHERE id IN ('changelog-6.10.0', 'changelog-6.11.0', 'changelog-6.12.0', 'changelog-6.13.0-ai-receipts');

-- Step 3: Verify cleanup
-- Should return 0 rows if cleanup successful
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('tc_expenses', 'tc_maintenance_logs', 'tc_ai_receipts_batches', 'tc_ai_receipts_batch_items');

-- Should return 0 rows if changelog entries removed
SELECT id, filename FROM databasechangelog
WHERE id IN ('changelog-6.10.0', 'changelog-6.11.0', 'changelog-6.12.0', 'changelog-6.13.0-ai-receipts');
