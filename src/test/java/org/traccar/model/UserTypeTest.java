package org.traccar.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class UserTypeTest {

    @Test
    void testEnumConstants() {
        assertEquals(4, UserType.values().length);
        assertNotNull(UserType.TRIAL_2025);
        assertNotNull(UserType.TAX_SEASON_2025);
        assertNotNull(UserType.TAX_SEASON_2026);
        assertNotNull(UserType.ANNUAL_USER);
    }

    @Test
    void testTrialUser() {
        UserType trial = UserType.TRIAL_2025;
        assertEquals("TRIAL_2025", trial.getCode());
        assertEquals(7, trial.getValidityDays());
        assertEquals(50, trial.getScanQuota());
        assertEquals("2025试用用户", trial.getDisplayName());

        // Test expiration date (7 days from now)
        Date expirationDate = trial.getExpirationDate();
        assertNotNull(expirationDate);

        long expectedTime = System.currentTimeMillis() + 7 * 86400000L;
        long actualTime = expirationDate.getTime();
        // Allow 1 second tolerance for test execution time
        assertTrue(Math.abs(expectedTime - actualTime) < 1000);
    }

    @Test
    void testTaxSeason2025() throws Exception {
        UserType taxSeason = UserType.TAX_SEASON_2025;
        assertEquals("TAX_SEASON_2025", taxSeason.getCode());
        assertEquals(-1, taxSeason.getValidityDays());
        assertEquals(500, taxSeason.getScanQuota());
        assertEquals("2025报税季用户", taxSeason.getDisplayName());

        // Test fixed expiration date
        Date expirationDate = taxSeason.getExpirationDate();
        assertNotNull(expirationDate);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        assertEquals("2026-05-01", sdf.format(expirationDate));
    }

    @Test
    void testTaxSeason2026() throws Exception {
        UserType taxSeason = UserType.TAX_SEASON_2026;
        assertEquals("TAX_SEASON_2026", taxSeason.getCode());
        assertEquals(-1, taxSeason.getValidityDays());
        assertEquals(500, taxSeason.getScanQuota());
        assertEquals("2026报税季用户", taxSeason.getDisplayName());

        // Test fixed expiration date
        Date expirationDate = taxSeason.getExpirationDate();
        assertNotNull(expirationDate);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        assertEquals("2027-05-01", sdf.format(expirationDate));
    }

    @Test
    void testAnnualUser() {
        UserType annual = UserType.ANNUAL_USER;
        assertEquals("ANNUAL_USER", annual.getCode());
        assertEquals(365, annual.getValidityDays());
        assertEquals(1000, annual.getScanQuota());
        assertEquals("年度标准用户", annual.getDisplayName());

        // Test expiration date (365 days from now)
        Date expirationDate = annual.getExpirationDate();
        assertNotNull(expirationDate);

        long expectedTime = System.currentTimeMillis() + 365 * 86400000L;
        long actualTime = expirationDate.getTime();
        // Allow 1 second tolerance
        assertTrue(Math.abs(expectedTime - actualTime) < 1000);
    }

    @ParameterizedTest
    @EnumSource(UserType.class)
    void testAllEnumValues(UserType userType) {
        assertNotNull(userType.getCode());
        assertNotNull(userType.getDisplayName());
        assertTrue(userType.getScanQuota() > 0);
        assertNotNull(userType.getExpirationDate());
    }

    @Test
    void testFromCodeValid() {
        assertEquals(UserType.TRIAL_2025, UserType.fromCode("TRIAL_2025"));
        assertEquals(UserType.TAX_SEASON_2025, UserType.fromCode("TAX_SEASON_2025"));
        assertEquals(UserType.TAX_SEASON_2026, UserType.fromCode("TAX_SEASON_2026"));
        assertEquals(UserType.ANNUAL_USER, UserType.fromCode("ANNUAL_USER"));
    }

    @Test
    void testFromCodeInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            UserType.fromCode("INVALID_CODE");
        });
    }

    @Test
    void testFromCodeNull() {
        assertThrows(Exception.class, () -> {
            UserType.fromCode(null);
        });
    }

    @Test
    void testFromCodeEmptyString() {
        assertThrows(IllegalArgumentException.class, () -> {
            UserType.fromCode("");
        });
    }

    @Test
    void testExpirationDateCalculation() {
        // Test that expiration dates are in the future
        for (UserType type : UserType.values()) {
            Date expirationDate = type.getExpirationDate();
            assertNotNull(expirationDate, "Expiration date should not be null for " + type);
            assertTrue(expirationDate.after(new Date()),
                "Expiration date should be in the future for " + type);
        }
    }
}
