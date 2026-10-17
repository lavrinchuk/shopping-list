package com.example.shoppinglist;

final class ShoppingItem {
    final long id;
    final long listId;
    final String name;
    final String quantity;
    final boolean purchased;

    ShoppingItem(long id, long listId, String name, String quantity, boolean purchased) {
        this.id = id;
        this.listId = listId;
        this.name = cleanName(name);
        this.quantity = quantity == null ? "" : quantity.trim();
        this.purchased = purchased;
    }

    static String cleanName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        return name.trim();
    }
}
