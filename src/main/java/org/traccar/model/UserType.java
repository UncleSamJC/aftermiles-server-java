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
