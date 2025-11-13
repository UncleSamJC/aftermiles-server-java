-- Verify new tcaf_ tables exist
SELECT table_name,
       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = t.table_name) AS column_count
FROM information_schema.tables t
WHERE table_schema = 'public'
  AND table_name LIKE 'tcaf_%'
ORDER BY table_name;

-- Verify old tables do NOT exist
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('tc_expenses', 'tc_maintenance_logs', 'tc_ai_receipts_batches', 'tc_ai_receipts_batch_items');

-- Check foreign keys on new tables
SELECT
    tc.table_name,
    kcu.column_name,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
  ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage AS ccu
  ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_name LIKE 'tcaf_%'
ORDER BY tc.table_name, kcu.column_name;
