# AI Receipt Processing - Frontend Integration Guide

## Overview

The AI Receipt Processing feature allows users to upload 1-5 receipt images per batch. The backend will:
1. Use Azure Document Intelligence to extract receipt data
2. Automatically classify expenses into categories
3. Create Expense records for high-confidence results (≥96%)
4. Flag low-confidence items for manual review

## API Endpoints

### 1. Create Receipt Batch (Upload Receipts)

**Endpoint:** `POST /api/ai-receipts/batches`

**Authentication:** Required (Bearer token or session cookie)

**Content-Type:** `multipart/form-data`

**Request Parameters:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `deviceId` | Long | Yes | The vehicle/device ID |
| `receipts` | File[] | Yes | 1-5 receipt images (JPEG, JPG, PNG) |

**Request Example (JavaScript):**

```javascript
const formData = new FormData();
formData.append('deviceId', 123);
formData.append('receipts', file1); // File from <input type="file">
formData.append('receipts', file2);
formData.append('receipts', file3);

const response = await fetch('/api/ai-receipts/batches', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}` // or use session cookie
  },
  body: formData
});

const result = await response.json();
```

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "batchId": 456,
    "deviceId": "123",
    "status": "PENDING",
    "totalReceipts": 3,
    "createdAt": "2025-11-01T15:30:00Z",
    "itemIds": [789, 790, 791]
  },
  "message": "Batch created successfully. Processing will begin shortly."
}
```

**Error Response (400 Bad Request):**

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid input parameters",
    "details": {
      "receipts": "Maximum 5 receipts allowed per batch"
    }
  }
}
```

**Possible Error Codes:**

- `DEVICE_NOT_FOUND` - Invalid deviceId
- `VALIDATION_ERROR` - Missing or invalid parameters
- `INVALID_FILE_TYPE` - Unsupported image format
- `FILE_TOO_LARGE` - Image exceeds size limit
- `PERMISSION_DENIED` - User doesn't have access to device

---

### 2. Get Batch Status (Poll for Results)

**Endpoint:** `GET /api/ai-receipts/batches/{batchId}`

**Authentication:** Required

**Request Example:**

```javascript
const response = await fetch(`/api/ai-receipts/batches/${batchId}`, {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

const result = await response.json();
```

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "batchId": 456,
    "deviceId": "123",
    "userId": "789",
    "status": "COMPLETED",
    "totalReceipts": 3,
    "successCount": 2,
    "failedCount": 0,
    "lowConfidenceCount": 1,
    "createdTime": "2025-11-01T15:30:00Z",
    "startedTime": "2025-11-01T15:30:02Z",
    "completedTime": "2025-11-01T15:30:45Z",
    "items": [
      {
        "id": 789,
        "receiptImagePath": "device123/2025/11/ai-receipts/receipt_1730476200123.jpg",
        "status": "SUCCESS",
        "confidence": 0.98,
        "expenseId": 1001,
        "errorMessage": null,
        "processedTime": "2025-11-01T15:30:25Z"
      },
      {
        "id": 790,
        "receiptImagePath": "device123/2025/11/ai-receipts/receipt_1730476200456.jpg",
        "status": "SUCCESS",
        "confidence": 0.97,
        "expenseId": 1002,
        "errorMessage": null,
        "processedTime": "2025-11-01T15:30:35Z"
      },
      {
        "id": 791,
        "receiptImagePath": "device123/2025/11/ai-receipts/receipt_1730476200789.jpg",
        "status": "LOW_CONFIDENCE",
        "confidence": 0.85,
        "expenseId": null,
        "errorMessage": null,
        "processedTime": "2025-11-01T15:30:45Z"
      }
    ]
  }
}
```

**Batch Status Values:**

- `PENDING` - Waiting in queue (initial state)
- `PROCESSING` - Currently being processed
- `COMPLETED` - All items processed successfully
- `PARTIAL_SUCCESS` - Some items succeeded, some failed/low confidence
- `FAILED` - All items failed

**Item Status Values:**

- `PENDING` - Not yet processed
- `PROCESSING` - Currently being analyzed
- `SUCCESS` - Processed successfully, Expense created (expenseId will be set)
- `LOW_CONFIDENCE` - Processed but confidence < 96%, requires manual entry
- `FAILED` - Processing failed (errorMessage will be set)

---

### 3. Get Receipt Image

**Endpoint:** `GET /api/ai-receipts/batches/{batchId}/items/{itemId}/receipt`

**Authentication:** Required

**Description:** Get the receipt image file for viewing/downloading. Useful for displaying low-confidence or failed receipts for manual review.

**Request Example:**

```javascript
// Get receipt image URL
const imageUrl = `/api/ai-receipts/batches/${batchId}/items/${itemId}/receipt`;

// Display in <img> tag
<img src={imageUrl} alt="Receipt" />

// Or fetch with authorization
const response = await fetch(imageUrl, {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

if (response.ok) {
  const blob = await response.blob();
  const imageObjectUrl = URL.createObjectURL(blob);
  // Use imageObjectUrl in <img> tag
}
```

**Success Response (200 OK):**

Returns the image file with appropriate `Content-Type` header:
- `image/jpeg` for JPEG files
- `image/png` for PNG files

**Content-Disposition:** `inline; filename="receipt_1730476200123.jpg"`

**Error Responses:**

- `404 Not Found` - Batch item or image file not found
- `400 Bad Request` - Item doesn't belong to specified batch
- `403 Forbidden` - User doesn't have permission to access this batch

**Usage Example - Display Low Confidence Receipts:**

```javascript
function displayLowConfidenceItems(batchId, items) {
  items.forEach(item => {
    if (item.status === 'LOW_CONFIDENCE') {
      const imageUrl = `/api/ai-receipts/batches/${batchId}/items/${item.id}/receipt`;

      // Show image and manual entry form
      const html = `
        <div class="low-confidence-item">
          <img src="${imageUrl}" alt="Receipt">
          <p>Confidence: ${(item.confidence * 100).toFixed(0)}%</p>
          <button onclick="manualEntry(${item.id})">Enter Manually</button>
        </div>
      `;

      document.getElementById('review-container').innerHTML += html;
    }
  });
}
```

---

## File Upload Requirements

### Supported Formats
- JPEG (.jpg, .jpeg)
- PNG (.png)

### Size Limits
- **Per file:** 10 MB (default, configurable)
- **Per batch:** 1-5 images

### Image Quality Recommendations
- Minimum resolution: 800x600 pixels
- Clear, well-lit images
- Receipt should be flat and fully visible
- Avoid blurry or cropped images

---

## Frontend Implementation Guide

### Step 1: File Upload Form

```html
<form id="receiptUploadForm">
  <label>Select Vehicle:</label>
  <select id="deviceSelect" required>
    <!-- Populate with user's devices -->
  </select>

  <label>Upload Receipts (Max 5):</label>
  <input type="file" id="receiptFiles"
         accept="image/jpeg,image/png"
         multiple
         required
         max="5">

  <button type="submit">Upload & Process</button>
</form>
```

### Step 2: Upload Handler

```javascript
document.getElementById('receiptUploadForm').addEventListener('submit', async (e) => {
  e.preventDefault();

  const deviceId = document.getElementById('deviceSelect').value;
  const files = document.getElementById('receiptFiles').files;

  // Validate file count
  if (files.length === 0 || files.length > 5) {
    alert('Please select 1-5 receipt images');
    return;
  }

  // Validate file types
  const validTypes = ['image/jpeg', 'image/png'];
  for (let file of files) {
    if (!validTypes.includes(file.type)) {
      alert(`Invalid file type: ${file.name}. Only JPEG and PNG are supported.`);
      return;
    }
  }

  // Create FormData
  const formData = new FormData();
  formData.append('deviceId', deviceId);
  for (let file of files) {
    formData.append('receipts', file);
  }

  // Show loading indicator
  showLoading('Uploading receipts...');

  try {
    // Upload
    const response = await fetch('/api/ai-receipts/batches', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${getAuthToken()}`
      },
      body: formData
    });

    const result = await response.json();

    if (result.success) {
      // Start polling for status
      pollBatchStatus(result.data.batchId);
    } else {
      hideLoading();
      showError(result.error.message);
    }
  } catch (error) {
    hideLoading();
    showError('Upload failed: ' + error.message);
  }
});
```

### Step 3: Poll for Results

```javascript
async function pollBatchStatus(batchId) {
  const maxAttempts = 60; // Poll for up to 1 minute
  const interval = 2000; // Poll every 2 seconds
  let attempts = 0;

  const pollInterval = setInterval(async () => {
    attempts++;

    try {
      const response = await fetch(`/api/ai-receipts/batches/${batchId}`, {
        headers: {
          'Authorization': `Bearer ${getAuthToken()}`
        }
      });

      const result = await response.json();

      if (!result.success) {
        clearInterval(pollInterval);
        hideLoading();
        showError(result.error.message);
        return;
      }

      const status = result.data.status;

      // Update progress message
      updateLoadingMessage(`Processing... ${result.data.successCount}/${result.data.totalReceipts} complete`);

      // Check if batch is complete
      if (status === 'COMPLETED' || status === 'PARTIAL_SUCCESS' || status === 'FAILED') {
        clearInterval(pollInterval);
        hideLoading();
        displayResults(result.data);
      }

      // Timeout after max attempts
      if (attempts >= maxAttempts) {
        clearInterval(pollInterval);
        hideLoading();
        showError('Processing timed out. Please check batch status manually.');
      }
    } catch (error) {
      clearInterval(pollInterval);
      hideLoading();
      showError('Failed to check status: ' + error.message);
    }
  }, interval);
}
```

### Step 4: Display Results

```javascript
function displayResults(batchData) {
  const successItems = batchData.items.filter(item => item.status === 'SUCCESS');
  const lowConfidenceItems = batchData.items.filter(item => item.status === 'LOW_CONFIDENCE');
  const failedItems = batchData.items.filter(item => item.status === 'FAILED');

  let message = `Batch processing completed!\n\n`;
  message += `✅ ${successItems.length} expense(s) created automatically\n`;

  if (lowConfidenceItems.length > 0) {
    message += `⚠️ ${lowConfidenceItems.length} receipt(s) require manual entry (low confidence)\n`;
  }

  if (failedItems.length > 0) {
    message += `❌ ${failedItems.length} receipt(s) failed to process\n`;
  }

  // Show modal with results
  showResultsModal({
    message: message,
    successItems: successItems,
    lowConfidenceItems: lowConfidenceItems,
    failedItems: failedItems
  });

  // Navigate to expenses page to see created records
  if (successItems.length > 0) {
    // Option 1: Navigate automatically
    // window.location.href = `/expenses?deviceId=${batchData.deviceId}`;

    // Option 2: Show button to navigate
    showButton('View Created Expenses', () => {
      window.location.href = `/expenses?deviceId=${batchData.deviceId}`;
    });
  }
}
```

---

## Low Confidence Handling

When items have `status: "LOW_CONFIDENCE"`, the frontend should:

1. **Display the receipt image** for manual review
2. **Pre-fill the manual expense form** with extracted data (if available in future)
3. **Allow user to manually create the expense**
4. **Link the manual expense back to the batch item** (optional)

Example flow:
```javascript
function handleLowConfidenceItems(items) {
  items.forEach(item => {
    // Show receipt image and manual entry form
    showManualEntryForm({
      receiptImagePath: item.receiptImagePath,
      batchItemId: item.id,
      confidence: item.confidence
    });
  });
}
```

---

## Error Handling Best Practices

### 1. File Validation (Client-Side)

```javascript
function validateFiles(files) {
  const errors = [];

  if (files.length === 0) {
    errors.push('Please select at least one receipt image');
  }

  if (files.length > 5) {
    errors.push('Maximum 5 receipts allowed per batch');
  }

  const validTypes = ['image/jpeg', 'image/png'];
  const maxSize = 10 * 1024 * 1024; // 10MB

  for (let file of files) {
    if (!validTypes.includes(file.type)) {
      errors.push(`${file.name}: Invalid file type. Only JPEG and PNG allowed.`);
    }

    if (file.size > maxSize) {
      errors.push(`${file.name}: File too large (max 10MB)`);
    }
  }

  return errors;
}
```

### 2. Network Error Handling

```javascript
try {
  const response = await fetch(url, options);

  if (!response.ok) {
    // HTTP error
    const error = await response.json();
    throw new Error(error.error?.message || `HTTP ${response.status}`);
  }

  return await response.json();
} catch (error) {
  if (error.name === 'TypeError' && error.message.includes('Failed to fetch')) {
    // Network error
    showError('Network error. Please check your connection.');
  } else {
    // Other errors
    showError(error.message);
  }
}
```

---

## Expected Processing Times

- **Upload:** < 2 seconds (depends on network and file size)
- **Azure AI Processing:** 3-10 seconds per receipt
- **Total batch processing:** ~10-50 seconds for 5 receipts

---

## Real-Time Updates: Polling vs WebSocket

### Current Implementation: Polling (Recommended)

The current API uses **HTTP polling** for status updates:

**Advantages:**
- ✅ Simple to implement
- ✅ Works with all browsers/clients
- ✅ No persistent connection needed
- ✅ Already implemented and tested

**Implementation:**
```javascript
// Poll every 2 seconds, timeout after 60 seconds
const pollInterval = setInterval(async () => {
  const result = await fetch(`/api/ai-receipts/batches/${batchId}`);
  // ... check status
}, 2000);
```

**Recommendation:** Poll every 2-3 seconds, timeout after 60 seconds.

---

### Future Option: WebSocket Push Notifications

**Status:** ⚠️ **NOT YET IMPLEMENTED** (Planned for Phase 5)

If real-time push notifications are needed, WebSocket support can be added:

**Proposed WebSocket Endpoint:**
```
ws://your-domain/api/websocket
```

**Proposed Message Format:**
```json
{
  "type": "AI_RECEIPT_BATCH_UPDATE",
  "data": {
    "batchId": 456,
    "status": "PROCESSING",
    "successCount": 2,
    "totalReceipts": 5
  }
}
```

**When to Use WebSocket:**
- User needs instant updates (< 1 second latency)
- Processing very large batches (> 10 receipts)
- Want to reduce server load from frequent polling

**Decision Needed:**
- **Option A (Current):** Continue with polling - Simple, works well for 5 receipts
- **Option B (Future):** Implement WebSocket - Better UX, more complex

**For now, please use polling.** If WebSocket is critical for your use case, let the backend team know and we can implement it in Phase 5.

---

---

## Testing Checklist

- [ ] Upload 1 receipt successfully
- [ ] Upload 5 receipts (max) successfully
- [ ] Try uploading 6 receipts (should fail)
- [ ] Try uploading non-image file (should fail)
- [ ] Try uploading oversized image (should fail)
- [ ] Test with invalid deviceId (should fail)
- [ ] Test without authentication (should fail with 401)
- [ ] Poll batch status until completion
- [ ] View receipt image for completed items
- [ ] View receipt image for low confidence items
- [ ] View receipt image for failed items
- [ ] Handle low confidence results with image display
- [ ] Handle failed items with image display
- [ ] Test network error handling
- [ ] Test timeout handling
- [ ] Test receipt image 404 (item not found)
- [ ] Test receipt image 403 (permission denied)

---

## Example: Complete Upload Flow

```javascript
class ReceiptUploader {
  constructor(authToken) {
    this.authToken = authToken;
    this.baseUrl = '/api/ai-receipts/batches';
  }

  async uploadReceipts(deviceId, files) {
    // Validate
    const errors = this.validateFiles(files);
    if (errors.length > 0) {
      throw new Error(errors.join('\n'));
    }

    // Upload
    const formData = new FormData();
    formData.append('deviceId', deviceId);
    files.forEach(file => formData.append('receipts', file));

    const response = await fetch(this.baseUrl, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${this.authToken}` },
      body: formData
    });

    const result = await response.json();

    if (!result.success) {
      throw new Error(result.error.message);
    }

    return result.data;
  }

  async pollBatchStatus(batchId, onProgress) {
    return new Promise((resolve, reject) => {
      const maxAttempts = 60;
      let attempts = 0;

      const interval = setInterval(async () => {
        attempts++;

        try {
          const response = await fetch(`${this.baseUrl}/${batchId}`, {
            headers: { 'Authorization': `Bearer ${this.authToken}` }
          });

          const result = await response.json();

          if (!result.success) {
            clearInterval(interval);
            reject(new Error(result.error.message));
            return;
          }

          const { status, successCount, totalReceipts } = result.data;

          // Call progress callback
          if (onProgress) {
            onProgress(successCount, totalReceipts, status);
          }

          // Check completion
          if (['COMPLETED', 'PARTIAL_SUCCESS', 'FAILED'].includes(status)) {
            clearInterval(interval);
            resolve(result.data);
            return;
          }

          // Timeout
          if (attempts >= maxAttempts) {
            clearInterval(interval);
            reject(new Error('Processing timeout'));
          }
        } catch (error) {
          clearInterval(interval);
          reject(error);
        }
      }, 2000);
    });
  }

  validateFiles(files) {
    const errors = [];
    const validTypes = ['image/jpeg', 'image/png'];
    const maxSize = 10 * 1024 * 1024;

    if (files.length === 0 || files.length > 5) {
      errors.push('Please select 1-5 receipt images');
    }

    for (let file of files) {
      if (!validTypes.includes(file.type)) {
        errors.push(`${file.name}: Invalid type`);
      }
      if (file.size > maxSize) {
        errors.push(`${file.name}: File too large`);
      }
    }

    return errors;
  }
}

// Usage:
const uploader = new ReceiptUploader(authToken);

try {
  // Upload
  const batch = await uploader.uploadReceipts(deviceId, files);
  console.log('Batch created:', batch.batchId);

  // Poll
  const result = await uploader.pollBatchStatus(batch.batchId, (success, total, status) => {
    console.log(`Progress: ${success}/${total} (${status})`);
  });

  console.log('Processing complete:', result);
} catch (error) {
  console.error('Error:', error.message);
}
```

---

## Support & Questions

For API issues or questions, contact the backend development team or refer to:
- Full design document: `AI_FEES_DESIGN.md`
- Classification rules: `EXPENSE_CLASSIFICATION_RULES.md`
- Implementation plan: `AI_FEES_IMPLEMENTATION_PLAN.md`
