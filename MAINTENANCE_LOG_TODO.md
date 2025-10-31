# Maintenance Log Feature Implementation TODO

## Overview
This document outlines the implementation plan for the Maintenance Log feature. The implementation is split into two phases:
1. **Phase 1**: Refactor existing Expense module to use shared utility class
2. **Phase 2**: Implement Maintenance Log feature using the shared utilities

---

## Phase 1: Refactor Expense Module (Foundation)

### 1.1 Create MediaUploadHelper Utility Class
**File**: `src/main/java/org/traccar/helper/MediaUploadHelper.java`

**Purpose**: Extract file upload logic from ExpenseResource into reusable utility class

**Key Methods**:
- `isValidImageType(FormDataBodyPart)` - Validate MIME type (JPEG/PNG only)
- `getFileExtension(String)` - Extract file extension from filename
- `buildHierarchicalPath(String, Date)` - Build deviceId/year/month/ structure
- `uploadSingleFile(...)` - Upload single file with size validation
- `uploadMultipleFiles(...)` - Upload multiple files with size validation

**Features**:
- Size limit validation (configurable via config)
- Hierarchical storage: `{deviceUniqueId}/{year}/{month}/`
- Error handling with descriptive error codes
- Buffer-based streaming for memory efficiency

**Status**: ⏳ To be created

---

### 1.2 Refactor ExpenseResource to Use MediaUploadHelper
**File**: `src/main/java/org/traccar/api/resource/ExpenseResource.java`

**Changes Required**:
1. Remove inline file upload code (lines ~257-295)
2. Import `MediaUploadHelper`
3. Replace file validation logic with `MediaUploadHelper.isValidImageType()`
4. Replace file extension extraction with `MediaUploadHelper.getFileExtension()`
5. Replace file upload logic with `MediaUploadHelper.uploadSingleFile()`
6. Maintain existing error handling pattern

**Benefits**:
- Reduces code duplication
- Validates utility class works correctly
- Ensures consistency with new Maintenance Log feature

**Testing**:
- Existing Expense POST endpoint must continue to work
- Receipt upload should produce identical file paths
- Error handling should remain the same

**Status**: ⏳ Pending (after 1.1)

---

### 1.3 Testing Phase 1
**Tasks**:
- [ ] Run `./gradlew checkstyle` - Ensure code style compliance
- [ ] Run `./gradlew build` - Compile and run tests
- [ ] Test Expense POST with receipt upload via Postman/cURL
- [ ] Verify receipt file is saved correctly in hierarchical structure
- [ ] Test file size limit enforcement
- [ ] Test invalid file type rejection
- [ ] Verify GET /api/expenses/{id}/receipt still works

**Status**: ⏳ Pending (after 1.2)

---

## Phase 2: Implement Maintenance Log Feature

### 2.1 Create MaintenanceLog Model
**File**: `src/main/java/org/traccar/model/MaintenanceLog.java`

**Fields**:
```java
private long id;
private long deviceId;
private Date date;
private String serviceCompleted;
private String completedBy;
private String notes;
private String maintenancePhotos;  // Comma-separated relative paths
private long createdByUserId;
private Date createdTime;
private Date modifiedTime;
```

**Annotations**:
- `@StorageName("tc_maintenance_logs")` - Table mapping
- Field annotations for column mapping (lowercase, no underscores)

**Helper Methods**:
- `getPhotosAsList()` - Split comma-separated paths into List<String>
- `setPhotosFromList(List<String>)` - Join list into comma-separated string

**Status**: ⏳ To be created

---

### 2.2 Create Database Migration
**File**: `src/main/resources/org/traccar/storage/query/database/changelog-6.12.0.xml`

**Content**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog ...>

  <changeSet id="changelog-6.12.0" author="aftermiles">

    <createTable tableName="tc_maintenance_logs">
      <column name="id" type="BIGINT" autoIncrement="true">
        <constraints primaryKey="true" />
      </column>
      <column name="deviceid" type="BIGINT">
        <constraints nullable="false" />
      </column>
      <column name="date" type="TIMESTAMP">
        <constraints nullable="false" />
      </column>
      <column name="servicecompleted" type="VARCHAR(4000)">
        <constraints nullable="false" />
      </column>
      <column name="completedby" type="VARCHAR(255)">
        <constraints nullable="false" />
      </column>
      <column name="notes" type="VARCHAR(4000)" />
      <column name="maintenancephotos" type="VARCHAR(4000)" />
      <column name="createdbyuserid" type="BIGINT">
        <constraints nullable="false" />
      </column>
      <column name="createdtime" type="TIMESTAMP">
        <constraints nullable="false" />
      </column>
      <column name="modifiedtime" type="TIMESTAMP">
        <constraints nullable="false" />
      </column>
    </createTable>

    <addForeignKeyConstraint
      constraintName="fk_maintenancelogs_deviceid"
      baseTableName="tc_maintenance_logs"
      baseColumnNames="deviceid"
      referencedTableName="tc_devices"
      referencedColumnNames="id"
      onDelete="CASCADE" />

    <addForeignKeyConstraint
      constraintName="fk_maintenancelogs_userid"
      baseTableName="tc_maintenance_logs"
      baseColumnNames="createdbyuserid"
      referencedTableName="tc_users"
      referencedColumnNames="id"
      onDelete="CASCADE" />

    <createIndex
      indexName="idx_maintenance_device_date"
      tableName="tc_maintenance_logs">
      <column name="deviceid" />
      <column name="date" />
    </createIndex>

  </changeSet>

</databaseChangeLog>
```

**Status**: ⏳ To be created

---

### 2.3 Update Master Changelog
**File**: `src/main/resources/org/traccar/storage/query/database/changelog-master.xml`

**Change**: Add include for new changelog at the end:
```xml
<include file="org/traccar/storage/query/database/changelog-6.12.0.xml" />
```

**Status**: ⏳ Pending (after 2.2)

---

### 2.4 Create MaintenanceLogResource - POST Endpoint
**File**: `src/main/java/org/traccar/api/resource/MaintenanceLogResource.java`

**Endpoint**: `POST /api/maintenance-logs`

**Request Parameters** (FormData):
- `deviceId` (long, required)
- `date` (String YYYY-MM-DD, required)
- `serviceCompleted` (String, required)
- `completedBy` (String, required)
- `notes` (String, optional)
- `photos` (File[], optional, max 10 files)

**Implementation Steps**:
1. Extend `BaseObjectResource<MaintenanceLog>`
2. Inject dependencies: `Config`, `MediaManager`, `LogAction`
3. Validate required fields
4. Parse and validate date format
5. Check device permissions using `permissionsService.checkPermission()`
6. Validate photo files using `MediaUploadHelper.isValidImageType()`
7. Upload photos using `MediaUploadHelper.uploadMultipleFiles()`
8. Join photo paths with comma separator
9. Create MaintenanceLog object with all fields
10. Save to database using `storage.addObject()`
11. Return success response with photo URLs

**Response Format**:
```json
{
  "id": 123,
  "deviceId": "12345",
  "date": "2024-12-15",
  "serviceCompleted": "Oil Change",
  "completedBy": "ABC Auto Shop",
  "notes": "Regular maintenance",
  "photos": [
    {
      "url": "/api/maintenance-logs/123/photos/0",
      "fileName": "maintenance_1735478401234_0.jpg"
    }
  ],
  "createdAt": "2024-12-15T10:30:00Z"
}
```

**Status**: ⏳ To be created

---

### 2.5 Create MaintenanceLogResource - GET Query Endpoint
**File**: Same as 2.4

**Endpoint**: `GET /api/maintenance-logs?deviceId={id}&year={year}`

**Query Parameters**:
- `deviceId` (long, required)
- `year` (int, required)

**Implementation Steps**:
1. Validate required query parameters
2. Check device permissions
3. Build date range: `{year}-01-01 00:00:00` to `{year}-12-31 23:59:59`
4. Query database with conditions:
   - `deviceId = ?`
   - `date >= startOfYear`
   - `date <= endOfYear`
5. Order by date DESC (newest first)
6. Transform each result to include photos array
7. Return JSON array

**Response Format**: Array of MaintenanceLog objects (same structure as POST response)

**Status**: ⏳ Pending (after 2.4)

---

### 2.6 Create MaintenanceLogResource - GET Photo Endpoint
**File**: Same as 2.4

**Endpoint**: `GET /api/maintenance-logs/{logId}/photos/{index}`

**Path Parameters**:
- `logId` (long) - Maintenance log ID
- `index` (int) - Photo index (0-based)

**Implementation Steps**:
1. Retrieve MaintenanceLog by ID
2. Check permissions for associated device
3. Get `maintenancephotos` field and split by comma
4. Validate index is within bounds
5. Get photo path at index
6. Use MediaManager to read file
7. Detect content type from extension
8. Return image binary data with proper headers:
   - `Content-Type: image/jpeg` or `image/png`
   - `Content-Disposition: inline; filename="..."`

**Error Handling**:
- 404 if log not found
- 404 if index out of bounds
- 403 if user lacks permission
- 404 if file doesn't exist on disk

**Status**: ⏳ Pending (after 2.5)

---

### 2.7 Testing Phase 2
**Tasks**:
- [ ] Run `./gradlew checkstyle` - Code style compliance
- [ ] Run `./gradlew build` - Compile and run tests
- [ ] Database migration runs successfully on H2
- [ ] POST /api/maintenance-logs with all fields → Success
- [ ] POST with multiple photos → Photos saved, comma-separated paths stored
- [ ] POST without photos → Success with null/empty maintenancephotos
- [ ] POST with missing required field → 400 error
- [ ] POST with invalid date format → 400 error
- [ ] POST with invalid deviceId → 404 error
- [ ] POST with >10 photos → 400 error
- [ ] POST with file >5MB → 413 error
- [ ] GET /api/maintenance-logs?deviceId=X&year=2024 → Returns records
- [ ] GET with no results → Returns empty array []
- [ ] GET without deviceId → 400 error
- [ ] GET without year → 400 error
- [ ] GET /api/maintenance-logs/{id}/photos/0 → Returns image
- [ ] GET photo with invalid index → 404 error
- [ ] Verify records sorted by date DESC

**Status**: ⏳ Pending (after 2.6)

---

## Configuration Keys

Add to `src/main/java/org/traccar/config/Keys.java` if not exists:
```java
public static final ConfigKey<Integer> MAINTENANCE_PHOTO_SIZE_LIMIT = new ConfigKey<>(
    "maintenance.photoSizeLimit",
    Collections.singletonList(KeyType.CONFIG),
    5242880); // 5MB default

public static final ConfigKey<Integer> MAINTENANCE_PHOTO_COUNT_LIMIT = new ConfigKey<>(
    "maintenance.photoCountLimit",
    Collections.singletonList(KeyType.CONFIG),
    10); // Max 10 photos
```

---

## File Structure Summary

```
src/main/java/org/traccar/
├── helper/
│   └── MediaUploadHelper.java          [NEW]
├── model/
│   └── MaintenanceLog.java             [NEW]
└── api/resource/
    ├── ExpenseResource.java            [MODIFIED - use MediaUploadHelper]
    └── MaintenanceLogResource.java     [NEW]

src/main/resources/org/traccar/storage/query/database/
├── changelog-master.xml                [MODIFIED - add include]
└── changelog-6.12.0.xml                [NEW]
```

---

## Implementation Order (Sequential)

1. ✅ **Update BK_MAINTENANCELOG_INTEGRATION.md** - Documentation aligned with comma-separated approach
2. ⏳ **Create MediaUploadHelper** - Foundation utility class
3. ⏳ **Refactor ExpenseResource** - Validate utility works
4. ⏳ **Test Expense functionality** - Ensure no regression
5. ⏳ **Create MaintenanceLog Model** - Data structure
6. ⏳ **Create database migration** - Schema changes
7. ⏳ **Update changelog-master.xml** - Migration registration
8. ⏳ **Implement POST /api/maintenance-logs** - Create records
9. ⏳ **Implement GET /api/maintenance-logs** - Query records
10. ⏳ **Implement GET photos endpoint** - Retrieve images
11. ⏳ **Comprehensive testing** - All endpoints with Postman
12. ✅ **Done** - Feature ready for production

---

## Notes

- **Code Reuse**: MediaUploadHelper enables consistent file handling across Expense and Maintenance Log
- **No Separate Photos Table**: Using comma-separated paths simplifies schema and queries
- **Index-Based Photo Access**: Photos accessed by 0-based index rather than separate photo IDs
- **Permission Control**: Both features check device permissions via createdByUserId
- **Hierarchical Storage**: Files organized by deviceId/year/month for easy management
- **File Naming**: `maintenance_{timestamp}_{index}.{ext}` - no logId needed in filename

---

## Risk Mitigation

1. **Phase 1 First**: Refactoring Expense module validates utility class before new feature
2. **Incremental Testing**: Test after each major step to catch issues early
3. **No Breaking Changes**: Expense API remains unchanged for frontend compatibility
4. **Rollback Plan**: Database migration can be reverted via Liquibase if needed
