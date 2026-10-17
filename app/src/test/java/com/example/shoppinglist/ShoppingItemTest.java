package com.example.shoppinglist;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShoppingItemTest {
    @Test public void trimsNameAndQuantity() {
        ShoppingItem item = new ShoppingItem(1, 1, "  Молоко  ", " 2 л ", false);
        assertEquals("Молоко", item.name);
        assertEquals("2 л", item.quantity);
        assertFalse(item.purchased);
    }
    @Test public void quantityIsOptional() {
        assertEquals("", new ShoppingItem(1, 1, "Хліб", null, false).quantity);
        assertEquals("", new ShoppingItem(2, 1, "Хліб", "   ", true).quantity);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsBlankName() {
        ShoppingItem.cleanName(" \t\n ");
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsNullName() {
        ShoppingItem.cleanName(null);
    }
}
