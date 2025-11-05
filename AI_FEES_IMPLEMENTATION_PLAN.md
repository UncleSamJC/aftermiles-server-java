# AI+ Fees - Implementation File Structure and Dependencies

**Project**: Aftermiles Traccar Server
**Feature**: AI-Powered Batch Receipt Processing
**Date**: 2025-10-31

---

## File Structure Overview

```
src/main/java/org/traccar/
│
├── model/
│   ├── AiReceiptBatch.java                    [NEW] - Batch entity
│   ├── AiReceiptBatchItem.java                [NEW] - Batch item entity
│   └── Expense.java                            [MODIFY] - Add new fields (gst, pst, hst, etc.)
│
├── api/resource/
│   └── AiReceiptResource.java                 [NEW] - REST API endpoints
│
├── ai/
│   ├── AzureDocumentIntelligenceService.java  [NEW] - Azure Doc Intelligence integration
│   ├── GptReceiptEnhancer.java                [NEW] - GPT-3.5 data enhancement (Phase 3)
│   ├── AiReceiptProcessor.java                [NEW] - Async batch processor with worker threads
│   └── ReceiptDataExtractor.java              [NEW] - Receipt data class (simplified)
│
├── websocket/
│   └── AiReceiptWebSocketEndpoint.java        [NEW] - WebSocket endpoint (Phase 5)
│
├── dto/
│   └── ReceiptAnalysisResult.java             [NEW] - Azure AI response DTO
│
├── config/
│   └── Keys.java                              [MODIFY] - Add Azure config keys
│
└── database/
    └── changelog/
        └── changelog-6.11.xml                 [NEW] - Database migrations

build.gradle                                   [MODIFY] - Add Azure SDK dependencies
```

---

## Complete File List (Updated Architecture)

### Phase 1: Foundation (6 files)

#### 1. Database Schema
**File**: `src/main/resources/org/traccar/storage/changelog-6.11.xml`
- Create `tc_ai_receipts_batches` table
- Create `tc_ai_receipts_batch_items` table
- Alter `tc_expenses` table (add 7 new columns)

#### 2. Data Models
**File**: `src/main/java/org/traccar/model/AiReceiptBatch.java`
```
Fields:
- id, deviceId, userId
- status (PENDING/PROCESSING/COMPLETED/PARTIAL_SUCCESS/FAILED)
- totalReceipts, successCount, failedCount, lowConfidenceCount
- createdTime, startedTime, completedTime
```

**File**: `src/main/java/org/traccar/model/AiReceiptBatchItem.java`
```
Fields:
- id, batchId
- receiptImagePath
- status (PENDING/PROCESSING/SUCCESS/FAILED/LOW_CONFIDENCE)
- confidence, expenseId
- errorMessage, rawAiResponse
- createdTime, processedTime
```

**File**: `src/main/java/org/traccar/model/Expense.java` (MODIFY)
```
Add fields:
- gst (BigDecimal)
- pst (BigDecimal)
- hst (BigDecimal)
- totalTax (BigDecimal)
- country (String)
- provinceState (String)
- batchItemId (Long)
```

#### 3. Configuration
**File**: `src/main/java/org/traccar/config/Keys.java` (MODIFY)
```
Add keys:
- AZURE_DOCUMENT_INTELLIGENCE_ENDPOINT
- AZURE_DOCUMENT_INTELLIGENCE_KEY
- AZURE_OPENAI_ENDPOINT (Phase 3 - GPT Enhancement)
- AZURE_OPENAI_KEY (Phase 3 - GPT Enhancement)
- AZURE_OPENAI_DEPLOYMENT (Phase 3 - e.g., gpt-35-turbo)
- AZURE_OPENAI_API_VERSION (Phase 3 - default: 2024-08-01-preview)
- AZURE_OPENAI_DEPLOYMENT_NAME (Phase 3)
- AI_RECEIPT_CONFIDENCE_THRESHOLD
- AI_RECEIPT_MAX_BATCH_SIZE
- AI_RECEIPT_WORKER_THREADS
```

#### 4. API Resource
**File**: `src/main/java/org/traccar/api/resource/AiReceiptResource.java`
```
Endpoints (Phase 1 stubs):
- POST /api/ai-receipts/batches
- GET /api/ai-receipts/batches/{id}
- GET /api/ai-receipts/batches/{batchId}/receipts/{itemId}
- GET /api/ai-receipts/batches (list)
```

---

### Phase 2: Azure AI Integration (4 files)

#### 5. Azure Service
**File**: `src/main/java/org/traccar/service/AzureDocumentIntelligenceService.java`
```
Methods:
- analyzeReceipt(String imagePath) → ReceiptAnalysisResult
- extractFields(AnalyzedDocument)
- inferCategory(AnalyzedDocument)
```

#### 6. DTO
**File**: `src/main/java/org/traccar/dto/ReceiptAnalysisResult.java`
```
Fields:
- merchantName, merchantAddress
- amount, currency
- transactionDate
- gst, pst, hst, totalTax
- country, provinceState
- rawCategory
- confidence
- rawResponse (JSON string)
```

#### 7. Async Task
**File**: `src/main/java/org/traccar/async/BatchProcessingTask.java`
```
Fields:
- batchId
- userId
```

#### 8. Async Processor
**File**: `src/main/java/org/traccar/async/BatchProcessor.java`
```
Methods:
- processBatch(batchId)
- processReceiptItem(item)
- handleSuccess(item, expense)
- handleLowConfidence(item, result)
- handleFailure(item, exception)
```

---

### Phase 3: Category Mapping (2 files)

#### 9. OpenAI Service
**File**: `src/main/java/org/traccar/service/AzureOpenAiService.java`
```
Methods:
- mapCategory(String rawCategory) → String
- applyRuleMappings(String rawCategory) → String (nullable)
- mapCategoryWithAI(String rawCategory) → String
```

#### 10. Rule Engine (optional, embedded in AzureOpenAiService)
```
Rule mappings for 70% of cases
```

---

### Phase 4: Batch Processing Service (2 files)

#### 11. Batch Processor Service
**File**: `src/main/java/org/traccar/service/BatchProcessorService.java`
```
Components:
- ExecutorService threadPool
- BlockingQueue<BatchProcessingTask> queue
- List<BatchProcessor> workers

Methods:
- start() (lifecycle)
- stop() (lifecycle)
- submitBatch(batchId)
```

#### 12. Expense Creation Logic
Integrate into `BatchProcessor.java`:
```
Methods:
- createExpenseFromResult(item, result, mappedCategory)
- linkExpenseToBatchItem(expense, item)
```

---

### Phase 5: WebSocket (3 files)

#### 13. WebSocket Endpoint
**File**: `src/main/java/org/traccar/websocket/AiReceiptWebSocketEndpoint.java`
```
Annotations:
- @ServerEndpoint("/ws/ai-receipts")

Methods:
- onOpen(Session, EndpointConfig)
- onMessage(String message, Session)
- onClose(Session)

Static maps:
- Map<userId, Set<Session>> userSessions
- Map<batchId, Set<Session>> batchSubscriptions
```

#### 14. WebSocket Notifier
**File**: `src/main/java/org/traccar/service/WebSocketNotifier.java`
```
Methods:
- sendBatchCompletionEvent(userId, batchId, completionData)
- sendBatchProgressEvent(userId, batchId, progress) (optional)
- buildCompletionMessage(batch, items) → JSON string
```

#### 15. WebSocket Configurator
**File**: `src/main/java/org/traccar/websocket/WebSocketEndpointConfigurator.java` (may exist)
- Handle authentication
- Set userId in EndpointConfig

---

### Phase 6: Dependencies & Tests (3 files)

#### 16. Build Configuration
**File**: `build.gradle` (MODIFY)
```gradle
dependencies {
    // Azure Document Intelligence
    implementation 'com.azure:azure-ai-formrecognizer:4.1.13'

    // Azure OpenAI (Phase 3)
    implementation 'com.azure:azure-ai-openai:1.0.0-beta.8'

    // WebSocket (may already exist)
    implementation 'jakarta.websocket:jakarta.websocket-api:2.1.0'
}
```

#### 17. Integration Test
**File**: `src/test/java/org/traccar/api/AiReceiptResourceTest.java`
```
Tests:
- testSubmitBatch()
- testGetBatchStatus()
- testGetReceipt()
- testPermissions()
```

#### 18. Service Test
**File**: `src/test/java/org/traccar/service/AzureDocumentIntelligenceServiceTest.java`
```
Tests (with mock):
- testAnalyzeReceipt()
- testFieldExtraction()
- testCategoryInference()
```

---

## Call Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                          FRONTEND                                   │
│  (Submit Receipt Batch)                                             │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ HTTP POST
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│ AiReceiptResource.java                                              │
│  - POST /api/ai-receipts/batches                                    │
│                                                                     │
│  1. Validate user & device permissions                              │
│  2. Validate files (count, type, size)                              │
│  3. Save images via MediaManager                                    │
│  4. Create AiReceiptBatch in DB                                     │
│  5. Create AiReceiptBatchItem(s) in DB                              │
│  6. Submit to BatchProcessorService                                 │
│  7. Return batch ID immediately                                     │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            │ submitBatch(batchId)
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│ BatchProcessorService.java                                          │
│  - Manages ExecutorService + BlockingQueue                          │
│                                                                     │
│  → Add BatchProcessingTask to queue                                 │
│  → Worker thread picks up task                                      │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            │ Worker Thread
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│ BatchProcessor.java                                                 │
│  - processBatch(batchId)                                            │
│                                                                     │
│  For each AiReceiptBatchItem:                                       │
│    ┌──────────────────────────────────────┐                        │
│    │ 1. Call Azure Document Intelligence  │                        │
│    │    ↓                                  │                        │
│    │    AzureDocumentIntelligenceService  │                        │
│    │    - analyzeReceipt(imagePath)       │                        │
│    │    - Returns ReceiptAnalysisResult   │                        │
│    └──────────────────────────────────────┘                        │
│                       │                                             │
│    ┌──────────────────▼──────────────────┐                         │
│    │ 2. Map Category (Phase 3)           │                         │
│    │    ↓                                 │                         │
│    │    AzureOpenAiService                │                         │
│    │    - mapCategory(rawCategory)        │                         │
│    │    - Returns mapped category         │                         │
│    └──────────────────────────────────────┘                        │
│                       │                                             │
│    ┌──────────────────▼──────────────────┐                         │
│    │ 3. Check Confidence                  │                         │
│    │    IF confidence >= 0.96:            │                         │
│    │      - Create Expense in DB          │                         │
│    │      - Link to BatchItem             │                         │
│    │      - Set status = SUCCESS          │                         │
│    │    ELSE:                             │                         │
│    │      - Set status = LOW_CONFIDENCE   │                         │
│    │      - Store rawAiResponse           │                         │
│    └──────────────────────────────────────┘                        │
│                       │                                             │
│  Update batch statistics                                            │
│  Set batch status = COMPLETED / PARTIAL_SUCCESS                     │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            │ Batch Complete
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│ WebSocketNotifier.java                                              │
│  - sendBatchCompletionEvent(userId, batchId, data)                  │
│                                                                     │
│  Build JSON message with:                                           │
│    - Batch summary (success/failed/low confidence counts)           │
│    - Item details (expenseId, receiptUrl, extractedData)            │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            │ WebSocket Message
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│ AiReceiptWebSocketEndpoint.java                                     │
│  - Lookup subscribed sessions for batchId                           │
│  - Send message to all subscribed sessions                          │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            │ WebSocket Push
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          FRONTEND                                   │
│  - Receive batch completion notification                            │
│  - Display results (success/failed items)                           │
│  - Allow manual entry for low confidence items                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Dependency Injection (Guice Module)

**File to modify**: `src/main/java/org/traccar/MainModule.java`

```java
// Add bindings for new services
bind(AzureDocumentIntelligenceService.class).in(Singleton.class);
bind(AzureOpenAiService.class).in(Singleton.class);  // Phase 3
bind(BatchProcessorService.class).in(Singleton.class);
bind(WebSocketNotifier.class).in(Singleton.class);

// Lifecycle management
bind(LifecycleObject.class).to(BatchProcessorService.class);
```

---

## File Dependencies Matrix

| File | Depends On | Used By |
|------|-----------|---------|
| **AiReceiptBatch.java** | ExtendedModel | Storage, AiReceiptResource |
| **AiReceiptBatchItem.java** | ExtendedModel | Storage, AiReceiptResource, BatchProcessor |
| **Expense.java** | ExtendedModel | BatchProcessor, ExpenseResource |
| **Keys.java** | Config | All services |
| **AiReceiptResource.java** | Storage, MediaManager, PermissionsService, BatchProcessorService | Frontend (REST API) |
| **AzureDocumentIntelligenceService.java** | Config, ReceiptAnalysisResult | BatchProcessor |
| **AzureOpenAiService.java** | Config | BatchProcessor |
| **ReceiptAnalysisResult.java** | (none) | AzureDocumentIntelligenceService, BatchProcessor |
| **BatchProcessingTask.java** | (none) | BatchProcessorService, BatchProcessor |
| **BatchProcessor.java** | Storage, AzureDocumentIntelligenceService, AzureOpenAiService, WebSocketNotifier | BatchProcessorService |
| **BatchProcessorService.java** | Config, BatchProcessor | AiReceiptResource |
| **WebSocketNotifier.java** | AiReceiptWebSocketEndpoint | BatchProcessor |
| **AiReceiptWebSocketEndpoint.java** | (none) | Frontend (WebSocket), WebSocketNotifier |

---

## Key Design Decisions

### 1. Package Structure
```
org.traccar.model.*          - Data entities (persist to DB)
org.traccar.api.resource.*   - REST endpoints (Jersey)
org.traccar.service.*        - Business logic services (stateless where possible)
org.traccar.async.*          - Async processing components
org.traccar.websocket.*      - WebSocket endpoints
org.traccar.dto.*            - Data Transfer Objects (non-persisted)
```

### 2. Singleton Services
All services are Guice singletons:
- Thread-safe
- Shared across requests
- Lifecycle managed by Traccar

### 3. Async Processing Strategy
- **Queue**: `LinkedBlockingQueue<BatchProcessingTask>`
- **Thread Pool**: Fixed size (default: 2 workers)
- **Task**: Lightweight wrapper containing only batchId

### 4. Error Propagation
```
Azure Service Error → BatchProcessor (catch) → Update item status=FAILED
                                              → Continue other items (partial success)
```

### 5. Transaction Boundaries
- **Synchronous (API)**: Upload + save batch + enqueue (1 transaction)
- **Asynchronous (Worker)**: Each item update (separate transactions)

---

## Implementation Phases with File Checklist

### Phase 1: Foundation ✅
- [x] changelog-6.11.xml
- [x] AiReceiptBatch.java
- [x] AiReceiptBatchItem.java
- [x] Expense.java (modify)
- [x] Keys.java (modify)
- [x] AiReceiptResource.java (stub)

**Verification**: Can submit batch, get batch ID, query batch status (no AI yet)

---

### Phase 2: Azure AI Integration ✅
- [x] build.gradle (add Azure SDK)
- [x] AzureDocumentIntelligenceService.java
- [x] ReceiptAnalysisResult.java
- [x] BatchProcessingTask.java
- [x] BatchProcessor.java (basic version)
- [x] BatchProcessorService.java

**Verification**: Submit batch → Azure extracts data → saves to DB (no category mapping yet)

---

### Phase 3: Category Mapping ✅
- [x] AzureOpenAiService.java
- [x] Update BatchProcessor.java (integrate category mapping)

**Verification**: Categories correctly mapped to database enums

---

### Phase 4: Expense Auto-Creation ✅
- [x] Update BatchProcessor.java (add expense creation logic)
- [x] Confidence threshold handling

**Verification**: High confidence → Expense created; Low confidence → flagged for review

---

### Phase 5: WebSocket ✅
- [x] AiReceiptWebSocketEndpoint.java
- [x] WebSocketNotifier.java
- [x] Update BatchProcessor.java (call notifier on completion)

**Verification**: Frontend receives real-time notification

---

### Phase 6: Testing & Polish ✅
- [x] AiReceiptResourceTest.java
- [x] AzureDocumentIntelligenceServiceTest.java
- [x] Integration tests
- [x] Error handling refinement

**Verification**: All tests pass, error cases handled

---

## TODO: Azure OpenAI Configuration

**Status**: Pending access approval

**Action Items**:
1. ⏳ Apply for Azure OpenAI access: https://aka.ms/oai/access
2. ⏳ Create Azure OpenAI resource in "AM" resource group
3. ⏳ Deploy gpt-35-turbo model
4. ⏳ Get endpoint and key
5. ⏳ Update AI_FEES_DESIGN.md with credentials
6. ⏳ Test category mapping

**Fallback Plan**: Use rule-based mapping only (70-80% accuracy, $0 cost)

---

## Critical Path Items

**Must Have (Phase 1-4)**:
1. ✅ Database schema
2. ✅ File upload + storage
3. ✅ Azure Document Intelligence integration
4. ✅ Async processing queue
5. ✅ Expense creation

**Nice to Have (Phase 5-6)**:
6. ⏳ WebSocket notifications (can use polling as fallback)
7. ⏳ Azure OpenAI category mapping (can use rules only)
8. ⏳ Progress updates (can just show completion)

---

## Estimated LOC (Lines of Code)

| File | Est. LOC | Complexity |
|------|----------|------------|
| AiReceiptBatch.java | 150 | Low |
| AiReceiptBatchItem.java | 150 | Low |
| Expense.java (changes) | +50 | Low |
| Keys.java (changes) | +100 | Low |
| AiReceiptResource.java | 500 | Medium |
| AzureDocumentIntelligenceService.java | 300 | High |
| AzureOpenAiService.java | 200 | Medium |
| ReceiptAnalysisResult.java | 100 | Low |
| BatchProcessingTask.java | 50 | Low |
| BatchProcessor.java | 400 | High |
| BatchProcessorService.java | 200 | Medium |
| WebSocketNotifier.java | 150 | Medium |
| AiReceiptWebSocketEndpoint.java | 200 | Medium |
| Tests | 400 | Medium |
| **Total** | **~3000 LOC** | |

**Development Time Estimate**: 6-8 weeks (1 developer, full-time)

---

## Next Steps

1. **Review this plan** with stakeholders
2. **Confirm Azure OpenAI strategy** (apply for access or use rules only?)
3. **Start Phase 1** (database + models + API skeleton)
4. **Set up development environment** (Azure credentials, test receipts)
5. **Create feature branch**: `feature/ai-fees`

---

**Document Status**: Ready for Development
**Last Updated**: 2025-10-31
**Approved By**: [Pending]
