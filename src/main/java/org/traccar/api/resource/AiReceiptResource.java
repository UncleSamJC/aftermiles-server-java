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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.ai.AiReceiptProcessor;
import org.traccar.api.BaseResource;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.database.MediaManager;
import org.traccar.helper.LogAction;
import org.traccar.helper.MediaUploadHelper;
import org.traccar.model.AiReceiptBatch;
import org.traccar.model.AiReceiptBatchItem;
import org.traccar.model.Device;
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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("ai-receipts/batches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AiReceiptResource extends BaseResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiReceiptResource.class);

    @Inject
    private Config config;

    @Inject
    private MediaManager mediaManager;

    @Inject
    private LogAction actionLogger;

    @Inject
    private AiReceiptProcessor aiReceiptProcessor;

    @Context
    private HttpServletRequest request;

    /**
     * Create a new AI receipt processing batch.
     * Uploads receipt images and creates batch for processing.
     *
     * Stores batch and items in database, then submits to async processing queue.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response createBatch(
            @FormDataParam("deviceId") long deviceId,
            @FormDataParam("receipts") List<FormDataBodyPart> receiptBodyParts) throws Exception {

        // Debug logging for frontend integration
        LOGGER.info("=== AI Receipt Batch Upload Request ===");
        LOGGER.info("DeviceId received: {}", deviceId);
        LOGGER.info("Content-Type: {}", request.getContentType());
        LOGGER.info("Receipt parts count: {}", receiptBodyParts != null ? receiptBodyParts.size() : "null");
        if (receiptBodyParts != null) {
            for (int i = 0; i < receiptBodyParts.size(); i++) {
                FormDataBodyPart part = receiptBodyParts.get(i);
                LOGGER.info("Receipt[{}]: mediaType={}, fileName={}, size={}",
                        i,
                        part.getMediaType(),
                        part.getContentDisposition().getFileName(),
                        part.getContentDisposition().getSize());
            }
        }
        LOGGER.info("========================================");

        try {
            // Validate device permission
            LOGGER.info("Step 1: Checking device permission for userId={}, deviceId={}", getUserId(), deviceId);
            permissionsService.checkPermission(Device.class, getUserId(), deviceId);
            LOGGER.info("Step 1: Permission check passed");

            LOGGER.info("Step 2: Fetching device from storage");
            Device device = storage.getObject(Device.class, new Request(
                    new Columns.All(), new Condition.Equals("id", deviceId)));
            if (device == null) {
                LOGGER.warn("Step 2: Device not found with id={}", deviceId);
                return buildErrorResponse("DEVICE_NOT_FOUND", "Device not found", null);
            }
            LOGGER.info("Step 2: Device found: {}", device.getName());

            // Validate receipts are provided
            LOGGER.info("Step 3: Validating receipts");
        if (receiptBodyParts == null || receiptBodyParts.isEmpty()) {
            Map<String, String> details = new HashMap<>();
            details.put("receipts", "At least one receipt image is required");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }

        // Validate batch size
        int maxBatchSize = config.getInteger(Keys.AI_RECEIPT_MAX_BATCH_SIZE);
        if (receiptBodyParts.size() > maxBatchSize) {
            Map<String, String> details = new HashMap<>();
            details.put("receipts", "Maximum " + maxBatchSize + " receipts allowed per batch");
            return buildErrorResponse("VALIDATION_ERROR", "Too many receipts", details);
        }

        // Validate all receipt types and extract InputStreams
            LOGGER.info("Step 4: Validating file types and extracting streams");
        List<InputStream> receiptStreams = new ArrayList<>();
        List<String> extensions = new ArrayList<>();

        for (int i = 0; i < receiptBodyParts.size(); i++) {
            FormDataBodyPart bodyPart = receiptBodyParts.get(i);

            // Validate content type
            String contentType = bodyPart.getMediaType().toString();
            if (!MediaUploadHelper.isValidImageType(config, contentType, Keys.EXPENSE_ALLOWED_TYPES)) {
                Map<String, String> details = new HashMap<>();
                details.put("receipts", "Receipt " + i + ": Only JPEG, JPG and PNG formats are supported. Received: "
                        + contentType);
                return buildErrorResponse("INVALID_FILE_TYPE", "Invalid file type", details);
            }

            // Extract InputStream from FormDataBodyPart
            receiptStreams.add(bodyPart.getValueAs(InputStream.class));

            // Extract file extension from ContentDisposition
            String fileName = bodyPart.getContentDisposition().getFileName();
            extensions.add(MediaUploadHelper.getFileExtension(fileName));
        }

        // Upload all receipts
            LOGGER.info("Step 5: Uploading {} receipt files", receiptStreams.size());
        int maxFileSize = config.getInteger(Keys.EXPENSE_FILE_SIZE_LIMIT);
        List<MediaUploadHelper.UploadResult> uploadResults = MediaUploadHelper.uploadMultipleFiles(
                mediaManager,
                receiptStreams,
                device.getUniqueId(),
                new Date(),
                "ai-receipts",
                extensions,
                maxFileSize);

        // Check for upload errors
        List<String> receiptPaths = new ArrayList<>();
        for (int i = 0; i < uploadResults.size(); i++) {
            MediaUploadHelper.UploadResult result = uploadResults.get(i);
            if (!result.isSuccess()) {
                    LOGGER.error("Step 5: Upload failed for receipt {}: {}", i, result.getErrorMessage());
                Map<String, String> details = new HashMap<>();
                details.put("receipts", result.getErrorMessage());
                return buildErrorResponse(result.getErrorCode(), result.getErrorMessage(), details);
            }
                LOGGER.info("Step 5: Receipt {} uploaded successfully: {}", i, result.getRelativePath());
            receiptPaths.add(result.getRelativePath());
        }
            LOGGER.info("Step 5: All {} receipts uploaded successfully", receiptPaths.size());

        // Create batch entity
            LOGGER.info("Step 6: Creating batch entity");
        AiReceiptBatch batch = new AiReceiptBatch();
        batch.setDeviceId(deviceId);
        batch.setUserId(getUserId());
        batch.setStatus(AiReceiptBatch.STATUS_PENDING);
        batch.setTotalReceipts(receiptPaths.size());
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        batch.setLowConfidenceCount(0);
        batch.setCreatedTime(new Date());

            LOGGER.info("Step 6: Batch entity created - deviceId={}, userId={}, totalReceipts={}",
                    batch.getDeviceId(), batch.getUserId(), batch.getTotalReceipts());

        // Save batch to database
            LOGGER.info("Step 6: Saving batch to database");
        batch.setId(storage.addObject(batch, new Request(
                new Columns.Exclude("id", "startedTime", "completedTime"))));
            LOGGER.info("Step 6: Batch saved with id={}", batch.getId());
        actionLogger.create(request, getUserId(), batch);

        // Create batch items
            LOGGER.info("Step 7: Creating batch items for {} receipts", receiptPaths.size());
        List<Long> itemIds = new ArrayList<>();
        for (int i = 0; i < receiptPaths.size(); i++) {
            String receiptPath = receiptPaths.get(i);
            AiReceiptBatchItem item = new AiReceiptBatchItem();
            item.setBatchId(batch.getId());
            item.setReceiptImagePath(receiptPath);
            item.setStatus(AiReceiptBatchItem.STATUS_PENDING);
            item.setCreatedTime(new Date());
            // Explicitly set optional fields to null
            item.setConfidence(null);
            item.setExpenseId(null);
            item.setErrorMessage(null);
            item.setRawAiResponse(null);
            item.setProcessedTime(null);

                LOGGER.info("Step 7.{}: Saving batch item - batchId={}, status={}, confidence={}",
                        i + 1, item.getBatchId(), item.getStatus(), item.getConfidence());

            item.setId(storage.addObject(item, new Request(
                    new Columns.Exclude("id", "confidence", "expenseId", "errorMessage",
                            "rawAiResponse", "processedTime"))));
                LOGGER.info("Step 7.{}: Batch item saved with id={}", i + 1, item.getId());
            itemIds.add(item.getId());
        }

        // Submit batch to async processing queue
        aiReceiptProcessor.submitBatch(batch.getId());

        // Build success response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);

        Map<String, Object> data = new HashMap<>();
        data.put("batchId", batch.getId());
        data.put("deviceId", batch.getDeviceId());  // Return as number, not string
        data.put("status", batch.getStatus());
        data.put("totalReceipts", batch.getTotalReceipts());
        data.put("createdAt", batch.getCreatedTime().toInstant().toString());
        data.put("itemIds", itemIds);

        response.put("data", data);
        response.put("message", "Batch created successfully. Processing will begin shortly.");

            LOGGER.info("Batch created successfully: batchId={}", batch.getId());
            return Response.ok(response).build();

        } catch (StorageException e) {
            LOGGER.error("Storage error in createBatch", e);
            return buildErrorResponse("STORAGE_ERROR", "Database error: " + e.getMessage(), null);
        } catch (SecurityException e) {
            LOGGER.error("Permission denied in createBatch", e);
            return buildErrorResponse("PERMISSION_DENIED", e.getMessage(), null);
        } catch (Exception e) {
            LOGGER.error("Unexpected error in createBatch", e);
            return buildErrorResponse("INTERNAL_ERROR", "An unexpected error occurred: " + e.getMessage(), null);
        }
    }

    /**
     * Get status of an AI receipt processing batch.
     * Returns batch details including processing status and results.
     */
    @GET
    @Path("{batchId}")
    public Response getBatchStatus(@PathParam("batchId") long batchId) throws StorageException {

        // Retrieve batch
        AiReceiptBatch batch = storage.getObject(AiReceiptBatch.class, new Request(
                new Columns.All(),
                new Condition.Equals("id", batchId)));

        if (batch == null) {
            return buildErrorResponse("BATCH_NOT_FOUND", "Batch not found", null);
        }

        // Check permission for the device
        permissionsService.checkPermission(Device.class, getUserId(), batch.getDeviceId());

        // Permission control: non-admin users can only view their own batches
        if (permissionsService.notAdmin(getUserId()) && batch.getUserId() != getUserId()) {
            return buildErrorResponse("PERMISSION_DENIED", "Access denied", null);
        }

        // Retrieve batch items
        List<AiReceiptBatchItem> items = storage.getObjects(AiReceiptBatchItem.class, new Request(
                new Columns.All(),
                new Condition.Equals("batchId", batchId)));

        // Build items response
        List<Map<String, Object>> itemsData = new ArrayList<>();
        for (AiReceiptBatchItem item : items) {
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("id", item.getId());
            itemData.put("receiptImagePath", item.getReceiptImagePath());
            itemData.put("status", item.getStatus());
            itemData.put("confidence", item.getConfidence());
            itemData.put("expenseId", item.getExpenseId());
            itemData.put("errorMessage", item.getErrorMessage());
            itemData.put("processedTime", item.getProcessedTime() != null
                    ? item.getProcessedTime().toInstant().toString() : null);
            itemsData.add(itemData);
        }

        // Build success response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);

        Map<String, Object> data = new HashMap<>();
        data.put("batchId", batch.getId());
        data.put("deviceId", batch.getDeviceId());  // Return as number, not string
        data.put("userId", batch.getUserId());      // Return as number, not string
        data.put("status", batch.getStatus());
        data.put("totalReceipts", batch.getTotalReceipts());
        data.put("successCount", batch.getSuccessCount());
        data.put("failedCount", batch.getFailedCount());
        data.put("lowConfidenceCount", batch.getLowConfidenceCount());
        data.put("createdTime", batch.getCreatedTime().toInstant().toString());
        data.put("startedTime", batch.getStartedTime() != null
                ? batch.getStartedTime().toInstant().toString() : null);
        data.put("completedTime", batch.getCompletedTime() != null
                ? batch.getCompletedTime().toInstant().toString() : null);
        data.put("items", itemsData);

        response.put("data", data);

        return Response.ok(response).build();
    }

    /**
     * Get receipt image for a batch item.
     * Returns the receipt image file for viewing/download.
     */
    @GET
    @Path("{batchId}/items/{itemId}/receipt")
    @Produces("image/*")
    public Response getReceiptImage(
            @PathParam("batchId") long batchId,
            @PathParam("itemId") long itemId) throws StorageException {

        // Retrieve batch item
        AiReceiptBatchItem item = storage.getObject(AiReceiptBatchItem.class, new Request(
                new Columns.All(),
                new Condition.Equals("id", itemId)));

        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Batch item not found").build();
        }

        // Verify item belongs to specified batch
        if (item.getBatchId() != batchId) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Item does not belong to specified batch").build();
        }

        // Retrieve batch to check permissions
        AiReceiptBatch batch = storage.getObject(AiReceiptBatch.class, new Request(
                new Columns.All(),
                new Condition.Equals("id", batchId)));

        if (batch == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Batch not found").build();
        }

        // Check permission for the device
        permissionsService.checkPermission(Device.class, getUserId(), batch.getDeviceId());

        // Permission control: non-admin users can only view their own batches
        if (permissionsService.notAdmin(getUserId()) && batch.getUserId() != getUserId()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Access denied").build();
        }

        // Construct file path
        String mediaPath = config.getString(Keys.MEDIA_PATH);
        File file = new File(mediaPath, item.getReceiptImagePath());

        if (!file.exists()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Receipt image file not found").build();
        }

        // Extract filename for Content-Disposition header
        String fileName = item.getReceiptImagePath().substring(
                item.getReceiptImagePath().lastIndexOf('/') + 1);

        return Response.ok(file)
                .header("Content-Disposition", "inline; filename=\"" + fileName + "\"")
                .build();
    }

    /**
     * Helper method to build error responses.
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
