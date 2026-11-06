package com.example.shoppinglist.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StoreLinkTest {
    @Test
    public void normalizesNameAndUrl() {
        StoreLink link =
                new StoreLink(1, 7, "  Епіцентр  ", " https://example.com/product?id=10 ");
        assertEquals("Епіцентр", link.storeName);
        assertEquals("https://example.com/product?id=10", link.url);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankStore() {
        new StoreLink(1, 1, " ", "https://example.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankUrl() {
        new StoreLink(1, 1, "Магазин", "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOverlongStoreName() {
        new StoreLink(1, 1, "A".repeat(81), "https://example.com");
    }
}
