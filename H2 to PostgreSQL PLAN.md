# H2 to PostgreSQL Migration Plan
## Traccar Server - Windows Server 2022

**Document Version:** 1.0
**Date:** November 5, 2025
**Target Environment:** Windows Server 2022
**Current Database:** H2 (jdbc:h2:./data/database)
**Target Database:** PostgreSQL 17.x

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Pre-Migration Checklist](#pre-migration-checklist)
4. [Migration Steps](#migration-steps)
5. [Rollback Plan](#rollback-plan)
6. [Post-Migration Validation](#post-migration-validation)
7. [Troubleshooting](#troubleshooting)
8. [References](#references)

---

## Overview


### Current H2 Configuration

```xml
<entry key='database.driver'>org.h2.Driver</entry>
<entry key='database.url'>jdbc:h2:./data/database</entry>
<entry key='database.user'>sa</entry>
<entry key='database.password'></entry>
```

**Database Location:** `C:\Program Files\Traccar\data\database.mv.db`

### Target PostgreSQL Configuration

```xml
<entry key='database.driver'>org.postgresql.Driver</entry>
<entry key='database.url'>jdbc:postgresql://localhost:5432/traccar</entry>
<entry key='database.user'>traccar</entry>
<entry key='database.password'>YOUR_SECURE_PASSWORD</entry>
```




### Backup Storage

- Minimum 5GB free space for backups
- Recommended location: `D:\Traccar_Migration_Backup\`

---

## Pre-Migration Checklist

### ✅ Preparation Tasks

- [ ] **Notify all users** about maintenance window (estimated 2-4 hours downtime)
- [ ] **Document current system state**
  - [ ] Note Traccar version: `6.10.0`
  - [ ] Record number of devices: `______`
  - [ ] Record number of users: `______`
  - [ ] Record total positions: `______`
- [ ] **Create backup directory**
  ```powershell
  New-Item -Path "D:\Traccar_Migration_Backup" -ItemType Directory
  ```
- [ ] **Verify disk space** (minimum 5GB free)
- [ ] **Stop all client connections** to Traccar
- [ ] **Download PostgreSQL installer**
- [ ] **Review this entire plan** with your team

---

## Migration Steps

### Phase 1: Backup Current System

#### Step 1.1: Stop Traccar Service

```powershell
# Open PowerShell as Administrator
Stop-Service -Name "Traccar"

# Verify service is stopped
Get-Service -Name "Traccar"
```

**Expected Output:**
```
Status   Name               DisplayName
------   ----               -----------
Stopped  Traccar            Traccar
```

#### Step 1.2: Backup Configuration Files

```powershell
# Backup entire Traccar directory
$BackupPath = "D:\Traccar_Migration_Backup"
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"

# Copy configuration
Copy-Item "C:\Program Files\Traccar\setup\traccar.xml" `
          "$BackupPath\traccar_$Timestamp.xml"

# Copy debug configuration if exists
if (Test-Path "C:\Program Files\Traccar\debug.xml") {
    Copy-Item "C:\Program Files\Traccar\debug.xml" `
              "$BackupPath\debug_$Timestamp.xml"
}

# Backup entire data directory
Copy-Item "C:\Program Files\Traccar\data" `
          "$BackupPath\data_backup_$Timestamp" -Recurse

# Backup media files
Copy-Item "C:\Program Files\Traccar\media" `
          "$BackupPath\media_backup_$Timestamp" -Recurse
```

**⚠️ CRITICAL:** Verify backups before proceeding!

```powershell
# Verify backup files exist
Get-ChildItem -Path $BackupPath -Recurse | Format-Table Name, Length, LastWriteTime
```

#### Step 1.3: Export H2 Database to SQL

```powershell
# Navigate to Traccar lib directory
cd "C:\Program Files\Traccar\lib"

# Find H2 JAR file
$H2Jar = Get-ChildItem -Filter "h2-*.jar" | Select-Object -First 1

# Export H2 database to SQL script
& "C:\Program Files\Traccar\jre\bin\java.exe" `
  -cp $H2Jar.Name `
  org.h2.tools.Script `
  -url "jdbc:h2:file:C:/Program Files/Traccar/data/database" `
  -user sa `
  -password "" `
  -script "D:\Traccar_Migration_Backup\h2_export_$Timestamp.sql"
```

**Expected Output:**
```
Exported database to: D:\Traccar_Migration_Backup\h2_export_xxxxxxxx_xxxxxx.sql
```

**✅ Checkpoint:** Verify the SQL file was created and is not empty:

```powershell
$ExportFile = Get-ChildItem "D:\Traccar_Migration_Backup\h2_export_*.sql" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Write-Host "Export file size: $($ExportFile.Length / 1MB) MB"
Get-Content $ExportFile -Head 20
```

---

### Phase 2: Install and Configure PostgreSQL

#### Skipped, we use a third party service

---

### Phase 3: Convert and Import Data

#### Step 3.1: Convert H2 SQL to PostgreSQL Format

The H2 export contains H2-specific syntax that needs conversion. Create this PowerShell script:

**File:** `D:\Traccar_Migration_Backup\convert_h2_to_postgres.ps1`

```powershell
# H2 to PostgreSQL SQL Converter
param(
    [string]$InputFile,
    [string]$OutputFile
)

Write-Host "Converting H2 SQL to PostgreSQL format..."

$content = Get-Content $InputFile -Raw

# Remove H2-specific commands
$content = $content -replace "(?m)^SET .*$", ""
$content = $content -replace "CREATE MEMORY TABLE", "CREATE TABLE"
$content = $content -replace "CREATE CACHED TABLE", "CREATE TABLE"

# Convert data types
$content = $content -replace "\bVARCHAR_IGNORECASE\b", "VARCHAR"
$content = $content -replace "\bTINYINT\b", "SMALLINT"
$content = $content -replace "\bBIGINT\b", "BIGINT"
$content = $content -replace "\bTIMESTAMP\b", "TIMESTAMP"

# Convert sequences
$content = $content -replace "CREATE SEQUENCE (.*?) START WITH (\d+)", "CREATE SEQUENCE `$1 START `$2"

# Convert identity/auto-increment
$content = $content -replace "IDENTITY", "SERIAL"
$content = $content -replace "AUTO_INCREMENT", "SERIAL"

# Remove H2 indexes on system columns
$content = $content -replace "(?m)^CREATE INDEX.*?PRIMARY_KEY.*?$", ""

# Convert LIMIT OFFSET syntax (if present)
$content = $content -replace "LIMIT\s+(\d+)\s+OFFSET\s+(\d+)", "LIMIT `$1 OFFSET `$2"

# Save converted SQL
$content | Set-Content $OutputFile -Encoding UTF8

Write-Host "Conversion complete. Output: $OutputFile"
Write-Host "File size: $((Get-Item $OutputFile).Length / 1MB) MB"
```

**Run the converter:**

```powershell
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$InputSQL = Get-ChildItem "D:\Traccar_Migration_Backup\h2_export_*.sql" | Sort-Object LastWriteTime -Descending | Select-Object -First 1

powershell -ExecutionPolicy Bypass `
  -File "D:\Traccar_Migration_Backup\convert_h2_to_postgres.ps1" `
  -InputFile $InputSQL.FullName `
  -OutputFile "D:\Traccar_Migration_Backup\postgres_import_$Timestamp.sql"
```

**⚠️ IMPORTANT:** Review the converted SQL file for any remaining H2-specific syntax!

#### Step 3.2: Initialize Traccar Schema with Liquibase

**Temporarily configure Traccar to use PostgreSQL:**

Edit `C:\Program Files\Traccar\setup\traccar.xml`:

```xml
<!-- TEMPORARY: For schema initialization only -->
<entry key='database.driver'>org.postgresql.Driver</entry>
<entry key='database.url'>jdbc:postgresql://localhost:5432/traccar</entry>
<entry key='database.user'>traccar</entry>
<entry key='database.password'>YOUR_SECURE_PASSWORD</entry>

<!-- Enable Liquibase changelog -->
<entry key='database.changelog'>schema/changelog-master.xml</entry>
```

**Start Traccar briefly to create schema:**

```powershell
# Start Traccar (it will run Liquibase migrations)
Start-Service -Name "Traccar"

# Wait for schema creation (monitor logs)
Start-Sleep -Seconds 30

# Check if tables were created
psql -U traccar -d traccar -c "\dt"

# Stop Traccar
Stop-Service -Name "Traccar"
```

**Expected Output:** List of all Traccar tables (tc_users, tc_devices, tc_positions, etc.)

#### Step 3.3: Import Data into PostgreSQL

**OPTION A: Import Full SQL (includes schema - may conflict)**

```powershell
# This might fail if schema already exists
psql -U traccar -d traccar -f "D:\Traccar_Migration_Backup\postgres_import_$Timestamp.sql"
```

**OPTION B: Import Data Only (Recommended)**

Create a data-only SQL file by removing CREATE TABLE statements:

```powershell
# Extract only INSERT statements
$ImportFile = Get-ChildItem "D:\Traccar_Migration_Backup\postgres_import_*.sql" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$DataOnly = "D:\Traccar_Migration_Backup\data_only_$Timestamp.sql"

Get-Content $ImportFile.FullName | Where-Object {
    $_ -match "^INSERT INTO" -or $_ -match "^COPY"
} | Set-Content $DataOnly

# Import data
psql -U traccar -d traccar -f $DataOnly
```

**✅ Checkpoint:** Verify data import:

```powershell
# Check row counts
psql -U traccar -d traccar -c "SELECT 'tc_users' as table_name, COUNT(*) FROM tc_users UNION ALL SELECT 'tc_devices', COUNT(*) FROM tc_devices UNION ALL SELECT 'tc_positions', COUNT(*) FROM tc_positions ORDER BY table_name;"
```

Compare counts with H2 database!

---

### Phase 4: Update Traccar Configuration

#### Step 4.1: Finalize PostgreSQL Configuration

Edit `C:\Program Files\Traccar\setup\traccar.xml`:

```xml -example
<!-- Production PostgreSQL Configuration -->
<entry key='database.driver'>org.postgresql.Driver</entry>
<entry key='database.url'>jdbc:postgresql://localhost:5432/traccar</entry>
<entry key='database.user'>traccar</entry>
<entry key='database.password'>YOUR_SECURE_PASSWORD</entry>

<!-- Connection pool settings -->
<entry key='database.maxPoolSize'>50</entry>
<entry key='database.checkConnection'>SELECT 1</entry>

<!-- REMOVE or comment out changelog after initial migration -->
<!-- <entry key='database.changelog'>schema/changelog-master.xml</entry> -->
```

#### Step 4.2: Add PostgreSQL JDBC Driver (if not present)

**Driver exists: carried by original Traccar project**

---

### Phase 5: Start and Validate

#### Step 5.1: Start Traccar with PostgreSQL

```powershell
# Start Traccar service
Start-Service -Name "Traccar"

# Monitor logs for errors
Get-Content "C:\Program Files\Traccar\logs\tracker-server.log" -Tail 50 -Wait
```

**Look for:**
- ✅ `PostgreSQL connection successful`
- ✅ `HikariPool started`
- ✅ No database errors

**⚠️ If errors occur:** See [Troubleshooting](#troubleshooting) section

#### Step 5.2: Verify Web Interface

1. Open browser: `http://localhost:8082`
2. Login with existing credentials
3. Check:
   - ✅ User list loads
   - ✅ Device list loads
   - ✅ Historical positions display
   - ✅ Live tracking works

#### Step 5.3: Test AI Receipt Processing

Upload a test receipt and verify:
- ✅ Image uploads successfully
- ✅ Azure OCR processes receipt
- ✅ Expense record created
- ✅ Data inserted into `tc_expenses` table

```powershell
# Check latest expense
psql -U traccar -d traccar -c "SELECT id, merchant, amount, currency, created_time FROM tc_expenses ORDER BY created_time DESC LIMIT 5;"
```

---

---

## Post-Migration Validation

### Functional Testing Checklist

- [ ] **User Authentication**
  - [ ] Admin login works
  - [ ] Regular user login works
  - [ ] Password reset functions

- [ ] **Device Management**
  - [ ] List all devices
  - [ ] View device details
  - [ ] Live tracking updates
  - [ ] Historical playback works

- [ ] **Position Data**
  - [ ] Recent positions display
  - [ ] Map rendering correct
  - [ ] Trip reports generate
  - [ ] Geofence alerts trigger

- [ ] **Expense Management**
  - [ ] List expenses
  - [ ] View expense details
  - [ ] Upload new receipt (manual)
  - [ ] AI receipt processing (batch)
  - [ ] Delete expense + image file

- [ ] **Reports**
  - [ ] Generate trip report
  - [ ] Generate summary report
  - [ ] Export to Excel/PDF

### Performance Benchmarks

**Record baselines before migration, compare after:**

| Metric | H2 Baseline | PostgreSQL | Status |
|--------|-------------|------------|--------|
| Login time | ____s | ____s | ☐ |
| Device list load (100 devices) | ____s | ____s | ☐ |
| Position query (1000 records) | ____s | ____s | ☐ |
| Report generation | ____s | ____s | ☐ |
| AI receipt processing (1 item) | ____s | ____s | ☐ |

### Data Integrity Validation

```sql
-- Compare row counts
SELECT
    'tc_users' as table_name,
    COUNT(*) as row_count
FROM tc_users
UNION ALL
SELECT 'tc_devices', COUNT(*) FROM tc_devices
UNION ALL
SELECT 'tc_positions', COUNT(*) FROM tc_positions
UNION ALL
SELECT 'tc_expenses', COUNT(*) FROM tc_expenses
UNION ALL
SELECT 'tc_ai_receipt_batches', COUNT(*) FROM tc_ai_receipt_batches
ORDER BY table_name;
```

**Compare with H2 backup counts!**

### Database Optimization

After successful migration, optimize PostgreSQL:

```sql
-- Update statistics
ANALYZE;

-- Rebuild indexes
REINDEX DATABASE traccar;

-- Vacuum database
VACUUM FULL ANALYZE;
```

---

## Troubleshooting

### Common Issues

#### Issue 1: "Password authentication failed for user 'traccar'"

**Solution:**
1. Verify password in `traccar.xml` matches what you set
2. Check `pg_hba.conf` has correct authentication method
3. Test connection manually:
   ```powershell
   psql -U traccar -d traccar -h localhost
   ```

#### Issue 2: "Connection refused" or "Could not connect to server"

**Solution:**
1. Check PostgreSQL service is running:
   ```powershell
   Get-Service -Name "postgresql-x64-17"
   ```
2. Verify port 5432 is open:
   ```powershell
   Test-NetConnection -ComputerName localhost -Port 5432
   ```
3. Check Windows Firewall rules

#### Issue 3: "Table does not exist" errors

**Solution:**
- Schema wasn't created by Liquibase
- Re-run Phase 3, Step 3.2 (Initialize Traccar Schema)
- Ensure `database.changelog` was set during first startup

#### Issue 4: Data conversion errors (e.g., BigDecimal to NUMERIC)

**Solution:**
- Review converted SQL file for data type mismatches
- Manually fix problematic INSERT statements
- Common fixes:
  ```sql
  -- H2: DECIMAL(10,2)
  -- PostgreSQL: NUMERIC(10,2)

  -- Ensure NULL values are NULL, not string 'null'
  UPDATE tc_expenses SET hst = NULL WHERE hst::TEXT = 'null';
  ```

#### Issue 5: Slow performance after migration

**Solution:**
1. **Update statistics:**
   ```sql
   ANALYZE VERBOSE;
   ```

2. **Check indexes:**
   ```sql
   SELECT schemaname, tablename, indexname
   FROM pg_indexes
   WHERE schemaname = 'public'
   ORDER BY tablename, indexname;
   ```

3. **Rebuild indexes if needed:**
   ```sql
   REINDEX DATABASE traccar;
   ```

4. **Increase shared_buffers** in `postgresql.conf`:
   ```ini
   shared_buffers = 512MB  # Increase based on available RAM
   ```

#### Issue 6: Foreign key constraint violations during import

**Solution:**
1. **Temporarily disable constraints:**
   ```sql
   SET session_replication_role = 'replica';
   -- Run import
   SET session_replication_role = 'origin';
   ```

2. **Or drop and recreate constraints:**
   ```sql
   -- List all foreign keys
   SELECT conname, conrelid::regclass AS table_name
   FROM pg_constraint
   WHERE contype = 'f';

   -- Drop problematic constraint
   ALTER TABLE tc_positions DROP CONSTRAINT fk_positions_deviceid;

   -- Import data

   -- Recreate constraint
   ALTER TABLE tc_positions
   ADD CONSTRAINT fk_positions_deviceid
   FOREIGN KEY (deviceid) REFERENCES tc_devices(id);
   ```

### Logs and Diagnostics

**Traccar Logs:**
```powershell
# Real-time monitoring
Get-Content "C:\Program Files\Traccar\logs\tracker-server.log" -Tail 100 -Wait

# Search for errors
Select-String -Path "C:\Program Files\Traccar\logs\tracker-server.log" -Pattern "ERROR" | Select-Object -Last 20
```

**PostgreSQL Logs:**
```powershell
# Located in:
Get-Content "C:\Program Files\PostgreSQL\17\data\log\postgresql-*.log" -Tail 50
```

**SQL Query for Connection Status:**
```sql
SELECT * FROM pg_stat_activity WHERE datname = 'traccar';
```

---

## References

### Official Documentation

- **Traccar Documentation:** https://www.traccar.org/documentation/
- **Traccar Forum Migration Thread:** https://www.traccar.org/forums/topic/2023-ho-i-migrated-from-h2-to-postgres-in-linux/
- **PostgreSQL Official Docs:** https://www.postgresql.org/docs/17/
- **PostgreSQL Windows Installation:** https://www.postgresql.org/download/windows/
- **Liquibase Documentation:** https://docs.liquibase.com/

### Tools

- **H2 Database Tools:** https://www.h2database.com/html/tutorial.html#tools
- **PostgreSQL JDBC Driver:** https://jdbc.postgresql.org/
- **pgAdmin 4:** https://www.pgadmin.org/
- **DBeaver (Database Client):** https://dbeaver.io/

### SQL Conversion References

- **H2 to PostgreSQL Migration:** https://h2database.com/html/features.html#compatibility
- **PostgreSQL Data Types:** https://www.postgresql.org/docs/17/datatype.html
- **Liquibase Best Practices:** https://docs.liquibase.com/concepts/bestpractices.html

---

## Migration Timeline Estimate

| Phase | Task | Estimated Time |
|-------|------|----------------|
| **Phase 0** | Preparation & Review | 30 minutes |
| **Phase 1** | Backup Current System | 15 minutes |
| **Phase 2** | Install PostgreSQL | 30 minutes |
| **Phase 3** | Convert & Import Data | 45-90 minutes* |
| **Phase 4** | Update Configuration | 15 minutes |
| **Phase 5** | Start & Validate | 30 minutes |
| **Post** | Validation & Optimization | 30 minutes |
| **TOTAL** | | **3-4 hours** |

*Time varies based on database size

---

## Success Criteria

✅ **Migration is successful when:**

1. PostgreSQL service running and accessible
2. Traccar service starts without database errors
3. Web interface loads and all functions work
4. All data counts match H2 baseline
5. Live tracking receives and displays new positions
6. AI receipt processing creates expenses successfully
7. Reports generate correctly
8. Performance is equal to or better than H2
9. No errors in logs after 24 hours of operation
10. Backup and recovery procedures tested

---

## Final Notes

### Post-Migration Tasks (Week 1)

- [ ] Monitor PostgreSQL logs daily for errors
- [ ] Monitor disk space usage
- [ ] Test backup/restore procedures
- [ ] Update documentation with PostgreSQL details
- [ ] Train team on PostgreSQL management tools (pgAdmin)
- [ ] Set up automated PostgreSQL backups
- [ ] Configure log rotation for PostgreSQL

### When to Delete H2 Database

**Wait at least 2 weeks** after successful migration before removing H2 files:

```powershell
# After validation period (2+ weeks):
$ArchivePath = "D:\Traccar_Archive_H2"
New-Item -Path $ArchivePath -ItemType Directory
Move-Item "C:\Program Files\Traccar\data\database.*" $ArchivePath
```

### PostgreSQL Maintenance Schedule

| Task | Frequency | Command |
|------|-----------|---------|
| VACUUM ANALYZE | Weekly | `VACUUM ANALYZE;` |
| REINDEX | Monthly | `REINDEX DATABASE traccar;` |
| Full Backup | Daily | Use pgAdmin or pg_dump |
| Check Disk Space | Daily | Monitor data directory |
| Review Logs | Daily | Check for errors/warnings |

---

## Appendix: Useful SQL Queries

### Check Database Size
```sql
SELECT
    pg_size_pretty(pg_database_size('traccar')) as database_size,
    pg_size_pretty(pg_total_relation_size('tc_positions')) as positions_table_size,
    pg_size_pretty(pg_total_relation_size('tc_expenses')) as expenses_table_size;
```

### List All Tables and Row Counts
```sql
SELECT
    schemaname,
    tablename,
    n_tup_ins as "Inserts",
    n_tup_upd as "Updates",
    n_tup_del as "Deletes"
FROM pg_stat_user_tables
ORDER BY tablename;
```

### Check Active Connections
```sql
SELECT
    datname,
    usename,
    application_name,
    client_addr,
    state,
    query_start
FROM pg_stat_activity
WHERE datname = 'traccar';
```

### Find Slow Queries
```sql
SELECT
    query,
    calls,
    total_time,
    mean_time,
    max_time
FROM pg_stat_statements
ORDER BY mean_time DESC
LIMIT 10;
```

---

**Document End**

For questions or issues during migration, contact:
- Traccar Community Forum: https://www.traccar.org/forums/
- PostgreSQL Community: https://www.postgresql.org/community/

---

*This migration plan is based on Traccar 6.10.0 and PostgreSQL 17.x for Windows Server 2022. Adapt as needed for your specific environment.*
