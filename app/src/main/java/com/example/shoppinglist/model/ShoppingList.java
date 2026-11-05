package com.example.shoppinglist.model;

public final class ShoppingList {
    public final long id;
    public final String name;
    public final long createdAt;
    public final long updatedAt;
    public final Long archivedAt;
    public final int total;
    public final int purchased;

    public ShoppingList(
            long id,
            String name,
            long createdAt,
            long updatedAt,
            Long archivedAt,
            int total,
            int purchased) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.archivedAt = archivedAt;
        this.total = total;
        this.purchased = purchased;
    }

    public static String cleanName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("List name is required");
        }
        return name.trim();
    }
}
