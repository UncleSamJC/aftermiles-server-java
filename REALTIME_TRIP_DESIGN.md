# Real-Time Trip Analysis System Design

## Overview

Implement a real-time trip analysis system that automatically segments vehicle journeys based on live GPS data from H02 protocol trackers. The system analyzes Position data in real-time using feature detection (ignition state, stop duration) to intelligently split trips, replacing the current report-based historical data segmentation approach.

## Architecture

### Data Flow
```
H02 Protocol → Position Decoder → Processing Pipeline → RealtimeTripDetectionHandler
                                                              ↓
                                                        RealtimeTripStateManager
                                                              ↓
                                                    Database (tcaf_realtime_trips)
                                                              ↓
                                                         RealtimeTrip Events
```

### Integration Points
- **Handler Pipeline**: Insert `RealtimeTripDetectionHandler` after `DatabaseHandler` in `ProcessingHandler.java`
- **State Management**: `RealtimeTripStateManager` as singleton service (Guice managed)
- **Database**: New Liquibase changelog for `tcaf_realtime_trips` table
- **API**: New `RealtimeTripResource` for REST endpoints
- **Events**: Emit REALTIME_TRIP_START/REALTIME_TRIP_END events via `NotificationManager`

## Data Model

### Database Schema

**Table: tcaf_realtime_trips**
```sql
- id (BIGINT, PRIMARY KEY)
- deviceId (BIGINT, NOT NULL, FK to tc_devices)
- userId (BIGINT, FK to tc_users)
- startOdometer (DOUBLE, odometer reading at trip start)
- startPositionId (BIGINT, FK to tc_positions)
- endPositionId (BIGINT, FK to tc_positions)
- startTime (TIMESTAMP, NOT NULL)
- endTime (TIMESTAMP, nullable for ongoing trips)
- distance (DOUBLE, calculated from positions)
- duration (BIGINT, milliseconds)
- startAddress (VARCHAR(512))
- endAddress (VARCHAR(512))
- attributes (VARCHAR(4000), JSON for extensibility)
```

**Indexes:**
- deviceId + startTime (for device trip history queries)
- startTime + endTime (for time range queries)
- endTime IS NULL (for active trips)

### Java Model

**org.traccar.model.AFTrip**
- Extends BaseModel
- Standard getters/setters
- Follows existing Traccar model patterns

## Core Components

### 1. RealtimeTripDetectionHandler
- **Package**: `org.traccar.handler`
- **Extends**: `BaseDataHandler`
- **Responsibilities**:
  - Receive Position from pipeline
  - Query RealtimeTripStateManager for device state
  - Detect trip start/end conditions
  - Create/update Trip records
  - Emit trip events

### 2. RealtimeTripStateManager
- **Package**: `org.traccar.session`
- **Type**: Singleton service (@Singleton)
- **State Storage**: `ConcurrentHashMap<Long, RealtimeTripState>` (deviceId → state)
- **RealtimeTripState fields**:
  - currentTripId
  - tripStartTime
  - lastIgnitionOn
  - lastStopTime
  - lastPosition
- **Methods**:
  - `RealtimeTripState getDeviceState(long deviceId)`
  - `void updateState(Position position)`
  - `boolean shouldStartTrip(RealtimeTripState state, Position position)`
  - `boolean shouldEndTrip(RealtimeTripState state, Position position)`

### 3. RealtimeTripResource
- **Package**: `org.traccar.api.resource`
- **Endpoints**:
  - `GET /api/realtimetrips` - Query trips by device/time range
  - `GET /api/realtimetrips/{id}` - Get single trip
  - `GET /api/realtimetrips/active` - Get currently active trips

### 4. Storage Layer
- **org.traccar.storage.query.Columns** - Add Trip columns
- **DatabaseStorage** - CRUD operations for Trip model

## Realtime Trip Detection Rules

### Start Conditions (any of):
1. Ignition ON → ON transition
2. Device changes from stopped (speed = 0) to moving (speed > threshold)

### End Conditions (any of):
1. Ignition OFF + stopped for > MIN_STOP_DURATION (default 3 minutes)
2. Continuous stop > MIN_STOP_DURATION with ignition OFF
3. Daily auto-split at 23:59:59 (if enabled)

### Configuration Keys
Add to `org.traccar.config.Keys`:
```
REALTIME_TRIP_MIN_STOP_DURATION (default: 180000ms / 3 minutes)
REALTIME_TRIP_MIN_DISTANCE (default: 100m, filter short trips)
REALTIME_TRIP_MIN_DURATION (default: 60000ms / 1 minute)
REALTIME_TRIP_DAILY_SPLIT (default: false)
REALTIME_TRIP_IGNITION_REQUIRED (default: true)
```

## Event Types

Add to Event model:
- `TYPE_REALTIME_TRIP_START` - "realtimeTripStart"
- `TYPE_REALTIME_TRIP_END` - "realtimeTripEnd"

## Implementation TODO

### Phase 1: Foundation
- [ ] Create `AFTrip.java` model in `org.traccar.model`
- [ ] Add Trip columns to `Columns.java`
- [ ] Create Liquibase changelog for `tcaf_realtime_trips` table
- [ ] Add configuration keys to `Keys.java`
- [ ] Add TRIP_START/TRIP_END event types to `Event.java`

### Phase 2: Core Logic
- [ ] Create `RealtimeTripState.java` class
- [ ] Implement `RealtimeTripStateManager.java` service
- [ ] Implement `RealtimeTripDetectionHandler.java`
- [ ] Register handler in `ProcessingHandler.java`
- [ ] Register service in `MainModule.java` (Guice binding)

### Phase 3: API & Storage
- [ ] Implement `RealtimeTripResource.java` REST endpoints
- [ ] Add trip queries to `DatabaseStorage` or create `RealtimeTripStorage`
- [ ] Add permissions checks (user can only see their device trips)
- [ ] Register resource in `WebModule.java`

### Phase 4: Integration
- [ ] Add trip event handling to `NotificationManager`
- [ ] Update `EventData.java` for trip event forwarding
- [ ] Add reverse geocoding for start/end addresses (async)
- [ ] Handle edge cases (device offline, server restart)

### Phase 5: Testing
- [ ] Unit tests for trip detection logic
- [ ] Test cases for edge scenarios (rapid ignition changes, etc.)
- [ ] Integration test with H02 protocol decoder
- [ ] Performance test with multiple devices

### Phase 6: Documentation
- [ ] API documentation for new endpoints
- [ ] Configuration guide for trip detection parameters
- [ ] Migration guide from report-based trips

## Future Enhancements

- Driver behavior analysis (harsh braking, speeding)
- Geofence-based trip segmentation
- Multi-day trip handling for long hauls
- Machine learning for intelligent stop detection
- Redis support for distributed deployment (use existing BroadcastService)

## Notes

- All trip state is in-memory; acceptable to rebuild on server restart
- Trip distance calculated from sum of position distances
- Start/end addresses populated asynchronously via GeocoderHandler
- Maintains backward compatibility with existing reports
