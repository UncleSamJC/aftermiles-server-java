/*
 * Copyright 2024 Aftermiles.ca
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.handler;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.database.NotificationManager;
import org.traccar.model.AFTrip;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.session.cache.CacheManager;
import org.traccar.session.state.RealtimeTripState;
import org.traccar.session.state.RealtimeTripStateManager;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

/**
 * Detects trip start/end in real-time as position data arrives.
 * Creates AFTrip records and emits trip events.
 */
public class RealtimeTripDetectionHandler extends BasePositionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RealtimeTripDetectionHandler.class);

    private final RealtimeTripStateManager stateManager;
    private final Storage storage;
    private final CacheManager cacheManager;
    private final NotificationManager notificationManager;

    @Inject
    public RealtimeTripDetectionHandler(
            RealtimeTripStateManager stateManager,
            Storage storage,
            CacheManager cacheManager,
            NotificationManager notificationManager) {
        this.stateManager = stateManager;
        this.storage = storage;
        this.cacheManager = cacheManager;
        this.notificationManager = notificationManager;
    }

    @Override
    public void onPosition(Position position, Callback callback) {
        try {
            processPosition(position);
        } catch (Exception e) {
            LOGGER.warn("Failed to process real-time trip for device {}", position.getDeviceId(), e);
        }
        callback.processed(false);
    }

    private void processPosition(Position position) throws StorageException {
        RealtimeTripState state = stateManager.getDeviceState(position.getDeviceId());

        // Check for trip start
        if (stateManager.shouldStartTrip(state, position)) {
            startTrip(state, position);
        }

        // Update distance for ongoing trip
        if (state.hasActiveTrip()) {
            stateManager.updateDistance(state, position);
        }

        // Check for trip end
        if (stateManager.shouldEndTrip(state, position)) {
            endTrip(state, position);
        }

        // Update state after processing
        stateManager.updateState(state, position);
    }

    private void startTrip(RealtimeTripState state, Position position) throws StorageException {
        // Create new trip record
        AFTrip trip = new AFTrip();
        trip.setDeviceId(position.getDeviceId());
        trip.setStartTime(position.getFixTime());
        trip.setStartPositionId(position.getId());

        // Get user ID from device if available
        var device = cacheManager.getObject(org.traccar.model.Device.class, position.getDeviceId());
        if (device != null && device.getAttributes().containsKey("userId")) {
            trip.setUserId((Long) device.getAttributes().get("userId"));
        }

        // Set start odometer
        if (position.getAttributes().containsKey(Position.KEY_ODOMETER)) {
            trip.setStartOdometer(position.getDouble(Position.KEY_ODOMETER));
        }

        // Save trip to database
        long tripId = storage.addObject(trip, new Request(new Columns.Exclude("id")));
        trip.setId(tripId);

        // Update state
        state.startNewTrip(tripId, position);

        LOGGER.info("Started trip {} for device {}", tripId, position.getDeviceId());

        // Emit trip start event
        Event event = new Event(Event.TYPE_REALTIME_TRIP_START, position);
        event.set("tripId", tripId);
        notificationManager.updateEvents(java.util.Map.of(event, position));
    }

    private void endTrip(RealtimeTripState state, Position position) throws StorageException {
        Long tripId = state.getCurrentTripId();
        if (tripId == null) {
            return;
        }

        // Check if trip meets minimum requirements
        if (!stateManager.meetsMinimumRequirements(state, position)) {
            LOGGER.debug("Trip {} for device {} does not meet minimum requirements, discarding",
                    tripId, position.getDeviceId());
            state.endTrip();

            // Delete the trip record
            storage.removeObject(AFTrip.class, new Request(new Condition.Equals("id", tripId)));
            return;
        }

        // Load existing trip record
        AFTrip trip = storage.getObject(AFTrip.class, new Request(
                new Columns.All(),
                new Condition.Equals("id", tripId)));

        if (trip == null) {
            LOGGER.warn("Trip {} not found for device {}", tripId, position.getDeviceId());
            state.endTrip();
            return;
        }

        // Update trip with end information
        trip.setEndTime(position.getFixTime());
        trip.setEndPositionId(position.getId());

        // Calculate duration (milliseconds)
        long duration = trip.getEndTime().getTime() - trip.getStartTime().getTime();
        trip.setDuration(duration);

        // Set distance from accumulated distance
        Double accumulatedDistance = state.getAccumulatedDistance();
        if (accumulatedDistance != null) {
            trip.setDistance(accumulatedDistance);
        }

        // Save updated trip
        storage.updateObject(trip, new Request(
                new Columns.Exclude("id", "deviceId", "startTime", "startPositionId", "userId", "startOdometer"),
                new Condition.Equals("id", tripId)));

        LOGGER.info("Ended trip {} for device {} - distance: {} m, duration: {} ms",
                tripId, position.getDeviceId(), trip.getDistance(), duration);

        // Reset state
        state.endTrip();

        // Emit trip end event
        Event event = new Event(Event.TYPE_REALTIME_TRIP_END, position);
        event.set("tripId", tripId);
        event.set("distance", trip.getDistance());
        event.set("duration", duration);
        notificationManager.updateEvents(java.util.Map.of(event, position));
    }

}
