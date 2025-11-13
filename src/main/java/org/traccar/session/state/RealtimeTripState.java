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

    // Current ongoing trip ID (null if no active trip)
    private Long currentTripId;

    public Long getCurrentTripId() {
        return currentTripId;
    }

    public void setCurrentTripId(Long currentTripId) {
        this.currentTripId = currentTripId;
    }

    public boolean hasActiveTrip() {
        return currentTripId != null;
    }

    // Trip start information
    private Date tripStartTime;

    public Date getTripStartTime() {
        return tripStartTime;
    }

    public void setTripStartTime(Date tripStartTime) {
        this.tripStartTime = tripStartTime;
    }

    private Long tripStartPositionId;

    public Long getTripStartPositionId() {
        return tripStartPositionId;
    }

    public void setTripStartPositionId(Long tripStartPositionId) {
        this.tripStartPositionId = tripStartPositionId;
    }

    private Double startOdometer;

    public Double getStartOdometer() {
        return startOdometer;
    }

    public void setStartOdometer(Double startOdometer) {
        this.startOdometer = startOdometer;
    }

    // Motion tracking
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

    private Double accumulatedDistance;

    public Double getAccumulatedDistance() {
        return accumulatedDistance;
    }

    public void setAccumulatedDistance(Double accumulatedDistance) {
        this.accumulatedDistance = accumulatedDistance;
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
     * Reset state when starting a new trip
     */
    public void startNewTrip(long tripId, Position startPosition) {
        this.currentTripId = tripId;
        this.tripStartTime = startPosition.getFixTime();
        this.tripStartPositionId = startPosition.getId();
        this.startOdometer = startPosition.getDouble(Position.KEY_ODOMETER);
        this.accumulatedDistance = 0.0;
        this.lastStopTime = null;
        this.lastPosition = startPosition;
    }

    /**
     * Reset state when ending a trip
     */
    public void endTrip() {
        this.currentTripId = null;
        this.tripStartTime = null;
        this.tripStartPositionId = null;
        this.startOdometer = null;
        this.accumulatedDistance = null;
        this.lastStopTime = null;
    }

}
