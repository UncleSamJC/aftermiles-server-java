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
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.traccar.api.BaseObjectResource;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.database.MediaManager;
import org.traccar.helper.LogAction;
import org.traccar.model.Device;
import org.traccar.model.Expense;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
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
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Path("expenses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ExpenseResource extends BaseObjectResource<Expense> {

    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private static final Set<String> VALID_CATEGORIES = Set.of(
        Expense.TYPE_FUEL,
        Expense.TYPE_INSURANCE,
        Expense.TYPE_MAINTENANCE,
        Expense.TYPE_PARKING,
        Expense.TYPE_TOLL,
        Expense.TYPE_CARWASH,
        Expense.TYPE_MOBILE,
        Expense.TYPE_LEGAL,
        Expense.TYPE_SUPPLIES,
        Expense.TYPE_OTHERS
    );

    @Inject
    private Config config;

    @Inject
    private MediaManager mediaManager;

    @Inject
    private LogAction actionLogger;

    @Context
    private HttpServletRequest request;

    public ExpenseResource() {
        super(Expense.class);
    }

    @GET
    public Stream<Expense> get(
            @QueryParam("deviceId") long deviceId,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to,
            @QueryParam("type") String type,
            @QueryParam("limit") Integer limit) throws StorageException {

        var conditions = new LinkedList<Condition>();

        // Permission control: non-admin users can only view their own expense records
        if (permissionsService.notAdmin(getUserId())) {
            conditions.add(new Condition.Equals("createdByUserId", getUserId()));
        }

        // Device filter
        if (deviceId > 0) {
            permissionsService.checkPermission(Device.class, getUserId(), deviceId);
            conditions.add(new Condition.Equals("deviceId", deviceId));
        }

        // Time range filter
        if (from != null && to != null) {
            conditions.add(new Condition.Between("expenseDate", from, to));
        }

        // Expense type filter
        if (type != null && !type.isEmpty()) {
            conditions.add(new Condition.Equals("type", type));
        }

        return storage.getObjectsStream(baseClass, new Request(
                new Columns.All(),
                Condition.merge(conditions),
                new Order("expenseDate", true, limit != null ? limit : 0)));
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response createWithReceipt(
            @FormDataParam("deviceId") long deviceId,
            @FormDataParam("category") String category,
            @FormDataParam("amount") String amountStr,
            @FormDataParam("currency") String currency,
            @FormDataParam("merchant") String merchant,
            @FormDataParam("date") String dateStr,
            @FormDataParam("receipt") InputStream receiptStream,
            @FormDataParam("receipt") FormDataContentDisposition receiptDetail,
            @FormDataParam("receipt") FormDataBodyPart receiptBodyPart,
            @FormDataParam("notes") String notes,
            @FormDataParam("tags") String tags) throws Exception {

        // Validate device permission
        permissionsService.checkPermission(Device.class, getUserId(), deviceId);
        Device device = storage.getObject(Device.class, new Request(
                new Columns.All(), new Condition.Equals("id", deviceId)));
        if (device == null) {
            return buildErrorResponse("DEVICE_NOT_FOUND", "Device not found", null);
        }

        // Validate category
        if (category == null || !VALID_CATEGORIES.contains(category)) {
            Map<String, String> details = new HashMap<>();
            details.put("category", "Invalid category. Must be one of: " + String.join(", ", VALID_CATEGORIES));
            return buildErrorResponse("INVALID_CATEGORY", "Invalid category", details);
        }

        // Validate and parse amount
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                Map<String, String> details = new HashMap<>();
                details.put("amount", "Amount must be a positive number");
                return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
            }
        } catch (NumberFormatException e) {
            Map<String, String> details = new HashMap<>();
            details.put("amount", "Amount must be a valid number");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }

        // Validate currency
        if (currency == null || currency.trim().isEmpty()) {
            Map<String, String> details = new HashMap<>();
            details.put("currency", "Currency is required");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }

        // Validate merchant
        if (merchant == null || merchant.trim().isEmpty()) {
            Map<String, String> details = new HashMap<>();
            details.put("merchant", "Merchant is required");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }
        if (merchant.length() > 100) {
            Map<String, String> details = new HashMap<>();
            details.put("merchant", "Merchant name must not exceed 100 characters");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }

        // Validate and parse date
        Date expenseDate;
        try {
            expenseDate = DATE_FORMAT.parse(dateStr);
            if (expenseDate.after(new Date())) {
                Map<String, String> details = new HashMap<>();
                details.put("date", "Date cannot be in the future");
                return buildErrorResponse("FUTURE_DATE_ERROR", "Invalid date", details);
            }
        } catch (ParseException e) {
            Map<String, String> details = new HashMap<>();
            details.put("date", "Date must be in format YYYY-MM-DD");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }

        // Validate receipt file
        if (receiptStream == null || receiptBodyPart == null) {
            Map<String, String> details = new HashMap<>();
            details.put("receipt", "Receipt file is required");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }

        // Get Content-Type from FormDataBodyPart (correct way to get MIME type)
        String contentType = receiptBodyPart.getMediaType().toString();
        if (!isValidImageType(contentType)) {
            Map<String, String> details = new HashMap<>();
            details.put("receipt", "Only JPEG, JPG and PNG formats are supported. Received: " + contentType);
            return buildErrorResponse("INVALID_FILE_TYPE", "Invalid file type", details);
        }

        // Validate notes length
        if (notes != null && notes.length() > 500) {
            Map<String, String> details = new HashMap<>();
            details.put("notes", "Notes must not exceed 500 characters");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }

        // Validate tags length
        if (tags != null && tags.length() > 200) {
            Map<String, String> details = new HashMap<>();
            details.put("tags", "Tags must not exceed 200 characters");
            return buildErrorResponse("VALIDATION_ERROR", "Invalid input parameters", details);
        }

        // Create expense entity
        Expense expense = new Expense();
        expense.setDeviceId(deviceId);
        expense.setType(category);
        expense.setAmount(amount);
        expense.setCurrency(currency);
        expense.setMerchant(merchant);
        expense.setExpenseDate(expenseDate);
        expense.setNotes(notes);
        expense.setTags(tags);
        expense.setCreatedByUserId(getUserId());
        expense.setCreatedTime(new Date());
        expense.setModifiedTime(new Date());

        // Save receipt file with hierarchical directory structure: deviceId/year/month/
        String receiptFileName = null;
        try {
            // Extract year and month from expense date
            Calendar cal = Calendar.getInstance();
            cal.setTime(expenseDate);
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1; // Calendar.MONTH is 0-based

            // Build hierarchical path: deviceId/year/month
            String subPath = device.getUniqueId() + "/" + year + "/" + String.format("%02d", month);

            String extension = getFileExtension(receiptDetail.getFileName());
            String fileName = "receipt_" + System.currentTimeMillis();

            try (var output = mediaManager.createFileStream(subPath, fileName, extension)) {
                long transferred = 0;
                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                int read;
                int maxFileSize = config.getInteger(Keys.EXPENSE_FILE_SIZE_LIMIT);

                while ((read = receiptStream.read(buffer, 0, buffer.length)) >= 0) {
                    output.write(buffer, 0, read);
                    transferred += read;
                    if (transferred > maxFileSize) {
                        Map<String, String> details = new HashMap<>();
                        details.put("receipt", "File size exceeds limit of " + (maxFileSize / (1024 * 1024)) + "MB");
                        return buildErrorResponse("FILE_TOO_LARGE", "File too large", details);
                    }
                }
            }

            receiptFileName = fileName + "." + extension;
            // Store path as: deviceId/year/month/receipt_timestamp.ext
            expense.setReceiptImagePath(subPath + "/" + receiptFileName);

        } catch (IOException e) {
            return buildErrorResponse("INTERNAL_ERROR", "Failed to save receipt file", null);
        }

        // Save to database (exclude id and optional fields that are not set)
        expense.setId(storage.addObject(expense, new Request(
                new Columns.Exclude("id", "mileage", "location", "description"))));
        actionLogger.create(request, getUserId(), expense);

        // Build success response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);

        Map<String, Object> data = new HashMap<>();
        data.put("id", expense.getId());
        data.put("deviceId", expense.getDeviceId());
        data.put("category", expense.getType());
        data.put("amount", expense.getAmount());
        data.put("currency", expense.getCurrency());
        data.put("merchant", expense.getMerchant());
        data.put("date", DATE_FORMAT.format(expense.getExpenseDate()));
        data.put("receiptUrl", "/api/expenses/" + expense.getId() + "/receipt");
        data.put("notes", expense.getNotes());
        data.put("tags", expense.getTags());
        data.put("createdAt", expense.getCreatedTime().toInstant().toString());

        response.put("data", data);
        response.put("message", "Expense record created successfully");

        return Response.ok(response).build();
    }

    @GET
    @Path("{id}/receipt")
    @Produces("image/*")
    public Response getReceipt(@PathParam("id") long expenseId) throws StorageException {
        // Permission check
        Expense expense = storage.getObject(baseClass, new Request(
                new Columns.All(),
                new Condition.Equals("id", expenseId)));

        if (expense == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Expense not found").build();
        }

        // Check permission
        if (permissionsService.notAdmin(getUserId()) && expense.getCreatedByUserId() != getUserId()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Access denied").build();
        }

        if (expense.getReceiptImagePath() == null || expense.getReceiptImagePath().isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Receipt not found").build();
        }

        // Construct file path
        String mediaPath = config.getString(Keys.MEDIA_PATH);
        File file = new File(mediaPath, expense.getReceiptImagePath());

        if (!file.exists()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Receipt file not found").build();
        }

        return Response.ok(file)
                .header("Content-Disposition", "inline; filename=\"receipt_" + expenseId + "\"")
                .build();
    }

    @Override
    public Response update(Expense entity) throws Exception {
        // Permission check: only can update own expense records
        if (permissionsService.notAdmin(getUserId())) {
            Expense existing = storage.getObject(baseClass, new Request(
                    new Columns.Include("createdByUserId"),
                    new Condition.Equals("id", entity.getId())));
            if (existing == null || existing.getCreatedByUserId() != getUserId()) {
                throw new SecurityException("Expense access denied");
            }
        }

        // Set modified time, but don't modify creation time and creator
        entity.setModifiedTime(new Date());

        storage.updateObject(entity, new Request(
                new Columns.Exclude("id", "createdTime", "createdByUserId", "receiptImagePath"),
                new Condition.Equals("id", entity.getId())));

        actionLogger.edit(request, getUserId(), entity);
        return Response.ok(entity).build();
    }

    @Override
    public Response remove(@PathParam("id") long id) throws Exception {
        // Permission check: only can delete own expense records
        if (permissionsService.notAdmin(getUserId())) {
            Expense existing = storage.getObject(baseClass, new Request(
                    new Columns.Include("createdByUserId"),
                    new Condition.Equals("id", id)));
            if (existing == null || existing.getCreatedByUserId() != getUserId()) {
                throw new SecurityException("Expense access denied");
            }
        }

        actionLogger.remove(request, getUserId(), baseClass, id);
        return super.remove(id);
    }

    private boolean isValidImageType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return false;
        }

        // Remove charset or other parameters (e.g., "image/jpeg; charset=UTF-8" -> "image/jpeg")
        String baseContentType = contentType.split(";")[0].trim().toLowerCase();

        // Get allowed types from config
        String allowedTypes = config.getString(Keys.EXPENSE_ALLOWED_TYPES);

        // Check if base content type matches any allowed type
        for (String allowedType : allowedTypes.split(",")) {
            if (baseContentType.equals(allowedType.trim().toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return "jpg";
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
