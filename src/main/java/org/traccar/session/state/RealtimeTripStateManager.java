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
package org.traccar.session.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.model.AFTrip;
import org.traccar.model.Position;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages real-time trip states for all devices.
 * Maintains in-memory state to detect trip start/end conditions as position data arrives.
 */
@Singleton
public class RealtimeTripStateManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RealtimeTripStateManager.class);

    private final ConcurrentHashMap<Long, RealtimeTripState> deviceStates = new ConcurrentHashMap<>();
    private final Config config;

    @Inject
    public RealtimeTripStateManager(Config config) {
        this.config = config;
    }

    /**
     * Get or create trip state for a device
     */
    public RealtimeTripState getDeviceState(long deviceId) {
        return deviceStates.computeIfAbsent(deviceId, RealtimeTripState::new);
    }

    /**
     * Remove trip state for a device (e.g., when device is deleted)
     */
    public void removeDeviceState(long deviceId) {
        deviceStates.remove(deviceId);
    }

    /**
     * Update state with new position and determine if trip should start
     */
    public boolean shouldStartTrip(RealtimeTripState state, Position position) {
        // Already has active trip
        if (state.hasActiveTrip()) {
            return false;
        }

        // Check if position indicates motion
        boolean isMoving = position.getBoolean(Position.KEY_MOTION);
        boolean ignitionOn = position.getAttributes().containsKey(Position.KEY_IGNITION)
                && position.getBoolean(Position.KEY_IGNITION);

        boolean ignitionRequired = config.getBoolean(Keys.REALTIME_TRIP_IGNITION_REQUIRED);

        // Trip starts when:
        // 1. Ignition turns ON (if ignition required)
        // 2. Device starts moving (if ignition not required or ignition is already ON)
        if (ignitionRequired) {
            // Ignition-based detection
            Boolean lastIgnition = state.getLastIgnitionState();
            if (lastIgnition != null && !lastIgnition && ignitionOn) {
                // Ignition turned ON
                LOGGER.debug("Trip start detected for device {} (ignition ON)", state.getDeviceId());
                return true;
            }
        }

        // Motion-based detection
        Boolean lastMotion = state.getLastMotionState();
        if (lastMotion != null && !lastMotion && isMoving) {
            // Started moving
            if (!ignitionRequired || ignitionOn) {
                LOGGER.debug("Trip start detected for device {} (motion started)", state.getDeviceId());
                return true;
            }
        }

        return false;
    }

    /**
     * Update state with new position and determine if trip should end
     */
    public boolean shouldEndTrip(RealtimeTripState state, Position position) {
        // No active trip
        if (!state.hasActiveTrip()) {
            return false;
        }

        boolean isMoving = position.getBoolean(Position.KEY_MOTION);
        boolean ignitionOn = position.getAttributes().containsKey(Position.KEY_IGNITION)
                && position.getBoolean(Position.KEY_IGNITION);

        long minStopDuration = config.getLong(Keys.REALTIME_TRIP_MIN_STOP_DURATION);
        boolean ignitionRequired = config.getBoolean(Keys.REALTIME_TRIP_IGNITION_REQUIRED);

        // Trip ends when:
        // 1. Ignition OFF (if ignition required)
        // 2. Stopped for minimum duration

        if (ignitionRequired && !ignitionOn) {
            // Ignition turned OFF
            if (state.getLastStopTime() != null) {
                long stopDuration = position.getFixTime().getTime() - state.getLastStopTime().getTime();
                if (stopDuration >= minStopDuration) {
                    LOGGER.debug("Trip end detected for device {} (ignition OFF + stopped {} ms)",
                            state.getDeviceId(), stopDuration);
                    return true;
                }
            }
        }

        // Check if stopped for minimum duration
        if (!isMoving) {
            if (state.getLastStopTime() == null) {
                // Just stopped, record the time
                state.setLastStopTime(position.getFixTime());
            } else {
                // Check stop duration
                long stopDuration = position.getFixTime().getTime() - state.getLastStopTime().getTime();
                if (stopDuration >= minStopDuration) {
                    // If ignition not required, or ignition is OFF, end the trip
                    if (!ignitionRequired || !ignitionOn) {
                        LOGGER.debug("Trip end detected for device {} (stopped for {} ms)",
                                state.getDeviceId(), stopDuration);
                        return true;
                    }
                }
            }
        } else {
            // Moving again, reset stop time
            state.setLastStopTime(null);
        }

        return false;
    }

    /**
     * Update accumulated distance for ongoing trip
     */
    public void updateDistance(RealtimeTripState state, Position position) {
        if (!state.hasActiveTrip()) {
            return;
        }

        Position lastPos = state.getLastPosition();
        if (lastPos != null) {
            // Calculate distance from last position
            double distance = position.getDouble(Position.KEY_DISTANCE);
            if (distance > 0) {
                state.updateTripDistance(distance);
            }
        }
    }

    /**
     * Update state after processing position
     */
    public void updateState(RealtimeTripState state, Position position) {
        // Update last states
        if (position.getAttributes().containsKey(Position.KEY_IGNITION)) {
            state.setLastIgnitionState(position.getBoolean(Position.KEY_IGNITION));
        }
        if (position.getAttributes().containsKey(Position.KEY_MOTION)) {
            state.setLastMotionState(position.getBoolean(Position.KEY_MOTION));
        }
        state.setLastPosition(position);
    }

    /**
     * Check if trip meets minimum requirements (distance and duration)
     */
    public boolean meetsMinimumRequirements(RealtimeTripState state, Position endPosition) {
        if (!state.hasActiveTrip()) {
            return false;
        }

        AFTrip trip = state.getCurrentTrip();
        double minDistance = config.getDouble(Keys.REALTIME_TRIP_MIN_DISTANCE);
        long minDuration = config.getLong(Keys.REALTIME_TRIP_MIN_DURATION);

        // Check distance
        Double distance = trip.getDistance();
        if (distance == null || distance < minDistance) {
            LOGGER.debug("Trip for device {} does not meet minimum distance ({} < {})",
                    state.getDeviceId(), distance, minDistance);
            return false;
        }

        // Check duration
        long duration = endPosition.getFixTime().getTime() - trip.getStartTime().getTime();
        if (duration < minDuration) {
            LOGGER.debug("Trip for device {} does not meet minimum duration ({} < {})",
                    state.getDeviceId(), duration, minDuration);
            return false;
        }

        return true;
    }

    /**
     * Get all active trips in memory, optionally filtered by time range, device, and user
     */
    public List<AFTrip> getActiveTrips(Date fromDate, Date toDate, Long deviceId, Long userId) {
        return deviceStates.values().stream()
                .filter(state -> state.hasActiveTrip())
                .map(RealtimeTripState::getCurrentTrip)
                .filter(trip -> {
                    // Filter by time range (startTime must be within range)
                    if (fromDate != null && trip.getStartTime().before(fromDate)) {
                        return false;
                    }
                    if (toDate != null && trip.getStartTime().after(toDate)) {
                        return false;
                    }
                    // Filter by deviceId
                    if (deviceId != null && trip.getDeviceId() != deviceId) {
                        return false;
                    }
                    // Filter by userId
                    if (userId != null && !userId.equals(trip.getUserId())) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

}
