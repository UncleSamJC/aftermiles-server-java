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

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.traccar.api.BaseObjectResource;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.database.MediaManager;
import org.traccar.helper.LogAction;
import org.traccar.helper.MediaUploadHelper;
import org.traccar.model.Device;
import org.traccar.model.MaintenanceLog;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Path("maintenance-logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MaintenanceLogResource extends BaseObjectResource<MaintenanceLog> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_PHOTOS = 10;

    @Inject
    private Config config;

    @Inject
    private MediaManager mediaManager;

    @Inject
    private LogAction actionLogger;

    @Context
    private HttpServletRequest request;

    public MaintenanceLogResource() {
        super(MaintenanceLog.class);
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response create(
            @FormDataParam("deviceId") long deviceId,
            @FormDataParam("date") String dateStr,
            @FormDataParam("serviceCompleted") String serviceCompleted,
            @FormDataParam("completedBy") String completedBy,
            @FormDataParam("notes") String notes,
            @FormDataParam("photos") List<FormDataBodyPart> photoBodyParts) throws Exception {

        // Validate device permission
        permissionsService.checkPermission(Device.class, getUserId(), deviceId);
        Device device = storage.getObject(Device.class, new Request(
                new Columns.All(), new Condition.Equals("id", deviceId)));
        if (device == null) {
            return buildErrorResponse("DEVICE_NOT_FOUND", "Device not found", null);
        }

        // Validate required fields
        if (serviceCompleted == null || serviceCompleted.trim().isEmpty()) {
            Map<String, String> details = new HashMap<>();
            details.put("serviceCompleted", "Service completed is required");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }
        if (serviceCompleted.length() > 4000) {
            Map<String, String> details = new HashMap<>();
            details.put("serviceCompleted", "Service completed must not exceed 4000 characters");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }

        if (completedBy == null || completedBy.trim().isEmpty()) {
            Map<String, String> details = new HashMap<>();
            details.put("completedBy", "Completed by is required");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }
        if (completedBy.length() > 255) {
            Map<String, String> details = new HashMap<>();
            details.put("completedBy", "Completed by must not exceed 255 characters");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }

        // Validate and parse date
        Date maintenanceDate;
        try {
            LocalDate localDate = LocalDate.parse(dateStr, DATE_FORMAT);
            maintenanceDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            if (maintenanceDate.after(new Date())) {
                Map<String, String> details = new HashMap<>();
                details.put("date", "Date cannot be in the future");
                return buildErrorResponse("FUTURE_DATE_ERROR", "Invalid date", details);
            }
        } catch (DateTimeParseException e) {
            Map<String, String> details = new HashMap<>();
            details.put("date", "Date must be in format YYYY-MM-DD");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }

        // Validate notes length
        if (notes != null && notes.length() > 4000) {
            Map<String, String> details = new HashMap<>();
            details.put("notes", "Notes must not exceed 4000 characters");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }

        // Create maintenance log entity
        MaintenanceLog maintenanceLog = new MaintenanceLog();
        maintenanceLog.setDeviceId(deviceId);
        maintenanceLog.setDate(maintenanceDate);
        maintenanceLog.setServiceCompleted(serviceCompleted);
        maintenanceLog.setCompletedBy(completedBy);
        maintenanceLog.setNotes(notes);
        maintenanceLog.setCreatedByUserId(getUserId());
        maintenanceLog.setCreatedTime(new Date());
        maintenanceLog.setModifiedTime(new Date());

        // Handle photo uploads (optional)
        List<String> photoPaths = new ArrayList<>();
        if (photoBodyParts != null && !photoBodyParts.isEmpty()) {
            // Validate photo count
            if (photoBodyParts.size() > MAX_PHOTOS) {
                Map<String, String> details = new HashMap<>();
                details.put("photos", "Maximum " + MAX_PHOTOS + " photos allowed");
                return buildErrorResponse("VALIDATION_ERROR", "Too many photos", details);
            }

            // Validate all photo types and extract InputStreams from FormDataBodyPart
            List<InputStream> photoStreams = new ArrayList<>();
            List<String> extensions = new ArrayList<>();

            for (int i = 0; i < photoBodyParts.size(); i++) {
                FormDataBodyPart bodyPart = photoBodyParts.get(i);

                // Validate content type
                String contentType = bodyPart.getMediaType().toString();
                if (!MediaUploadHelper.isValidImageType(config, contentType, Keys.EXPENSE_ALLOWED_TYPES)) {
                    Map<String, String> details = new HashMap<>();
                    details.put("photos", "Photo " + i + ": Only JPEG, JPG and PNG formats are supported. Received: "
                            + contentType);
                    return buildErrorResponse("INVALID_FILE_TYPE", "Invalid file type", details);
                }

                // Extract InputStream from FormDataBodyPart (correct way for multipart)
                photoStreams.add(bodyPart.getValueAs(InputStream.class));

                // Extract file extension from ContentDisposition
                String fileName = bodyPart.getContentDisposition().getFileName();
                extensions.add(MediaUploadHelper.getFileExtension(fileName));
            }

            // Upload all photos
            int maxFileSize = config.getInteger(Keys.EXPENSE_FILE_SIZE_LIMIT);
            List<MediaUploadHelper.UploadResult> uploadResults = MediaUploadHelper.uploadMultipleFiles(
                    mediaManager,
                    photoStreams,
                    device.getUniqueId(),
                    maintenanceDate,
                    "maintenance",
                    extensions,
                    maxFileSize);

            // Check for upload errors
            for (MediaUploadHelper.UploadResult result : uploadResults) {
                if (!result.isSuccess()) {
                    Map<String, String> details = new HashMap<>();
                    details.put("photos", result.getErrorMessage());
                    return buildErrorResponse(result.getErrorCode(), result.getErrorMessage(), details);
                }
                photoPaths.add(result.getRelativePath());
            }
        }

        // Set photo paths (comma-separated or null if no photos)
        if (!photoPaths.isEmpty()) {
            maintenanceLog.updatePhotosFromList(photoPaths);
        }

        // Save to database
        maintenanceLog.setId(storage.addObject(maintenanceLog, new Request(
                new Columns.Exclude("id"))));
        actionLogger.create(request, getUserId(), maintenanceLog);

        // Build success response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);

        Map<String, Object> data = new HashMap<>();
        data.put("id", maintenanceLog.getId());
        data.put("deviceId", String.valueOf(maintenanceLog.getDeviceId()));
        data.put("date", maintenanceLog.getDate().toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT));
        data.put("serviceCompleted", maintenanceLog.getServiceCompleted());
        data.put("completedBy", maintenanceLog.getCompletedBy());
        data.put("notes", maintenanceLog.getNotes());
        data.put("photos", buildPhotosArray(maintenanceLog));
        data.put("createdAt", maintenanceLog.getCreatedTime().toInstant().toString());

        response.put("data", data);
        response.put("message", "Maintenance log created successfully");

        return Response.ok(response).build();
    }

    @GET
    public Response query(
            @QueryParam("deviceId") long deviceId,
            @QueryParam("year") Integer year) throws StorageException {

        // Validate required parameters
        if (deviceId <= 0) {
            return buildErrorResponse("MISSING_PARAMETERS", "deviceId is required", null);
        }
        if (year == null) {
            return buildErrorResponse("MISSING_PARAMETERS", "year is required", null);
        }

        // Check device permission
        permissionsService.checkPermission(Device.class, getUserId(), deviceId);

        // Build date range for the year: YYYY-01-01 00:00:00 to YYYY-12-31 23:59:59
        Calendar startCal = Calendar.getInstance();
        startCal.set(year, Calendar.JANUARY, 1, 0, 0, 0);
        startCal.set(Calendar.MILLISECOND, 0);
        Date startDate = startCal.getTime();

        Calendar endCal = Calendar.getInstance();
        endCal.set(year, Calendar.DECEMBER, 31, 23, 59, 59);
        endCal.set(Calendar.MILLISECOND, 999);
        Date endDate = endCal.getTime();

        // Query maintenance logs for this device and year
        var conditions = new LinkedList<Condition>();
        conditions.add(new Condition.Equals("deviceId", deviceId));
        conditions.add(new Condition.Between("date", startDate, endDate));

        // Permission control: non-admin users can only view their own maintenance logs
        if (permissionsService.notAdmin(getUserId())) {
            conditions.add(new Condition.Equals("createdByUserId", getUserId()));
        }

        // Get all matching records (returns immutable list)
        List<MaintenanceLog> immutableLogs = storage.getObjects(baseClass, new Request(
                new Columns.All(),
                Condition.merge(conditions)));

        // Create mutable copy for sorting
        List<MaintenanceLog> logs = new ArrayList<>(immutableLogs);

        // Sort by date descending (newest first)
        logs.sort((a, b) -> b.getDate().compareTo(a.getDate()));

        // Convert to response format
        List<Map<String, Object>> results = new ArrayList<>();
        for (MaintenanceLog log : logs) {
            Map<String, Object> logData = new HashMap<>();
            logData.put("id", log.getId());
            logData.put("deviceId", String.valueOf(log.getDeviceId()));
            logData.put("date", log.getDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT));
            logData.put("serviceCompleted", log.getServiceCompleted());
            logData.put("completedBy", log.getCompletedBy());
            logData.put("notes", log.getNotes());
            logData.put("photos", buildPhotosArray(log));
            logData.put("createdAt", log.getCreatedTime().toInstant().toString());
            results.add(logData);
        }

        // Build success response with wrapper
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", results);

        return Response.ok(response).build();
    }

    @GET
    @Path("{logId}/photos/{index}")
    @Produces("image/*")
    public Response getPhoto(
            @PathParam("logId") long logId,
            @PathParam("index") int index) throws StorageException {

        // Retrieve maintenance log
        MaintenanceLog log = storage.getObject(baseClass, new Request(
                new Columns.All(),
                new Condition.Equals("id", logId)));

        if (log == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Maintenance log not found").build();
        }

        // Check permission for the device
        permissionsService.checkPermission(Device.class, getUserId(), log.getDeviceId());

        // Get photos list
        List<String> photoPaths = log.extractPhotosList();

        // Validate index
        if (index < 0 || index >= photoPaths.size()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Photo not found").build();
        }

        // Get photo path at index
        String photoPath = photoPaths.get(index);

        // Construct file path
        String mediaPath = config.getString(Keys.MEDIA_PATH);
        File file = new File(mediaPath, photoPath);

        if (!file.exists()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Photo file not found").build();
        }

        // Extract filename for Content-Disposition header
        String fileName = photoPath.substring(photoPath.lastIndexOf('/') + 1);

        return Response.ok(file)
                .header("Content-Disposition", "inline; filename=\"" + fileName + "\"")
                .build();
    }

    /**
     * Helper method to build photos array for API response.
     * Generates URL and fileName for each photo.
     */
    private List<Map<String, String>> buildPhotosArray(MaintenanceLog maintenanceLog) {
        List<Map<String, String>> photos = new ArrayList<>();
        List<String> photoPaths = maintenanceLog.extractPhotosList();

        for (int i = 0; i < photoPaths.size(); i++) {
            String path = photoPaths.get(i);
            String fileName = path.substring(path.lastIndexOf('/') + 1);

            Map<String, String> photo = new HashMap<>();
            photo.put("url", "/api/maintenance-logs/" + maintenanceLog.getId() + "/photos/" + i);
            photo.put("fileName", fileName);
            photos.add(photo);
        }

        return photos;
    }

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
