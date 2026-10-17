package com.example.shoppinglist;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

class ShoppingDatabase extends SQLiteOpenHelper {
    static final class IncompleteListException extends SQLiteException { }
    ShoppingDatabase(Context context) { this(context, "shopping.db"); }
    ShoppingDatabase(Context context, String name) { super(context, name, null, 2); }

    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    private void createLists(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE shopping_lists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL CHECK(length(trim(name)) > 0), createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, archivedAt INTEGER)");
        db.execSQL("CREATE INDEX lists_updated ON shopping_lists(archivedAt, updatedAt DESC, id DESC)");
    }
    private void createItems(SQLiteDatabase db, String table) {
        db.execSQL("CREATE TABLE " + table + " (id INTEGER PRIMARY KEY AUTOINCREMENT, listId INTEGER NOT NULL REFERENCES shopping_lists(id) ON DELETE RESTRICT, name TEXT NOT NULL, quantity TEXT NOT NULL, purchased INTEGER NOT NULL DEFAULT 0 CHECK(purchased IN (0,1)))");
    }
    @Override public void onCreate(SQLiteDatabase db) {
        createLists(db);
        createItems(db, "items");
        db.execSQL("CREATE INDEX items_list_order ON items(listId, purchased, id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion != 1 || newVersion != 2) throw new SQLiteException("Unsupported database upgrade");
        // SQLiteOpenHelper wraps onUpgrade and user_version in a single transaction.
        long previousSequence = 0;
        try (Cursor cursor = db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name = 'items'", null)) {
            if (cursor.moveToFirst()) previousSequence = cursor.getLong(0);
        }
        createLists(db);
        long now = System.currentTimeMillis();
        long listId = insertList(db, "Мій список", now);
        createItems(db, "items_v2");
        db.execSQL("INSERT INTO items_v2(id, listId, name, quantity, purchased) SELECT id, ?, name, quantity, purchased FROM items", new Object[]{listId});
        db.execSQL("DROP TABLE items");
        db.execSQL("ALTER TABLE items_v2 RENAME TO items");
        db.execSQL("UPDATE sqlite_sequence SET seq = MAX(seq, ?) WHERE name = 'items'", new Object[]{previousSequence});
        // An empty old table may still have a nonzero AUTOINCREMENT sequence.
        db.execSQL("INSERT INTO sqlite_sequence(name, seq) SELECT 'items', ? WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = 'items')", new Object[]{previousSequence});
        db.execSQL("CREATE INDEX items_list_order ON items(listId, purchased, id)");
    }
    private long insertList(SQLiteDatabase db, String name, long now) {
        ContentValues values = new ContentValues();
        values.put("name", name); values.put("createdAt", now); values.put("updatedAt", now);
        return db.insertOrThrow("shopping_lists", null, values);
    }
    long createList(String name) { return insertList(getWritableDatabase(), ShoppingList.cleanName(name), System.currentTimeMillis()); }
    List<ShoppingList> getLists(boolean archived) {
        return queryLists("l.archivedAt IS " + (archived ? "NOT NULL" : "NULL"), null,
                archived ? "l.archivedAt DESC, l.id DESC" : "l.updatedAt DESC, l.id DESC");
    }
    ShoppingList getList(long id) {
        List<ShoppingList> lists = queryLists("l.id = ?", new String[]{String.valueOf(id)}, "l.id");
        if (lists.isEmpty()) throw new SQLiteException("Shopping list not found: " + id);
        return lists.get(0);
    }
    private List<ShoppingList> queryLists(String where, String[] args, String order) {
        List<ShoppingList> lists = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT l.id, l.name, l.createdAt, l.updatedAt, l.archivedAt, COUNT(i.id), COALESCE(SUM(i.purchased),0) FROM shopping_lists l LEFT JOIN items i ON i.listId = l.id WHERE " + where + " GROUP BY l.id ORDER BY " + order, args)) {
            while (c.moveToNext()) lists.add(new ShoppingList(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3), c.isNull(4) ? null : c.getLong(4), c.getInt(5), c.getInt(6)));
        }
        return lists;
    }
    private ShoppingList requireActive(long id) {
        ShoppingList list = getList(id);
        if (list.archivedAt != null) throw new SQLiteException("Archived shopping list is read-only");
        return list;
    }
    private long nextTimestamp(ShoppingList list) { return Math.max(System.currentTimeMillis(), list.updatedAt + 1); }
    private void touch(SQLiteDatabase db, ShoppingList list) {
        ContentValues values = new ContentValues(); values.put("updatedAt", nextTimestamp(list));
        db.update("shopping_lists", values, "id = ?", new String[]{String.valueOf(list.id)});
    }
    void rename(long id, String name) {
        String clean = ShoppingList.cleanName(name);
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ShoppingList list = requireActive(id);
            if (!list.name.equals(clean)) {
                ContentValues values = new ContentValues(); values.put("name", clean); values.put("updatedAt", nextTimestamp(list));
                db.update("shopping_lists", values, "id = ?", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    void archive(long id, boolean confirmed) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ShoppingList list = requireActive(id);
            if (!confirmed && list.purchased != list.total) throw new IncompleteListException();
            ContentValues values = new ContentValues(); long now = nextTimestamp(list);
            values.put("archivedAt", now); values.put("updatedAt", now);
            db.update("shopping_lists", values, "id = ?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    void restore(long id) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ShoppingList list = getList(id);
            if (list.archivedAt != null) {
                ContentValues values = new ContentValues(); values.putNull("archivedAt"); values.put("updatedAt", nextTimestamp(list));
                db.update("shopping_lists", values, "id = ?", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    void add(long listId, String name, String quantity) {
        ShoppingItem item = new ShoppingItem(0, listId, name, quantity, false);
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ShoppingList list = requireActive(listId);
            ContentValues values = new ContentValues(); values.put("listId", listId); values.put("name", item.name); values.put("quantity", item.quantity);
            db.insertOrThrow("items", null, values); touch(db, list); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    void setPurchased(long listId, long id, boolean purchased) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ShoppingList list = requireActive(listId);
            ContentValues values = new ContentValues(); values.put("purchased", purchased ? 1 : 0);
            int changed = db.update("items", values, "listId = ? AND id = ? AND purchased != ?", new String[]{String.valueOf(listId), String.valueOf(id), purchased ? "1" : "0"});
            if (changed > 0) touch(db, list);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    void delete(long listId, long id) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ShoppingList list = requireActive(listId);
            if (db.delete("items", "listId = ? AND id = ?", new String[]{String.valueOf(listId), String.valueOf(id)}) > 0) touch(db, list);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    List<ShoppingItem> getItems(long listId) {
        List<ShoppingItem> items = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("items", null, "listId = ?", new String[]{String.valueOf(listId)}, null, null, "purchased ASC, id ASC")) {
            while (c.moveToNext()) items.add(new ShoppingItem(c.getLong(c.getColumnIndexOrThrow("id")), listId, c.getString(c.getColumnIndexOrThrow("name")), c.getString(c.getColumnIndexOrThrow("quantity")), c.getInt(c.getColumnIndexOrThrow("purchased")) != 0));
        }
        return items;
    }
}
