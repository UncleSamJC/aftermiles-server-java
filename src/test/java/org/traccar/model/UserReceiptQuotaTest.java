package org.traccar.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class UserReceiptQuotaTest {

    private UserReceiptQuota quota;

    @BeforeEach
    void setUp() {
        quota = new UserReceiptQuota();
        quota.setId(1L);
        quota.setUserId(100L);
        quota.setYear(2025);
        quota.setUserType("TRIAL_2025");
        quota.setMaxLimit(50);
        quota.setCurrentUsage(0);
        quota.setCreatedAt(new Date());
        quota.setUpdatedAt(new Date());
    }

    @Test
    void testGettersAndSetters() {
        assertEquals(1L, quota.getId());
        assertEquals(100L, quota.getUserId());
        assertEquals(2025, quota.getYear());
        assertEquals("TRIAL_2025", quota.getUserType());
        assertEquals(50, quota.getMaxLimit());
        assertEquals(0, quota.getCurrentUsage());
        assertNotNull(quota.getCreatedAt());
        assertNotNull(quota.getUpdatedAt());
    }

    @Test
    void testGetRemainingQuota_Normal() {
        quota.setMaxLimit(100);
        quota.setCurrentUsage(30);
        assertEquals(70, quota.getRemainingQuota());
    }

    @Test
    void testGetRemainingQuota_Full() {
        quota.setMaxLimit(50);
        quota.setCurrentUsage(50);
        assertEquals(0, quota.getRemainingQuota());
    }

    @Test
    void testGetRemainingQuota_OverLimit() {
        // Defensive case - should not happen in practice
        quota.setMaxLimit(50);
        quota.setCurrentUsage(60);
        assertEquals(0, quota.getRemainingQuota());
    }

    @Test
    void testGetRemainingQuota_Unlimited() {
        quota.setMaxLimit(-1);
        quota.setCurrentUsage(100);
        assertEquals(10000, quota.getRemainingQuota());
    }

    @Test
    void testGetRemainingQuota_ZeroUsage() {
        quota.setMaxLimit(100);
        quota.setCurrentUsage(0);
        assertEquals(100, quota.getRemainingQuota());
    }

    @Test
    void testHasQuota_WithQuota() {
        quota.setMaxLimit(100);
        quota.setCurrentUsage(30);
        assertTrue(quota.hasQuota());
    }

    @Test
    void testHasQuota_NoQuota() {
        quota.setMaxLimit(50);
        quota.setCurrentUsage(50);
        assertFalse(quota.hasQuota());
    }

    @Test
    void testHasQuota_Unlimited() {
        quota.setMaxLimit(-1);
        quota.setCurrentUsage(1000);
        assertTrue(quota.hasQuota());
    }

    @Test
    void testHasQuota_OneRemaining() {
        quota.setMaxLimit(50);
        quota.setCurrentUsage(49);
        assertTrue(quota.hasQuota());
    }

    @Test
    void testHasQuota_OverLimit() {
        // Defensive case - should not happen in practice
        quota.setMaxLimit(50);
        quota.setCurrentUsage(60);
        assertFalse(quota.hasQuota());
    }

    @Test
    void testGetUserTypeEnum_Valid() {
        quota.setUserType("TRIAL_2025");
        UserType userType = quota.getUserTypeEnum();
        assertNotNull(userType);
        assertEquals(UserType.TRIAL_2025, userType);
    }

    @Test
    void testGetUserTypeEnum_AnotherValid() {
        quota.setUserType("TAX_SEASON_2025");
        UserType userType = quota.getUserTypeEnum();
        assertNotNull(userType);
        assertEquals(UserType.TAX_SEASON_2025, userType);
    }

    @Test
    void testGetUserTypeEnum_Null() {
        quota.setUserType(null);
        assertNull(quota.getUserTypeEnum());
    }

    @Test
    void testGetUserTypeEnum_Invalid() {
        quota.setUserType("INVALID_TYPE");
        assertThrows(IllegalArgumentException.class, () -> {
            quota.getUserTypeEnum();
        });
    }

    @Test
    void testEdgeCases() {
        // Test with max integer values
        quota.setMaxLimit(Integer.MAX_VALUE);
        quota.setCurrentUsage(0);
        assertEquals(Integer.MAX_VALUE, quota.getRemainingQuota());
        assertTrue(quota.hasQuota());

        // Test with current usage at max
        quota.setCurrentUsage(Integer.MAX_VALUE);
        assertEquals(0, quota.getRemainingQuota());
        assertFalse(quota.hasQuota());
    }

    @Test
    void testMultipleScenarios() {
        // Scenario 1: Trial user just started
        quota.setUserType("TRIAL_2025");
        quota.setMaxLimit(50);
        quota.setCurrentUsage(0);
        assertEquals(50, quota.getRemainingQuota());
        assertTrue(quota.hasQuota());

        // Scenario 2: Used half
        quota.setCurrentUsage(25);
        assertEquals(25, quota.getRemainingQuota());
        assertTrue(quota.hasQuota());

        // Scenario 3: Almost depleted
        quota.setCurrentUsage(49);
        assertEquals(1, quota.getRemainingQuota());
        assertTrue(quota.hasQuota());

        // Scenario 4: Fully depleted
        quota.setCurrentUsage(50);
        assertEquals(0, quota.getRemainingQuota());
        assertFalse(quota.hasQuota());
    }

    @Test
    void testTaxSeasonUser() {
        quota.setUserType("TAX_SEASON_2025");
        quota.setMaxLimit(500);
        quota.setCurrentUsage(150);

        assertEquals(350, quota.getRemainingQuota());
        assertTrue(quota.hasQuota());
        assertEquals(UserType.TAX_SEASON_2025, quota.getUserTypeEnum());
    }

    @Test
    void testAnnualUser() {
        quota.setUserType("ANNUAL_USER");
        quota.setMaxLimit(1000);
        quota.setCurrentUsage(999);

        assertEquals(1, quota.getRemainingQuota());
        assertTrue(quota.hasQuota());
        assertEquals(UserType.ANNUAL_USER, quota.getUserTypeEnum());
    }
}
