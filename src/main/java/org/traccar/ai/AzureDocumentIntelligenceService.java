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
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Config;
import org.traccar.config.Keys;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service for interacting with Azure Document Intelligence API.
 * Uses prebuilt-receipt model to extract data from receipt images.
 */
@Singleton
public class AzureDocumentIntelligenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureDocumentIntelligenceService.class);

    private static final String API_VERSION = "2024-11-30";
    private static final String MODEL_ID = "prebuilt-receipt";
    private static final int MAX_POLLING_ATTEMPTS = 30;
    private static final int POLLING_INTERVAL_MS = 1000;

    private final String endpoint;
    private final String apiKey;
    private final Client httpClient;
    private final ObjectMapper objectMapper;

    @Inject
    public AzureDocumentIntelligenceService(Config config, Client httpClient, ObjectMapper objectMapper) {
        this.endpoint = config.getString(Keys.AZURE_DOCUMENT_INTELLIGENCE_ENDPOINT);
        this.apiKey = config.getString(Keys.AZURE_DOCUMENT_INTELLIGENCE_KEY);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Analyze a receipt image and extract structured data.
     *
     * @param imageFile The receipt image file to analyze
     * @return JsonNode containing the analysis result
     * @throws IOException If file reading fails
     * @throws AzureDocumentIntelligenceException If Azure API call fails
     */
    public JsonNode analyzeReceipt(File imageFile) throws IOException, AzureDocumentIntelligenceException {
        // Step 1: Submit document for analysis
        String operationLocation = submitDocument(imageFile);

        // Step 2: Poll for results
        return pollForResults(operationLocation);
    }

    /**
     * Submit document to Azure for analysis.
     * Returns operation location URL for polling.
     */
    private String submitDocument(File imageFile) throws IOException, AzureDocumentIntelligenceException {
        String analyzeUrl = String.format(
                "%s/documentintelligence/documentModels/%s:analyze?api-version=%s",
                endpoint, MODEL_ID, API_VERSION);

        // Read image file and encode to base64
        byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        // Prepare request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("base64Source", base64Image);

        try {
            Response response = httpClient.target(analyzeUrl)
                    .request()
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .post(Entity.entity(requestBody, MediaType.APPLICATION_JSON));

            if (response.getStatus() == Response.Status.ACCEPTED.getStatusCode()) {
                String operationLocation = response.getHeaderString("Operation-Location");
                if (operationLocation == null || operationLocation.isEmpty()) {
                    throw new AzureDocumentIntelligenceException(
                            "Operation-Location header missing from Azure response");
                }
                LOGGER.debug("Document submitted successfully. Operation location: {}", operationLocation);
                return operationLocation;
            } else {
                String errorBody = response.readEntity(String.class);
                LOGGER.error("Azure Document Intelligence API error: {} - {}", response.getStatus(), errorBody);
                throw new AzureDocumentIntelligenceException(
                        "Failed to submit document. Status: " + response.getStatus() + ", Body: " + errorBody);
            }
        } catch (Exception e) {
            if (e instanceof AzureDocumentIntelligenceException) {
                throw e;
            }
            throw new AzureDocumentIntelligenceException("Error calling Azure Document Intelligence API", e);
        }
    }

    /**
     * Poll the operation location until analysis is complete.
     * Implements exponential backoff for polling.
     */
    private JsonNode pollForResults(String operationLocation) throws AzureDocumentIntelligenceException {
        for (int attempt = 0; attempt < MAX_POLLING_ATTEMPTS; attempt++) {
            try {
                Response response = httpClient.target(operationLocation)
                        .request()
                        .header("Ocp-Apim-Subscription-Key", apiKey)
                        .get();

                if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                    String errorBody = response.readEntity(String.class);
                    throw new AzureDocumentIntelligenceException(
                            "Failed to get analysis result. Status: " + response.getStatus()
                            + ", Body: " + errorBody);
                }

                String responseBody = response.readEntity(String.class);
                JsonNode result = objectMapper.readTree(responseBody);

                String status = result.path("status").asText();
                LOGGER.debug("Analysis status (attempt {}): {}", attempt + 1, status);

                switch (status) {
                    case "succeeded":
                        LOGGER.info("Receipt analysis completed successfully");
                        return result;
                    case "failed":
                        String error = result.path("error").path("message").asText("Unknown error");
                        throw new AzureDocumentIntelligenceException("Analysis failed: " + error);
                    case "running":
                    case "notStarted":
                        // Continue polling
                        TimeUnit.MILLISECONDS.sleep(POLLING_INTERVAL_MS);
                        break;
                    default:
                        throw new AzureDocumentIntelligenceException("Unknown status: " + status);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AzureDocumentIntelligenceException("Polling interrupted", e);
            } catch (IOException e) {
                throw new AzureDocumentIntelligenceException("Failed to parse Azure response", e);
            } catch (Exception e) {
                if (e instanceof AzureDocumentIntelligenceException) {
                    throw e;
                }
                throw new AzureDocumentIntelligenceException("Error during polling", e);
            }
        }

        throw new AzureDocumentIntelligenceException(
                "Analysis timed out after " + MAX_POLLING_ATTEMPTS + " attempts");
    }

    /**
     * Custom exception for Azure Document Intelligence errors.
     */
    public static class AzureDocumentIntelligenceException extends Exception {
        public AzureDocumentIntelligenceException(String message) {
            super(message);
        }

        public AzureDocumentIntelligenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
