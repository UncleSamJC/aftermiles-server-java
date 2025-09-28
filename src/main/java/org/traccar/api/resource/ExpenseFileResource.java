/*
 * Copyright 2024 Anton Tananaev (anton@traccar.org)
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

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.traccar.api.BaseResource;
import org.traccar.model.Expense;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Path("expenses")
public class ExpenseFileResource extends BaseResource {

    private static final String UPLOAD_DIR = "uploads/receipts/";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String[] ALLOWED_TYPES = {"image/jpeg", "image/png", "image/jpg"};

    @POST
    @Path("{id}/receipt")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadReceipt(
            @PathParam("id") long expenseId,
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail) throws Exception {

        // 权限检查：验证费用记录是否属于当前用户
        if (permissionsService.notAdmin(getUserId())) {
            Expense expense = storage.getObject(Expense.class, new Request(
                    new Columns.Include("createdByUserId"),
                    new Condition.Equals("id", expenseId)));
            if (expense == null || expense.getCreatedByUserId() != getUserId()) {
                throw new SecurityException("Expense access denied");
            }
        }

        // 文件验证
        String contentType = fileDetail.getType();
        if (!isValidImageFile(contentType)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid file type. Only JPEG and PNG are allowed.");
            return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
        }

        // 生成唯一文件名
        String fileExtension = getFileExtension(fileDetail.getFileName());
        String fileName = UUID.randomUUID().toString() + fileExtension;
        String filePath = UPLOAD_DIR + fileName;

        // 确保目录存在
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // 保存文件
        try (FileOutputStream out = new FileOutputStream(new File(filePath))) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > MAX_FILE_SIZE) {
                    // 删除已经写入的文件
                    Files.deleteIfExists(Paths.get(filePath));
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "File size exceeds limit of 10MB");
                    return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
                }
                out.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to save file: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(error).build();
        }

        // 更新费用记录的收据图片路径
        try {
            Expense expense = storage.getObject(Expense.class, new Request(
                    new Columns.All(), new Condition.Equals("id", expenseId)));
            if (expense != null) {
                // 删除旧文件（如果存在）
                if (expense.getReceiptImagePath() != null && !expense.getReceiptImagePath().isEmpty()) {
                    try {
                        Files.deleteIfExists(Paths.get(expense.getReceiptImagePath()));
                    } catch (IOException ignored) {
                        // 忽略删除旧文件的错误
                    }
                }

                expense.setReceiptImagePath(filePath);
                expense.setModifiedTime(new Date());
                storage.updateObject(expense, new Request(
                        new Columns.Include("receiptImagePath", "modifiedTime"),
                        new Condition.Equals("id", expenseId)));
            }
        } catch (StorageException e) {
            // 如果数据库更新失败，删除已上传的文件
            try {
                Files.deleteIfExists(Paths.get(filePath));
            } catch (IOException ignored) {
                // 忽略删除文件的错误
            }
            throw e;
        }

        // 返回成功结果
        Map<String, String> result = new HashMap<>();
        result.put("message", "Receipt uploaded successfully");
        result.put("fileName", fileName);
        result.put("filePath", filePath);
        return Response.ok(result).build();
    }

    @GET
    @Path("{id}/receipt")
    @Produces("image/*")
    public Response getReceipt(@PathParam("id") long expenseId) throws Exception {
        // 权限检查
        if (permissionsService.notAdmin(getUserId())) {
            Expense expense = storage.getObject(Expense.class, new Request(
                    new Columns.Include("createdByUserId"),
                    new Condition.Equals("id", expenseId)));
            if (expense == null || expense.getCreatedByUserId() != getUserId()) {
                throw new SecurityException("Expense access denied");
            }
        }

        Expense expense = storage.getObject(Expense.class, new Request(
                new Columns.Include("receiptImagePath"),
                new Condition.Equals("id", expenseId)));

        if (expense == null || expense.getReceiptImagePath() == null || expense.getReceiptImagePath().isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Receipt not found").build();
        }

        File file = new File(expense.getReceiptImagePath());
        if (!file.exists()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Receipt file not found").build();
        }

        return Response.ok(file)
                .header("Content-Disposition", "inline; filename=\"receipt_" + expenseId + "\"")
                .build();
    }

    private boolean isValidImageFile(String contentType) {
        if (contentType == null) {
            return false;
        }
        for (String allowedType : ALLOWED_TYPES) {
            if (allowedType.equals(contentType)) {
                return true;
            }
        }
        return false;
    }

    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf("."));
        }
        return ".jpg"; // 默认扩展名
    }

}
