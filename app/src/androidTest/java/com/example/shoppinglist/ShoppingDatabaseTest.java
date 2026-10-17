package com.example.shoppinglist;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ShoppingDatabaseTest {
    private Context context;
    private String file;
    private ShoppingDatabase db;
    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        file = "database-test-" + java.util.UUID.randomUUID() + ".db";
        db = new ShoppingDatabase(context, file);
    }
    @After public void cleanup() { db.close(); context.deleteDatabase(file); }
    @Test public void independentListsAllowDuplicateNamesAndPreserveOrder() {
        long a = db.createList(" Їжа "), b = db.createList("Їжа");
        assertNotEquals(a, b); assertEquals(2, db.getLists(false).size());
        db.add(a, "Молоко", "2 л"); db.add(a, "Хліб", ""); db.add(b, "Одяг", "1");
        long milk = db.getItems(a).get(0).id;
        db.setPurchased(a, milk, true);
        assertEquals("Хліб", db.getItems(a).get(0).name);
        assertEquals("Одяг", db.getItems(b).get(0).name);
        db.delete(b, milk); db.setPurchased(b, milk, false);
        assertEquals(2, db.getItems(a).size()); assertTrue(db.getItems(a).get(1).purchased);
        assertEquals(1, db.getList(a).purchased); assertEquals(2, db.getList(a).total);
        db.delete(a, milk); assertEquals(1, db.getItems(a).size());
    }
    @Test public void renameAndItemMutationsUpdateTimestampButReadsDoNot() {
        long id = db.createList("Старе");
        ShoppingList initial = db.getList(id);
        db.getItems(id); db.getLists(false); assertEquals(initial.updatedAt, db.getList(id).updatedAt);
        db.rename(id, "Нове"); ShoppingList renamed = db.getList(id);
        assertEquals("Нове", renamed.name); assertTrue(renamed.updatedAt > initial.updatedAt);
        assertEquals(initial.createdAt, renamed.createdAt);
        db.add(id, "Молоко", ""); long added = db.getList(id).updatedAt; assertTrue(added > renamed.updatedAt);
        long item = db.getItems(id).get(0).id;
        db.setPurchased(id, item, true); long checked = db.getList(id).updatedAt; assertTrue(checked > added);
        db.setPurchased(id, item, true); assertEquals(checked, db.getList(id).updatedAt);
        db.delete(id, item); assertTrue(db.getList(id).updatedAt > checked);
    }
    @Test public void archiveRequiresConfirmationAndPreservesStatusesAndRejectsAllEdits() {
        long id = db.createList("Покупки"); db.add(id, "Молоко", "2 л"); db.add(id, "Хліб", "1");
        long milk = db.getItems(id).get(0).id; db.setPurchased(id, milk, true);
        try { db.archive(id, false); fail("Unpurchased items need confirmation"); }
        catch (ShoppingDatabase.IncompleteListException expected) { }
        assertNull(db.getList(id).archivedAt);
        db.archive(id, true); ShoppingList archived = db.getList(id);
        assertNotNull(archived.archivedAt); assertTrue(db.getLists(false).isEmpty()); assertEquals(id, db.getLists(true).get(0).id);
        assertEquals(2, archived.total); assertEquals(1, archived.purchased);
        assertRejected(() -> db.add(id, "Сир", ""));
        assertRejected(() -> db.delete(id, milk));
        assertRejected(() -> db.setPurchased(id, milk, false));
        assertRejected(() -> db.rename(id, "Зміна"));
        assertEquals(archived.updatedAt, db.getList(id).updatedAt); assertEquals(2, db.getItems(id).size());
        db.close(); db = new ShoppingDatabase(context, file);
        assertEquals(archived.archivedAt, db.getList(id).archivedAt);
        db.restore(id); assertNull(db.getList(id).archivedAt); assertTrue(db.getList(id).updatedAt > archived.updatedAt);
        assertEquals(id, db.getLists(false).get(0).id); assertEquals(1, db.getList(id).purchased);
        db.setPurchased(id, milk, false); assertEquals(0, db.getList(id).purchased);
    }
    @Test public void completingAllItemsDoesNotAutomaticallyArchive() {
        long id = db.createList("Їжа"); db.add(id, "Хліб", ""); db.setPurchased(id, db.getItems(id).get(0).id, true);
        assertNull(db.getList(id).archivedAt); db.archive(id, false); assertNotNull(db.getList(id).archivedAt);
    }
    @Test public void activeAndArchiveSortByTheirOwnDates() {
        long a = db.createList("A"), b = db.createList("B"); db.rename(a, "A1");
        assertTrue(db.getLists(false).get(0).updatedAt >= db.getLists(false).get(1).updatedAt);
        db.archive(a, false); db.archive(b, false);
        List<ShoppingList> archived = db.getLists(true);
        assertTrue(archived.get(0).archivedAt >= archived.get(1).archivedAt);
        db.restore(a); assertEquals(a, db.getLists(false).get(0).id);
    }
    private void createLegacy(boolean empty) {
        db.close();
        try (SQLiteDatabase old = context.openOrCreateDatabase(file, 0, null)) {
            old.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, quantity TEXT NOT NULL, purchased INTEGER NOT NULL DEFAULT 0)");
            old.execSQL("INSERT INTO items VALUES (42, 'Молоко', '2 л', 1)");
            old.execSQL("INSERT INTO items VALUES (43, 'Хліб', '', 0)");
            old.execSQL("INSERT INTO items VALUES (1000, 'Видалений', '', 0)");
            old.execSQL("DELETE FROM items WHERE id = 1000");
            if (empty) old.execSQL("DELETE FROM items");
            old.setVersion(1);
        }
    }
    @Test public void migrationPreservesEveryFieldIdsSequenceAndForeignKeys() {
        createLegacy(false);
        db = new ShoppingDatabase(context, file) {
            @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
                assertTrue(database.inTransaction()); super.onUpgrade(database, oldVersion, newVersion);
            }
        };
        ShoppingList list = db.getLists(false).get(0);
        assertEquals("Мій список", list.name); assertEquals(2, list.total); assertEquals(1, list.purchased);
        assertEquals(list.createdAt, list.updatedAt); assertNull(list.archivedAt);
        List<ShoppingItem> items = db.getItems(list.id);
        assertEquals(43, items.get(0).id); assertEquals("Хліб", items.get(0).name); assertEquals("", items.get(0).quantity); assertFalse(items.get(0).purchased);
        assertEquals(42, items.get(1).id); assertEquals("Молоко", items.get(1).name); assertEquals("2 л", items.get(1).quantity); assertTrue(items.get(1).purchased);
        assertEquals(list.id, items.get(1).listId);
        db.add(list.id, "Новий", ""); assertTrue(db.getItems(list.id).get(1).id > 1000);
        try (Cursor c = db.getReadableDatabase().rawQuery("PRAGMA foreign_key_check", null)) { assertFalse(c.moveToFirst()); }
        assertRejected(() -> db.getWritableDatabase().execSQL("INSERT INTO items(listId,name,quantity) VALUES (999999,'Invalid','')"));
        db.close(); db = new ShoppingDatabase(context, file); assertEquals(1, db.getLists(false).size());
    }
    @Test public void emptyLegacyMigrationAndFreshDatabaseAreSupported() {
        createLegacy(true); db = new ShoppingDatabase(context, file);
        assertEquals(1, db.getLists(false).size()); assertEquals(0, db.getLists(false).get(0).total);
        long id = db.getLists(false).get(0).id; db.add(id, "Новий", ""); assertTrue(db.getItems(id).get(0).id > 1000);
    }
    @Test public void failedMigrationRollsBackOldSchemaAndData() {
        createLegacy(false);
        db = new ShoppingDatabase(context, file) {
            @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
                super.onUpgrade(database, oldVersion, newVersion); throw new SQLiteException("Injected migration failure");
            }
        };
        assertRejected(() -> db.getWritableDatabase()); db.close();
        try (SQLiteDatabase old = context.openOrCreateDatabase(file, 0, null)) {
            assertEquals(1, old.getVersion());
            try (Cursor c = old.rawQuery("SELECT id,name,quantity,purchased FROM items ORDER BY id", null)) {
                assertEquals(2, c.getCount()); assertTrue(c.moveToFirst()); assertEquals(42, c.getLong(0)); assertEquals("Молоко", c.getString(1));
            }
            try (Cursor c = old.rawQuery("SELECT name FROM sqlite_master WHERE name = 'shopping_lists'", null)) { assertFalse(c.moveToFirst()); }
        }
        db = new ShoppingDatabase(context, file); assertEquals(2, db.getLists(false).get(0).total);
    }
    @Test public void blankListNamesAreRejected() {
        try { db.createList("  "); fail("Blank name accepted"); } catch (IllegalArgumentException expected) { }
        assertTrue(db.getLists(false).isEmpty());
    }
    private void assertRejected(Runnable action) {
        try { action.run(); fail("Operation should fail"); } catch (SQLiteException expected) { }
    }
}
