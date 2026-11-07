package com.example.shoppinglist.data;

/** A reusable, independent snapshot of shopping items. */
public final class ShoppingTemplate {
    public final long id;
    public final String name;
    public final int itemCount;

    public ShoppingTemplate(long id, String name, int itemCount) {
        this.id = id;
        this.name = name;
        this.itemCount = itemCount;
    }
}
