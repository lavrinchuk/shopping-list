package com.example.shoppinglist;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

class ShoppingDatabase extends SQLiteOpenHelper {
    ShoppingDatabase(Context context) { this(context, "shopping.db"); }
    ShoppingDatabase(Context context, String name) { super(context, name, null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, quantity TEXT NOT NULL, purchased INTEGER NOT NULL DEFAULT 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported database upgrade");
    }
    void add(String name, String quantity) {
        ShoppingItem item = new ShoppingItem(0, name, quantity, false);
        ContentValues values = new ContentValues();
        values.put("name", item.name);
        values.put("quantity", item.quantity);
        getWritableDatabase().insertOrThrow("items", null, values);
    }
    void setPurchased(long id, boolean purchased) {
        ContentValues values = new ContentValues();
        values.put("purchased", purchased ? 1 : 0);
        getWritableDatabase().update("items", values, "id = ?", new String[]{String.valueOf(id)});
    }
    void delete(long id) {
        getWritableDatabase().delete("items", "id = ?", new String[]{String.valueOf(id)});
    }
    List<ShoppingItem> getItems() {
        List<ShoppingItem> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("items", null, null, null, null, null, "purchased ASC, id ASC")) {
            while (cursor.moveToNext()) {
                items.add(new ShoppingItem(cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    cursor.getString(cursor.getColumnIndexOrThrow("quantity")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("purchased")) != 0));
            }
        }
        return items;
    }
}
