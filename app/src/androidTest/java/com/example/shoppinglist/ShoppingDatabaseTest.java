package com.example.shoppinglist;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ShoppingDatabaseTest {
    @Test public void opensExistingVersionOneWithoutChangingData() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String file = "legacy-test-" + java.util.UUID.randomUUID() + ".db";
        try {
            // Build a database exactly as the original MVP did, independently of the helper.
            try (android.database.sqlite.SQLiteDatabase legacy = context.openOrCreateDatabase(file, 0, null)) {
                legacy.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, quantity TEXT NOT NULL, purchased INTEGER NOT NULL DEFAULT 0)");
                legacy.execSQL("INSERT INTO items (id, name, quantity, purchased) VALUES (42, 'Молоко', '2 л', 1)");
                legacy.setVersion(1);
            }
            try (ShoppingDatabase upgraded = new ShoppingDatabase(context, file)) {
                List<ShoppingItem> items = upgraded.getItems();
                assertEquals(1, items.size());
                assertEquals(42, items.get(0).id);
                assertEquals("Молоко", items.get(0).name);
                assertEquals("2 л", items.get(0).quantity);
                assertTrue(items.get(0).purchased);
            }
        } finally { context.deleteDatabase(file); }
    }
    @Test public void persistsSortsTogglesAndDeletes() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String file = "shopping-test-" + System.nanoTime() + ".db";
        ShoppingDatabase db = new ShoppingDatabase(context, file);
        try {
            db.add(" Молоко ", " 2 л ");
            db.add("Хліб", "");
            List<ShoppingItem> items = db.getItems();
            long milk = items.get(0).id;
            long bread = items.get(1).id;
            db.setPurchased(milk, true);
            assertEquals(bread, db.getItems().get(0).id);
            db.close();
            db = new ShoppingDatabase(context, file);
            items = db.getItems();
            assertEquals(2, items.size());
            assertEquals("Хліб", items.get(0).name);
            assertEquals("", items.get(0).quantity);
            assertEquals("Молоко", items.get(1).name);
            assertEquals("2 л", items.get(1).quantity);
            assertTrue(items.get(1).purchased);
            db.setPurchased(milk, false);
            assertEquals(milk, db.getItems().get(0).id);
            db.delete(milk);
            db.delete(bread);
            assertTrue(db.getItems().isEmpty());
        } finally {
            db.close();
            context.deleteDatabase(file);
        }
    }
}
