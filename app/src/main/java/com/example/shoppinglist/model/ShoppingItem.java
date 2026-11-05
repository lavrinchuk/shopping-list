package com.example.shoppinglist.model;

public final class ShoppingItem {
    public final long id;
    public final long listId;
    public final String name;
    public final String quantity;
    public final boolean purchased;

    public ShoppingItem(long id, long listId, String name, String quantity, boolean purchased) {
        this.id = id;
        this.listId = listId;
        this.name = cleanName(name);
        this.quantity = quantity == null ? "" : quantity.trim();
        this.purchased = purchased;
    }

    public static String cleanName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        return name.trim();
    }
}
