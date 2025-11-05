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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Data class for standardized receipt information.
 * This class matches the JSON schema output from GPT-3.5 Turbo enhancement.
 */
public final class ReceiptDataExtractor {

    private ReceiptDataExtractor() {
    }

    /**
     * Custom deserializer for BigDecimal that handles string "null" values.
     * Prevents H2 JdbcSQLDataException when GPT returns "null" string instead of JSON null.
     */
    public static class NullSafeBigDecimalDeserializer extends JsonDeserializer<BigDecimal> {
        @Override
        public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getText();
            if (value == null || value.trim().isEmpty()
                    || value.equalsIgnoreCase("null")
                    || value.equalsIgnoreCase("n/a")
                    || value.equalsIgnoreCase("no-info")) {
                return null;
            }
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                // If conversion fails, log and return null instead of throwing exception
                return null;
            }
        }
    }

    /**
     * Receipt data in standardized format (populated by GPT from Azure Doc Intelligence).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReceiptData {
        private String merchant;

        @JsonDeserialize(using = NullSafeBigDecimalDeserializer.class)
        private BigDecimal amount;

        private String currency;
        private Date transactionDate;
        private String location;

        @JsonDeserialize(using = NullSafeBigDecimalDeserializer.class)
        private BigDecimal gst;

        @JsonDeserialize(using = NullSafeBigDecimalDeserializer.class)
        private BigDecimal pst;

        @JsonDeserialize(using = NullSafeBigDecimalDeserializer.class)
        private BigDecimal hst;

        @JsonDeserialize(using = NullSafeBigDecimalDeserializer.class)
        private BigDecimal totalTax;

        private String country;
        private String provinceState;

        @JsonDeserialize(using = NullSafeBigDecimalDeserializer.class)
        private BigDecimal confidence;

        private String type;
        private String description;
        private String notes;

        // Getters and Setters

        public String getMerchant() {
            return merchant;
        }

        public void setMerchant(String merchant) {
            this.merchant = merchant;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        @JsonProperty("expenseDate")
        public Date getTransactionDate() {
            return transactionDate;
        }

        @JsonProperty("expenseDate")
        public void setTransactionDate(Date transactionDate) {
            this.transactionDate = transactionDate;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public BigDecimal getGst() {
            return gst;
        }

        public void setGst(BigDecimal gst) {
            this.gst = gst;
        }

        public BigDecimal getPst() {
            return pst;
        }

        public void setPst(BigDecimal pst) {
            this.pst = pst;
        }

        public BigDecimal getHst() {
            return hst;
        }

        public void setHst(BigDecimal hst) {
            this.hst = hst;
        }

        public BigDecimal getTotalTax() {
            return totalTax;
        }

        public void setTotalTax(BigDecimal totalTax) {
            this.totalTax = totalTax;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getProvinceState() {
            return provinceState;
        }

        public void setProvinceState(String provinceState) {
            this.provinceState = provinceState;
        }

        public BigDecimal getConfidence() {
            return confidence;
        }

        public void setConfidence(BigDecimal confidence) {
            this.confidence = confidence;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
