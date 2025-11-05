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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Config;
import org.traccar.config.Keys;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Service to enhance and standardize receipt data using Azure OpenAI GPT-3.5 Turbo.
 * Takes raw Azure Document Intelligence output and converts it to standardized JSON format.
 */
@Singleton
public class GptReceiptEnhancer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GptReceiptEnhancer.class);

    private static final String SYSTEM_PROMPT = """
            You are an expert receipt data extractor for Canadian and US business expenses.

            Your task:
            1. Receive raw receipt data from Azure Document Intelligence
            2. Extract and standardize the data into a specific JSON format
            3. Intelligently classify the expense type
            4. Correctly identify and separate Canadian taxes (GST/HST/PST)

            CRITICAL TAX RULES FOR CANADA:
            - HST provinces (ON, NB, NS, PE, NL): Use "hst" field ONLY, leave "gst" and "pst" null
            - GST+PST provinces (BC, SK, MB, QC): Use "gst" + "pst" fields, leave "hst" null
            - GST-only provinces (AB, NT, NU, YT): Use "gst" field only
            - GST rate: 5%% (federal)
            - PST rates: BC 7%%, SK 6%%, MB 7%%, QC 9.975%%
            - HST rates: ON 13%%, NB 15%%, NS 15%%, PE 15%%, NL 15%%

            EXPENSE TYPE CLASSIFICATION:
            - "fuel": Gas stations (Mobil, Shell, Esso, Petro-Canada, Chevron, etc.)
            - "parking": Parking lots, meters, parkades
            - "toll": Highway tolls (407 ETR, etc.)
            - "carwash": Car wash services
            - "maintenance": Auto repair, oil change, tire services, mechanic
            - "insurance": Insurance payments
            - "mobile": Phone bills, data plans, cellular services
            - "legal": Legal fees, permits, registration
            - "supplies": Office supplies, equipment, tools, hardware stores
            - "others": Anything else

            OUTPUT FORMAT (JSON only, no explanations):
            {
              "merchant": "string",
              "amount": number,
              "currency": "string",
              "type": "string",
              "expenseDate": "YYYY-MM-DD",
              "description": "string or null",
              "location": "string or null",
              "country": "string or null",
              "provinceState": "string or null",
              "gst": number or null,
              "pst": number or null,
              "hst": number or null,
              "totalTax": number or null,
              "notes": "string or null"
            }

            VALIDATION RULES:
            - All amounts must be positive numbers with max 2 decimal places
            - merchant is required and max 100 characters
            - amount is required and > 0. This is the total price paid, including tax.
            - currency is required (default "CAD" if not found)
            - type must be one of the valid types listed above
            - expenseDate is required (format: YYYY-MM-DD)
            - If province is identified, apply correct tax structure
            - totalTax should equal sum of all tax fields (gst + pst OR hst)
            - Use null for optional fields if data is not available or uncertain
            - Extract only facts from the receipt, do not invent data
            """;

    private final Config config;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String apiKey;
    private final String deployment;
    private final String apiVersion;

    @Inject
    public GptReceiptEnhancer(Config config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.endpoint = config.getString(Keys.AZURE_OPENAI_ENDPOINT);
        this.apiKey = config.getString(Keys.AZURE_OPENAI_KEY);
        this.deployment = config.getString(Keys.AZURE_OPENAI_DEPLOYMENT);
        this.apiVersion = config.getString(Keys.AZURE_OPENAI_API_VERSION);

        if (endpoint == null || apiKey == null || deployment == null) {
            LOGGER.warn("Azure OpenAI configuration incomplete. GPT enhancement will not be available.");
        } else {
            LOGGER.info("GPT Receipt Enhancer initialized with deployment: {}", deployment);
        }
    }

    /**
     * Enhance receipt data using GPT-3.5 Turbo.
     * @param azureDocIntelligenceJson Raw JSON from Azure Document Intelligence
     * @return Standardized JSON string matching ReceiptData schema
     * @throws Exception if GPT call fails or response is invalid
     */
    public String enhanceReceiptData(JsonNode azureDocIntelligenceJson) throws Exception {
        if (endpoint == null || apiKey == null || deployment == null) {
            throw new Exception("Azure OpenAI is not configured");
        }

        // Build request payload
        ObjectNode requestBody = objectMapper.createObjectNode();

        // Add messages array
        ArrayNode messages = objectMapper.createArrayNode();

        // System message
        ObjectNode systemMessage = objectMapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", SYSTEM_PROMPT);
        messages.add(systemMessage);

        // User message with Azure Doc Intelligence data
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        String userContent = "Extract and standardize the following receipt data from Azure Document Intelligence:\n\n"
                + objectMapper.writeValueAsString(azureDocIntelligenceJson);
        userMessage.put("content", userContent);
        messages.add(userMessage);

        requestBody.set("messages", messages);
        requestBody.put("temperature", 0.1);  // Low temperature for consistent output
        requestBody.put("max_tokens", 500);
        requestBody.put("response_format", objectMapper.createObjectNode().put("type", "json_object"));

        // Build URL
        String urlString = String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
                endpoint.replaceAll("/$", ""), deployment, apiVersion);

        LOGGER.debug("Calling Azure OpenAI: {}", urlString);

        // Make HTTP request
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("api-key", apiKey);
        conn.setDoOutput(true);

        // Send request
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = objectMapper.writeValueAsBytes(requestBody);
            os.write(input, 0, input.length);
        }

        // Read response
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String errorBody = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            LOGGER.error("Azure OpenAI API error ({}): {}", responseCode, errorBody);
            throw new Exception("Azure OpenAI API returned error: " + responseCode);
        }

        String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        LOGGER.debug("GPT Response: {}", responseBody);

        // Parse response
        JsonNode responseJson = objectMapper.readTree(responseBody);
        JsonNode choices = responseJson.path("choices");
        if (choices.isEmpty() || !choices.isArray()) {
            throw new Exception("Invalid GPT response: no choices found");
        }

        String content = choices.get(0).path("message").path("content").asText();
        if (content == null || content.trim().isEmpty()) {
            throw new Exception("GPT returned empty content");
        }

        // Validate that content is valid JSON
        objectMapper.readTree(content);  // Will throw if invalid JSON

        LOGGER.info("Successfully enhanced receipt data using GPT");
        return content;
    }
}
