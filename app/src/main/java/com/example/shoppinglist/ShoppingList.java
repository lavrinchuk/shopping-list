package com.example.shoppinglist;

final class ShoppingList {
    final long id;
    final String name;
    final long createdAt;
    final long updatedAt;
    final Long archivedAt;
    final int total;
    final int purchased;

    ShoppingList(long id, String name, long createdAt, long updatedAt, Long archivedAt, int total, int purchased) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.archivedAt = archivedAt;
        this.total = total;
        this.purchased = purchased;
    }
    static String cleanName(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("List name is required");
        return name.trim();
    }
}
