# Table Rename Plan - Aftermiles Custom Tables

## Overview

Rename all Aftermiles custom tables from `tc_*` prefix to `tcaf_*` prefix to distinguish them from original Traccar tables for better maintainability and clarity during future upgrades.

**Assumption**: Database contains only test data and can be cleared. This plan uses the simpler approach of directly modifying Liquibase changelogs.

## Tables to Rename

| Current Name | New Name | Created In | Purpose |
|-------------|----------|------------|---------|
| `tc_expenses` | `tcaf_expenses` | changelog-6.10.0 | Expense tracking |
| `tc_maintenance_logs` | `tcaf_maintenance_logs` | changelog-6.12.0 | Maintenance records |
| `tc_ai_receipts_batches` | `tcaf_ai_receipts_batches` | changelog-6.13.0 | AI receipt batch processing |
| `tc_ai_receipts_batch_items` | `tcaf_ai_receipts_batch_items` | changelog-6.13.0 | Individual receipt items |

## Files to Modify

### 1. Java Model Classes (4 files)

**Location**: `src/main/java/org/traccar/model/`

| File | Current @StorageName | New @StorageName |
|------|---------------------|------------------|
| `Expense.java` | `tc_expenses` | `tcaf_expenses` |
| `MaintenanceLog.java` | `tc_maintenance_logs` | `tcaf_maintenance_logs` |
| `AiReceiptBatch.java` | `tc_ai_receipts_batches` | `tcaf_ai_receipts_batches` |
| `AiReceiptBatchItem.java` | `tc_ai_receipts_batch_items` | `tcaf_ai_receipts_batch_items` |

### 2. Liquibase Changelogs (4 files)

**Location**: `schema/`

#### changelog-6.10.0.xml
- Table: `tc_expenses` → `tcaf_expenses`
- Foreign keys: `fk_expenses_*` → `fk_tcaf_expenses_*`
- Indexes: `idx_expenses_*` → `idx_tcaf_expenses_*`

#### changelog-6.11.0.xml
- Table: `tc_expenses` → `tcaf_expenses`
- Indexes: `idx_expenses_*` → `idx_tcaf_expenses_*`

#### changelog-6.12.0.xml
- Table: `tc_maintenance_logs` → `tcaf_maintenance_logs`
- Foreign keys: `fk_maintenancelogs_*` → `fk_tcaf_maintenancelogs_*`
- Indexes: `idx_maintenance_*` → `idx_tcaf_maintenance_*`

#### changelog-6.13.0.xml
- Table: `tc_ai_receipts_batches` → `tcaf_ai_receipts_batches`
- Table: `tc_ai_receipts_batch_items` → `tcaf_ai_receipts_batch_items`
- Foreign keys: `fk_ai_receipts_*` → `fk_tcaf_ai_receipts_*`
- Indexes: `idx_ai_receipts_*` → `idx_tcaf_ai_receipts_*`
- **Special**: Foreign key referencing `tc_expenses` → `tcaf_expenses`
- **Special**: `addColumn` operations on `tc_expenses` → `tcaf_expenses`

## Detailed Change Patterns

### Pattern 1: Table Names
```xml
<!-- Before -->
<createTable tableName="tc_expenses">

<!-- After -->
<createTable tableName="tcaf_expenses">
```

### Pattern 2: Foreign Key Constraints
```xml
<!-- Before -->
<addForeignKeyConstraint
    baseTableName="tc_expenses"
    constraintName="fk_expenses_deviceid"
    referencedTableName="tc_devices" />

<!-- After -->
<addForeignKeyConstraint
    baseTableName="tcaf_expenses"
    constraintName="fk_tcaf_expenses_deviceid"
    referencedTableName="tc_devices" />
```

### Pattern 3: Indexes
```xml
<!-- Before -->
<createIndex tableName="tc_expenses" indexName="idx_expenses_deviceid">

<!-- After -->
<createIndex tableName="tcaf_expenses" indexName="idx_tcaf_expenses_deviceid">
```

### Pattern 4: Add Column Operations
```xml
<!-- Before -->
<addColumn tableName="tc_expenses">

<!-- After -->
<addColumn tableName="tcaf_expenses">
```

### Pattern 5: Cross-References (Important!)
```xml
<!-- In changelog-6.13.0.xml, expense foreign key -->
<!-- Before -->
<addForeignKeyConstraint
    baseTableName="tc_ai_receipts_batch_items"
    referencedTableName="tc_expenses" />

<!-- After -->
<addForeignKeyConstraint
    baseTableName="tcaf_ai_receipts_batch_items"
    referencedTableName="tcaf_expenses" />
```

## Naming Convention Summary

| Element Type | Old Pattern | New Pattern |
|-------------|-------------|-------------|
| Table | `tc_[name]` | `tcaf_[name]` |
| Foreign Key | `fk_[table]_[column]` | `fk_tcaf_[table]_[column]` |
| Index | `idx_[table]_[column]` | `idx_tcaf_[table]_[column]` |

**Note**: Keep original Traccar table references as `tc_devices`, `tc_users`, etc. - only change custom tables.

## Execution Steps

### Pre-Execution
- [ ] Backup current database (if needed)
- [ ] Stop application server
- [ ] Clear database or drop affected tables

### Execution Order

#### Phase 1: Java Models (4 files)
- [ ] Modify `Expense.java` - @StorageName
- [ ] Modify `MaintenanceLog.java` - @StorageName
- [ ] Modify `AiReceiptBatch.java` - @StorageName
- [ ] Modify `AiReceiptBatchItem.java` - @StorageName

#### Phase 2: Changelog 6.10.0 (Expenses Creation)
- [ ] Update table name: `tc_expenses` → `tcaf_expenses`
- [ ] Update FK: `fk_expenses_deviceid` → `fk_tcaf_expenses_deviceid`
- [ ] Update FK: `fk_expenses_createdbyuserid` → `fk_tcaf_expenses_createdbyuserid`
- [ ] Update index: `idx_expenses_deviceid` → `idx_tcaf_expenses_deviceid`
- [ ] Update index: `idx_expenses_expensedate` → `idx_tcaf_expenses_expensedate`
- [ ] Update index: `idx_expenses_createdbyuserid` → `idx_tcaf_expenses_createdbyuserid`

#### Phase 3: Changelog 6.11.0 (Expenses Extensions)
- [ ] Update `addColumn tableName`: `tc_expenses` → `tcaf_expenses`
- [ ] Update index: `idx_expenses_merchant` → `idx_tcaf_expenses_merchant`
- [ ] Update index: `idx_expenses_device_date` → `idx_tcaf_expenses_device_date`
- [ ] Update index: `idx_expenses_type` → `idx_tcaf_expenses_type`

#### Phase 4: Changelog 6.12.0 (Maintenance Logs)
- [ ] Update table name: `tc_maintenance_logs` → `tcaf_maintenance_logs`
- [ ] Update FK: `fk_maintenancelogs_deviceid` → `fk_tcaf_maintenancelogs_deviceid`
- [ ] Update FK: `fk_maintenancelogs_userid` → `fk_tcaf_maintenancelogs_userid`
- [ ] Update index: `idx_maintenance_deviceid` → `idx_tcaf_maintenance_deviceid`
- [ ] Update index: `idx_maintenance_date` → `idx_tcaf_maintenance_date`
- [ ] Update index: `idx_maintenance_device_date` → `idx_tcaf_maintenance_device_date`
- [ ] Update index: `idx_maintenance_createdbyuserid` → `idx_tcaf_maintenance_createdbyuserid`

#### Phase 5: Changelog 6.13.0 (AI Receipts)
- [ ] Update table name: `tc_ai_receipts_batches` → `tcaf_ai_receipts_batches`
- [ ] Update FK: `fk_ai_receipts_batches_deviceid` → `fk_tcaf_ai_receipts_batches_deviceid`
- [ ] Update FK: `fk_ai_receipts_batches_userid` → `fk_tcaf_ai_receipts_batches_userid`
- [ ] Update index: `idx_ai_receipts_batches_user` → `idx_tcaf_ai_receipts_batches_user`
- [ ] Update index: `idx_ai_receipts_batches_status` → `idx_tcaf_ai_receipts_batches_status`
- [ ] Update table name: `tc_ai_receipts_batch_items` → `tcaf_ai_receipts_batch_items`
- [ ] Update FK: `fk_ai_receipts_batch_items_batchid` → `fk_tcaf_ai_receipts_batch_items_batchid`
- [ ] Update FK: `fk_ai_receipts_batch_items_expenseid` → `fk_tcaf_ai_receipts_batch_items_expenseid`
- [ ] Update FK referenced table: `tc_ai_receipts_batches` → `tcaf_ai_receipts_batches`
- [ ] Update FK referenced table: `tc_expenses` → `tcaf_expenses`
- [ ] Update index: `idx_ai_receipts_batch_items_batch` → `idx_tcaf_ai_receipts_batch_items_batch`
- [ ] Update index: `idx_ai_receipts_batch_items_status` → `idx_tcaf_ai_receipts_batch_items_status`
- [ ] Update `addColumn tableName`: `tc_expenses` → `tcaf_expenses`
- [ ] Update FK: `fk_expenses_batchitemid` → `fk_tcaf_expenses_batchitemid`
- [ ] Update FK referenced table: `tc_ai_receipts_batch_items` → `tcaf_ai_receipts_batch_items`
- [ ] Update index: `idx_expenses_batchitem` → `idx_tcaf_expenses_batchitem`

#### Phase 6: Database Migration
- [ ] Drop existing custom tables (or clear entire database)
- [ ] Run Liquibase update: `./gradlew update` (or restart application)
- [ ] Verify all 4 tables created with `tcaf_` prefix
- [ ] Verify all foreign keys and indexes created correctly

#### Phase 7: Testing
- [ ] Test ExpenseResource API endpoints (GET/POST/PUT/DELETE)
- [ ] Test MaintenanceLogResource API endpoints
- [ ] Test AI Receipt Batch processing (if applicable)
- [ ] Verify foreign key cascades work correctly
- [ ] Check application logs for any table-related errors

### Post-Execution
- [ ] Confirm no references to old `tc_expenses`, `tc_maintenance_logs` table names in code
- [ ] Update any documentation referencing these tables
- [ ] Commit changes with message: "Rename Aftermiles tables to tcaf_ prefix"

## Database Reset Commands

### PostgreSQL
```sql
-- Drop custom tables
DROP TABLE IF EXISTS tc_ai_receipts_batch_items CASCADE;
DROP TABLE IF EXISTS tc_ai_receipts_batches CASCADE;
DROP TABLE IF EXISTS tc_maintenance_logs CASCADE;
DROP TABLE IF EXISTS tc_expenses CASCADE;

-- Clear Liquibase tracking for these changelogs
DELETE FROM databasechangelog WHERE filename IN (
  'changelog-6.10.0.xml',
  'changelog-6.11.0.xml',
  'changelog-6.12.0.xml',
  'changelog-6.13.0.xml'
);
```

### MySQL (if applicable)
```sql
-- Drop custom tables
DROP TABLE IF EXISTS tc_ai_receipts_batch_items;
DROP TABLE IF EXISTS tc_ai_receipts_batches;
DROP TABLE IF EXISTS tc_maintenance_logs;
DROP TABLE IF EXISTS tc_expenses;

-- Clear Liquibase tracking
DELETE FROM DATABASECHANGELOG WHERE filename IN (
  'changelog-6.10.0.xml',
  'changelog-6.11.0.xml',
  'changelog-6.12.0.xml',
  'changelog-6.13.0.xml'
);
```

## Verification Queries

After migration, run these to verify:

```sql
-- Check all tables exist with new names
SELECT table_name FROM information_schema.tables
WHERE table_name LIKE 'tcaf_%';

-- Should return:
-- tcaf_expenses
-- tcaf_maintenance_logs
-- tcaf_ai_receipts_batches
-- tcaf_ai_receipts_batch_items

-- Check foreign keys
SELECT constraint_name, table_name, referenced_table_name
FROM information_schema.key_column_usage
WHERE table_name LIKE 'tcaf_%';

-- Check indexes
SELECT indexname FROM pg_indexes
WHERE tablename LIKE 'tcaf_%';
```

## Estimated Time

- Phase 1 (Models): 5 minutes
- Phase 2-5 (Changelogs): 20 minutes
- Phase 6 (Migration): 5 minutes
- Phase 7 (Testing): 10 minutes

**Total**: ~40 minutes

## Rollback Plan

If issues occur:
1. Restore database backup (if created)
2. Revert Git changes: `git checkout -- schema/ src/main/java/org/traccar/model/`
3. Restart application

## Notes

- All changes are in Aftermiles custom code only
- No modifications to original Traccar tables
- Future trip analysis feature (`tcaf_trips`) will follow this naming convention
- Keep this document updated if new custom tables are added

====Veryfication SQL =============

SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public'
AND table_name IN ('tc_devices', 'tc_maintenance_logs', 'tc_ai_receipts_batches', 'tc_ai_receipts_batch_items');



      SELECT id, filename FROM databasechangelog
     WHERE filename IN (
       'changelog-6.10.0.xml',
       'changelog-6.11.0.xml',
       'changelog-6.12.0.xml',
       'changelog-6.13.0.xml'
     );
      
      
           SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name LIKE 'tcaf_%'
     ORDER BY table_name;
