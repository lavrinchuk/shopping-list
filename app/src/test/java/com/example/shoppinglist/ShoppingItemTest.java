package com.example.shoppinglist;

import static org.junit.Assert.*;

import com.example.shoppinglist.model.ShoppingItem;
import org.junit.Test;

public class ShoppingItemTest {
    @Test
    public void trimsNameAndQuantity() {
        ShoppingItem item = new ShoppingItem(1, 1, "  Молоко  ", " 2 л ", false);
        assertEquals("Молоко", item.name);
        assertEquals("2 л", item.quantity);
        assertFalse(item.purchased);
    }

    @Test
    public void quantityIsOptional() {
        assertEquals("", new ShoppingItem(1, 1, "Хліб", null, false).quantity);
        assertEquals("", new ShoppingItem(2, 1, "Хліб", "   ", true).quantity);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankName() {
        ShoppingItem.cleanName(" \t\n ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullName() {
        ShoppingItem.cleanName(null);
    }

    @Test
    public void constructorPreservesIdentityAndPurchaseState() {
        ShoppingItem item = new ShoppingItem(42, 7, "Кава", "250 г", true);
        assertEquals(42, item.id);
        assertEquals(7, item.listId);
        assertTrue(item.purchased);
    }

    @Test
    public void cleanNamePreservesInternalSpacesAndUnicode() {
        assertEquals("Їжа для сім’ї", ShoppingItem.cleanName("  Їжа для сім’ї  "));
        assertEquals("Хліб  і  масло", ShoppingItem.cleanName("Хліб  і  масло"));
    }
}
