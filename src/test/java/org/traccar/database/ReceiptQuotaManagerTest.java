package org.traccar.database;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.traccar.model.User;
import org.traccar.model.UserReceiptQuota;
import org.traccar.model.UserType;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Request;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReceiptQuotaManagerTest {

    private Storage storage;
    private ReceiptQuotaManager manager;

    private static final long TEST_USER_ID = 100L;
    private static final long TEST_QUOTA_ID = 1L;
    private static final int CURRENT_YEAR = Calendar.getInstance().get(Calendar.YEAR);

    @BeforeEach
    void setUp() {
        storage = mock(Storage.class);
        manager = new ReceiptQuotaManager(storage);
    }

    @Test
    void testInitializeQuota() throws StorageException {
        // Arrange
        UserType userType = UserType.TRIAL_2025;
        when(storage.addObject(any(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(TEST_QUOTA_ID);

        // Act
        UserReceiptQuota result = manager.initializeQuota(TEST_USER_ID, userType);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_QUOTA_ID, result.getId());
        assertEquals(TEST_USER_ID, result.getUserId());
        assertEquals(CURRENT_YEAR, result.getYear());
        assertEquals(userType.getCode(), result.getUserType());
        assertEquals(userType.getScanQuota(), result.getMaxLimit());
        assertEquals(0, result.getCurrentUsage());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        verify(storage, times(1)).addObject(any(UserReceiptQuota.class), any(Request.class));
    }

    @Test
    void testGetCurrentQuota() throws StorageException {
        // Arrange
        UserReceiptQuota expectedQuota = createTestQuota(TEST_USER_ID, CURRENT_YEAR, 50, 10);
        when(storage.getObject(eq(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(expectedQuota);

        // Act
        UserReceiptQuota result = manager.getCurrentQuota(TEST_USER_ID);

        // Assert
        assertNotNull(result);
        assertEquals(expectedQuota.getUserId(), result.getUserId());
        assertEquals(expectedQuota.getYear(), result.getYear());

        verify(storage, times(1)).getObject(eq(UserReceiptQuota.class), any(Request.class));
    }

    @Test
    void testGetQuotaByYear() throws StorageException {
        // Arrange
        int testYear = 2025;
        UserReceiptQuota expectedQuota = createTestQuota(TEST_USER_ID, testYear, 500, 100);
        when(storage.getObject(eq(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(expectedQuota);

        // Act
        UserReceiptQuota result = manager.getQuotaByYear(TEST_USER_ID, testYear);

        // Assert
        assertNotNull(result);
        assertEquals(testYear, result.getYear());

        verify(storage, times(1)).getObject(eq(UserReceiptQuota.class), any(Request.class));
    }

    @Test
    void testHasQuota_WithAvailableQuota() throws StorageException {
        // Arrange
        UserReceiptQuota quota = createTestQuota(TEST_USER_ID, CURRENT_YEAR, 50, 30);
        when(storage.getObject(eq(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(quota);

        // Act
        boolean result = manager.hasQuota(TEST_USER_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    void testHasQuota_NoQuotaLeft() throws StorageException {
        // Arrange
        UserReceiptQuota quota = createTestQuota(TEST_USER_ID, CURRENT_YEAR, 50, 50);
        when(storage.getObject(eq(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(quota);

        // Act
        boolean result = manager.hasQuota(TEST_USER_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    void testHasQuota_NoQuotaRecord() throws StorageException {
        // Arrange
        when(storage.getObject(eq(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(null);

        // Act
        boolean result = manager.hasQuota(TEST_USER_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    void testGetRemainingQuota() throws StorageException {
        // Arrange
        UserReceiptQuota quota = createTestQuota(TEST_USER_ID, CURRENT_YEAR, 100, 30);
        when(storage.getObject(eq(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(quota);

        // Act
        int result = manager.getRemainingQuota(TEST_USER_ID);

        // Assert
        assertEquals(70, result);
    }

    @Test
    void testGetRemainingQuota_NoQuotaRecord() throws StorageException {
        // Arrange
        when(storage.getObject(eq(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(null);

        // Act
        int result = manager.getRemainingQuota(TEST_USER_ID);

        // Assert
        assertEquals(0, result);
    }

    @Test
    void testIncrementReceiptUsage_Success() throws StorageException {
        // Arrange
        UserReceiptQuota quota = createTestQuota(TEST_USER_ID, CURRENT_YEAR, 50, 25);
        when(storage.getObject(eq(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(quota);
        doNothing().when(storage).updateObject(any(UserReceiptQuota.class), any(Request.class));

        // Act
        manager.incrementReceiptUsage(TEST_USER_ID, 999L);

        // Assert
        assertEquals(26, quota.getCurrentUsage());
        verify(storage, times(1)).updateObject(any(UserReceiptQuota.class), any(Request.class));
    }

    @Test
    void testIncrementReceiptUsage_QuotaExceeded() throws StorageException {
        // Arrange
        UserReceiptQuota quota = createTestQuota(TEST_USER_ID, CURRENT_YEAR, 50, 50);
        when(storage.getObject(eq(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(quota);

        // Act & Assert
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            manager.incrementReceiptUsage(TEST_USER_ID, 999L);
        });

        assertTrue(exception.getMessage().contains("Scan quota exceeded"));
        verify(storage, never()).updateObject(any(UserReceiptQuota.class), any(Request.class));
    }

    @Test
    void testIncrementReceiptUsage_NoQuotaRecord() throws StorageException {
        // Arrange
        when(storage.getObject(eq(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(null);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            manager.incrementReceiptUsage(TEST_USER_ID, 999L);
        });

        assertTrue(exception.getMessage().contains("User quota not found"));
    }

    @Test
    void testDecrementReceiptUsage_Success() throws StorageException {
        // Arrange
        UserReceiptQuota quota = createTestQuota(TEST_USER_ID, CURRENT_YEAR, 50, 25);
        when(storage.getObject(eq(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(quota);
        doNothing().when(storage).updateObject(any(UserReceiptQuota.class), any(Request.class));

        // Act
        manager.decrementReceiptUsage(TEST_USER_ID, 999L);

        // Assert
        assertEquals(24, quota.getCurrentUsage());
        verify(storage, times(1)).updateObject(any(UserReceiptQuota.class), any(Request.class));
    }

    @Test
    void testDecrementReceiptUsage_AlreadyZero() throws StorageException {
        // Arrange
        UserReceiptQuota quota = createTestQuota(TEST_USER_ID, CURRENT_YEAR, 50, 0);
        when(storage.getObject(eq(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(quota);

        // Act
        manager.decrementReceiptUsage(TEST_USER_ID, 999L);

        // Assert
        assertEquals(0, quota.getCurrentUsage());
        verify(storage, never()).updateObject(any(UserReceiptQuota.class), any(Request.class));
    }

    @Test
    void testUpgradeUserType_WithExistingQuota() throws StorageException {
        // Arrange
        User user = createTestUser(TEST_USER_ID);
        UserReceiptQuota quota = createTestQuota(TEST_USER_ID, CURRENT_YEAR, 50, 25);

        when(storage.getObject(eq(User.class), any(Request.class)))
                .thenReturn(user);
        when(storage.getObject(eq(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(quota);
        doNothing().when(storage).updateObject(any(), any(Request.class));

        UserType newUserType = UserType.TAX_SEASON_2025;

        // Act
        manager.upgradeUserType(TEST_USER_ID, newUserType);

        // Assert
        assertFalse(user.getTemporary());
        assertEquals(newUserType.getExpirationDate(), user.getExpirationTime());
        assertEquals(newUserType.getCode(), quota.getUserType());
        assertEquals(newUserType.getScanQuota(), quota.getMaxLimit());
        assertEquals(25, quota.getCurrentUsage()); // currentUsage should remain unchanged

        verify(storage, times(2)).updateObject(any(), any(Request.class));
    }

    @Test
    void testUpgradeUserType_CreateNewQuota() throws StorageException {
        // Arrange
        User user = createTestUser(TEST_USER_ID);

        when(storage.getObject(eq(User.class), any(Request.class)))
                .thenReturn(user);
        when(storage.getObject(eq(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(null); // No existing quota
        when(storage.addObject(any(UserReceiptQuota.class), any(Request.class)))
                .thenReturn(TEST_QUOTA_ID);
        doNothing().when(storage).updateObject(any(), any(Request.class));

        UserType newUserType = UserType.TAX_SEASON_2025;

        // Act
        manager.upgradeUserType(TEST_USER_ID, newUserType);

        // Assert
        verify(storage, times(1)).updateObject(any(User.class), any(Request.class));
        verify(storage, times(1)).addObject(any(UserReceiptQuota.class), any(Request.class));
    }

    @Test
    void testUpgradeUserType_UserNotFound() throws StorageException {
        // Arrange
        when(storage.getObject(eq(User.class), any(Request.class)))
                .thenReturn(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            manager.upgradeUserType(TEST_USER_ID, UserType.TAX_SEASON_2025);
        });

        assertTrue(exception.getMessage().contains("User not found"));
    }

    // Helper methods

    private UserReceiptQuota createTestQuota(long userId, int year, int maxLimit, int currentUsage) {
        UserReceiptQuota quota = new UserReceiptQuota();
        quota.setId(TEST_QUOTA_ID);
        quota.setUserId(userId);
        quota.setYear(year);
        quota.setUserType("TRIAL_2025");
        quota.setMaxLimit(maxLimit);
        quota.setCurrentUsage(currentUsage);
        quota.setCreatedAt(new Date());
        quota.setUpdatedAt(new Date());
        return quota;
    }

    private User createTestUser(long userId) {
        User user = new User();
        user.setId(userId);
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setTemporary(true);
        return user;
    }
}