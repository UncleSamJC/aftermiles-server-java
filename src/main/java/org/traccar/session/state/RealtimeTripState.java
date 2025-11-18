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

import org.traccar.model.AFTrip;
import org.traccar.model.Position;

import java.util.Date;

/**
 * Tracks the real-time trip state for a device.
 * This state is maintained in memory and used to detect trip start/end events as position data arrives.
 */
public class RealtimeTripState {

    private final long deviceId;

    public RealtimeTripState(long deviceId) {
        this.deviceId = deviceId;
    }

    public long getDeviceId() {
        return deviceId;
    }

    // Current ongoing trip object (null if no active trip)
    private AFTrip currentTrip;

    public AFTrip getCurrentTrip() {
        return currentTrip;
    }

    public void setCurrentTrip(AFTrip currentTrip) {
        this.currentTrip = currentTrip;
    }

    public boolean hasActiveTrip() {
        return currentTrip != null;
    }

    // Motion tracking (for trip detection)
    private Boolean lastIgnitionState;

    public Boolean getLastIgnitionState() {
        return lastIgnitionState;
    }

    public void setLastIgnitionState(Boolean lastIgnitionState) {
        this.lastIgnitionState = lastIgnitionState;
    }

    private Boolean lastMotionState;

    public Boolean getLastMotionState() {
        return lastMotionState;
    }

    public void setLastMotionState(Boolean lastMotionState) {
        this.lastMotionState = lastMotionState;
    }

    // Stop tracking (for detecting trip end)
    private Date lastStopTime;

    public Date getLastStopTime() {
        return lastStopTime;
    }

    public void setLastStopTime(Date lastStopTime) {
        this.lastStopTime = lastStopTime;
    }

    // Last processed position (for calculating distance)
    private Position lastPosition;

    public Position getLastPosition() {
        return lastPosition;
    }

    public void setLastPosition(Position lastPosition) {
        this.lastPosition = lastPosition;
    }

    /**
     * Start a new trip with the given AFTrip object
     */
    public void startNewTrip(AFTrip trip, Position startPosition) {
        this.currentTrip = trip;
        this.lastStopTime = null;
        this.lastPosition = startPosition;
    }

    /**
     * Update accumulated distance for ongoing trip
     */
    public void updateTripDistance(double distanceIncrement) {
        if (currentTrip != null) {
            Double current = currentTrip.getDistance();
            currentTrip.setDistance((current != null ? current : 0.0) + distanceIncrement);
        }
    }

    /**
     * Reset state when ending a trip
     */
    public void endTrip() {
        this.currentTrip = null;
        this.lastStopTime = null;
    }

}
