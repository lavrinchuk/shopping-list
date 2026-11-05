package com.example.shoppinglist.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ItemLinkTest {
    @Test
    public void emptyUrlIsOptional() {
        assertNull(ItemLink.normalize(null));
        assertNull(ItemLink.normalize("   "));
    }

    @Test
    public void trimsAndAcceptsHttpsOrHttp() {
        assertEquals("https://shop.example.ua/p?q=1", ItemLink.normalize(" https://shop.example.ua/p?q=1 "));
        assertEquals("http://example.com/item", ItemLink.normalize("http://example.com/item"));
    }

    @Test
    public void rejectsJavascriptFileAndRelativeUrls() {
        assertRejected("javascript:alert(1)");
        assertRejected("file:///sdcard/private");
        assertRejected("shop.example.ua/item");
        assertRejected("https://");
        assertRejected("https://user:pass@example.com/item");
        assertRejected("https://example.com/spaced path");
    }

    private static void assertRejected(String url) {
        try {
            ItemLink.normalize(url);
            fail("Accepted invalid URL: " + url);
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }
}
