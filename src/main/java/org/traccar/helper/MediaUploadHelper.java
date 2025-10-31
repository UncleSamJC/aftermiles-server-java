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
package org.traccar.helper;

import org.traccar.config.Config;
import org.traccar.config.ConfigKey;
import org.traccar.database.MediaManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Helper class for handling media file uploads with hierarchical storage.
 * Provides utilities for file validation, path generation, and upload operations.
 */
public final class MediaUploadHelper {

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private MediaUploadHelper() {
        // Utility class
    }

    /**
     * Result of a file upload operation.
     */
    public static final class UploadResult {
        private final boolean success;
        private final String relativePath;
        private final String errorCode;
        private final String errorMessage;

        private UploadResult(boolean success, String relativePath, String errorCode, String errorMessage) {
            this.success = success;
            this.relativePath = relativePath;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static UploadResult success(String relativePath) {
            return new UploadResult(true, relativePath, null, null);
        }

        public static UploadResult error(String errorCode, String errorMessage) {
            return new UploadResult(false, null, errorCode, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Validates if the content type is an allowed image format.
     * Compares against the allowed types from configuration.
     *
     * @param config Config instance to read allowed types
     * @param contentType MIME type to validate
     * @param allowedTypesKey Config key for allowed types (comma-separated)
     * @return true if content type is allowed, false otherwise
     */
    public static boolean isValidImageType(Config config, String contentType, ConfigKey<String> allowedTypesKey) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return false;
        }

        // Remove charset or other parameters (e.g., "image/jpeg; charset=UTF-8" -> "image/jpeg")
        String baseContentType = contentType.split(";")[0].trim().toLowerCase();

        // Get allowed types from config
        String allowedTypes = config.getString(allowedTypesKey);

        // Check if base content type matches any allowed type
        for (String allowedType : allowedTypes.split(",")) {
            if (baseContentType.equals(allowedType.trim().toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extracts file extension from filename.
     *
     * @param fileName Original filename
     * @return File extension without dot (e.g., "jpg"), defaults to "jpg" if no extension found
     */
    public static String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return "jpg";
    }

    /**
     * Builds hierarchical storage path based on device and date.
     * Format: deviceUniqueId/year/month
     *
     * @param deviceUniqueId Device unique identifier
     * @param date Date for hierarchical path (year/month extraction)
     * @return Relative path string (e.g., "device123/2024/12")
     */
    public static String buildHierarchicalPath(String deviceUniqueId, Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1; // Calendar.MONTH is 0-based
        return deviceUniqueId + "/" + year + "/" + String.format("%02d", month);
    }

    /**
     * Uploads a single file with size limit validation.
     *
     * @param mediaManager MediaManager instance for file storage
     * @param inputStream File input stream
     * @param deviceUniqueId Device unique identifier
     * @param date Date for hierarchical path (year/month)
     * @param filePrefix File name prefix (e.g., "receipt", "maintenance")
     * @param extension File extension
     * @param maxFileSizeBytes Maximum allowed file size in bytes
     * @return UploadResult containing success status and relative path or error details
     */
    public static UploadResult uploadSingleFile(
            MediaManager mediaManager,
            InputStream inputStream,
            String deviceUniqueId,
            Date date,
            String filePrefix,
            String extension,
            int maxFileSizeBytes) {

        String subPath = buildHierarchicalPath(deviceUniqueId, date);
        String fileName = filePrefix + "_" + System.currentTimeMillis();
        String fullFileName = fileName + "." + extension;

        try (OutputStream output = mediaManager.createFileStream(subPath, fileName, extension)) {
            long transferred = 0;
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int read;

            while ((read = inputStream.read(buffer, 0, buffer.length)) >= 0) {
                output.write(buffer, 0, read);
                transferred += read;
                if (transferred > maxFileSizeBytes) {
                    return UploadResult.error(
                        "FILE_TOO_LARGE",
                        "File size exceeds limit of " + (maxFileSizeBytes / (1024 * 1024)) + "MB"
                    );
                }
            }

            String relativePath = subPath + "/" + fullFileName;
            return UploadResult.success(relativePath);

        } catch (IOException e) {
            return UploadResult.error("UPLOAD_FAILED", "Failed to save file: " + e.getMessage());
        }
    }

    /**
     * Uploads multiple files with size limit validation.
     * Each file is validated independently. If any file fails, the entire operation
     * returns with the first error encountered.
     *
     * @param mediaManager MediaManager instance for file storage
     * @param inputStreams List of file input streams
     * @param deviceUniqueId Device unique identifier
     * @param date Date for hierarchical path (year/month)
     * @param filePrefix File name prefix (e.g., "maintenance")
     * @param extensions List of file extensions corresponding to input streams
     * @param maxFileSizeBytes Maximum allowed file size per file in bytes
     * @return List of UploadResult for each file (stops on first error)
     */
    public static List<UploadResult> uploadMultipleFiles(
            MediaManager mediaManager,
            List<InputStream> inputStreams,
            String deviceUniqueId,
            Date date,
            String filePrefix,
            List<String> extensions,
            int maxFileSizeBytes) {

        List<UploadResult> results = new ArrayList<>();
        String subPath = buildHierarchicalPath(deviceUniqueId, date);

        for (int i = 0; i < inputStreams.size(); i++) {
            InputStream inputStream = inputStreams.get(i);
            String extension = extensions.get(i);
            long timestamp = System.currentTimeMillis();
            String fileName = filePrefix + "_" + timestamp + "_" + i;
            String fullFileName = fileName + "." + extension;

            try (OutputStream output = mediaManager.createFileStream(subPath, fileName, extension)) {
                long transferred = 0;
                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                int read;

                while ((read = inputStream.read(buffer, 0, buffer.length)) >= 0) {
                    output.write(buffer, 0, read);
                    transferred += read;
                    if (transferred > maxFileSizeBytes) {
                        results.add(UploadResult.error(
                            "FILE_TOO_LARGE",
                            "File " + i + " size exceeds limit of " + (maxFileSizeBytes / (1024 * 1024)) + "MB"
                        ));
                        return results; // Stop on first error
                    }
                }

                String relativePath = subPath + "/" + fullFileName;
                results.add(UploadResult.success(relativePath));

                // Small delay to ensure unique timestamps for consecutive uploads
                if (i < inputStreams.size() - 1) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

            } catch (IOException e) {
                results.add(UploadResult.error("UPLOAD_FAILED", "Failed to save file " + i + ": " + e.getMessage()));
                return results; // Stop on first error
            }
        }

        return results;
    }
}
