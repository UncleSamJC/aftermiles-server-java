# 报税季User Quota市场活动技术方案 V2.0（最终版）

## 📋 方案概述

**核心设计原则**：
1. ✅ `usertype` **仅存储在** `tcaf_user_receipt_quota` 表中
2. ✅ **不改动** `tc_users` 表结构
3. ✅ 使用枚举标准化用户类型配置

---

## 🗄️ 数据库设计

### 1. 用户表（tc_users）- **无需改动**

```sql
-- tc_users 表保持原样，不添加 usertype 字段
-- 使用现有的 expirationTime 字段
```

### 2. 收据扫描限额表（tcaf_user_receipt_quota）- 新建表

```sql
CREATE TABLE tcaf_user_receipt_quota (
    id BIGSERIAL PRIMARY KEY,
    userid BIGINT NOT NULL,
    year INT NOT NULL,                    -- 年度（2025, 2026...）
    usertype VARCHAR(64) NOT NULL,        -- 用户类型（TRIAL_2025, TAX_SEASON_2025等）
    maxlimit INT NOT NULL,                -- 最大扫描限额
    currentusage INT DEFAULT 0,           -- 当前已使用数量
    createdat TIMESTAMP DEFAULT NOW(),
    updatedat TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_user_receipt_quota_userid
        FOREIGN KEY (userid) REFERENCES tc_users(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_year UNIQUE (userid, year),
    CONSTRAINT chk_current_usage CHECK (currentusage >= 0),
    CONSTRAINT chk_max_limit CHECK (maxlimit > 0)
);

CREATE INDEX idx_user_receipt_quota_userid ON tcaf_user_receipt_quota(userid);
CREATE INDEX idx_user_receipt_quota_year ON tcaf_user_receipt_quota(year);
```

### 3. 收据扫描历史表（tcaf_user_receipt_usage_log）- 可选，用于审计

```sql
CREATE TABLE tcaf_user_receipt_usage_log (
    id BIGSERIAL PRIMARY KEY,
    userid BIGINT NOT NULL,
    receiptid BIGINT,                     -- 关联的收据ID
    action VARCHAR(32) NOT NULL,          -- 'ADD' 或 'REMOVE'
    changeamount INT NOT NULL,            -- +1 或 -1
    usagebefore INT NOT NULL,             -- 操作前的usage
    usageafter INT NOT NULL,              -- 操作后的usage
    createdat TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_user_receipt_log_userid
        FOREIGN KEY (userid) REFERENCES tc_users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_receipt_log_userid ON tcaf_user_receipt_usage_log(userid);
CREATE INDEX idx_user_receipt_log_createdat ON tcaf_user_receipt_usage_log(createdat);
```

---

## 📦 Java 模型设计

### 1. 用户类型枚举（UserType.java）

```java
package org.traccar.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 用户类型枚举 - 对应不同的营销活动
 */
public enum UserType {

    /**
     * 试用用户 - 7天有效期，50张扫描
     */
    TRIAL_2025("TRIAL_2025", 7, 50, "2025试用用户"),

    /**
     * 2025报税季付费用户 - 至2026.5.1，500张扫描
     */
    TAX_SEASON_2025("TAX_SEASON_2025", -1, 500, "2025报税季用户") {
        @Override
        public Date getExpirationDate() {
            return parseDate("2026-05-01");
        }
    },

    /**
     * 2026报税季付费用户 - 至2027.5.1，500张扫描
     */
    TAX_SEASON_2026("TAX_SEASON_2026", -1, 500, "2026报税季用户") {
        @Override
        public Date getExpirationDate() {
            return parseDate("2027-05-01");
        }
    },

    /**
     * 标准年度用户 - 1年有效期，1000张扫描
     */
    ANNUAL_USER("ANNUAL_USER", 365, 1000, "年度标准用户");

    private final String code;
    private final int validityDays;      // -1表示使用固定到期日期
    private final int scanQuota;         // -1表示无限额
    private final String displayName;

    UserType(String code, int validityDays, int scanQuota, String displayName) {
        this.code = code;
        this.validityDays = validityDays;
        this.scanQuota = scanQuota;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public int getValidityDays() {
        return validityDays;
    }

    public int getScanQuota() {
        return scanQuota;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取到期时间
     */
    public Date getExpirationDate() {
        if (validityDays == -1) {
            return null; // 子类覆盖
        }
        return new Date(System.currentTimeMillis() + validityDays * 86400000L);
    }

    /**
     * 从代码获取枚举
     */
    public static UserType fromCode(String code) {
        for (UserType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown user type: " + code);
    }

    private static Date parseDate(String dateStr) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
```

### 2. 收据配额模型（UserReceiptQuota.java）

```java
package org.traccar.model;

import org.traccar.storage.StorageName;
import java.util.Date;

@StorageName("tcaf_user_receipt_quota")
public class UserReceiptQuota extends BaseModel {

    private long userId;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    private int year;

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    private String userType;

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    private int maxLimit;

    public int getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(int maxLimit) {
        this.maxLimit = maxLimit;
    }

    private int currentUsage;

    public int getCurrentUsage() {
        return currentUsage;
    }

    public void setCurrentUsage(int currentUsage) {
        this.currentUsage = currentUsage;
    }

    private Date createdAt;

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    private Date updatedAt;

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 获取剩余额度
     */
    public int getRemainingQuota() {
        if (maxLimit == -1) {
            return 10000; // 最大返回10000，避免无限
        }
        return Math.max(0, maxLimit - currentUsage);
    }

    /**
     * 检查是否还有额度
     */
    public boolean hasQuota() {
        return maxLimit == -1 || currentUsage < maxLimit;
    }

    /**
     * 获取用户类型枚举
     */
    public UserType getUserTypeEnum() {
        return userType != null ? UserType.fromCode(userType) : null;
    }
}
```

---

## 🔧 业务逻辑层

### 收据配额管理服务（ReceiptQuotaManager.java）

```java
package org.traccar.manager;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.traccar.model.User;
import org.traccar.model.UserReceiptQuota;
import org.traccar.model.UserType;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.*;

import java.util.Calendar;
import java.util.Date;

@Singleton
public class ReceiptQuotaManager {

    private final Storage storage;

    @Inject
    public ReceiptQuotaManager(Storage storage) {
        this.storage = storage;
    }

    /**
     * 为用户初始化配额（创建用户时调用）
     *
     * @param userId 用户ID
     * @param userType 用户类型枚举
     */
    public UserReceiptQuota initializeQuota(long userId, UserType userType) throws StorageException {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        UserReceiptQuota quota = new UserReceiptQuota();
        quota.setUserId(userId);
        quota.setYear(currentYear);
        quota.setUserType(userType.getCode());
        quota.setMaxLimit(userType.getScanQuota());
        quota.setCurrentUsage(0);
        quota.setCreatedAt(new Date());
        quota.setUpdatedAt(new Date());

        quota.setId(storage.addObject(quota, new Request(new Columns.Exclude("id"))));
        return quota;
    }

    /**
     * 获取用户当前年度配额
     */
    public UserReceiptQuota getCurrentQuota(long userId) throws StorageException {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        return getQuotaByYear(userId, currentYear);
    }

    /**
     * 获取指定年度配额
     */
    public UserReceiptQuota getQuotaByYear(long userId, int year) throws StorageException {
        return storage.getObject(UserReceiptQuota.class, new Request(
            new Columns.All(),
            new Condition.And(
                new Condition.Equals("userId", userId),
                new Condition.Equals("year", year)
            )
        ));
    }

    /**
     * 检查用户是否还有扫描额度
     */
    public boolean hasQuota(long userId) throws StorageException {
        UserReceiptQuota quota = getCurrentQuota(userId);
        if (quota == null) {
            return false;
        }
        return quota.hasQuota();
    }

    /**
     * 获取剩余额度
     */
    public int getRemainingQuota(long userId) throws StorageException {
        UserReceiptQuota quota = getCurrentQuota(userId);
        if (quota == null) {
            return 0;
        }
        return quota.getRemainingQuota();
    }

    /**
     * 增加使用量（扫描一张收据）
     */
    public synchronized void incrementReceiptUsage(long userId, Long receiptId) throws StorageException {
        UserReceiptQuota quota = getCurrentQuota(userId);
        if (quota == null) {
            throw new IllegalStateException("User quota not found");
        }

        if (!quota.hasQuota()) {
            throw new SecurityException("Scan quota exceeded");
        }

        int usageBefore = quota.getCurrentUsage();
        quota.setCurrentUsage(usageBefore + 1);
        quota.setUpdatedAt(new Date());

        storage.updateObject(quota, new Request(
            new Columns.Include("currentUsage", "updatedAt"),
            new Condition.Equals("id", quota.getId())
        ));

        // 记录日志（可选）
        logUsageChange(userId, receiptId, "ADD", 1, usageBefore, usageBefore + 1);
    }

    /**
     * 减少使用量（删除收据）
     */
    public synchronized void decrementReceiptUsage(long userId, Long receiptId) throws StorageException {
        UserReceiptQuota quota = getCurrentQuota(userId);
        if (quota == null) {
            throw new IllegalStateException("User quota not found");
        }

        int usageBefore = quota.getCurrentUsage();
        if (usageBefore <= 0) {
            return; // 已经是0了，不能再减
        }

        quota.setCurrentUsage(usageBefore - 1);
        quota.setUpdatedAt(new Date());

        storage.updateObject(quota, new Request(
            new Columns.Include("currentUsage", "updatedAt"),
            new Condition.Equals("id", quota.getId())
        ));

        // 记录日志（可选）
        logUsageChange(userId, receiptId, "REMOVE", -1, usageBefore, usageBefore - 1);
    }

    /**
     * 升级用户类型（例如从试用升级为付费）
     *
     * @param userId 用户ID
     * @param newUserType 新的用户类型
     */
    public void upgradeUserType(long userId, UserType newUserType) throws StorageException {
        // 1. 更新用户的过期时间（仅更新 expirationTime）
        User user = storage.getObject(User.class, new Request(
            new Columns.All(),
            new Condition.Equals("id", userId)
        ));

        user.setExpirationTime(newUserType.getExpirationDate());
        user.setTemporary(false); // 付费后不再是临时用户

        storage.updateObject(user, new Request(
            new Columns.Include("expirationTime", "temporary"),
            new Condition.Equals("id", userId)
        ));

        // 2. 更新当前年度配额（保持 currentUsage 不变）
        UserReceiptQuota quota = getCurrentQuota(userId);
        if (quota != null) {
            quota.setUserType(newUserType.getCode());
            quota.setMaxLimit(newUserType.getScanQuota());
            quota.setUpdatedAt(new Date());

            storage.updateObject(quota, new Request(
                new Columns.Include("userType", "maxLimit", "updatedAt"),
                new Condition.Equals("id", quota.getId())
            ));
        } else {
            // 如果没有配额记录，创建一个
            initializeQuota(userId, newUserType);
        }
    }

    /**
     * 记录使用变更日志（可选实现）
     */
    private void logUsageChange(long userId, Long receiptId, String action,
                                int changeAmount, int usageBefore, int usageAfter) {
        // TODO: 实现日志记录逻辑
        // 可以插入到 tcaf_user_receipt_usage_log 表
    }
}
```

---

## 🌐 API接口设计

### 1. 创建试用用户 API

```java
package org.traccar.api.resource;

@Path("users/trial-registration")
@POST
@PermitAll
public Response createTrialUser(TrialRegistrationRequest request) throws StorageException {

    // 1. 验证邮箱是否已存在
    if (emailExists(request.getEmail())) {
        throw new SecurityException("Email already registered");
    }

    // 2. 创建用户实体
    User user = new User();
    user.setName(request.getName());
    user.setEmail(request.getEmail());
    user.setPassword(request.getPassword());
    user.setPhone(request.getPhone());

    // 设置试用期到期时间
    UserType userType = UserType.TRIAL_2025;
    user.setExpirationTime(userType.getExpirationDate());
    user.setTemporary(true); // 标记为临时用户

    // 3. 保存用户
    user.setId(storage.addObject(user, new Request(new Columns.Exclude("id"))));
    storage.updateObject(user, new Request(
        new Columns.Include("hashedPassword", "salt"),
        new Condition.Equals("id", user.getId())
    ));

    // 4. 初始化扫描配额（传入 userType）
    receiptQuotaManager.initializeQuota(user.getId(), userType);

    // 5. 发送欢迎邮件
    sendWelcomeEmail(user, userType);

    return Response.ok(user).build();
}

/**
 * 试用注册请求对象
 */
public static class TrialRegistrationRequest {
    private String name;
    private String email;
    private String password;
    private String phone;

    // Getters and Setters...
}
```

### 2. 升级为付费用户 API（线下现金付费，人工操作）

**业务流程**：
1. 用户线下支付现金给管理员
2. 管理员人工验证收款
3. 管理员在前端选中用户 → 选择新的用户类型（如 TAX_SEASON_2025）→ 点击"升级"
4. 系统更新用户账户和配额

```java
@Path("users/{id}/upgrade")
@POST
public Response upgradeUser(
        @PathParam("id") long userId,
        @QueryParam("userType") String userTypeCode,
        @QueryParam("remark") String remark) throws StorageException {

    // 1. 权限检查：只有管理员可以操作
    permissionsService.checkAdmin(getUserId());

    // 2. 解析新的用户类型
    UserType newUserType;
    try {
        newUserType = UserType.fromCode(userTypeCode);
    } catch (IllegalArgumentException e) {
        throw new WebApplicationException(
            Response.status(Response.Status.BAD_REQUEST)
                .entity("Invalid user type: " + userTypeCode)
                .build()
        );
    }

    // 3. 获取当前用户信息
    User user = storage.getObject(User.class, new Request(
        new Columns.All(), new Condition.Equals("id", userId)
    ));

    if (user == null) {
        throw new WebApplicationException(Response.Status.NOT_FOUND);
    }

    // 4. 升级用户类型（会更新 expirationTime 和 quota）
    receiptQuotaManager.upgradeUserType(userId, newUserType);

    // 5. 记录操作信息到 attributes
    user.set("lastUpgradeTime", new Date());
    user.set("lastUpgradeUserType", newUserType.getCode());
    user.set("upgradeOperator", getUserId()); // 记录操作的管理员ID

    if (remark != null && !remark.isEmpty()) {
        user.set("upgradeRemark", remark); // 备注信息（如：现金付款99元）
    }

    storage.updateObject(user, new Request(
        new Columns.Include("attributes"),
        new Condition.Equals("id", userId)
    ));

    // 6. 记录操作日志
    actionLogger.create(request, getUserId(),
        "User " + userId + " upgraded to " + newUserType.getCode());

    // 7. 发送升级成功邮件给用户
    sendUpgradeSuccessEmail(user, newUserType);

    // 8. 返回更新后的用户和配额信息
    UserReceiptQuota quota = receiptQuotaManager.getCurrentQuota(userId);

    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    response.put("user", user);
    response.put("quota", quota);
    response.put("newUserType", newUserType.getCode());
    response.put("newUserTypeName", newUserType.getDisplayName());
    response.put("expirationTime", user.getExpirationTime());
    response.put("maxLimit", quota.getMaxLimit());
    response.put("currentUsage", quota.getCurrentUsage());
    response.put("remainingQuota", quota.getRemainingQuota());

    return Response.ok(response).build();
}
```

**请求示例**：
```bash
# 管理员将用户123升级为2025报税季用户
POST /api/users/123/upgrade?userType=TAX_SEASON_2025
Authorization: Bearer <admin_token>
```

**响应示例**：
```json
{
  "success": true,
  "user": {
    "id": 123,
    "email": "user@example.com",
    "expirationTime": "2026-05-01T00:00:00Z",
    "temporary": false
  },
  "quota": {
    "userId": 123,
    "year": 2025,
    "userType": "TAX_SEASON_2025",
    "maxLimit": 500,
    "currentUsage": 15
  },
  "newUserType": "TAX_SEASON_2025",
  "newUserTypeName": "2025报税季用户",
  "expirationTime": "2026-05-01T00:00:00Z",
  "maxLimit": 500,
  "currentUsage": 15,
  "remainingQuota": 485
}
```

### 3. 查询扫描配额 API

```java
@Path("users/{id}/receipt-quota")
@GET
public Response getReceiptQuota(@PathParam("id") long userId) throws StorageException {
    permissionsService.checkUser(getUserId(), userId);

    UserReceiptQuota quota = receiptQuotaManager.getCurrentQuota(userId);

    if (quota == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("Quota not found for user")
            .build();
    }

    // 返回配额信息
    Map<String, Object> response = new HashMap<>();
    response.put("userId", userId);
    response.put("year", quota.getYear());
    response.put("userType", quota.getUserType());
    response.put("userTypeName", quota.getUserTypeEnum().getDisplayName());
    response.put("maxLimit", quota.getMaxLimit());
    response.put("currentUsage", quota.getCurrentUsage());
    response.put("remainingQuota", quota.getRemainingQuota());
    response.put("hasQuota", quota.hasQuota());

    // 获取用户到期时间
    User user = storage.getObject(User.class, new Request(
        new Columns.Include("expirationTime"),
        new Condition.Equals("id", userId)
    ));
    response.put("expirationTime", user.getExpirationTime());

    return Response.ok(response).build();
}
```

**响应示例**：
```json
{
  "userId": 123,
  "year": 2025,
  "userType": "TRIAL_2025",
  "userTypeName": "2025试用用户",
  "maxLimit": 50,
  "currentUsage": 15,
  "remainingQuota": 35,
  "hasQuota": true,
  "expirationTime": "2025-11-25T12:00:00Z"
}
```

### 4. 集成配额检查到现有 Expense 功能

**说明**：系统现有两种添加费用的方式：
1. **Add Expense**：用户手动逐条添加费用记录
2. **AI Plus Fees**：使用 Azure AI 批量提取收据信息

需要在这两个功能中集成配额检查逻辑。

#### 4.1 修改 Add Expense API（手动添加费用）

**位置**：`src/main/java/org/traccar/api/resource/ExpenseResource.java`（或类似位置）

```java
@Path("expenses")
@POST
public Response addExpense(Expense expense) throws StorageException {
    long userId = getUserId(); // 从session获取当前用户

    // ===== 新增：配额检查逻辑 =====
    // 1. 检查用户是否过期
    User user = storage.getObject(User.class, new Request(
        new Columns.Include("expirationTime"),
        new Condition.Equals("id", userId)
    ));

    if (user.getExpirationTime() != null &&
        user.getExpirationTime().before(new Date())) {
        throw new SecurityException("Account expired. Please renew your subscription.");
    }

    // 2. 检查扫描额度
    if (!receiptQuotaManager.hasQuota(userId)) {
        int remaining = receiptQuotaManager.getRemainingQuota(userId);
        throw new SecurityException(
            "Scan quota exceeded (" + remaining + " remaining). Please upgrade your account."
        );
    }
    // ===== 配额检查结束 =====

    // 原有的费用添加逻辑
    expense.setUserId(userId);
    expense.setCreatedAt(new Date());
    expense.setId(storage.addObject(expense, new Request(new Columns.Exclude("id"))));

    // ===== 新增：扣减配额 =====
    receiptQuotaManager.incrementReceiptUsage(userId, expense.getId());
    // ===== 配额扣减结束 =====

    // 返回结果（包含剩余配额信息）
    Map<String, Object> response = new HashMap<>();
    response.put("expense", expense);
    response.put("remainingQuota", receiptQuotaManager.getRemainingQuota(userId));

    return Response.ok(response).build();
}
```

#### 4.2 修改 AI Plus Fees API（批量AI提取）

**位置**：`src/main/java/org/traccar/api/resource/ExpenseResource.java`（或类似位置）

```java
@Path("expenses/ai-batch")
@POST
public Response processBatchWithAI(BatchAIRequest request) throws StorageException {
    long userId = getUserId();

    // ===== 新增：批量配额检查 =====
    // 1. 检查用户是否过期
    User user = storage.getObject(User.class, new Request(
        new Columns.Include("expirationTime"),
        new Condition.Equals("id", userId)
    ));

    if (user.getExpirationTime() != null &&
        user.getExpirationTime().before(new Date())) {
        throw new SecurityException("Account expired. Please renew your subscription.");
    }

    // 2. 检查批量额度是否足够
    int batchSize = request.getReceiptImages().size();
    int remaining = receiptQuotaManager.getRemainingQuota(userId);

    if (remaining < batchSize) {
        throw new SecurityException(
            "Insufficient quota for batch processing. " +
            "Required: " + batchSize + ", Available: " + remaining + ". " +
            "Please upgrade your account."
        );
    }
    // ===== 批量配额检查结束 =====

    // 原有的AI批量处理逻辑
    List<Expense> processedExpenses = new ArrayList<>();

    for (String receiptImage : request.getReceiptImages()) {
        // 使用 Azure AI 提取信息
        Expense expense = extractExpenseWithAI(receiptImage);
        expense.setUserId(userId);
        expense.setCreatedAt(new Date());

        // 保存到数据库
        expense.setId(storage.addObject(expense, new Request(new Columns.Exclude("id"))));

        // ===== 新增：每成功处理一张，扣减配额 =====
        receiptQuotaManager.incrementReceiptUsage(userId, expense.getId());
        // ===== 配额扣减结束 =====

        processedExpenses.add(expense);
    }

    // 返回结果（包含剩余配额）
    Map<String, Object> response = new HashMap<>();
    response.put("processedCount", processedExpenses.size());
    response.put("expenses", processedExpenses);
    response.put("remainingQuota", receiptQuotaManager.getRemainingQuota(userId));

    return Response.ok(response).build();
}
```

#### 4.3 修改 Delete Expense API（删除费用，恢复配额）

**位置**：`src/main/java/org/traccar/api/resource/ExpenseResource.java`（或类似位置）

```java
@Path("expenses/{id}")
@DELETE
public Response deleteExpense(@PathParam("id") long expenseId) throws StorageException {
    long currentUserId = getUserId();

    // 1. 获取费用记录并验证权限
    Expense expense = storage.getObject(Expense.class, new Request(
        new Columns.All(),
        new Condition.Equals("id", expenseId)
    ));

    if (expense == null) {
        throw new WebApplicationException(Response.Status.NOT_FOUND);
    }

    // 2. 检查是否是该用户的费用
    if (expense.getUserId() != currentUserId) {
        permissionsService.checkAdmin(currentUserId); // 非本人需要管理员权限
    }

    long userId = expense.getUserId();

    // 3. 删除费用记录（原有逻辑）
    storage.removeObject(Expense.class, new Request(
        new Condition.Equals("id", expenseId)
    ));

    // ===== 新增：恢复配额 =====
    receiptQuotaManager.decrementReceiptUsage(userId, expenseId);
    // ===== 配额恢复结束 =====

    // 5. 返回剩余额度
    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    response.put("remainingQuota", receiptQuotaManager.getRemainingQuota(userId));

    return Response.ok(response).build();
}
```

### 5. Expense 集成修改总结

**修改位置**：
- `ExpenseResource.java` 中的 `addExpense()` 方法
- `ExpenseResource.java` 中的 `processBatchWithAI()` 方法（或类似的 AI 批量处理方法）
- `ExpenseResource.java` 中的 `deleteExpense()` 方法

**核心修改点**：
1. **添加依赖注入**：在 `ExpenseResource` 类中注入 `ReceiptQuotaManager`
   ```java
   @Inject
   private ReceiptQuotaManager receiptQuotaManager;
   ```

2. **添加前置检查**：
   - 检查用户是否过期（`expirationTime`）
   - 检查是否有足够配额（`hasQuota()` 或批量检查 `getRemainingQuota() >= batchSize`）

3. **插入后扣减**：
   - 单条添加：调用 `receiptQuotaManager.incrementReceiptUsage(userId, expenseId)`
   - 批量添加：在循环中每次成功插入后调用 `incrementReceiptUsage()`

4. **删除后恢复**：
   - 调用 `receiptQuotaManager.decrementReceiptUsage(userId, expenseId)`

5. **响应中返回配额信息**：
   - 添加 `remainingQuota` 字段，便于前端实时显示剩余额度

**注意事项**：
- 确保 `incrementReceiptUsage()` 和 `decrementReceiptUsage()` 的调用在事务范围内
- 如果 AI 批量处理失败，需要回滚已扣减的配额（建议使用数据库事务或手动回滚）
- 前端需要处理配额不足的异常，提示用户升级账户

---

## 📊 Liquibase 迁移脚本

```xml
<!-- schema/changelog-custom-receipt-quota.xml -->
<?xml version="1.1" encoding="UTF-8"?>
<databaseChangeLog
  xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                      http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.6.xsd">

  <!-- 注意：不需要修改 tc_users 表 -->

  <changeSet author="dev" id="create-user-receipt-quota-table">
    <createTable tableName="tcaf_user_receipt_quota">
      <column name="id" type="BIGSERIAL" autoIncrement="true">
        <constraints primaryKey="true" />
      </column>
      <column name="userid" type="BIGINT">
        <constraints nullable="false" />
      </column>
      <column name="year" type="INT">
        <constraints nullable="false" />
      </column>
      <column name="usertype" type="VARCHAR(64)">
        <constraints nullable="false" />
      </column>
      <column name="maxlimit" type="INT">
        <constraints nullable="false" />
      </column>
      <column name="currentusage" type="INT" defaultValueNumeric="0">
        <constraints nullable="false" />
      </column>
      <column name="createdat" type="TIMESTAMP" defaultValueComputed="NOW()">
        <constraints nullable="false" />
      </column>
      <column name="updatedat" type="TIMESTAMP" defaultValueComputed="NOW()">
        <constraints nullable="false" />
      </column>
    </createTable>

    <addForeignKeyConstraint
        baseTableName="tcaf_user_receipt_quota"
        baseColumnNames="userid"
        constraintName="fk_user_receipt_quota_userid"
        referencedTableName="tc_users"
        referencedColumnNames="id"
        onDelete="CASCADE" />

    <addUniqueConstraint
        tableName="tcaf_user_receipt_quota"
        columnNames="userid, year"
        constraintName="uq_user_year" />

    <createIndex tableName="tcaf_user_receipt_quota" indexName="idx_user_receipt_quota_userid">
      <column name="userid" />
    </createIndex>

    <createIndex tableName="tcaf_user_receipt_quota" indexName="idx_user_receipt_quota_year">
      <column name="year" />
    </createIndex>

    <addCheckConstraint
        tableName="tcaf_user_receipt_quota"
        constraintName="chk_current_usage"
        checkCondition="currentusage >= 0" />

    <addCheckConstraint
        tableName="tcaf_user_receipt_quota"
        constraintName="chk_max_limit"
        checkCondition="maxlimit > 0" />
  </changeSet>

  <changeSet author="dev" id="create-user-receipt-usage-log-table">
    <createTable tableName="tcaf_user_receipt_usage_log">
      <column name="id" type="BIGSERIAL" autoIncrement="true">
        <constraints primaryKey="true" />
      </column>
      <column name="userid" type="BIGINT">
        <constraints nullable="false" />
      </column>
      <column name="receiptid" type="BIGINT">
        <constraints nullable="true" />
      </column>
      <column name="action" type="VARCHAR(32)">
        <constraints nullable="false" />
      </column>
      <column name="changeamount" type="INT">
        <constraints nullable="false" />
      </column>
      <column name="usagebefore" type="INT">
        <constraints nullable="false" />
      </column>
      <column name="usageafter" type="INT">
        <constraints nullable="false" />
      </column>
      <column name="createdat" type="TIMESTAMP" defaultValueComputed="NOW()">
        <constraints nullable="false" />
      </column>
    </createTable>

    <addForeignKeyConstraint
        baseTableName="tcaf_user_receipt_usage_log"
        baseColumnNames="userid"
        constraintName="fk_user_receipt_log_userid"
        referencedTableName="tc_users"
        referencedColumnNames="id"
        onDelete="CASCADE" />

    <createIndex tableName="tcaf_user_receipt_usage_log" indexName="idx_user_receipt_log_userid">
      <column name="userid" />
    </createIndex>

    <createIndex tableName="tcaf_user_receipt_usage_log" indexName="idx_user_receipt_log_createdat">
      <column name="createdat" />
    </createIndex>
  </changeSet>

</databaseChangeLog>
```

**引用到主changelog**：
```xml
<!-- schema/changelog-master.xml -->
<include file="changelog-custom-receipt-quota.xml"/>
```

---

## 🎨 前后端约定

### 用户类型代码约定

| 前端代码 | 后端枚举 | 说明 | 有效期 | 扫描额度 |
|---------|---------|------|--------|---------|
| `TRIAL_2025` | `UserType.TRIAL_2025` | 2025试用用户 | 7天 | 50张 |
| `TAX_SEASON_2025` | `UserType.TAX_SEASON_2025` | 2025报税季用户 | 至2026.5.1 | 500张 |
| `TAX_SEASON_2026` | `UserType.TAX_SEASON_2026` | 2026报税季用户 | 至2027.5.1 | 500张 |
| `ANNUAL_USER` | `UserType.ANNUAL_USER` | 年度标准用户 | 365天 | 1000张 |

### 前端配置示例（TypeScript）

```typescript
// frontend/types/UserType.ts
export enum UserType {
  TRIAL_2025 = 'TRIAL_2025',
  TAX_SEASON_2025 = 'TAX_SEASON_2025',
  TAX_SEASON_2026 = 'TAX_SEASON_2026',
  ANNUAL_USER = 'ANNUAL_USER'
}

export const UserTypeConfig = {
  [UserType.TRIAL_2025]: {
    name: '试用用户',
    badge: '试用中',
    badgeColor: 'orange',
    quota: 50,
    validityDays: 7,
    price: 0
  },
  [UserType.TAX_SEASON_2025]: {
    name: '2025报税季用户',
    badge: '付费用户',
    badgeColor: 'green',
    quota: 500,
    expirationDate: '2026-05-01',
    price: 99
  },
  [UserType.TAX_SEASON_2026]: {
    name: '2026报税季用户',
    badge: '付费用户',
    badgeColor: 'green',
    quota: 500,
    expirationDate: '2027-05-01',
    price: 99
  },
  [UserType.ANNUAL_USER]: {
    name: '年度用户',
    badge: 'VIP',
    badgeColor: 'blue',
    quota: 1000,
    validityDays: 365,
    price: 299
  }
};
```

---


## 📋 开发任务清单

### 阶段1：数据库（2人天）
- [ ] 创建 `tcaf_user_receipt_quota` 表
- [ ] 创建 `tcaf_user_receipt_usage_log` 表
- [ ] 编写 Liquibase 迁移脚本
- [ ] 验证表结构和索引

### 阶段2：后端开发（15人天）
- [ ] `UserType` 枚举类（1人天）
- [ ] `UserReceiptQuota` 模型（1人天）
- [ ] `ReceiptQuotaManager` 服务（3人天）
- [ ] 创建试用用户 API（2人天）
- [ ] 升级用户 API（线下现金支付，管理员操作）（2人天）
- [ ] 配额查询 API（1人天）
- [ ] 集成配额检查到 Add Expense API（手动添加费用）（2人天）
- [ ] 集成配额检查到 AI Plus Fees API（批量AI提取）（2人天）
- [ ] 集成配额恢复到 Delete Expense API（1人天）
- [ ] 单元测试（1人天）

### 阶段3：前端开发（8人天）
- [ ] 用户类型配置文件（1人天）
- [ ] 试用注册页面（2人天）
- [ ] 配额进度条组件（1人天）
- [ ] 升级/支付页面（2人天）
- [ ] Dashboard 配额展示（1人天）
- [ ] 前端集成测试（1人天）

### 阶段4：集成与测试（5人天）
- [ ] 后端集成测试（2人天）
- [ ] 配额并发测试（1人天）
- [ ] 升级流程端到端测试（1人天）
- [ ] 性能测试（1人天）

**总工作量**：约30人天（4周）

---

## 🚀 实施步骤

### Week 1: 数据库 + 核心模型
1. 创建数据库表
2. 实现 UserType 枚举
3. 实现 UserReceiptQuota 模型
4. 编写基础单元测试

### Week 2-3: 业务逻辑 + API
1. 实现 ReceiptQuotaManager 服务
2. 实现所有 REST API 接口
3. 集成支付功能
4. 编写 API 集成测试

### Week 4: 前端 + 测试
1. 前端页面开发
2. 前后端联调
3. 端到端测试
4. 性能优化

---

## 🔐 安全考虑

1. **并发控制**: `incrementUsage` 和 `decrementUsage` 使用 `synchronized`
2. **事务完整性**: 扫描和配额扣减在同一事务中
3. **权限检查**: 删除收据时验证用户权限
4. **支付验证**: 升级前必须验证支付
5. **输入验证**: userType 参数必须是合法枚举值

---

## 📊 监控指标

建议监控以下指标：
- 试用用户注册数
- 试用->付费转化率
- 每日扫描次数
- 配额超限次数
- 平均剩余配额
- 用户类型分布

---

**方案版本**: V2.0 Final
**最后更新**: 2025-11-18

---

## 📋 详细开发任务清单（架构师视角）

### 总体架构原则
1. **模块化设计**：将用户配额管理作为独立模块，降低与现有代码的耦合
2. **向后兼容**：不修改 `tc_users` 表，确保现有功能不受影响
3. **事务一致性**：配额操作与业务操作在同一事务中，保证数据一致性
4. **并发安全**：使用数据库级别的并发控制（乐观锁/悲观锁）
5. **可扩展性**：UserType 枚举设计允许未来添加新的用户类型

---

## Phase 0: 准备阶段（1人天）

### Task 0.1 技术方案评审
**负责人**: 架构师 + Tech Lead
**工作量**: 0.5人天
**内容**:
- [ ] 评审本方案的技术可行性
- [ ] 确认数据库表设计（tcaf_user_receipt_quota, tcaf_user_receipt_usage_log）
- [ ] 确认不修改 tc_users 表的决策
- [ ] 确认 UserType 枚举的扩展性
- [ ] 评估对现有 Expense 功能的影响范围

**验收标准**:
- 技术方案评审通过
- 确认所有技术决策点
- 识别潜在技术风险

**技术要点**:
- 确保不引入破坏性变更
- 确认 Liquibase 迁移策略

---

### Task 0.2 环境准备和依赖检查
**负责人**: 后端开发
**工作量**: 0.5人天
**内容**:
- [ ] 确认开发/测试环境 PostgreSQL 版本（建议 13+）
- [ ] 确认 Java 17 环境
- [ ] 确认 Gradle 构建环境
- [ ] 确认现有 Expense 相关代码位置（ExpenseResource.java, Expense.java 等）
- [ ] 确认 MailManager 邮件服务可用性
- [ ] 准备测试数据库环境

**验收标准**:
- 所有开发环境就绪
- 现有代码结构清晰
- 测试数据库可访问

**技术要点**:
- 使用独立的测试数据库，避免污染生产数据

---

## Phase 1: 数据库设计（2人天）

### Task 1.1 创建 Liquibase 迁移脚本
**负责人**: 后端开发 + DBA
**工作量**: 1人天
**内容**:
- [ ] 创建文件 `schema/changelog-custom-receipt-quota.xml`
- logicalFilePath="changelog-6.15.0
- [ ] 定义 `tcaf_user_receipt_quota` 表结构
  - 主键：id (BIGSERIAL)
  - 字段：userid, year, usertype, maxlimit, currentusage, createdat, updatedat
  - 外键：userid → tc_users(id) ON DELETE CASCADE
  - 唯一约束：(userid, year)
  - Check 约束：currentusage >= 0, maxlimit > 0
  - 索引：idx_user_receipt_quota_userid, idx_user_receipt_quota_year
- [ ] 定义 `tcaf_user_receipt_usage_log` 表结构（可选）
  - 主键：id (BIGSERIAL)
  - 字段：userid, receiptid, action, changeamount, usagebefore, usageafter, createdat
  - 外键：userid → tc_users(id) ON DELETE CASCADE
  - 索引：idx_user_receipt_log_userid, idx_user_receipt_log_createdat
- [ ] 在 `schema/changelog-master.xml` 中引入新的 changeset

**验收标准**:
- Liquibase 脚本符合规范
- 所有约束和索引正确定义
- 脚本可重复执行（幂等性）

**技术要点**:
- 使用 `<changeSet>` 的 `preconditions` 避免重复执行
- 外键使用 CASCADE 删除，避免孤儿数据
- 索引优化查询性能（userid 和 year 是高频查询字段）

**依赖**: Task 0.1, Task 0.2

---

### Task 1.2 数据库迁移测试
**负责人**: 后端开发
**工作量**: 0.5人天
**内容**:
- [ ] 在测试数据库执行 Liquibase 迁移
- [ ] 验证表结构正确性（字段类型、约束、索引）
- [ ] 测试外键级联删除行为
- [ ] 测试唯一约束（同一用户同一年只能有一条记录）
- [ ] 测试 Check 约束（currentusage >= 0, maxlimit > 0）
- [ ] 验证回滚脚本（如果需要）

**验收标准**:
- 迁移成功无错误
- 表结构符合设计
- 约束和索引工作正常

**技术要点**:
- 使用 `./gradlew update` 或 `liquibase update` 执行迁移
- 检查 `databasechangelog` 表确认迁移记录

**依赖**: Task 1.1

---

### Task 1.3 数据库性能测试
**负责人**: DBA/后端开发
**工作量**: 0.5人天
**内容**:
- [ ] 插入测试数据（模拟 10,000 用户 × 2 年 = 20,000 条记录）
- [ ] 测试查询性能：
  - 按 userid 查询当前年度配额
  - 按 year 统计用户类型分布
  - 更新 currentusage（模拟并发更新）
- [ ] 使用 `EXPLAIN ANALYZE` 分析查询计划
- [ ] 确认索引被正确使用

**验收标准**:
- 单次查询 < 10ms
- 索引被查询优化器使用
- 并发更新无死锁

**技术要点**:
- PostgreSQL 的 `EXPLAIN ANALYZE` 查看执行计划
- 使用 `pg_stat_statements` 监控慢查询

**依赖**: Task 1.2

---

## Phase 2: 核心模型和枚举（2人天）

### Task 2.1 实现 UserType 枚举
**负责人**: 后端开发
**工作量**: 0.5人天
**文件**: `src/main/java/org/traccar/model/UserType.java`
**内容**:
- [ ] 定义枚举常量：TRIAL_2025, TAX_SEASON_2025, TAX_SEASON_2026, ANNUAL_USER
- [ ] 实现字段：code, validityDays, scanQuota, displayName
- [ ] 实现方法：
  - `getExpirationDate()` - 计算到期时间
  - `fromCode(String code)` - 从代码解析枚举
  - `parseDate(String dateStr)` - 日期解析工具
- [ ] TAX_SEASON_2025 和 TAX_SEASON_2026 重写 `getExpirationDate()` 返回固定日期

**验收标准**:
- 枚举定义完整
- 所有方法正确实现
- 异常处理完善（fromCode 对无效 code 抛出 IllegalArgumentException）

**技术要点**:
- 使用 `SimpleDateFormat` 解析固定日期时注意线程安全（局部变量）
- 考虑使用 `java.time.LocalDate` 替代 `Date`（更现代的 API）

**依赖**: Task 0.2

---

### Task 2.2 实现 UserReceiptQuota 模型
**负责人**: 后端开发
**工作量**: 0.5人天
**文件**: `src/main/java/org/traccar/model/UserReceiptQuota.java`
**内容**:
- [ ] 继承 `BaseModel`
- [ ] 添加 `@StorageName("tcaf_user_receipt_quota")` 注解
- [ ] 定义字段：userId, year, userType, maxLimit, currentUsage, createdAt, updatedAt
- [ ] 实现 Getter/Setter
- [ ] 实现业务方法：
  - `getRemainingQuota()` - 计算剩余额度
  - `hasQuota()` - 检查是否还有额度
  - `getUserTypeEnum()` - 获取用户类型枚举

**验收标准**:
- 模型字段与数据库表一致
- 业务方法逻辑正确
- 处理边界情况（maxLimit = -1 表示无限额）

**技术要点**:
- `@StorageName` 注解确保 ORM 映射正确
- 确保字段类型与数据库类型匹配（long vs BIGINT, int vs INT）

**依赖**: Task 1.2, Task 2.1

---

### Task 2.3 单元测试 - UserType 枚举
**负责人**: 后端开发
**工作量**: 0.5人天
**文件**: `src/test/java/org/traccar/model/UserTypeTest.java`
**内容**:
- [ ] 测试枚举常量定义
- [ ] 测试 `getExpirationDate()` 计算逻辑
  - TRIAL_2025: 7天后
  - TAX_SEASON_2025: 固定到 2026-05-01
  - TAX_SEASON_2026: 固定到 2027-05-01
  - ANNUAL_USER: 365天后
- [ ] 测试 `fromCode()` 方法
  - 有效代码返回正确枚举
  - 无效代码抛出 IllegalArgumentException
- [ ] 测试边界情况

**验收标准**:
- 测试覆盖率 > 90%
- 所有测试通过

**技术要点**:
- 使用 JUnit 5 的 `@ParameterizedTest` 测试多个枚举值
- 使用 `assertThrows` 测试异常

**依赖**: Task 2.1

---

### Task 2.4 单元测试 - UserReceiptQuota 模型
**负责人**: 后端开发
**工作量**: 0.5人天
**文件**: `src/test/java/org/traccar/model/UserReceiptQuotaTest.java`
**内容**:
- [ ] 测试 `getRemainingQuota()`
  - 正常情况：maxLimit=100, currentUsage=30 → remaining=70
  - 用完情况：maxLimit=50, currentUsage=50 → remaining=0
  - 超限情况：maxLimit=50, currentUsage=60 → remaining=0（不应出现，但要防御）
  - 无限额：maxLimit=-1 → remaining=10000
- [ ] 测试 `hasQuota()`
  - maxLimit=100, currentUsage=30 → true
  - maxLimit=50, currentUsage=50 → false
  - maxLimit=-1 → true（无限额）
- [ ] 测试 `getUserTypeEnum()`

**验收标准**:
- 测试覆盖率 > 90%
- 所有边界情况测试通过

**技术要点**:
- 测试防御性编程（currentUsage > maxLimit 的异常情况）

**依赖**: Task 2.2

---

## Phase 3: 业务逻辑层（4人天）

### Task 3.1 实现 ReceiptQuotaManager 核心服务
**负责人**: 后端开发（资深）
**工作量**: 2人天
**文件**: `src/main/java/org/traccar/manager/ReceiptQuotaManager.java`
**内容**:
- [ ] 添加 `@Singleton` 注解
- [ ] 注入 `Storage` 依赖
- [ ] 实现方法：
  - `initializeQuota(long userId, UserType userType)` - 初始化配额
  - `getCurrentQuota(long userId)` - 获取当前年度配额
  - `getQuotaByYear(long userId, int year)` - 获取指定年度配额
  - `hasQuota(long userId)` - 检查是否有额度
  - `getRemainingQuota(long userId)` - 获取剩余额度
  - `incrementReceiptUsage(long userId, Long receiptId)` - 增加使用量（关键）
  - `decrementReceiptUsage(long userId, Long receiptId)` - 减少使用量（关键）
  - `upgradeUserType(long userId, UserType newUserType)` - 升级用户类型
  - `logUsageChange(...)` - 记录变更日志（可选）

**验收标准**:
- 所有方法实现完整
- 异常处理完善
- 并发安全（synchronized 或数据库锁）

**技术要点**:
- **关键**: `incrementReceiptUsage` 和 `decrementReceiptUsage` 必须是原子操作
- 使用 `synchronized` 方法级锁或数据库行锁（SELECT FOR UPDATE）
- 确保在事务中执行，避免脏读/脏写
- `incrementReceiptUsage` 检查配额超限，抛出 `SecurityException`
- `decrementReceiptUsage` 检查 currentUsage >= 0，防止负数

**依赖**: Task 2.2

---

### Task 3.2 并发安全性设计和实现
**负责人**: 后端开发（资深）+ 架构师
**工作量**: 1人天
**内容**:
- [ ] 选择并发控制策略：
  - **方案A**: Java synchronized 方法锁（简单但性能较低）
  - **方案B**: 数据库乐观锁（使用 version 字段）
  - **方案C**: 数据库悲观锁（SELECT FOR UPDATE）
  - **推荐方案B或C**
- [ ] 实现选定的并发控制策略
- [ ] 添加重试机制（乐观锁冲突时重试）
- [ ] 添加超时控制（悲观锁等待超时）

**验收标准**:
- 并发更新不丢失数据
- 无死锁
- 性能测试通过（100并发请求 < 5s）

**技术要点**:
- **乐观锁示例**:
  ```java
  // 添加 version 字段到 tcaf_user_receipt_quota
  // 更新时检查 version，冲突则重试
  UPDATE tcaf_user_receipt_quota
  SET currentusage = currentusage + 1,
      updatedat = NOW(),
      version = version + 1
  WHERE id = ? AND version = ?
  ```
- **悲观锁示例**:
  ```java
  // 事务中先锁行
  SELECT * FROM tcaf_user_receipt_quota
  WHERE userid = ? AND year = ?
  FOR UPDATE
  // 然后更新
  UPDATE tcaf_user_receipt_quota SET currentusage = currentusage + 1
  ```

**依赖**: Task 3.1

---

### Task 3.3 实现日志记录功能（可选）
**负责人**: 后端开发
**工作量**: 0.5人天
**文件**: `ReceiptQuotaManager.java` 中的 `logUsageChange` 方法
**内容**:
- [ ] 实现 `logUsageChange` 方法，插入到 `tcaf_user_receipt_usage_log` 表
- [ ] 记录字段：userid, receiptid, action, changeamount, usagebefore, usageafter
- [ ] 异步记录日志（不阻塞主流程）
- [ ] 日志失败不影响主流程

**验收标准**:
- 日志记录成功
- 异步执行，不影响性能
- 日志失败不影响业务

**技术要点**:
- 使用 `CompletableFuture.runAsync()` 异步记录
- 捕获异常，记录到应用日志但不抛出

**依赖**: Task 3.1

---

### Task 3.4 单元测试 - ReceiptQuotaManager
**负责人**: 后端开发
**工作量**: 0.5人天
**文件**: `src/test/java/org/traccar/manager/ReceiptQuotaManagerTest.java`
**内容**:
- [ ] Mock `Storage` 依赖
- [ ] 测试 `initializeQuota` - 创建配额记录
- [ ] 测试 `getCurrentQuota` - 查询当前年度配额
- [ ] 测试 `hasQuota` - 检查额度
- [ ] 测试 `incrementReceiptUsage` - 增加使用量
  - 正常情况
  - 配额不足时抛出异常
- [ ] 测试 `decrementReceiptUsage` - 减少使用量
  - 正常情况
  - currentUsage=0 时不减少
- [ ] 测试 `upgradeUserType` - 升级用户类型

**验收标准**:
- 测试覆盖率 > 85%
- 所有边界情况测试通过

**技术要点**:
- 使用 Mockito mock Storage
- 使用 `verify()` 验证 Storage 方法调用

**依赖**: Task 3.1

---

## Phase 4: API 接口开发（4人天）

### Task 4.1 实现试用用户注册 API
**负责人**: 后端开发
**工作量**: 1.5人天
**文件**: `src/main/java/org/traccar/api/resource/UserResource.java`（扩展现有）
**内容**:
- [ ] 添加 `@Path("users/trial-registration")` 端点
- [ ] 定义 `TrialRegistrationRequest` DTO（name, email, password, phone）
- [ ] 实现注册逻辑：
  1. 验证邮箱是否已存在
  2. 创建 User 实体（设置 expirationTime, temporary=true）
  3. 保存用户
  4. 调用 `receiptQuotaManager.initializeQuota(userId, UserType.TRIAL_2025)`
  5. 发送欢迎邮件
- [ ] 异常处理：
  - 邮箱已存在 → 400 Bad Request
  - 其他错误 → 500 Internal Server Error

**验收标准**:
- API 正确创建试用用户
- 配额记录正确初始化
- 邮件发送成功（或日志记录）

**技术要点**:
- 使用 `@PermitAll` 允许未登录访问
- 密码加密使用现有的 `UserUtil` 工具类
- 事务中执行（用户创建 + 配额初始化）

**依赖**: Task 3.1

---

### Task 4.2 实现升级用户 API（管理员操作）
**负责人**: 后端开发
**工作量**: 1.5人天
**文件**: `src/main/java/org/traccar/api/resource/UserResource.java`（扩展现有）
**内容**:
- [ ] 添加 `@Path("users/{id}/upgrade")` 端点
- [ ] 参数：userId, userTypeCode, remark（可选）
- [ ] 实现升级逻辑：
  1. 权限检查：`permissionsService.checkAdmin(getUserId())`
  2. 解析 userTypeCode 为 UserType 枚举
  3. 调用 `receiptQuotaManager.upgradeUserType(userId, newUserType)`
  4. 记录操作信息到 user.attributes（lastUpgradeTime, upgradeOperator, upgradeRemark）
  5. 记录操作日志
  6. 发送升级成功邮件
- [ ] 返回：user, quota, newUserType, expirationTime, remainingQuota

**验收标准**:
- 只有管理员可以操作
- 用户类型正确升级
- 配额正确更新
- 操作日志完整

**技术要点**:
- 使用 `@PathParam` 和 `@QueryParam` 接收参数
- 使用 user.attributes 存储扩展信息（升级记录）
- 异常处理：无效 userTypeCode → 400 Bad Request

**依赖**: Task 3.1

---

### Task 4.3 实现配额查询 API
**负责人**: 后端开发
**工作量**: 0.5人天
**文件**: `src/main/java/org/traccar/api/resource/UserResource.java`（扩展现有）
**内容**:
- [ ] 添加 `@Path("users/{id}/receipt-quota")` 端点
- [ ] 权限检查：用户只能查询自己的配额，管理员可查询所有
- [ ] 查询逻辑：
  1. 调用 `receiptQuotaManager.getCurrentQuota(userId)`
  2. 查询用户的 expirationTime
  3. 组装响应：userId, year, userType, userTypeName, maxLimit, currentUsage, remainingQuota, hasQuota, expirationTime

**验收标准**:
- 用户可以查询自己的配额
- 管理员可以查询所有用户配额
- 响应格式正确

**技术要点**:
- 使用 `permissionsService.checkUser(getUserId(), userId)` 检查权限

**依赖**: Task 3.1

---

### Task 4.4 API 集成测试
**负责人**: 后端开发 + QA
**工作量**: 0.5人天
**文件**: `src/test/java/org/traccar/api/resource/UserResourceTest.java`
**内容**:
- [ ] 测试试用用户注册流程（POST /api/users/trial-registration）
  - 正常注册
  - 邮箱已存在（400）
- [ ] 测试升级用户流程（POST /api/users/{id}/upgrade）
  - 管理员升级成功
  - 非管理员被拒绝（403）
  - 无效 userTypeCode（400）
- [ ] 测试配额查询（GET /api/users/{id}/receipt-quota）
  - 查询自己的配额
  - 查询他人配额（403）
  - 管理员查询他人配额（200）

**验收标准**:
- 所有 API 测试通过
- 权限检查正确
- 异常处理正确

**技术要点**:
- 使用 Jersey Test Framework 或 Spring MockMvc
- Mock ReceiptQuotaManager 和 PermissionsService

**依赖**: Task 4.1, Task 4.2, Task 4.3

---

## Phase 5: 集成现有 Expense 功能（3人天）

### Task 5.1 定位并理解现有 Expense 代码
**负责人**: 后端开发
**工作量**: 0.5人天
**内容**:
- [ ] 定位 Expense 相关文件：
  - `Expense.java` 模型
  - `ExpenseResource.java` API
  - AI 批量处理相关代码
- [ ] 理解现有流程：
  - Add Expense 手动添加流程
  - AI Plus Fees 批量处理流程
  - Delete Expense 删除流程
- [ ] 确认数据库表结构（tc_expenses 或类似）
- [ ] 确认 userId 字段存在

**验收标准**:
- 现有代码结构清晰
- 流程理解正确
- 确认集成点

**技术要点**:
- 阅读代码，绘制流程图
- 确认事务边界

**依赖**: Task 0.2

---

### Task 5.2 集成配额检查到 Add Expense API
**负责人**: 后端开发
**工作量**: 1人天
**文件**: `src/main/java/org/traccar/api/resource/ExpenseResource.java`
**内容**:
- [ ] 在 `ExpenseResource` 类中注入 `ReceiptQuotaManager`
  ```java
  @Inject
  private ReceiptQuotaManager receiptQuotaManager;
  ```
- [ ] 在 `addExpense` 方法中添加配额检查（插入前）：
  1. 检查用户是否过期
  2. 检查是否有扫描额度
  3. 如果不满足，抛出 `SecurityException`
- [ ] 在 `addExpense` 方法中添加配额扣减（插入后）：
  ```java
  receiptQuotaManager.incrementReceiptUsage(userId, expense.getId());
  ```
- [ ] 在响应中添加 `remainingQuota` 字段
- [ ] 确保在事务中执行

**验收标准**:
- 配额不足时无法添加费用
- 配额正确扣减
- 响应包含剩余额度

**技术要点**:
- 确保配额检查和扣减在同一事务中
- 异常处理：配额不足 → SecurityException → 403 Forbidden

**依赖**: Task 3.1, Task 5.1

---

### Task 5.3 集成配额检查到 AI Plus Fees API
**负责人**: 后端开发
**工作量**: 1人天
**文件**: `src/main/java/org/traccar/api/resource/ExpenseResource.java`（或 AI 相关类）
**内容**:
- [ ] 定位 AI 批量处理 API 端点
- [ ] 添加批量配额检查（处理前）：
  1. 检查用户是否过期
  2. 检查剩余配额是否 >= 批量大小
  3. 如果不满足，抛出 `SecurityException`
- [ ] 在循环中每次成功插入后扣减配额：
  ```java
  receiptQuotaManager.incrementReceiptUsage(userId, expense.getId());
  ```
- [ ] 在响应中添加 `remainingQuota` 字段
- [ ] 考虑事务回滚（如果中途失败，已扣减的配额如何处理）

**验收标准**:
- 批量配额不足时无法处理
- 每条记录成功后正确扣减配额
- 处理失败时配额回滚

**技术要点**:
- **事务处理**：
  - 方案A：整个批量在一个事务中，失败全部回滚
  - 方案B：逐条提交，失败的跳过（需手动回滚配额）
  - **推荐方案A**
- 批量大小限制（建议 <= 10，避免长事务）

**依赖**: Task 3.1, Task 5.1

---

### Task 5.4 集成配额恢复到 Delete Expense API
**负责人**: 后端开发
**工作量**: 0.5人天
**文件**: `src/main/java/org/traccar/api/resource/ExpenseResource.java`
**内容**:
- [ ] 定位 `deleteExpense` 方法
- [ ] 在删除费用记录后添加配额恢复：
  ```java
  receiptQuotaManager.decrementReceiptUsage(userId, expenseId);
  ```
- [ ] 在响应中添加 `remainingQuota` 字段
- [ ] 确保在事务中执行

**验收标准**:
- 删除费用后配额正确恢复
- 响应包含剩余额度

**技术要点**:
- 确保配额恢复在事务中
- 如果配额恢复失败，考虑是否回滚删除操作

**依赖**: Task 3.1, Task 5.1

---

## Phase 6: 依赖注入配置（0.5人天）

### Task 6.1 配置 Guice 模块
**负责人**: 后端开发
**工作量**: 0.5人天
**文件**: `src/main/java/org/traccar/MainModule.java`（或相关模块）
**内容**:
- [ ] 确认 `ReceiptQuotaManager` 使用 `@Singleton` 注解
- [ ] 确认 Guice 自动扫描或手动绑定
- [ ] 测试依赖注入：
  - 在 `ExpenseResource` 中注入 `ReceiptQuotaManager`
  - 在 `UserResource` 中注入 `ReceiptQuotaManager`
- [ ] 启动应用，确认依赖注入成功

**验收标准**:
- ReceiptQuotaManager 正确注入
- 应用启动无错误

**技术要点**:
- Traccar 使用 Google Guice 进行依赖注入
- `@Singleton` 注解确保单例模式

**依赖**: Task 3.1, Task 5.2, Task 5.3

---

## Phase 7: 测试（3人天）

### Task 7.1 单元测试补充
**负责人**: 后端开发
**工作量**: 1人天
**内容**:
- [ ] 补充所有模块的单元测试
- [ ] 确保测试覆盖率 > 85%
- [ ] 测试边界情况和异常情况
- [ ] 运行 `./gradlew test` 确保所有测试通过

**验收标准**:
- 测试覆盖率 > 85%
- 所有单元测试通过

**技术要点**:
- 使用 JaCoCo 生成测试覆盖率报告
- 重点测试并发场景

**依赖**: Task 2.3, Task 2.4, Task 3.4, Task 4.4

---

### Task 7.2 集成测试 - 端到端流程
**负责人**: QA + 后端开发
**工作量**: 1.5人天
**文件**: `src/test/java/org/traccar/integration/ReceiptQuotaIntegrationTest.java`
**内容**:
- [ ] 测试完整的试用用户注册流程：
  1. 注册试用用户
  2. 验证配额初始化（50张）
  3. 添加 10 条费用记录
  4. 验证配额减少到 40
  5. 删除 5 条费用记录
  6. 验证配额恢复到 45
  7. 尝试添加 50 条记录，验证配额超限异常
- [ ] 测试升级流程：
  1. 创建试用用户
  2. 管理员升级为 TAX_SEASON_2025
  3. 验证配额更新为 500
  4. 验证 expirationTime 更新为 2026-05-01
- [ ] 测试 AI 批量处理流程：
  1. 批量添加 5 条记录
  2. 验证配额减少 5
  3. 批量大小超过剩余配额，验证异常
- [ ] 测试并发场景：
  1. 10 个线程同时添加费用
  2. 验证配额正确扣减
  3. 验证无数据丢失

**验收标准**:
- 所有集成测试通过
- 并发测试无数据不一致

**技术要点**:
- 使用真实数据库（测试数据库）
- 使用 `@Transactional` 确保测试间隔离
- 并发测试使用 `CountDownLatch` 同步线程

**依赖**: Task 4.4, Task 5.2, Task 5.3, Task 5.4

---

### Task 7.3 性能测试
**负责人**: QA + 后端开发
**工作量**: 0.5人天
**内容**:
- [ ] 测试配额查询性能（1000 QPS）
- [ ] 测试配额扣减性能（100 并发）
- [ ] 测试 AI 批量处理性能（10 批次并发）
- [ ] 使用 JMeter 或 Gatling 进行压力测试

**验收标准**:
- 配额查询：P99 < 50ms
- 配额扣减：100 并发 < 5s
- 无死锁和超时

**技术要点**:
- 使用数据库连接池（HikariCP）
- 监控数据库连接数和锁等待

**依赖**: Task 7.2

---

## Phase 8: 代码审查和优化（1人天）

### Task 8.1 代码审查
**负责人**: Tech Lead + 架构师
**工作量**: 0.5人天
**内容**:
- [ ] 审查数据库设计：表结构、索引、约束
- [ ] 审查 UserType 枚举：扩展性、易读性
- [ ] 审查 ReceiptQuotaManager：并发安全、事务处理
- [ ] 审查 API 接口：权限检查、异常处理、响应格式
- [ ] 审查 Expense 集成：事务边界、配额回滚
- [ ] 审查测试：覆盖率、边界情况

**验收标准**:
- 代码符合团队规范
- 无明显性能问题
- 无安全漏洞

**技术要点**:
- 使用 Checkstyle 检查代码风格（`./gradlew checkstyle`）
- 使用 SonarQube 静态分析（如有）

**依赖**: Phase 1-7 完成

---

### Task 8.2 性能优化
**负责人**: 后端开发（资深）
**工作量**: 0.5人天
**内容**:
- [ ] 优化数据库查询（减少 N+1 查询）
- [ ] 添加缓存（Redis）缓存配额信息（可选）
- [ ] 优化并发控制策略（如果性能不达标）
- [ ] 数据库索引优化

**验收标准**:
- 性能测试达标
- 缓存命中率 > 80%（如使用缓存）

**技术要点**:
- 配额信息频繁读取，适合缓存
- 缓存失效策略：配额更新时清除缓存
- 使用 Redis 或本地缓存（Caffeine）

**依赖**: Task 7.3, Task 8.1

---

## Phase 9: 文档和部署（1人天）

### Task 9.1 编写技术文档
**负责人**: 后端开发 + Tech Writer
**工作量**: 0.5人天
**内容**:
- [ ] API 文档（Swagger/OpenAPI）
  - POST /api/users/trial-registration
  - POST /api/users/{id}/upgrade
  - GET /api/users/{id}/receipt-quota
- [ ] 数据库文档
  - tcaf_user_receipt_quota 表结构
  - tcaf_user_receipt_usage_log 表结构
- [ ] 部署文档
  - Liquibase 迁移步骤
  - 配置项说明
- [ ] 开发者文档
  - UserType 枚举使用指南
  - ReceiptQuotaManager API 使用指南

**验收标准**:
- 文档完整、准确
- 示例代码可运行

**技术要点**:
- 使用 Swagger 注解生成 API 文档
- 文档版本控制

**依赖**: Phase 1-8 完成

---

### Task 9.2 部署准备
**负责人**: DevOps + 后端开发
**工作量**: 0.5人天
**内容**:
- [ ] 准备生产环境 Liquibase 迁移脚本
- [ ] 编写数据库迁移计划（回滚方案）
- [ ] 配置监控和告警：
  - 配额超限次数
  - 配额查询性能
  - 数据库连接池
- [ ] 准备灰度发布方案（如有）
- [ ] 编写部署 Checklist

**验收标准**:
- 部署计划完整
- 回滚方案可行
- 监控告警配置完成

**技术要点**:
- 生产环境迁移前备份数据库
- 使用蓝绿部署或滚动发布
- 监控关键指标（Prometheus + Grafana）

**依赖**: Phase 1-8 完成

---

## 🎯 总体时间线（基于 2 人并行开发）

| 阶段 | 任务 | 工作量 | 时间线 |
|------|------|--------|--------|
| Phase 0 | 准备阶段 | 1人天 | Day 1 |
| Phase 1 | 数据库设计 | 2人天 | Day 1-2 |
| Phase 2 | 核心模型和枚举 | 2人天 | Day 2-3 |
| Phase 3 | 业务逻辑层 | 4人天 | Day 3-5 |
| Phase 4 | API 接口开发 | 4人天 | Day 5-7 |
| Phase 5 | Expense 集成 | 3人天 | Day 7-9 |
| Phase 6 | 依赖注入配置 | 0.5人天 | Day 9 |
| Phase 7 | 测试 | 3人天 | Day 9-11 |
| Phase 8 | 代码审查和优化 | 1人天 | Day 11-12 |
| Phase 9 | 文档和部署 | 1人天 | Day 12 |
| **总计** | | **21.5人天** | **约 3 周（2人并行）** |

---

## ⚠️ 关键风险和缓解措施

### 风险 1: 并发控制复杂度
**影响**: 配额扣减可能出现数据不一致
**缓解措施**:
- 采用数据库级别的并发控制（乐观锁或悲观锁）
- 充分的并发测试
- 监控并发异常和重试次数

### 风险 2: 现有 Expense 代码集成
**影响**: 可能破坏现有功能
**缓解措施**:
- 充分理解现有代码
- 添加完整的集成测试
- 灰度发布，先在小范围测试

### 风险 3: 事务回滚处理
**影响**: AI 批量处理失败时配额可能不一致
**缓解措施**:
- 整个批量在一个事务中
- 添加事务回滚测试
- 添加手动补偿机制（如有必要）

### 风险 4: 数据库迁移失败
**影响**: 生产环境数据损坏
**缓解措施**:
- 充分的迁移测试
- 备份生产数据库
- 准备回滚方案

### 风险 5: 性能问题
**影响**: 配额查询/扣减影响用户体验
**缓解措施**:
- 性能测试
- 数据库索引优化
- 添加缓存（如需要）

---

## 📊 质量标准

| 指标 | 目标 |
|------|------|
| 单元测试覆盖率 | > 85% |
| 集成测试覆盖率 | > 70% |
| 代码风格检查 | 0 violations |
| 配额查询性能 | P99 < 50ms |
| 配额扣减性能 | 100 并发 < 5s |
| API 响应时间 | P99 < 200ms |
| 并发安全 | 无数据不一致 |
| 错误率 | < 0.1% |

---

## 🔧 技术选型总结

| 组件 | 技术选型 | 理由 |
|------|---------|------|
| 数据库 | PostgreSQL | 现有技术栈 |
| ORM | Traccar Storage | 现有技术栈 |
| 迁移工具 | Liquibase | 现有技术栈 |
| 依赖注入 | Google Guice | 现有技术栈 |
| 并发控制 | 数据库乐观锁/悲观锁 | 性能 + 可靠性平衡 |
| 日志 | SLF4J + Logback | 现有技术栈 |
| 测试框架 | JUnit 5 + Mockito | 现有技术栈 |
| 邮件服务 | Traccar MailManager | 现有技术栈 |

---

**清单编写人**: Claude Code (架构师视角)
**最后更新**: 2025-11-18
