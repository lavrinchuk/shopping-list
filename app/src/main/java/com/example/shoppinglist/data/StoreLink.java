package com.example.shoppinglist.data;

import com.example.shoppinglist.model.ItemLink;

/** One store option for a shopping item; preserves the URL exactly as entered. */
public final class StoreLink {
    public final long id;
    public final long itemId;
    public final String storeName;
    public final String url;

    public StoreLink(long id, long itemId, String storeName, String url) {
        this.id = id;
        this.itemId = itemId;
        this.storeName = cleanStoreName(storeName);
        String normalized = ItemLink.normalize(url);
        if (normalized == null) {
            throw new IllegalArgumentException("Store URL is required");
        }
        this.url = normalized;
    }

    public static String cleanStoreName(String name) {
        if (name == null
                || name.trim().isEmpty()
                || name.trim().length() > 80
                || name.indexOf('\n') >= 0
                || name.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Store name is required (1-80 characters)");
        }
        return name.trim();
    }
}
