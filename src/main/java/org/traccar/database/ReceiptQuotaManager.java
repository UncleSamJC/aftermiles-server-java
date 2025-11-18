/*
 * Copyright 2025 Aftermiles Team
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
package org.traccar.database;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.model.User;
import org.traccar.model.UserReceiptQuota;
import org.traccar.model.UserType;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.util.Calendar;
import java.util.Date;

/**
 * 收据配额管理服务
 * 管理用户的收据扫描配额，包括初始化、查询、增减和升级功能
 */
@Singleton
public class ReceiptQuotaManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptQuotaManager.class);

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
     * @return 创建的配额记录
     * @throws StorageException 数据库异常
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

        LOGGER.info("Initialized quota for user {} with type {} (max: {})",
                userId, userType.getCode(), userType.getScanQuota());

        return quota;
    }

    /**
     * 获取用户当前年度配额
     *
     * @param userId 用户ID
     * @return 配额记录，如果不存在返回 null
     * @throws StorageException 数据库异常
     */
    public UserReceiptQuota getCurrentQuota(long userId) throws StorageException {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        return getQuotaByYear(userId, currentYear);
    }

    /**
     * 获取指定年度配额
     *
     * @param userId 用户ID
     * @param year 年份
     * @return 配额记录，如果不存在返回 null
     * @throws StorageException 数据库异常
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
     *
     * @param userId 用户ID
     * @return true 如果还有额度，false 否则
     * @throws StorageException 数据库异常
     */
    public boolean hasQuota(long userId) throws StorageException {
        UserReceiptQuota quota = getCurrentQuota(userId);
        if (quota == null) {
            LOGGER.warn("No quota record found for user {}", userId);
            return false;
        }
        return quota.hasQuota();
    }

    /**
     * 获取剩余额度
     *
     * @param userId 用户ID
     * @return 剩余额度数量
     * @throws StorageException 数据库异常
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
     * 使用 synchronized 确保并发安全
     *
     * @param userId 用户ID
     * @param receiptId 收据ID（用于日志记录）
     * @throws StorageException 数据库异常
     * @throws SecurityException 配额不足时抛出
     */
    public synchronized void incrementReceiptUsage(long userId, Long receiptId) throws StorageException {
        UserReceiptQuota quota = getCurrentQuota(userId);
        if (quota == null) {
            throw new IllegalStateException("User quota not found for user " + userId);
        }

        if (!quota.hasQuota()) {
            throw new SecurityException(
                "Scan quota exceeded for user " + userId + ". Current usage: "
                + quota.getCurrentUsage() + ", Max limit: " + quota.getMaxLimit()
            );
        }

        int usageBefore = quota.getCurrentUsage();
        quota.setCurrentUsage(usageBefore + 1);
        quota.setUpdatedAt(new Date());

        storage.updateObject(quota, new Request(
            new Columns.Include("currentUsage", "updatedAt"),
            new Condition.Equals("id", quota.getId())
        ));

        LOGGER.info("Incremented receipt usage for user {} from {} to {} (receiptId: {})",
                userId, usageBefore, usageBefore + 1, receiptId);

        // 记录日志（可选）
        logUsageChange(userId, receiptId, "ADD", 1, usageBefore, usageBefore + 1);
    }

    /**
     * 减少使用量（删除收据）
     * 使用 synchronized 确保并发安全
     *
     * @param userId 用户ID
     * @param receiptId 收据ID（用于日志记录）
     * @throws StorageException 数据库异常
     */
    public synchronized void decrementReceiptUsage(long userId, Long receiptId) throws StorageException {
        UserReceiptQuota quota = getCurrentQuota(userId);
        if (quota == null) {
            throw new IllegalStateException("User quota not found for user " + userId);
        }

        int usageBefore = quota.getCurrentUsage();
        if (usageBefore <= 0) {
            LOGGER.warn("Attempted to decrement quota for user {} but currentUsage is already 0", userId);
            return; // 已经是0了，不能再减
        }

        quota.setCurrentUsage(usageBefore - 1);
        quota.setUpdatedAt(new Date());

        storage.updateObject(quota, new Request(
            new Columns.Include("currentUsage", "updatedAt"),
            new Condition.Equals("id", quota.getId())
        ));

        LOGGER.info("Decremented receipt usage for user {} from {} to {} (receiptId: {})",
                userId, usageBefore, usageBefore - 1, receiptId);

        // 记录日志（可选）
        logUsageChange(userId, receiptId, "REMOVE", -1, usageBefore, usageBefore - 1);
    }

    /**
     * 升级用户类型（例如从试用升级为付费）
     *
     * @param userId 用户ID
     * @param newUserType 新的用户类型
     * @throws StorageException 数据库异常
     */
    public void upgradeUserType(long userId, UserType newUserType) throws StorageException {
        // 1. 更新用户的过期时间（仅更新 expirationTime 和 temporary）
        User user = storage.getObject(User.class, new Request(
            new Columns.All(),
            new Condition.Equals("id", userId)
        ));

        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

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

            LOGGER.info("Upgraded user {} to type {} (new maxLimit: {}, currentUsage: {})",
                    userId, newUserType.getCode(), newUserType.getScanQuota(), quota.getCurrentUsage());
        } else {
            // 如果没有配额记录，创建一个
            initializeQuota(userId, newUserType);
            LOGGER.info("Upgraded user {} to type {} (created new quota record)", userId, newUserType.getCode());
        }
    }

    /**
     * 记录使用变更日志（可选实现）
     * 目前仅记录到应用日志，未来可以持久化到 tcaf_user_receipt_usage_log 表
     *
     * @param userId 用户ID
     * @param receiptId 收据ID
     * @param action 操作类型（ADD/REMOVE）
     * @param changeAmount 变更量
     * @param usageBefore 操作前使用量
     * @param usageAfter 操作后使用量
     */
    private void logUsageChange(long userId, Long receiptId, String action,
                                int changeAmount, int usageBefore, int usageAfter) {
        // TODO: 可以实现持久化到 tcaf_user_receipt_usage_log 表
        // 目前仅记录到应用日志
        LOGGER.debug("Usage change log: userId={}, receiptId={}, action={}, change={}, before={}, after={}",
                userId, receiptId, action, changeAmount, usageBefore, usageAfter);
    }
}
