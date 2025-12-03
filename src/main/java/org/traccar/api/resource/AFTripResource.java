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
package org.traccar.api.resource;

import org.traccar.api.BaseObjectResource;
import org.traccar.helper.LogAction;
import org.traccar.model.AFTrip;
import org.traccar.model.Device;
import org.traccar.model.Position;
import org.traccar.session.state.RealtimeTripStateManager;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Path("realtimetrips")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AFTripResource extends BaseObjectResource<AFTrip> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AFTripResource.class);
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Inject
    private LogAction actionLogger;

    @Inject
    private RealtimeTripStateManager stateManager;

    @Context
    private HttpServletRequest request;

    public AFTripResource() {
        super(AFTrip.class);
    }

    /**
     * Get trips by time range and optional deviceId filter.
     *
     * Examples:
     * /api/realtimetrips?from=2025-01-12T00:00&to=2025-01-12T23:59
     * /api/realtimetrips?from=2025-01-10T00:00&to=2025-01-12T23:59&deviceId=123
     */
    @GET
    public Response query(
            @QueryParam("from") String fromStr,
            @QueryParam("to") String toStr,
            @QueryParam("deviceId") Long deviceId) throws StorageException {

        // Validate required parameters
        if (fromStr == null || fromStr.trim().isEmpty()) {
            return buildErrorResponse(
                    "MISSING_PARAMETERS",
                    "from parameter is required (format: YYYY-MM-DDTHH:mm)",
                    null);
        }
        if (toStr == null || toStr.trim().isEmpty()) {
            return buildErrorResponse(
                    "MISSING_PARAMETERS",
                    "to parameter is required (format: YYYY-MM-DDTHH:mm)",
                    null);
        }

        // Parse date range
        Date fromDate;
        Date toDate;
        try {
            LocalDateTime fromDateTime = LocalDateTime.parse(fromStr, DATETIME_FORMAT);
            fromDate = Date.from(fromDateTime.atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            return buildErrorResponse(
                    "INVALID_DATE_FORMAT",
                    "Invalid from date format. Expected: YYYY-MM-DDTHH:mm (e.g., 2025-01-12T08:30)",
                    null);
        }

        try {
            LocalDateTime toDateTime = LocalDateTime.parse(toStr, DATETIME_FORMAT);
            toDate = Date.from(toDateTime.atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            return buildErrorResponse(
                    "INVALID_DATE_FORMAT",
                    "Invalid to date format. Expected: YYYY-MM-DDTHH:mm (e.g., 2025-01-12T23:59)",
                    null);
        }

        // Validate date range
        if (fromDate.after(toDate)) {
            return buildErrorResponse(
                    "INVALID_DATE_RANGE",
                    "from date must be before or equal to to date",
                    null);
        }

        // Build query conditions
        var conditions = new LinkedList<Condition>();

        // Add device filter if provided
        if (deviceId != null && deviceId > 0) {
            // Check device permission
            permissionsService.checkPermission(Device.class, getUserId(), deviceId);
            conditions.add(new Condition.Equals("deviceId", deviceId));
        }

        // Add time range filter
        conditions.add(new Condition.Between("startTime", fromDate, toDate));

        // Permission control: non-admin users can only view their own trips
        if (permissionsService.notAdmin(getUserId())) {
            conditions.add(new Condition.Equals("userId", getUserId()));
        }

        // Get all matching trips from database (returns immutable list)
        List<AFTrip> immutableTrips = storage.getObjects(baseClass, new Request(
                new Columns.All(),
                Condition.merge(conditions)));

        // Create mutable copy for merging
        List<AFTrip> trips = new ArrayList<>(immutableTrips);

        // Get active trips from memory and merge
        Long userIdFilter = permissionsService.notAdmin(getUserId()) ? getUserId() : null;
        List<AFTrip> activeTrips = stateManager.getActiveTrips(fromDate, toDate, deviceId, userIdFilter);
        trips.addAll(activeTrips);

        // Sort by startTime descending (newest first)
        trips.sort((a, b) -> b.getStartTime().compareTo(a.getStartTime()));

        // Convert to response format
        List<Map<String, Object>> results = new ArrayList<>();
        for (AFTrip trip : trips) {
            results.add(buildTripData(trip));
        }

        // Build success response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", results);
        response.put("count", results.size());
        response.put("from", fromStr);
        response.put("to", toStr);

        return Response.ok(response).build();
    }

    /**
     * Get single trip by ID.
     *
     * Example: /api/realtimetrips/123
     */
    @GET
    @Path("{id}")
    @Override
    public Response getSingle(@PathParam("id") long id) throws StorageException {

        // Retrieve trip
        AFTrip trip = storage.getObject(baseClass, new Request(
                new Columns.All(),
                new Condition.Equals("id", id)));

        if (trip == null) {
            return buildErrorResponse("TRIP_NOT_FOUND", "Trip not found", null);
        }

        // Check permission for the device
        permissionsService.checkPermission(Device.class, getUserId(), trip.getDeviceId());

        // Permission control: non-admin users can only view their own trips
        if (permissionsService.notAdmin(getUserId()) && !trip.getUserId().equals(getUserId())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(buildErrorResponse(
                            "PERMISSION_DENIED",
                            "You don't have permission to view this trip",
                            null))
                    .build();
        }

        // Build success response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", buildTripData(trip));

        return Response.ok(response).build();
    }

    /**
     * Helper method to build trip data for API response.
     */
    private Map<String, Object> buildTripData(AFTrip trip) {
        Map<String, Object> tripData = new HashMap<>();
        tripData.put("id", trip.getId());
        tripData.put("deviceId", trip.getDeviceId());
        tripData.put("userId", trip.getUserId());
        tripData.put("startTime", trip.getStartTime().toInstant().toString());
        tripData.put("endTime", trip.getEndTime() != null ? trip.getEndTime().toInstant().toString() : null);
        tripData.put("startPositionId", trip.getStartPositionId());
        tripData.put("endPositionId", trip.getEndPositionId());
        tripData.put("startOdometer", trip.getStartOdometer());
        tripData.put("distance", trip.getDistance());
        tripData.put("duration", trip.getDuration());
        tripData.put("startAddress", trip.getStartAddress());
        tripData.put("endAddress", trip.getEndAddress());

        // Get start position lat/lon
        if (trip.getStartPositionId() != null && trip.getStartPositionId() > 0) {
            try {
                Position startPos = storage.getObject(Position.class, new Request(
                        new Columns.All(), new Condition.Equals("id", trip.getStartPositionId())));
                if (startPos != null) {
                    tripData.put("startLat", startPos.getLatitude());
                    tripData.put("startLon", startPos.getLongitude());
                }
            } catch (StorageException e) {
                LOGGER.warn("Failed to get start position for trip {}", trip.getId(), e);
            }
        }

        // Get end position lat/lon
        if (trip.getEndPositionId() != null && trip.getEndPositionId() > 0) {
            try {
                Position endPos = storage.getObject(Position.class, new Request(
                        new Columns.All(), new Condition.Equals("id", trip.getEndPositionId())));
                if (endPos != null) {
                    tripData.put("endLat", endPos.getLatitude());
                    tripData.put("endLon", endPos.getLongitude());
                }
            } catch (StorageException e) {
                LOGGER.warn("Failed to get end position for trip {}", trip.getId(), e);
            }
        }

        // Calculate status
        String status = trip.getEndTime() == null ? "active" : "completed";
        tripData.put("status", status);

        return tripData;
    }

    /**
     * Helper method to build error response.
     */
    private Response buildErrorResponse(String code, String message, Map<String, String> details) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);

        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (details != null) {
            error.put("details", details);
        }

        response.put("error", error);
        return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
    }

}
