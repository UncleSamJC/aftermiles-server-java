package org.traccar.model;

import org.traccar.storage.QueryIgnore;
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
    @QueryIgnore
    public int getRemainingQuota() {
        if (maxLimit == -1) {
            return 10000; // 最大返回10000，避免无限
        }
        return Math.max(0, maxLimit - currentUsage);
    }

    /**
     * 检查是否还有额度
     */
    @QueryIgnore
    public boolean hasQuota() {
        return maxLimit == -1 || currentUsage < maxLimit;
    }

    /**
     * 获取用户类型枚举
     */
    @QueryIgnore
    public UserType getUserTypeEnum() {
        return userType != null ? UserType.fromCode(userType) : null;
    }
}
