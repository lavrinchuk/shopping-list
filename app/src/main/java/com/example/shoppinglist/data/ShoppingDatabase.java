package com.example.shoppinglist.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import com.example.shoppinglist.model.ItemLink;
import com.example.shoppinglist.model.ProductBarcode;
import com.example.shoppinglist.model.ShoppingItem;
import com.example.shoppinglist.model.ShoppingList;
import java.util.ArrayList;
import java.util.List;

public class ShoppingDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "shopping.db";
    private static final int DATABASE_VERSION = 8;
    private static final String LEGACY_DEFAULT_LIST_NAME = "Мій список";
    private final Context context;

    public Context context() {
        return context;
    }

    public static final class IncompleteListException extends SQLiteException {}

    public ShoppingDatabase(Context context) {
        this(context, DATABASE_NAME);
    }

    public ShoppingDatabase(Context context, String name) {
        super(context, name, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    private void createLists(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE shopping_lists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL CHECK(length(trim(name)) > 0), createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, archivedAt INTEGER, type TEXT NOT NULL DEFAULT 'shopping' CHECK(type IN ('shopping','wish')), receiptFile TEXT)");
        db.execSQL(
                "CREATE INDEX lists_updated ON shopping_lists(archivedAt, updatedAt DESC, id DESC)");
    }

    private void createItems(SQLiteDatabase db, String table) {
        db.execSQL(
                "CREATE TABLE "
                        + table
                        + " (id INTEGER PRIMARY KEY AUTOINCREMENT, listId INTEGER NOT NULL REFERENCES shopping_lists(id) ON DELETE RESTRICT, name TEXT NOT NULL, quantity TEXT NOT NULL, purchased INTEGER NOT NULL DEFAULT 0 CHECK(purchased IN (0,1)), url TEXT, barcode TEXT)");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createLists(db);
        createItems(db, "items");
        db.execSQL("CREATE INDEX items_list_order ON items(listId, purchased, id)");
        createReceiptTable(db);
        createItemLinksTable(db);
    }

    private void createItemLinksTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE item_links (id INTEGER PRIMARY KEY AUTOINCREMENT, itemId INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE, storeName TEXT NOT NULL, url TEXT NOT NULL, UNIQUE(itemId, url))");
        db.execSQL("CREATE INDEX item_links_item_order ON item_links(itemId, id)");
    }

    private void createReceiptTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE receipt_attachments (id INTEGER PRIMARY KEY AUTOINCREMENT, listId INTEGER NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE, fileName TEXT NOT NULL UNIQUE, mimeType TEXT NOT NULL CHECK(mimeType IN ('image/jpeg','image/png','image/webp','application/pdf')), createdAt INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX receipts_by_list ON receipt_attachments(listId, id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 1 || oldVersion > 7 || newVersion != DATABASE_VERSION) {
            throw new SQLiteException("Unsupported database upgrade");
        }
        // SQLiteOpenHelper wraps schema migration and user_version in a transaction.
        if (oldVersion == 1) {
            upgradeFromV1(db);
        } else if (oldVersion == 2) {
            db.execSQL("ALTER TABLE items ADD COLUMN url TEXT");
        }
        // Version-one migration creates the latest shopping_lists table.
        if (oldVersion >= 2 && oldVersion < 4) {
            db.execSQL(
                    "ALTER TABLE shopping_lists ADD COLUMN type TEXT NOT NULL DEFAULT 'shopping' CHECK(type IN ('shopping','wish'))");
        }
        if (oldVersion >= 2 && oldVersion < 5) {
            db.execSQL("ALTER TABLE shopping_lists ADD COLUMN receiptFile TEXT");
        }
        if (oldVersion >= 2 && oldVersion < 6) {
            db.execSQL("ALTER TABLE items ADD COLUMN barcode TEXT");
        }
        if (oldVersion < 7) {
            createReceiptTable(db);
            db.execSQL(
                    "INSERT INTO receipt_attachments(listId, fileName, mimeType, createdAt) "
                            + "SELECT id, receiptFile, 'image/jpeg', updatedAt FROM shopping_lists "
                            + "WHERE receiptFile IS NOT NULL");
        }
        createItemLinksTable(db);
        db.execSQL(
                "INSERT INTO item_links(itemId, storeName, url) "
                        + "SELECT id, 'Без назви магазину', url FROM items WHERE url IS NOT NULL");
    }

    private void upgradeFromV1(SQLiteDatabase db) {
        long previousSequence = 0;
        try (Cursor cursor =
                db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name = 'items'", null)) {
            if (cursor.moveToFirst()) {
                previousSequence = cursor.getLong(0);
            }
        }
        createLists(db);
        long now = System.currentTimeMillis();
        long listId = insertList(db, LEGACY_DEFAULT_LIST_NAME, now);
        createItems(db, "items_v2");
        db.execSQL(
                "INSERT INTO items_v2(id, listId, name, quantity, purchased) SELECT id, ?, name, quantity, purchased FROM items",
                new Object[] {listId});
        db.execSQL("DROP TABLE items");
        db.execSQL("ALTER TABLE items_v2 RENAME TO items");
        db.execSQL(
                "UPDATE sqlite_sequence SET seq = MAX(seq, ?) WHERE name = 'items'",
                new Object[] {previousSequence});
        // An empty old table may still have a nonzero AUTOINCREMENT sequence.
        db.execSQL(
                "INSERT INTO sqlite_sequence(name, seq) SELECT 'items', ? WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = 'items')",
                new Object[] {previousSequence});
        db.execSQL("CREATE INDEX items_list_order ON items(listId, purchased, id)");
    }

    private long insertList(SQLiteDatabase db, String name, long now) {
        return insertList(db, name, now, ShoppingList.TYPE_SHOPPING);
    }

    private long insertList(SQLiteDatabase db, String name, long now, String type) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("type", ShoppingList.requireType(type));
        values.put("createdAt", now);
        values.put("updatedAt", now);
        return db.insertOrThrow("shopping_lists", null, values);
    }

    public long createList(String name) {
        return createList(name, ShoppingList.TYPE_SHOPPING);
    }

    public long createList(String name, String type) {
        return insertList(
                getWritableDatabase(),
                ShoppingList.cleanName(name),
                System.currentTimeMillis(),
                ShoppingList.requireType(type));
    }

    public List<ShoppingList> getLists(boolean archived) {
        return queryLists(
                "l.archivedAt IS " + (archived ? "NOT NULL" : "NULL"),
                null,
                archived ? "l.archivedAt DESC, l.id DESC" : "l.updatedAt DESC, l.id DESC");
    }

    public ShoppingList getList(long id) {
        List<ShoppingList> lists =
                queryLists("l.id = ?", new String[] {String.valueOf(id)}, "l.id");
        if (lists.isEmpty()) {
            throw new SQLiteException("Shopping list not found: " + id);
        }
        return lists.get(0);
    }

    private List<ShoppingList> queryLists(String where, String[] args, String order) {
        List<ShoppingList> lists = new ArrayList<>();
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT l.id, l.name, l.createdAt, l.updatedAt, l.archivedAt, COUNT(i.id), COALESCE(SUM(i.purchased),0), l.type, l.receiptFile FROM shopping_lists l LEFT JOIN items i ON i.listId = l.id WHERE "
                                        + where
                                        + " GROUP BY l.id ORDER BY "
                                        + order,
                                args)) {
            while (c.moveToNext()) {
                lists.add(
                        new ShoppingList(
                                c.getLong(0),
                                c.getString(1),
                                c.getLong(2),
                                c.getLong(3),
                                c.isNull(4) ? null : c.getLong(4),
                                c.getInt(5),
                                c.getInt(6),
                                c.getString(7),
                                c.getString(8)));
            }
        }
        return lists;
    }

    private ShoppingList requireActive(long id) {
        ShoppingList list = getList(id);
        if (list.archivedAt != null) {
            throw new SQLiteException("Archived shopping list is read-only");
        }
        return list;
    }

    private long nextTimestamp(ShoppingList list) {
        return Math.max(System.currentTimeMillis(), list.updatedAt + 1);
    }

    private void touch(SQLiteDatabase db, ShoppingList list) {
        ContentValues values = new ContentValues();
        values.put("updatedAt", nextTimestamp(list));
        db.update("shopping_lists", values, "id = ?", new String[] {String.valueOf(list.id)});
    }

    public void rename(long id, String name) {
        String clean = ShoppingList.cleanName(name);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = requireActive(id);
            if (!list.name.equals(clean)) {
                ContentValues values = new ContentValues();
                values.put("name", clean);
                values.put("updatedAt", nextTimestamp(list));
                db.update("shopping_lists", values, "id = ?", new String[] {String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void archive(long id, boolean confirmed) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = requireActive(id);
            if (!list.isWish() && !confirmed && list.purchased != list.total) {
                throw new IncompleteListException();
            }
            ContentValues values = new ContentValues();
            long now = nextTimestamp(list);
            values.put("archivedAt", now);
            values.put("updatedAt", now);
            db.update("shopping_lists", values, "id = ?", new String[] {String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Remove only a still-empty active shopping list. Never delete archived or wish lists. */
    public void deleteEmptyShoppingList(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = requireActive(id);
            if (list.isWish() || list.total != 0 || list.receiptFile != null) {
                throw new SQLiteException("Only empty active shopping lists may be deleted");
            }
            int removed =
                    db.delete(
                            "shopping_lists",
                            "id = ? AND NOT EXISTS (SELECT 1 FROM items WHERE listId = ?)",
                            new String[] {String.valueOf(id), String.valueOf(id)});
            if (removed != 1) {
                throw new SQLiteException("List is not empty");
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void restore(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = getList(id);
            if (list.archivedAt != null) {
                ContentValues values = new ContentValues();
                values.putNull("archivedAt");
                values.put("updatedAt", nextTimestamp(list));
                db.update("shopping_lists", values, "id = ?", new String[] {String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public java.util.List<ReceiptAttachment> getReceipts(long listId) {
        java.util.List<ReceiptAttachment> result = new java.util.ArrayList<>();
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT id, listId, fileName, mimeType FROM receipt_attachments "
                                        + "WHERE listId = ? ORDER BY id",
                                new String[] {String.valueOf(listId)})) {
            while (c.moveToNext()) {
                result.add(
                        new ReceiptAttachment(
                                c.getLong(0), c.getLong(1), c.getString(2), c.getString(3)));
            }
        }
        return result;
    }

    /** Add a receipt in the same transaction as the legacy first-receipt pointer update. */
    public void addReceipt(long listId, String name, String mimeType) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = getList(listId);
            if (list.archivedAt == null) {
                throw new SQLiteException("Receipts require an archived list");
            }
            ContentValues record = new ContentValues();
            record.put("listId", listId);
            record.put("fileName", name);
            record.put("mimeType", mimeType);
            record.put("createdAt", System.currentTimeMillis());
            db.insertOrThrow("receipt_attachments", null, record);
            ContentValues values = new ContentValues();
            if (list.receiptFile == null) {
                values.put("receiptFile", name);
            }
            values.put("updatedAt", nextTimestamp(list));
            db.update("shopping_lists", values, "id = ?", new String[] {String.valueOf(listId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Adds all selected files atomically, including the legacy first receipt pointer. */
    public void addReceipts(long listId, java.util.List<ReceiptAttachment> files) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = getList(listId);
            if (list.archivedAt == null || files.isEmpty()) {
                throw new SQLiteException("Receipts require an archived list");
            }
            ContentValues changed = new ContentValues();
            for (ReceiptAttachment file : files) {
                ContentValues record = new ContentValues();
                record.put("listId", listId);
                record.put("fileName", file.fileName);
                record.put("mimeType", file.mimeType);
                record.put("createdAt", System.currentTimeMillis());
                db.insertOrThrow("receipt_attachments", null, record);
                if (list.receiptFile == null && !changed.containsKey("receiptFile")) {
                    changed.put("receiptFile", file.fileName);
                }
            }
            changed.put("updatedAt", nextTimestamp(list));
            db.update("shopping_lists", changed, "id = ?", new String[] {String.valueOf(listId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Returns the deleted filename only after successful commit. */
    public String removeReceipt(long listId, long attachmentId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = getList(listId);
            if (list.archivedAt == null) {
                throw new SQLiteException("Receipts require an archived list");
            }
            String name;
            try (Cursor cursor =
                    db.rawQuery(
                            "SELECT fileName FROM receipt_attachments WHERE id = ? AND listId = ?",
                            new String[] {String.valueOf(attachmentId), String.valueOf(listId)})) {
                if (!cursor.moveToFirst()) {
                    throw new SQLiteException("Receipt not found");
                }
                name = cursor.getString(0);
            }
            db.delete(
                    "receipt_attachments",
                    "id = ? AND listId = ?",
                    new String[] {String.valueOf(attachmentId), String.valueOf(listId)});
            ContentValues values = new ContentValues();
            if (name.equals(list.receiptFile)) {
                try (Cursor next =
                        db.rawQuery(
                                "SELECT fileName FROM receipt_attachments WHERE listId = ? ORDER BY id LIMIT 1",
                                new String[] {String.valueOf(listId)})) {
                    if (next.moveToFirst()) {
                        values.put("receiptFile", next.getString(0));
                    } else {
                        values.putNull("receiptFile");
                    }
                }
            }
            values.put("updatedAt", nextTimestamp(list));
            db.update("shopping_lists", values, "id = ?", new String[] {String.valueOf(listId)});
            db.setTransactionSuccessful();
            return name;
        } finally {
            db.endTransaction();
        }
    }

    /** Receipts can only be modified on archived lists; returns previous file after commit. */
    public String setReceipt(long listId, String filename) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = getList(listId);
            if (list.archivedAt == null) {
                throw new SQLiteException("Receipts require an archived list");
            }
            ContentValues values = new ContentValues();
            if (filename == null) {
                values.putNull("receiptFile");
            } else {
                values.put("receiptFile", filename);
            }
            db.delete("receipt_attachments", "listId = ?", new String[] {String.valueOf(listId)});
            if (filename != null) {
                ContentValues record = new ContentValues();
                record.put("listId", listId);
                record.put("fileName", filename);
                record.put("mimeType", "image/jpeg");
                record.put("createdAt", System.currentTimeMillis());
                db.insertOrThrow("receipt_attachments", null, record);
            }
            values.put("updatedAt", nextTimestamp(list));
            int changed =
                    db.update(
                            "shopping_lists",
                            values,
                            "id = ?",
                            new String[] {String.valueOf(listId)});
            if (changed != 1) {
                throw new SQLiteException("Could not update receipt");
            }
            db.setTransactionSuccessful();
            return list.receiptFile;
        } finally {
            db.endTransaction();
        }
    }

    public void add(long listId, String name, String quantity) {
        ShoppingItem item = new ShoppingItem(0, listId, name, quantity, false);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = requireActive(listId);
            ContentValues values = new ContentValues();
            values.put("listId", listId);
            values.put("name", item.name);
            values.put("quantity", item.quantity);
            db.insertOrThrow("items", null, values);
            touch(db, list);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void setPurchased(long listId, long id, boolean purchased) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = requireActive(listId);
            ContentValues values = new ContentValues();
            values.put("purchased", purchased ? 1 : 0);
            int changed =
                    db.update(
                            "items",
                            values,
                            "listId = ? AND id = ? AND purchased != ?",
                            new String[] {
                                String.valueOf(listId), String.valueOf(id), purchased ? "1" : "0"
                            });
            if (changed > 0) {
                touch(db, list);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void delete(long listId, long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = requireActive(listId);
            if (db.delete(
                            "items",
                            "listId = ? AND id = ?",
                            new String[] {String.valueOf(listId), String.valueOf(id)})
                    > 0) {
                touch(db, list);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void requireItem(SQLiteDatabase db, long listId, long itemId) {
        try (Cursor c =
                db.rawQuery(
                        "SELECT id FROM items WHERE id = ? AND listId = ?",
                        new String[] {String.valueOf(itemId), String.valueOf(listId)})) {
            if (!c.moveToFirst()) {
                throw new SQLiteException("Item not found");
            }
        }
    }

    private StoreLink requireStoreLink(SQLiteDatabase db, long listId, long linkId) {
        try (Cursor c =
                db.rawQuery(
                        "SELECT li.id, li.itemId, li.storeName, li.url FROM item_links li "
                                + "JOIN items i ON li.itemId = i.id "
                                + "WHERE li.id = ? AND i.listId = ?",
                        new String[] {String.valueOf(linkId), String.valueOf(listId)})) {
            if (!c.moveToFirst()) {
                throw new SQLiteException("Store link not found");
            }
            return new StoreLink(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3));
        }
    }

    private void syncPrimaryLink(SQLiteDatabase db, long itemId) {
        String url = null;
        try (Cursor c =
                db.rawQuery(
                        "SELECT url FROM item_links WHERE itemId = ? ORDER BY id LIMIT 1",
                        new String[] {String.valueOf(itemId)})) {
            if (c.moveToFirst()) {
                url = c.getString(0);
            }
        }
        ContentValues values = new ContentValues();
        if (url == null) {
            values.putNull("url");
        } else {
            values.put("url", url);
        }
        db.update("items", values, "id = ?", new String[] {String.valueOf(itemId)});
    }

    /**
     * Compatibility with LINK-01. Replaces all links because the old API has one URL field.
     * LINK-02 UI uses add/update/deleteStoreLink instead.
     */
    public void setItemUrl(long listId, long id, String url) {
        String normalized = ItemLink.normalize(url);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = requireActive(listId);
            requireItem(db, listId, id);
            try (Cursor c =
                    db.rawQuery(
                            "SELECT url FROM items WHERE listId = ? AND id = ?",
                            new String[] {String.valueOf(listId), String.valueOf(id)})) {
                if (c.moveToFirst() && java.util.Objects.equals(c.getString(0), normalized)) {
                    db.setTransactionSuccessful();
                    return;
                }
            }
            db.delete("item_links", "itemId = ?", new String[] {String.valueOf(id)});
            if (normalized != null) {
                ContentValues link = new ContentValues();
                link.put("itemId", id);
                link.put("storeName", "Без назви магазину");
                link.put("url", normalized);
                db.insertOrThrow("item_links", null, link);
            }
            syncPrimaryLink(db, id);
            touch(db, list);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<StoreLink> getStoreLinks(long listId) {
        List<StoreLink> links = new ArrayList<>();
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT li.id, li.itemId, li.storeName, li.url FROM item_links li "
                                        + "JOIN items i ON li.itemId = i.id "
                                        + "WHERE i.listId = ? ORDER BY li.itemId, li.id",
                                new String[] {String.valueOf(listId)})) {
            while (c.moveToNext()) {
                links.add(
                        new StoreLink(
                                c.getLong(0), c.getLong(1), c.getString(2), c.getString(3)));
            }
        }
        return links;
    }

    public void addStoreLink(long listId, long itemId, String storeName, String url) {
        StoreLink link = new StoreLink(0, itemId, storeName, url);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = requireActive(listId);
            requireItem(db, listId, itemId);
            ContentValues values = new ContentValues();
            values.put("itemId", itemId);
            values.put("storeName", link.storeName);
            values.put("url", link.url);
            db.insertOrThrow("item_links", null, values);
            syncPrimaryLink(db, itemId);
            touch(db, list);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void updateStoreLink(long listId, long linkId, String storeName, String url) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = requireActive(listId);
            StoreLink old = requireStoreLink(db, listId, linkId);
            StoreLink changed = new StoreLink(linkId, old.itemId, storeName, url);
            if (!old.storeName.equals(changed.storeName) || !old.url.equals(changed.url)) {
                ContentValues values = new ContentValues();
                values.put("storeName", changed.storeName);
                values.put("url", changed.url);
                db.update("item_links", values, "id = ?", new String[] {String.valueOf(linkId)});
                syncPrimaryLink(db, old.itemId);
                touch(db, list);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void deleteStoreLink(long listId, long linkId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = requireActive(listId);
            StoreLink link = requireStoreLink(db, listId, linkId);
            db.delete("item_links", "id = ?", new String[] {String.valueOf(linkId)});
            syncPrimaryLink(db, link.itemId);
            touch(db, list);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void setItemBarcode(long listId, long id, String code) {
        String normalized = ProductBarcode.normalize(code);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ShoppingList list = requireActive(listId);
            ContentValues values = new ContentValues();
            if (normalized == null) {
                values.putNull("barcode");
            } else {
                values.put("barcode", normalized);
            }
            int changed =
                    db.update(
                            "items",
                            values,
                            "id = ? AND listId = ? AND "
                                    + (normalized == null
                                            ? "barcode IS NOT NULL"
                                            : "(barcode IS NULL OR barcode != ?)"),
                            normalized == null
                                    ? new String[] {String.valueOf(id), String.valueOf(listId)}
                                    : new String[] {
                                        String.valueOf(id), String.valueOf(listId), normalized
                                    });
            if (changed > 0) {
                touch(db, list);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<ShoppingItem> getItems(long listId) {
        List<ShoppingItem> items = new ArrayList<>();
        try (Cursor c =
                getReadableDatabase()
                        .query(
                                "items",
                                null,
                                "listId = ?",
                                new String[] {String.valueOf(listId)},
                                null,
                                null,
                                "purchased ASC, id ASC")) {
            while (c.moveToNext()) {
                items.add(
                        new ShoppingItem(
                                c.getLong(c.getColumnIndexOrThrow("id")),
                                listId,
                                c.getString(c.getColumnIndexOrThrow("name")),
                                c.getString(c.getColumnIndexOrThrow("quantity")),
                                c.getInt(c.getColumnIndexOrThrow("purchased")) != 0,
                                c.getString(c.getColumnIndexOrThrow("url")),
                                c.getString(c.getColumnIndexOrThrow("barcode"))));
            }
        }
        return items;
    }
}
