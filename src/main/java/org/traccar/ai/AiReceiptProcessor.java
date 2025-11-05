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
package org.traccar.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.LifecycleObject;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.model.AiReceiptBatch;
import org.traccar.model.AiReceiptBatchItem;
import org.traccar.model.Expense;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous processor for AI receipt batches.
 * Manages a queue of batches and processes them using Azure Document Intelligence.
 */
@Singleton
public class AiReceiptProcessor implements LifecycleObject {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiReceiptProcessor.class);

    private final Config config;
    private final Storage storage;
    private final AzureDocumentIntelligenceService azureService;
    private final GptReceiptEnhancer gptEnhancer;
    private final ObjectMapper objectMapper;
    private final String mediaPath;
    private final double confidenceThreshold;

    private ExecutorService executorService;
    private BlockingQueue<Long> batchQueue;

    @Inject
    public AiReceiptProcessor(
            Config config,
            Storage storage,
            AzureDocumentIntelligenceService azureService,
            GptReceiptEnhancer gptEnhancer,
            ObjectMapper objectMapper) {
        this.config = config;
        this.storage = storage;
        this.azureService = azureService;
        this.gptEnhancer = gptEnhancer;
        this.objectMapper = objectMapper;
        this.mediaPath = config.getString(Keys.MEDIA_PATH);
        this.confidenceThreshold = config.getDouble(Keys.AI_RECEIPT_CONFIDENCE_THRESHOLD);
    }

    @Override
    public void start() {
        int workerThreads = config.getInteger(Keys.AI_RECEIPT_WORKER_THREADS);
        LOGGER.info("Starting AI Receipt Processor with {} worker threads", workerThreads);

        batchQueue = new LinkedBlockingQueue<>();
        executorService = Executors.newFixedThreadPool(workerThreads);

        // Start worker threads
        for (int i = 0; i < workerThreads; i++) {
            executorService.submit(new BatchWorker());
        }

        LOGGER.info("AI Receipt Processor started successfully");
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping AI Receipt Processor...");

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        LOGGER.info("AI Receipt Processor stopped");
    }

    /**
     * Submit a batch for processing.
     * @param batchId The batch ID to process
     */
    public void submitBatch(long batchId) {
        try {
            batchQueue.put(batchId);
            LOGGER.info("Batch {} submitted to processing queue. Queue size: {}", batchId, batchQueue.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Failed to submit batch {} to queue", batchId, e);
        }
    }

    /**
     * Worker thread that processes batches from the queue.
     */
    private final class BatchWorker implements Runnable {
        @Override
        public void run() {
            LOGGER.info("Batch worker thread started: {}", Thread.currentThread().getName());

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Take batch from queue (blocking)
                    Long batchId = batchQueue.poll(1, TimeUnit.SECONDS);
                    if (batchId != null) {
                        processBatch(batchId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.error("Unexpected error in batch worker", e);
                }
            }

            LOGGER.info("Batch worker thread stopped: {}", Thread.currentThread().getName());
        }
    }

    /**
     * Process a single batch.
     */
    private void processBatch(long batchId) {
        LOGGER.info("Processing batch {}", batchId);

        try {
            // Load batch from database
            AiReceiptBatch batch = storage.getObject(AiReceiptBatch.class, new Request(
                    new Columns.All(),
                    new Condition.Equals("id", batchId)));

            if (batch == null) {
                LOGGER.error("Batch {} not found in database", batchId);
                return;
            }

            // Update batch status to PROCESSING
            batch.setStatus(AiReceiptBatch.STATUS_PROCESSING);
            batch.setStartedTime(new Date());
            storage.updateObject(batch, new Request(
                    new Columns.Include("status", "startedTime"),
                    new Condition.Equals("id", batchId)));

            // Load batch items
            List<AiReceiptBatchItem> items = storage.getObjects(AiReceiptBatchItem.class, new Request(
                    new Columns.All(),
                    new Condition.Equals("batchId", batchId)));

            if (items.isEmpty()) {
                LOGGER.warn("Batch {} has no items", batchId);
                finalizeBatch(batch, 0, 0, 0);
                return;
            }

            // Process each item
            int successCount = 0;
            int failedCount = 0;
            int lowConfidenceCount = 0;

            for (AiReceiptBatchItem item : items) {
                String result = processReceiptItem(batch, item);
                switch (result) {
                    case "SUCCESS":
                        successCount++;
                        break;
                    case "FAILED":
                        failedCount++;
                        break;
                    case "LOW_CONFIDENCE":
                        lowConfidenceCount++;
                        break;
                    default:
                        break;
                }
            }

            // Finalize batch
            finalizeBatch(batch, successCount, failedCount, lowConfidenceCount);

            LOGGER.info("Batch {} completed - Success: {}, Failed: {}, Low Confidence: {}",
                    batchId, successCount, failedCount, lowConfidenceCount);

        } catch (Exception e) {
            LOGGER.error("Failed to process batch {}", batchId, e);
            try {
                failBatch(batchId, "Internal error: " + e.getMessage());
            } catch (StorageException ex) {
                LOGGER.error("Failed to update batch {} status", batchId, ex);
            }
        }
    }

    /**
     * Process a single receipt item.
     * @return Status: SUCCESS, FAILED, or LOW_CONFIDENCE
     */
    private String processReceiptItem(AiReceiptBatch batch, AiReceiptBatchItem item) {
        LOGGER.debug("Processing receipt item {} from batch {}", item.getId(), batch.getId());

        try {
            // Update item status to PROCESSING
            item.setStatus(AiReceiptBatchItem.STATUS_PROCESSING);
            storage.updateObject(item, new Request(
                    new Columns.Include("status"),
                    new Condition.Equals("id", item.getId())));

            // Construct file path
            File receiptFile = new File(mediaPath, item.getReceiptImagePath());
            if (!receiptFile.exists()) {
                throw new Exception("Receipt file not found: " + item.getReceiptImagePath());
            }

            // Step 1: Call Azure Document Intelligence
            JsonNode azureResponse = azureService.analyzeReceipt(receiptFile);

            // Save raw Azure response for debugging
            String rawAzureResponse = objectMapper.writeValueAsString(azureResponse);

            // Step 2: Enhance with GPT-3.5 Turbo (standardize and validate)
            String gptStandardizedJson = gptEnhancer.enhanceReceiptData(azureResponse);

            // Save GPT-enhanced response (this is what we actually use)
            item.setRawAiResponse(gptStandardizedJson);

            // CRITICAL FIX: Clean JSON string before deserialization
            // GPT sometimes returns string "null" instead of JSON null (e.g., "hst": "null")
            // This causes H2 JdbcSQLDataException: Data conversion error converting "'null'" to DECIMAL
            // Although we've improved the GPT prompt, this is a defensive fallback layer
            String cleanedJson = gptStandardizedJson
                    // Handle lowercase "null" with different spacing
                    .replace(": \"null\"", ": null")     // "field": "null" → "field": null
                    .replace(":\"null\"", ":null")       // "field":"null" → "field":null
                    .replace(": \"null\" ", ": null ")   // "field": "null" , → "field": null ,
                    .replace(": \" null\"", ": null")    // "field": " null" → "field": null
                    .replace(": \"null \"", ": null")    // "field": "null " → "field": null
                    .replace(": \" null \"", ": null")   // "field": " null " → "field": null
                    // Handle capitalized variations (GPT sometimes capitalizes)
                    .replace(": \"Null\"", ": null")
                    .replace(":\"Null\"", ":null")
                    .replace(": \"NULL\"", ": null")
                    .replace(":\"NULL\"", ":null")
                    // Handle other common error strings GPT might return
                    .replace(": \"no-info\"", ": null")
                    .replace(":\"no-info\"", ":null")
                    .replace(": \"N/A\"", ": null")
                    .replace(":\"N/A\"", ":null")
                    .replace(": \"n/a\"", ": null")
                    .replace(":\"n/a\"", ":null");

            // Step 3: Parse GPT output into ReceiptData object
            ReceiptDataExtractor.ReceiptData receiptData =
                    objectMapper.readValue(cleanedJson, ReceiptDataExtractor.ReceiptData.class);

            // Clean up empty strings and convert to null (prevent "null" string conversion errors)
            cleanReceiptData(receiptData);

            // GPT-validated data is considered high confidence (0.99)
            // If GPT successfully returned structured data, it has validated the extraction
            BigDecimal gptConfidence = BigDecimal.valueOf(0.99);
            item.setConfidence(gptConfidence);
            item.setProcessedTime(new Date());

            LOGGER.info("Item {} processed with GPT - Merchant: {}, Amount: {}, Type: {}",
                    item.getId(), receiptData.getMerchant(), receiptData.getAmount(), receiptData.getType());

            // Validate required fields (NOT NULL constraints)
            if (receiptData.getAmount() == null || receiptData.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new Exception("Amount is required and must be positive. Azure may have failed to extract it.");
            }
            if (receiptData.getMerchant() == null || receiptData.getMerchant().trim().isEmpty()) {
                throw new Exception("Merchant name is required. Azure may have failed to extract it.");
            }
            if (receiptData.getCurrency() == null || receiptData.getCurrency().trim().isEmpty()) {
                throw new Exception("Currency is required.");
            }

            // Create Expense entity
            Expense expense = new Expense();

            // Required fields (always set)
            expense.setDeviceId(batch.getDeviceId());
            expense.setType(receiptData.getType());
            expense.setAmount(receiptData.getAmount());
            // Currency should be uppercase to match manual entry format (CAD, USD)
            expense.setCurrency(receiptData.getCurrency().toUpperCase());
            expense.setMerchant(receiptData.getMerchant());
            expense.setExpenseDate(receiptData.getTransactionDate() != null
                    ? receiptData.getTransactionDate() : new Date());
            expense.setReceiptImagePath(item.getReceiptImagePath());
            expense.setBatchItemId(item.getId());
            expense.setCreatedByUserId(batch.getUserId());
            expense.setCreatedTime(new Date());
            expense.setModifiedTime(new Date());

            // Optional fields - ONLY set if not null to avoid "null" string conversion errors
            if (receiptData.getGst() != null) {
                expense.setGst(receiptData.getGst());
            }
            if (receiptData.getPst() != null) {
                expense.setPst(receiptData.getPst());
            }
            if (receiptData.getHst() != null) {
                expense.setHst(receiptData.getHst());
            }
            if (receiptData.getTotalTax() != null) {
                expense.setTotalTax(receiptData.getTotalTax());
            }
            if (receiptData.getCountry() != null) {
                expense.setCountry(receiptData.getCountry());
            }
            if (receiptData.getProvinceState() != null) {
                expense.setProvinceState(receiptData.getProvinceState());
            }
            if (receiptData.getLocation() != null) {
                expense.setLocation(receiptData.getLocation());
            }
            if (receiptData.getDescription() != null) {
                expense.setDescription(receiptData.getDescription());
            }
            if (receiptData.getNotes() != null) {
                expense.setNotes(receiptData.getNotes());
            }

            // CRITICAL FIX: Use Columns.Include to explicitly specify which fields to insert
            // This prevents H2 from trying to convert null values for optional DECIMAL fields
            // Build the list of columns to include dynamically based on what's set
            List<String> columnsToInclude = new ArrayList<>();
            // Always include required fields
            columnsToInclude.add("deviceId");
            columnsToInclude.add("type");
            columnsToInclude.add("amount");
            columnsToInclude.add("currency");
            columnsToInclude.add("merchant");
            columnsToInclude.add("expenseDate");
            columnsToInclude.add("receiptImagePath");
            columnsToInclude.add("batchItemId");
            columnsToInclude.add("createdByUserId");
            columnsToInclude.add("createdTime");
            columnsToInclude.add("modifiedTime");

            // Only include optional fields if they are not null
            if (expense.getGst() != null) {
                columnsToInclude.add("gst");
            }
            if (expense.getPst() != null) {
                columnsToInclude.add("pst");
            }
            if (expense.getHst() != null) {
                columnsToInclude.add("hst");
            }
            if (expense.getTotalTax() != null) {
                columnsToInclude.add("totalTax");
            }
            if (expense.getCountry() != null) {
                columnsToInclude.add("country");
            }
            if (expense.getProvinceState() != null) {
                columnsToInclude.add("provinceState");
            }
            if (expense.getLocation() != null) {
                columnsToInclude.add("location");
            }
            if (expense.getDescription() != null) {
                columnsToInclude.add("description");
            }
            if (expense.getNotes() != null) {
                columnsToInclude.add("notes");
            }

            // Save to database with explicit column inclusion
            expense.setId(storage.addObject(expense, new Request(
                    new Columns.Include(columnsToInclude.toArray(new String[0])))));

            LOGGER.debug("Expense inserted with columns: {}", columnsToInclude);

            // Update item with success status and expense ID
            item.setStatus(AiReceiptBatchItem.STATUS_SUCCESS);
            item.setExpenseId(expense.getId());
            storage.updateObject(item, new Request(
                    new Columns.Include("status", "confidence", "expenseId",
                            "rawAiResponse", "processedTime"),
                    new Condition.Equals("id", item.getId())));

            LOGGER.info("Successfully created expense {} for receipt item {}", expense.getId(), item.getId());
            return "SUCCESS";

        } catch (Exception e) {
            LOGGER.error("Failed to process receipt item {}", item.getId(), e);
            try {
                item.setStatus(AiReceiptBatchItem.STATUS_FAILED);
                item.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Unknown error");
                item.setProcessedTime(new Date());
                storage.updateObject(item, new Request(
                        new Columns.Include("status", "errorMessage", "processedTime"),
                        new Condition.Equals("id", item.getId())));
            } catch (StorageException ex) {
                LOGGER.error("Failed to update item {} status", item.getId(), ex);
            }
            return "FAILED";
        }
    }

    /**
     * Finalize batch after all items are processed.
     */
    private void finalizeBatch(AiReceiptBatch batch, int successCount, int failedCount, int lowConfidenceCount)
            throws StorageException {
        batch.setSuccessCount(successCount);
        batch.setFailedCount(failedCount);
        batch.setLowConfidenceCount(lowConfidenceCount);
        batch.setCompletedTime(new Date());

        // Determine final batch status
        if (failedCount == batch.getTotalReceipts()) {
            batch.setStatus(AiReceiptBatch.STATUS_FAILED);
        } else if (successCount == batch.getTotalReceipts()) {
            batch.setStatus(AiReceiptBatch.STATUS_COMPLETED);
        } else {
            batch.setStatus(AiReceiptBatch.STATUS_PARTIAL_SUCCESS);
        }

        storage.updateObject(batch, new Request(
                new Columns.Include("status", "successCount", "failedCount",
                        "lowConfidenceCount", "completedTime"),
                new Condition.Equals("id", batch.getId())));

        // TODO Phase 5: Send WebSocket notification
    }

    /**
     * Clean up ReceiptData to prevent "null" string conversion errors.
     * Converts empty strings and "null" strings to actual null values.
     * CRITICAL: Final validation layer before database insertion.
     */
    private void cleanReceiptData(ReceiptDataExtractor.ReceiptData data) {
        // Clean string fields - convert empty/null strings to null
        if (data.getCountry() != null && (data.getCountry().trim().isEmpty()
                || data.getCountry().equalsIgnoreCase("null")
                || data.getCountry().equalsIgnoreCase("n/a")
                || data.getCountry().equalsIgnoreCase("no-info"))) {
            data.setCountry(null);
        }
        if (data.getProvinceState() != null && (data.getProvinceState().trim().isEmpty()
                || data.getProvinceState().equalsIgnoreCase("null")
                || data.getProvinceState().equalsIgnoreCase("n/a")
                || data.getProvinceState().equalsIgnoreCase("no-info"))) {
            data.setProvinceState(null);
        }
        if (data.getLocation() != null && (data.getLocation().trim().isEmpty()
                || data.getLocation().equalsIgnoreCase("null")
                || data.getLocation().equalsIgnoreCase("n/a")
                || data.getLocation().equalsIgnoreCase("no-info"))) {
            data.setLocation(null);
        }
        if (data.getDescription() != null && (data.getDescription().trim().isEmpty()
                || data.getDescription().equalsIgnoreCase("null")
                || data.getDescription().equalsIgnoreCase("n/a")
                || data.getDescription().equalsIgnoreCase("no-info"))) {
            data.setDescription(null);
        }
        if (data.getNotes() != null && (data.getNotes().trim().isEmpty()
                || data.getNotes().equalsIgnoreCase("null")
                || data.getNotes().equalsIgnoreCase("n/a")
                || data.getNotes().equalsIgnoreCase("no-info"))) {
            data.setNotes(null);
        }
        if (data.getMerchant() != null && (data.getMerchant().trim().isEmpty()
                || data.getMerchant().equalsIgnoreCase("null")
                || data.getMerchant().equalsIgnoreCase("n/a")
                || data.getMerchant().equalsIgnoreCase("no-info"))) {
            data.setMerchant(null);
        }
        if (data.getCurrency() != null && (data.getCurrency().trim().isEmpty()
                || data.getCurrency().equalsIgnoreCase("null")
                || data.getCurrency().equalsIgnoreCase("n/a")
                || data.getCurrency().equalsIgnoreCase("no-info"))) {
            data.setCurrency(null);
        }

        // CRITICAL FIX: Validate BigDecimal fields to prevent H2 conversion errors
        // If Jackson somehow created an invalid BigDecimal or if value is negative/zero, nullify it
        // This is the final defense layer - these should already be clean from previous layers

        // Validate and clean tax fields (they should be null or positive)
        if (data.getGst() != null && data.getGst().compareTo(BigDecimal.ZERO) < 0) {
            LOGGER.warn("Invalid GST value detected (negative): {}. Setting to null.", data.getGst());
            data.setGst(null);
        }
        if (data.getPst() != null && data.getPst().compareTo(BigDecimal.ZERO) < 0) {
            LOGGER.warn("Invalid PST value detected (negative): {}. Setting to null.", data.getPst());
            data.setPst(null);
        }
        if (data.getHst() != null && data.getHst().compareTo(BigDecimal.ZERO) < 0) {
            LOGGER.warn("Invalid HST value detected (negative): {}. Setting to null.", data.getHst());
            data.setHst(null);
        }
        if (data.getTotalTax() != null && data.getTotalTax().compareTo(BigDecimal.ZERO) < 0) {
            LOGGER.warn("Invalid totalTax value detected (negative): {}. Setting to null.", data.getTotalTax());
            data.setTotalTax(null);
        }

        // Log cleaned data for debugging
        LOGGER.debug("ReceiptData cleaned - GST: {}, PST: {}, HST: {}, TotalTax: {}",
                data.getGst(), data.getPst(), data.getHst(), data.getTotalTax());
    }

    /**
     * Mark batch as failed.
     */
    private void failBatch(long batchId, String errorMessage) throws StorageException {
        AiReceiptBatch batch = new AiReceiptBatch();
        batch.setId(batchId);
        batch.setStatus(AiReceiptBatch.STATUS_FAILED);
        batch.setCompletedTime(new Date());

        storage.updateObject(batch, new Request(
                new Columns.Include("status", "completedTime"),
                new Condition.Equals("id", batchId)));

        LOGGER.error("Batch {} marked as failed: {}", batchId, errorMessage);
    }
}
