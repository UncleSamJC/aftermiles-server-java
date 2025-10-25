package org.traccar.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExpenseTest {

    @Test
    public void testExpenseTypeConstants() {
        assertEquals("fuel", Expense.TYPE_FUEL);
        assertEquals("insurance", Expense.TYPE_INSURANCE);
        assertEquals("maintenance", Expense.TYPE_MAINTENANCE);
        assertEquals("parking", Expense.TYPE_PARKING);
        assertEquals("toll", Expense.TYPE_TOLL);
        assertEquals("carwash", Expense.TYPE_CARWASH);
        assertEquals("mobile", Expense.TYPE_MOBILE);
        assertEquals("legal", Expense.TYPE_LEGAL);
        assertEquals("supplies", Expense.TYPE_SUPPLIES);
        assertEquals("others", Expense.TYPE_OTHERS);
    }

    @Test
    public void testExpenseBasicProperties() {
        Expense expense = new Expense();

        // Test device ID
        expense.setDeviceId(123L);
        assertEquals(123L, expense.getDeviceId());

        // Test type
        expense.setType(Expense.TYPE_FUEL);
        assertEquals(Expense.TYPE_FUEL, expense.getType());

        // Test amount
        BigDecimal amount = new BigDecimal("65.50");
        expense.setAmount(amount);
        assertEquals(amount, expense.getAmount());
        assertEquals(0, amount.compareTo(expense.getAmount()));

        // Test currency
        expense.setCurrency("CAD");
        assertEquals("CAD", expense.getCurrency());

        // Test merchant
        expense.setMerchant("Shell Gas Station");
        assertEquals("Shell Gas Station", expense.getMerchant());

        // Test description
        expense.setDescription("Regular gasoline");
        assertEquals("Regular gasoline", expense.getDescription());

        // Test expense date
        Date expenseDate = new Date();
        expense.setExpenseDate(expenseDate);
        assertEquals(expenseDate, expense.getExpenseDate());

        // Test receipt image path
        expense.setReceiptImagePath("/path/to/receipt.jpg");
        assertEquals("/path/to/receipt.jpg", expense.getReceiptImagePath());

        // Test mileage
        expense.setMileage(12345.67);
        assertEquals(12345.67, expense.getMileage(), 0.01);

        // Test location
        expense.setLocation("Vancouver, BC");
        assertEquals("Vancouver, BC", expense.getLocation());

        // Test notes
        expense.setNotes("Full tank");
        assertEquals("Full tank", expense.getNotes());

        // Test tags
        expense.setTags("business,highway");
        assertEquals("business,highway", expense.getTags());

        // Test created time
        Date createdTime = new Date();
        expense.setCreatedTime(createdTime);
        assertEquals(createdTime, expense.getCreatedTime());

        // Test modified time
        Date modifiedTime = new Date();
        expense.setModifiedTime(modifiedTime);
        assertEquals(modifiedTime, expense.getModifiedTime());

        // Test created by user ID
        expense.setCreatedByUserId(456L);
        assertEquals(456L, expense.getCreatedByUserId());
    }

    @Test
    public void testExpenseNullableFields() {
        Expense expense = new Expense();

        // Test that nullable fields are null by default
        assertNull(expense.getDescription());
        assertNull(expense.getReceiptImagePath());
        assertNull(expense.getMileage());
        assertNull(expense.getLocation());
        assertNull(expense.getNotes());
        assertNull(expense.getTags());
        assertNull(expense.getMerchant());
    }

    @Test
    public void testExpenseAmountPrecision() {
        Expense expense = new Expense();

        // Test that BigDecimal preserves precision for monetary amounts
        BigDecimal amount1 = new BigDecimal("123.45");
        expense.setAmount(amount1);
        assertEquals("123.45", expense.getAmount().toString());

        BigDecimal amount2 = new BigDecimal("0.01");
        expense.setAmount(amount2);
        assertEquals("0.01", expense.getAmount().toString());

        BigDecimal amount3 = new BigDecimal("9999.99");
        expense.setAmount(amount3);
        assertEquals("9999.99", expense.getAmount().toString());
    }

    @Test
    public void testExpenseAllTypes() {
        String[] allTypes = {
            Expense.TYPE_FUEL,
            Expense.TYPE_INSURANCE,
            Expense.TYPE_MAINTENANCE,
            Expense.TYPE_PARKING,
            Expense.TYPE_TOLL,
            Expense.TYPE_CARWASH,
            Expense.TYPE_MOBILE,
            Expense.TYPE_LEGAL,
            Expense.TYPE_SUPPLIES,
            Expense.TYPE_OTHERS
        };

        for (String type : allTypes) {
            Expense expense = new Expense();
            expense.setType(type);
            assertEquals(type, expense.getType());
        }
    }

    @Test
    public void testExpenseCompleteScenario() {
        // Create a complete expense scenario
        Expense expense = new Expense();
        expense.setDeviceId(1L);
        expense.setType(Expense.TYPE_FUEL);
        expense.setAmount(new BigDecimal("65.50"));
        expense.setCurrency("CAD");
        expense.setMerchant("Shell Gas Station");
        expense.setExpenseDate(new Date());
        expense.setReceiptImagePath("device123/receipt_123456.jpg");
        expense.setNotes("Regular gasoline, full tank");
        expense.setTags("business,highway");
        expense.setMileage(45000.0);
        expense.setLocation("Vancouver, BC");
        expense.setCreatedByUserId(100L);
        expense.setCreatedTime(new Date());
        expense.setModifiedTime(new Date());

        // Verify all fields are set
        assertNotNull(expense.getDeviceId());
        assertNotNull(expense.getType());
        assertNotNull(expense.getAmount());
        assertNotNull(expense.getCurrency());
        assertNotNull(expense.getMerchant());
        assertNotNull(expense.getExpenseDate());
        assertNotNull(expense.getReceiptImagePath());
        assertNotNull(expense.getNotes());
        assertNotNull(expense.getTags());
        assertNotNull(expense.getMileage());
        assertNotNull(expense.getLocation());
        assertNotNull(expense.getCreatedByUserId());
        assertNotNull(expense.getCreatedTime());
        assertNotNull(expense.getModifiedTime());

        // Verify values
        assertEquals(1L, expense.getDeviceId());
        assertEquals(Expense.TYPE_FUEL, expense.getType());
        assertTrue(expense.getAmount().compareTo(BigDecimal.ZERO) > 0);
        assertEquals("CAD", expense.getCurrency());
    }

}
