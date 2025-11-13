-- Verification queries for table rename cleanup

-- 1. Check if old tables are dropped (should return 0 rows)
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('tc_expenses', 'tc_maintenance_logs', 'tc_ai_receipts_batches', 'tc_ai_receipts_batch_items');

-- 2. Check if databasechangelog entries are removed (should return 0 rows)
SELECT id, filename FROM databasechangelog
WHERE filename IN (
  'changelog-6.10.0.xml',
  'changelog-6.11.0.xml',
  'changelog-6.12.0.xml',
  'changelog-6.13.0.xml'
);

-- 3. Check if new tables exist (should return 0 rows before migration, 4 rows after)
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name LIKE 'tcaf_%'
ORDER BY table_name;
