package com.example.shoppinglist.model;

public final class ShoppingList {
    public static final String TYPE_SHOPPING = "shopping";
    public static final String TYPE_WISH = "wish";

    public static String requireType(String type) {
        if (!TYPE_SHOPPING.equals(type) && !TYPE_WISH.equals(type)) {
            throw new IllegalArgumentException("Unsupported list type");
        }
        return type;
    }

    public boolean isWish() {
        return TYPE_WISH.equals(type);
    }

    public final long id;
    public final String name;
    public final String type;
    public final String receiptFile;
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
        this(id, name, createdAt, updatedAt, archivedAt, total, purchased, TYPE_SHOPPING, null);
    }

    public ShoppingList(
            long id,
            String name,
            long createdAt,
            long updatedAt,
            Long archivedAt,
            int total,
            int purchased,
            String type) {
        this(id, name, createdAt, updatedAt, archivedAt, total, purchased, type, null);
    }

    public ShoppingList(
            long id,
            String name,
            long createdAt,
            long updatedAt,
            Long archivedAt,
            int total,
            int purchased,
            String type,
            String receiptFile) {
        this.id = id;
        this.type = requireType(type);
        this.receiptFile = receiptFile;
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
