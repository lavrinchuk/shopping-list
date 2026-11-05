package com.example.shoppinglist;

import static org.junit.Assert.*;

import com.example.shoppinglist.model.ShoppingList;
import org.junit.Test;

public class ShoppingListTest {
    @Test
    public void cleanNameTrimsWhitespaceWithoutChangingInnerSpaces() {
        assertEquals("Список для дому", ShoppingList.cleanName("  Список для дому \n"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void cleanNameRejectsWhitespaceOnly() {
        ShoppingList.cleanName("\t \n ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void cleanNameRejectsNull() {
        ShoppingList.cleanName(null);
    }

    @Test
    public void constructorPreservesDatabaseMetadataAndArchiveFlag() {
        ShoppingList list = new ShoppingList(7L, "Сільпо", 100L, 200L, 300L, 5, 2);
        assertEquals(7L, list.id);
        assertEquals("Сільпо", list.name);
        assertEquals(100L, list.createdAt);
        assertEquals(200L, list.updatedAt);
        assertEquals(Long.valueOf(300L), list.archivedAt);
        assertEquals(5, list.total);
        assertEquals(2, list.purchased);
    }

    @Test
    public void activeListHasNoArchiveTimestamp() {
        ShoppingList list = new ShoppingList(7L, "Одяг", 100L, 100L, null, 0, 0);
        assertNull(list.archivedAt);
    }
}
