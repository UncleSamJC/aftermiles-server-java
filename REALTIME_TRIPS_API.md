# Real-Time Trips API Documentation

## Overview
Real-time trips API provides endpoints to query vehicle trip data that is automatically detected and segmented based on ignition state and motion detection.

## Authentication
All endpoints require authentication. Include session token in request headers or cookies.

---

## Endpoints

### 1. Get Trips by Time Range

**Endpoint:** `GET /api/realtimetrips`

**Description:** Query trips within a specified time range with optional device filter.

**Query Parameters:**

| Parameter | Type   | Required | Format            | Description                    |
|-----------|--------|----------|-------------------|--------------------------------|
| from      | string | Yes      | YYYY-MM-DDTHH:mm  | Start time of query range      |
| to        | string | Yes      | YYYY-MM-DDTHH:mm  | End time of query range        |
| deviceId  | long   | No       | -                 | Filter by specific device ID   |

**Example Requests:**
```
GET /api/realtimetrips?from=2025-01-12T00:00&to=2025-01-12T23:59
GET /api/realtimetrips?from=2025-01-10T08:00&to=2025-01-15T18:00&deviceId=123
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "deviceId": 123,
      "userId": 456,
      "startTime": "2025-01-12T08:00:00Z",
      "endTime": "2025-01-12T09:30:00Z",
      "startPositionId": 1001,
      "endPositionId": 1150,
      "startOdometer": 50000.0,
      "distance": 15500.0,
      "duration": 5400000,
      "startAddress": "123 Main St, Toronto, ON",
      "endAddress": "456 Oak Ave, Mississauga, ON",
      "status": "completed"
    },
    {
      "id": 2,
      "deviceId": 123,
      "userId": 456,
      "startTime": "2025-01-12T14:00:00Z",
      "endTime": null,
      "startPositionId": 1200,
      "endPositionId": null,
      "startOdometer": 65500.0,
      "distance": null,
      "duration": null,
      "startAddress": "789 King St, Toronto, ON",
      "endAddress": null,
      "status": "active"
    }
  ],
  "count": 2,
  "from": "2025-01-12T00:00",
  "to": "2025-01-12T23:59"
}
```

**Field Descriptions:**

| Field           | Type   | Description                                      |
|-----------------|--------|--------------------------------------------------|
| id              | long   | Unique trip identifier                           |
| deviceId        | long   | Device ID                                        |
| userId          | long   | User ID (nullable)                               |
| startTime       | string | Trip start time (ISO 8601 UTC)                   |
| endTime         | string | Trip end time (ISO 8601 UTC, null if active)     |
| startPositionId | long   | Position ID at trip start                        |
| endPositionId   | long   | Position ID at trip end (null if active)         |
| startOdometer   | double | Odometer reading at trip start (meters)          |
| distance        | double | Trip distance in meters (null if active)         |
| duration        | long   | Trip duration in milliseconds (null if active)   |
| startAddress    | string | Reverse geocoded start address (nullable)        |
| endAddress      | string | Reverse geocoded end address (nullable)          |
| status          | string | Trip status: "active" or "completed"             |

**Error Response (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "code": "MISSING_PARAMETERS",
    "message": "from parameter is required (format: YYYY-MM-DDTHH:mm)"
  }
}
```

---

### 2. Get Trip by ID

**Endpoint:** `GET /api/realtimetrips/{id}`

**Description:** Retrieve detailed information about a specific trip.

**Path Parameters:**

| Parameter | Type | Required | Description       |
|-----------|------|----------|-------------------|
| id        | long | Yes      | Trip ID           |

**Example Request:**
```
GET /api/realtimetrips/123
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": 123,
    "deviceId": 456,
    "userId": 789,
    "startTime": "2025-01-12T08:00:00Z",
    "endTime": "2025-01-12T09:30:00Z",
    "startPositionId": 1001,
    "endPositionId": 1150,
    "startOdometer": 50000.0,
    "distance": 15500.0,
    "duration": 5400000,
    "startAddress": "123 Main St, Toronto, ON",
    "endAddress": "456 Oak Ave, Mississauga, ON",
    "status": "completed"
  }
}
```

**Error Response (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "code": "TRIP_NOT_FOUND",
    "message": "Trip not found"
  }
}
```

---

## Error Codes

| Error Code            | Description                                          |
|-----------------------|------------------------------------------------------|
| MISSING_PARAMETERS    | Required parameter is missing                        |
| INVALID_DATE_FORMAT   | Date format is incorrect (expected: YYYY-MM-DDTHH:mm)|
| INVALID_DATE_RANGE    | from date must be before or equal to to date         |
| TRIP_NOT_FOUND        | Trip with specified ID does not exist                |
| PERMISSION_DENIED     | User doesn't have permission to view this trip       |

---

## Notes

### Permission Control
- Non-admin users can only view their own trips
- Admin users can view all trips
- Users must have permission to access the device associated with the trip

### Active Trips
- Trips with `status: "active"` indicate ongoing trips
- `endTime`, `endPositionId`, `distance`, and `duration` will be `null` for active trips
- `endAddress` may also be `null` for active trips

### Time Range Query
- Query uses `startTime` field for filtering
- Both completed and active trips within the range are returned
- Times are interpreted in system default timezone

### Address Geocoding
- Addresses are reverse geocoded asynchronously (Phase 4 feature)
- `startAddress` and `endAddress` may be `null` if geocoding hasn't completed
- Geocoding depends on server configuration

### Trip Detection Rules
- Trips are automatically detected based on ignition state and motion detection
- Minimum distance and duration requirements apply (configurable)
- Trips not meeting minimum requirements are automatically discarded
- See server configuration for trip detection parameters:
  - `realtimeTrip.minStopDuration` (default: 180000 ms)
  - `realtimeTrip.minDistance` (default: 100 m)
  - `realtimeTrip.minDuration` (default: 60000 ms)
  - `realtimeTrip.ignitionRequired` (default: true)

---

## Frontend Implementation Suggestions

### 1. Trip List Page (Time Range Query)
```javascript
// Example: Get today's trips
const today = new Date();
const from = today.toISOString().slice(0, 16).replace('T', 'T'); // "2025-01-12T00:00"
const to = today.toISOString().slice(0, 11) + "23:59"; // "2025-01-12T23:59"

fetch(`/api/realtimetrips?from=${from}&to=${to}`)
  .then(res => res.json())
  .then(data => {
    console.log(`Found ${data.count} trips`);
    data.data.forEach(trip => {
      console.log(`Trip ${trip.id}: ${trip.distance}m, ${trip.duration}ms`);
    });
  });
```

### 2. Trip Detail Page
```javascript
// Get specific trip details
fetch(`/api/realtimetrips/123`)
  .then(res => res.json())
  .then(data => {
    const trip = data.data;
    console.log(`Trip from ${trip.startAddress} to ${trip.endAddress}`);
  });
```

### 3. Handle Active Trips
```javascript
// Check if trip is still active
if (trip.status === "active") {
  console.log("Trip is still in progress");
  // Show loading state for distance/duration
  // Poll for updates or use WebSocket
}
```

### 4. Format Display Values
```javascript
// Distance: meters to kilometers
const distanceKm = (trip.distance / 1000).toFixed(2);

// Duration: milliseconds to hours:minutes
const hours = Math.floor(trip.duration / 3600000);
const minutes = Math.floor((trip.duration % 3600000) / 60000);
const durationStr = `${hours}h ${minutes}m`;

// Odometer: meters to kilometers
const odometerKm = (trip.startOdometer / 1000).toFixed(1);
```

---

## Version
API Version: 6.14.0 (Phase 3 - API & Storage Layer)

Last Updated: 2025-01-12
