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
        boolean hasIgnition = position.getAttributes().containsKey(Position.KEY_IGNITION);
        boolean ignitionOn = hasIgnition && position.getBoolean(Position.KEY_IGNITION);

        boolean ignitionRequired = config.getBoolean(Keys.REALTIME_TRIP_IGNITION_REQUIRED);

        Boolean lastIgnition = state.getLastIgnitionState();
        Boolean lastMotion = state.getLastMotionState();

        // Cold start detection: first position or after server restart
        // If device is already moving/ignition on, start trip immediately
        if (lastIgnition == null && lastMotion == null) {
            if (ignitionRequired) {
                // Need ignition ON to start
                if (ignitionOn && isMoving) {
                    LOGGER.info("Trip start detected for device {} (cold start: ignition ON + moving)",
                            state.getDeviceId());
                    return true;
                }
            } else {
                // Just need motion
                if (isMoving) {
                    LOGGER.info("Trip start detected for device {} (cold start: moving)",
                            state.getDeviceId());
                    return true;
                }
            }
            // Not moving yet, wait for state transition
            return false;
        }

        // Normal state transition detection
        // Trip starts when:
        // 1. Ignition turns ON (if ignition required)
        // 2. Device starts moving (if ignition not required or ignition is already ON)
        if (ignitionRequired) {
            // Ignition-based detection: OFF -> ON transition
            if (lastIgnition != null && !lastIgnition && ignitionOn) {
                LOGGER.info("Trip start detected for device {} (ignition OFF -> ON)", state.getDeviceId());
                return true;
            }
        }

        // Motion-based detection: stopped -> moving transition
        if (lastMotion != null && !lastMotion && isMoving) {
            if (!ignitionRequired || ignitionOn) {
                LOGGER.info("Trip start detected for device {} (motion started)", state.getDeviceId());
                return true;
            }
        }

        return false;
    }

    /**
     * Update state with new position and determine if trip should end.
     *
     * Trip ends when:
     * 1. Ignition transitions from true -> false (immediate)
     * 2. Ignition stays true but distance=0 for more than minStopDuration (idle timeout)
     */
    public boolean shouldEndTrip(RealtimeTripState state, Position position) {
        // No active trip
        if (!state.hasActiveTrip()) {
            return false;
        }

        boolean hasIgnition = position.getAttributes().containsKey(Position.KEY_IGNITION);
        boolean ignitionOn = hasIgnition && position.getBoolean(Position.KEY_IGNITION);
        Boolean lastIgnition = state.getLastIgnitionState();

        // Condition 1: Ignition true -> false, end trip immediately
        if (lastIgnition != null && lastIgnition && !ignitionOn) {
            LOGGER.info("Trip end detected for device {} (ignition OFF)", state.getDeviceId());
            return true;
        }

        // Condition 2: Ignition stays true but idle (distance=0) for too long
        long minStopDuration = config.getLong(Keys.REALTIME_TRIP_MIN_STOP_DURATION);
        double distance = position.getDouble(Position.KEY_DISTANCE);

        // Consider idle if distance is 0 or very small (< 1 meter)
        boolean isIdle = distance < 1.0;

        if (isIdle) {
            if (state.getLastStopTime() == null) {
                // Just became idle, record the time
                state.setLastStopTime(position.getFixTime());
            } else {
                // Check idle duration
                long idleDuration = position.getFixTime().getTime() - state.getLastStopTime().getTime();
                if (idleDuration >= minStopDuration) {
                    LOGGER.info("Trip end detected for device {} (idle for {} ms, ignition still ON)",
                            state.getDeviceId(), idleDuration);
                    return true;
                }
            }
        } else {
            // Moving again, reset idle timer
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
        LOGGER.debug("getActiveTrips called: fromDate={}, toDate={}, deviceId={}, userId={}, totalStates={}",
                fromDate, toDate, deviceId, userId, deviceStates.size());

        return deviceStates.values().stream()
                .filter(state -> {
                    boolean hasActive = state.hasActiveTrip();
                    LOGGER.debug("Device {} hasActiveTrip: {}", state.getDeviceId(), hasActive);
                    return hasActive;
                })
                .map(RealtimeTripState::getCurrentTrip)
                .filter(trip -> {
                    // Filter by time range (startTime must be within range)
                    if (fromDate != null && trip.getStartTime().before(fromDate)) {
                        LOGGER.debug("Trip filtered out: startTime {} before fromDate {}",
                                trip.getStartTime(), fromDate);
                        return false;
                    }
                    if (toDate != null && trip.getStartTime().after(toDate)) {
                        LOGGER.debug("Trip filtered out: startTime {} after toDate {}",
                                trip.getStartTime(), toDate);
                        return false;
                    }
                    // Filter by deviceId
                    if (deviceId != null && trip.getDeviceId() != deviceId) {
                        LOGGER.debug("Trip filtered out: deviceId {} != {}",
                                trip.getDeviceId(), deviceId);
                        return false;
                    }
                    // Filter by userId - skip if trip has no userId set
                    if (userId != null && trip.getUserId() != null && !userId.equals(trip.getUserId())) {
                        LOGGER.debug("Trip filtered out: userId {} != {}",
                                trip.getUserId(), userId);
                        return false;
                    }
                    LOGGER.debug("Trip passed filters: deviceId={}, startTime={}",
                            trip.getDeviceId(), trip.getStartTime());
                    return true;
                })
                .collect(Collectors.toList());
    }

}
