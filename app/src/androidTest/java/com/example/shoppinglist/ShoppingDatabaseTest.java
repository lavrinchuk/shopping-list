package com.example.shoppinglist;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.shoppinglist.data.ShoppingDatabase;
import com.example.shoppinglist.model.ShoppingItem;
import com.example.shoppinglist.model.ShoppingList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ShoppingDatabaseTest {
    private Context context;
    private String file;
    private ShoppingDatabase db;

    @Before
    public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        file = "database-test-" + java.util.UUID.randomUUID() + ".db";
        db = new ShoppingDatabase(context, file);
    }

    @After
    public void cleanup() {
        db.close();
        context.deleteDatabase(file);
    }

    @Test
    public void independentListsAllowDuplicateNamesAndPreserveOrder() {
        long a = db.createList(" Їжа "), b = db.createList("Їжа");
        assertNotEquals(a, b);
        assertEquals(2, db.getLists(false).size());
        db.add(a, "Молоко", "2 л");
        db.add(a, "Хліб", "");
        db.add(b, "Одяг", "1");
        long milk = db.getItems(a).get(0).id;
        db.setPurchased(a, milk, true);
        assertEquals("Хліб", db.getItems(a).get(0).name);
        assertEquals("Одяг", db.getItems(b).get(0).name);
        db.delete(b, milk);
        db.setPurchased(b, milk, false);
        assertEquals(2, db.getItems(a).size());
        assertTrue(db.getItems(a).get(1).purchased);
        assertEquals(1, db.getList(a).purchased);
        assertEquals(2, db.getList(a).total);
        db.delete(a, milk);
        assertEquals(1, db.getItems(a).size());
    }

    @Test
    public void renameAndItemMutationsUpdateTimestampButReadsDoNot() {
        long id = db.createList("Старе");
        ShoppingList initial = db.getList(id);
        db.getItems(id);
        db.getLists(false);
        assertEquals(initial.updatedAt, db.getList(id).updatedAt);
        db.rename(id, "Нове");
        ShoppingList renamed = db.getList(id);
        assertEquals("Нове", renamed.name);
        assertTrue(renamed.updatedAt > initial.updatedAt);
        assertEquals(initial.createdAt, renamed.createdAt);
        db.add(id, "Молоко", "");
        long added = db.getList(id).updatedAt;
        assertTrue(added > renamed.updatedAt);
        long item = db.getItems(id).get(0).id;
        db.setPurchased(id, item, true);
        long checked = db.getList(id).updatedAt;
        assertTrue(checked > added);
        db.setPurchased(id, item, true);
        assertEquals(checked, db.getList(id).updatedAt);
        db.delete(id, item);
        assertTrue(db.getList(id).updatedAt > checked);
    }

    @Test
    public void archiveRequiresConfirmationAndPreservesStatusesAndRejectsAllEdits() {
        long id = db.createList("Покупки");
        db.add(id, "Молоко", "2 л");
        db.add(id, "Хліб", "1");
        long milk = db.getItems(id).get(0).id;
        db.setPurchased(id, milk, true);
        try {
            db.archive(id, false);
            fail("Unpurchased items need confirmation");
        } catch (ShoppingDatabase.IncompleteListException expected) {
        }
        assertNull(db.getList(id).archivedAt);
        db.archive(id, true);
        ShoppingList archived = db.getList(id);
        assertNotNull(archived.archivedAt);
        assertTrue(db.getLists(false).isEmpty());
        assertEquals(id, db.getLists(true).get(0).id);
        assertEquals(2, archived.total);
        assertEquals(1, archived.purchased);
        assertRejected(() -> db.add(id, "Сир", ""));
        assertRejected(() -> db.delete(id, milk));
        assertRejected(() -> db.setPurchased(id, milk, false));
        assertRejected(() -> db.rename(id, "Зміна"));
        assertEquals(archived.updatedAt, db.getList(id).updatedAt);
        assertEquals(2, db.getItems(id).size());
        db.close();
        db = new ShoppingDatabase(context, file);
        assertEquals(archived.archivedAt, db.getList(id).archivedAt);
        db.restore(id);
        assertNull(db.getList(id).archivedAt);
        assertTrue(db.getList(id).updatedAt > archived.updatedAt);
        assertEquals(id, db.getLists(false).get(0).id);
        assertEquals(1, db.getList(id).purchased);
        db.setPurchased(id, milk, false);
        assertEquals(0, db.getList(id).purchased);
    }

    @Test
    public void completingAllItemsDoesNotAutomaticallyArchive() {
        long id = db.createList("Їжа");
        db.add(id, "Хліб", "");
        db.setPurchased(id, db.getItems(id).get(0).id, true);
        assertNull(db.getList(id).archivedAt);
        db.archive(id, false);
        assertNotNull(db.getList(id).archivedAt);
    }

    @Test
    public void activeAndArchiveSortByTheirOwnDates() {
        long a = db.createList("A"), b = db.createList("B");
        db.rename(a, "A1");
        assertTrue(db.getLists(false).get(0).updatedAt >= db.getLists(false).get(1).updatedAt);
        db.archive(a, false);
        db.archive(b, false);
        List<ShoppingList> archived = db.getLists(true);
        assertTrue(archived.get(0).archivedAt >= archived.get(1).archivedAt);
        db.restore(a);
        assertEquals(a, db.getLists(false).get(0).id);
    }

    private void createLegacy(boolean empty) {
        db.close();
        try (SQLiteDatabase old = context.openOrCreateDatabase(file, 0, null)) {
            old.execSQL(
                    "CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, quantity TEXT NOT NULL, purchased INTEGER NOT NULL DEFAULT 0)");
            old.execSQL("INSERT INTO items VALUES (42, 'Молоко', '2 л', 1)");
            old.execSQL("INSERT INTO items VALUES (43, 'Хліб', '', 0)");
            old.execSQL("INSERT INTO items VALUES (1000, 'Видалений', '', 0)");
            old.execSQL("DELETE FROM items WHERE id = 1000");
            if (empty) {
                old.execSQL("DELETE FROM items");
            }
            old.setVersion(1);
        }
    }

    @Test
    public void migrationPreservesEveryFieldIdsSequenceAndForeignKeys() {
        createLegacy(false);
        db =
                new ShoppingDatabase(context, file) {
                    @Override
                    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
                        assertTrue(database.inTransaction());
                        super.onUpgrade(database, oldVersion, newVersion);
                    }
                };
        ShoppingList list = db.getLists(false).get(0);
        assertEquals("Мій список", list.name);
        assertEquals(2, list.total);
        assertEquals(1, list.purchased);
        assertEquals(list.createdAt, list.updatedAt);
        assertNull(list.archivedAt);
        List<ShoppingItem> items = db.getItems(list.id);
        assertEquals(43, items.get(0).id);
        assertEquals("Хліб", items.get(0).name);
        assertEquals("", items.get(0).quantity);
        assertFalse(items.get(0).purchased);
        assertEquals(42, items.get(1).id);
        assertEquals("Молоко", items.get(1).name);
        assertEquals("2 л", items.get(1).quantity);
        assertTrue(items.get(1).purchased);
        assertEquals(list.id, items.get(1).listId);
        db.add(list.id, "Новий", "");
        assertTrue(db.getItems(list.id).get(1).id > 1000);
        try (Cursor c = db.getReadableDatabase().rawQuery("PRAGMA foreign_key_check", null)) {
            assertFalse(c.moveToFirst());
        }
        assertRejected(() ->
                db.getWritableDatabase()
                        .execSQL(
                                "INSERT INTO items(listId,name,quantity) VALUES (999999,'Invalid','')"));
        db.close();
        db = new ShoppingDatabase(context, file);
        assertEquals(1, db.getLists(false).size());
    }

    @Test
    public void emptyLegacyMigrationAndFreshDatabaseAreSupported() {
        createLegacy(true);
        db = new ShoppingDatabase(context, file);
        assertEquals(1, db.getLists(false).size());
        assertEquals(0, db.getLists(false).get(0).total);
        long id = db.getLists(false).get(0).id;
        db.add(id, "Новий", "");
        assertTrue(db.getItems(id).get(0).id > 1000);
    }

    @Test
    public void failedMigrationRollsBackOldSchemaAndData() {
        createLegacy(false);
        db =
                new ShoppingDatabase(context, file) {
                    @Override
                    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
                        super.onUpgrade(database, oldVersion, newVersion);
                        throw new SQLiteException("Injected migration failure");
                    }
                };
        assertRejected(() -> db.getWritableDatabase());
        db.close();
        try (SQLiteDatabase old = context.openOrCreateDatabase(file, 0, null)) {
            assertEquals(1, old.getVersion());
            try (Cursor c =
                    old.rawQuery(
                            "SELECT id,name,quantity,purchased FROM items ORDER BY id", null)) {
                assertEquals(2, c.getCount());
                assertTrue(c.moveToFirst());
                assertEquals(42, c.getLong(0));
                assertEquals("Молоко", c.getString(1));
            }
            try (Cursor c =
                    old.rawQuery(
                            "SELECT name FROM sqlite_master WHERE name = 'shopping_lists'", null)) {
                assertFalse(c.moveToFirst());
            }
        }
        db = new ShoppingDatabase(context, file);
        assertEquals(2, db.getLists(false).get(0).total);
    }

    @Test
    public void blankListNamesAreRejected() {
        try {
            db.createList("  ");
            fail("Blank name accepted");
        } catch (IllegalArgumentException expected) {
        }
        assertTrue(db.getLists(false).isEmpty());
    }

    @Test
    public void rollbackOnFailedArchiveKeepsListAndItemsWritable() {
        long listId = db.createList("Продукти");
        db.add(listId, "Хліб", "1 шт.");
        long itemId = db.getItems(listId).get(0).id;
        ShoppingList before = db.getList(listId);

        try {
            db.archive(listId, false);
            fail("Incomplete list should require confirmation");
        } catch (ShoppingDatabase.IncompleteListException expected) {
            // The transaction must roll back, not partially archive the list.
        }

        ShoppingList after = db.getList(listId);
        assertNull(after.archivedAt);
        assertEquals(before.updatedAt, after.updatedAt);
        assertEquals(1, after.total);
        db.setPurchased(listId, itemId, true);
        assertEquals(1, db.getList(listId).purchased);
        db.archive(listId, false);
        assertNotNull(db.getList(listId).archivedAt);
    }

    @Test
    public void noOpMutationsDoNotChangeUpdateTimestamp() {
        long listId = db.createList("Покупки");
        db.add(listId, "Кава", "");
        ShoppingList original = db.getList(listId);

        db.rename(listId, " Покупки ");
        db.setPurchased(listId, 999999L, true);
        db.delete(listId, 999999L);
        assertEquals(original.updatedAt, db.getList(listId).updatedAt);
        assertEquals(1, db.getList(listId).total);

        db.restore(listId);
        assertEquals(original.updatedAt, db.getList(listId).updatedAt);
    }

    @Test
    public void completedListKeepsItemsAfterReopeningDatabaseAndRestoring() {
        long listId = db.createList("Дім");
        db.add(listId, "Лампа", "2");
        db.add(listId, "Дріт", "3 м");
        long firstItemId = db.getItems(listId).get(0).id;
        db.setPurchased(listId, firstItemId, true);
        db.archive(listId, true);
        long archivedAt = db.getList(listId).archivedAt;

        db.close();
        db = new ShoppingDatabase(context, file);

        ShoppingList archivedList = db.getList(listId);
        assertEquals(Long.valueOf(archivedAt), archivedList.archivedAt);
        assertEquals(2, archivedList.total);
        assertEquals(1, archivedList.purchased);
        assertEquals(2, db.getItems(listId).size());
        assertEquals(firstItemId, db.getItems(listId).get(1).id);

        db.restore(listId);
        assertNull(db.getList(listId).archivedAt);
        assertEquals(1, db.getList(listId).purchased);
    }

    @Test
    public void itemLinkPersistsAcrossReopenAndCanBeCleared() {
        long listId = db.createList("Покупки");
        db.add(listId, "Кава", "");
        long itemId = db.getItems(listId).get(0).id;
        assertNull(db.getItems(listId).get(0).url);

        db.setItemUrl(listId, itemId, " https://shop.example.ua/coffee ");
        long updated = db.getList(listId).updatedAt;
        assertEquals("https://shop.example.ua/coffee", db.getItems(listId).get(0).url);
        db.setItemUrl(listId, itemId, "https://shop.example.ua/coffee");
        assertEquals(updated, db.getList(listId).updatedAt);

        db.close();
        db = new ShoppingDatabase(context, file);
        assertEquals("https://shop.example.ua/coffee", db.getItems(listId).get(0).url);
        db.setItemUrl(listId, itemId, "");
        assertNull(db.getItems(listId).get(0).url);
        assertTrue(db.getList(listId).updatedAt > updated);
    }

    @Test
    public void archiveKeepsUrlReadableButRejectsEditing() {
        long listId = db.createList("Покупки");
        db.add(listId, "Лампа", "2");
        long itemId = db.getItems(listId).get(0).id;
        db.setItemUrl(listId, itemId, "https://example.com/lamp");
        db.archive(listId, true);
        assertEquals("https://example.com/lamp", db.getItems(listId).get(0).url);
        assertRejected(() -> db.setItemUrl(listId, itemId, "https://example.com/other"));
        assertEquals("https://example.com/lamp", db.getItems(listId).get(0).url);
    }

    @Test
    public void upgradeFromVersionTwoPreservesItemsAndArchivedLists() {
        db.close();
        try (SQLiteDatabase old = context.openOrCreateDatabase(file, 0, null)) {
            old.execSQL(
                    "CREATE TABLE shopping_lists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, archivedAt INTEGER)");
            old.execSQL(
                    "CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, listId INTEGER NOT NULL REFERENCES shopping_lists(id), name TEXT NOT NULL, quantity TEXT NOT NULL, purchased INTEGER NOT NULL)");
            old.execSQL("INSERT INTO shopping_lists VALUES (12, 'Архів', 100, 120, 120)");
            old.execSQL("INSERT INTO items VALUES (42, 12, 'Молоко', '2 л', 1)");
            old.setVersion(2);
        }
        db = new ShoppingDatabase(context, file);
        ShoppingList list = db.getList(12);
        assertEquals(Long.valueOf(120), list.archivedAt);
        assertEquals(1, list.total);
        assertEquals(1, list.purchased);
        ShoppingItem item = db.getItems(12).get(0);
        assertEquals(42, item.id);
        assertEquals("Молоко", item.name);
        assertEquals("2 л", item.quantity);
        assertNull(item.url);
        assertEquals(6, db.getReadableDatabase().getVersion());
    }

    @Test
    public void legacyVersionOneMigrationAddsOptionalUrl() {
        createLegacy(false);
        db = new ShoppingDatabase(context, file);
        ShoppingList list = db.getLists(false).get(0);
        assertEquals(2, list.total);
        assertEquals(1, list.purchased);
        assertNull(db.getItems(list.id).get(0).url);
        long itemId = db.getItems(list.id).get(0).id;
        db.setItemUrl(list.id, itemId, "https://example.com/bread");
        assertEquals("https://example.com/bread", db.getItems(list.id).get(0).url);
        assertEquals(6, db.getReadableDatabase().getVersion());
    }

    @Test
    public void wishListsKeepTypeAndArchiveWithoutPurchasingAllItems() {
        long shopping = db.createList("Звичайні покупки");
        long wishes = db.createList("Подарунки", ShoppingList.TYPE_WISH);
        assertEquals(ShoppingList.TYPE_SHOPPING, db.getList(shopping).type);
        assertTrue(db.getList(wishes).isWish());
        db.add(wishes, "Велосипед", "1");
        db.add(wishes, "Книга", "");
        long firstId = db.getItems(wishes).get(0).id;
        db.setPurchased(wishes, firstId, true);
        db.archive(wishes, false);
        assertNotNull(db.getList(wishes).archivedAt);
        assertEquals(1, db.getList(wishes).purchased);
        assertRejected(() -> db.rename(wishes, "Нова назва"));
        db.close();
        db = new ShoppingDatabase(context, file);
        assertTrue(db.getList(wishes).isWish());
        assertEquals(2, db.getList(wishes).total);
        db.restore(wishes);
        assertTrue(db.getList(wishes).isWish());
        assertNull(db.getList(wishes).archivedAt);
        db.rename(wishes, "Оновлені бажання");
        assertEquals("Оновлені бажання", db.getList(wishes).name);
        assertEquals(ShoppingList.TYPE_WISH, db.getList(wishes).type);
    }

    @Test
    public void upgradeFromVersionThreePreservesListsItemsAndLinks() {
        db.close();
        try (SQLiteDatabase old = context.openOrCreateDatabase(file, 0, null)) {
            old.execSQL(
                    "CREATE TABLE shopping_lists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, archivedAt INTEGER)");
            old.execSQL(
                    "CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, listId INTEGER NOT NULL REFERENCES shopping_lists(id), name TEXT NOT NULL, quantity TEXT NOT NULL, purchased INTEGER NOT NULL, url TEXT)");
            old.execSQL("INSERT INTO shopping_lists VALUES (15, 'Старий список', 100, 150, 150)");
            old.execSQL(
                    "INSERT INTO items VALUES (33, 15, 'Кава', '1 пачка', 1, 'https://example.com/coffee')");
            old.setVersion(3);
        }
        db = new ShoppingDatabase(context, file);
        ShoppingList list = db.getList(15);
        assertEquals(ShoppingList.TYPE_SHOPPING, list.type);
        assertEquals(Long.valueOf(150), list.archivedAt);
        ShoppingItem item = db.getItems(15).get(0);
        assertEquals(33L, item.id);
        assertEquals("https://example.com/coffee", item.url);
        assertTrue(item.purchased);
        assertEquals(6, db.getReadableDatabase().getVersion());
    }

    @Test
    public void receiptCanBeAttachedOnlyToArchivedListAndSurvivesRestore() {
        long id = db.createList("Продукти");
        assertRejected(() -> db.setReceipt(id, "test-receipt.img"));
        db.add(id, "Молоко", "1");
        db.archive(id, true);
        assertNull(db.getList(id).receiptFile);
        assertNull(db.setReceipt(id, "first.img"));
        assertEquals("first.img", db.getList(id).receiptFile);
        assertEquals("first.img", db.setReceipt(id, "second.img"));
        db.close();
        db = new ShoppingDatabase(context, file);
        assertEquals("second.img", db.getList(id).receiptFile);
        assertEquals("second.img", db.setReceipt(id, null));
        assertNull(db.getList(id).receiptFile);
        db.restore(id);
        assertRejected(() -> db.setReceipt(id, "third.img"));
        assertNull(db.getList(id).receiptFile);
    }

    @Test
    public void upgradeFromVersionFourAddsEmptyReceiptWithoutChangingListsOrLinks() {
        db.close();
        try (SQLiteDatabase old = context.openOrCreateDatabase(file, 0, null)) {
            old.execSQL(
                    "CREATE TABLE shopping_lists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, archivedAt INTEGER, type TEXT NOT NULL DEFAULT 'shopping')");
            old.execSQL(
                    "CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, listId INTEGER NOT NULL REFERENCES shopping_lists(id), name TEXT NOT NULL, quantity TEXT NOT NULL, purchased INTEGER NOT NULL, url TEXT)");
            old.execSQL(
                    "INSERT INTO shopping_lists VALUES (21, 'Завершений', 100, 120, 120, 'shopping')");
            old.execSQL(
                    "INSERT INTO items VALUES (72, 21, 'Кава', '1', 1, 'https://example.com/coffee')");
            old.setVersion(4);
        }
        db = new ShoppingDatabase(context, file);
        assertNull(db.getList(21).receiptFile);
        assertEquals(Long.valueOf(120L), db.getList(21).archivedAt);
        assertEquals("https://example.com/coffee", db.getItems(21).get(0).url);
        assertEquals(72L, db.getItems(21).get(0).id);
        assertEquals(6, db.getReadableDatabase().getVersion());
    }

    @Test
    public void onlyEmptyActiveShoppingListCanBeDeleted() {
        long empty = db.createList("Порожній");
        long filled = db.createList("Непорожній");
        long wish = db.createList("Бажання", ShoppingList.TYPE_WISH);
        long archived = db.createList("Архівний");
        db.add(filled, "Хліб", "1");
        db.archive(archived, false);
        assertRejected(() -> db.deleteEmptyShoppingList(filled));
        assertRejected(() -> db.deleteEmptyShoppingList(wish));
        assertRejected(() -> db.deleteEmptyShoppingList(archived));
        assertEquals(4, db.getLists(false).size() + db.getLists(true).size());
        db.deleteEmptyShoppingList(empty);
        assertRejected(() -> db.getList(empty));
        assertEquals(3, db.getLists(false).size() + db.getLists(true).size());
        assertEquals(1, db.getList(filled).total);
    }

    @Test
    public void barcodePersistsThroughReopenArchiveAndRestore() {
        long listId = db.createList("Крамниця");
        db.add(listId, "Молоко", "1");
        long itemId = db.getItems(listId).get(0).id;
        db.setItemBarcode(listId, itemId, " 012345678905 ");
        assertEquals("012345678905", db.getItems(listId).get(0).barcode);
        long updated = db.getList(listId).updatedAt;
        db.setItemBarcode(listId, itemId, "012345678905");
        assertEquals(updated, db.getList(listId).updatedAt);
        db.archive(listId, true);
        assertRejected(() -> db.setItemBarcode(listId, itemId, "9876543210123"));
        db.close();
        db = new ShoppingDatabase(context, file);
        assertEquals("012345678905", db.getItems(listId).get(0).barcode);
        db.restore(listId);
        db.setItemBarcode(listId, itemId, null);
        assertNull(db.getItems(listId).get(0).barcode);
    }

    @Test
    public void upgradeFromVersionFiveKeepsExistingItemAndAddsBarcode() {
        db.close();
        try (SQLiteDatabase old = context.openOrCreateDatabase(file, 0, null)) {
            old.execSQL(
                    "CREATE TABLE shopping_lists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, archivedAt INTEGER, type TEXT NOT NULL DEFAULT 'shopping', receiptFile TEXT)");
            old.execSQL(
                    "CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, listId INTEGER NOT NULL REFERENCES shopping_lists(id), name TEXT NOT NULL, quantity TEXT NOT NULL, purchased INTEGER NOT NULL, url TEXT)");
            old.execSQL("INSERT INTO shopping_lists VALUES (1, 'Завершений', 100, 200, 200, 'shopping', 'receipt.img')");
            old.execSQL(
                    "INSERT INTO items VALUES (3, 1, 'Кава', '2', 1, 'https://example.com/item')");
            old.setVersion(5);
        }
        db = new ShoppingDatabase(context, file);
        assertEquals(6, db.getReadableDatabase().getVersion());
        assertEquals("receipt.img", db.getList(1).receiptFile);
        ShoppingItem item = db.getItems(1).get(0);
        assertEquals(3L, item.id);
        assertEquals("https://example.com/item", item.url);
        assertNull(item.barcode);
        assertTrue(item.purchased);
    }

    private void assertRejected(Runnable action) {
        try {
            action.run();
            fail("Operation should fail");
        } catch (SQLiteException expected) {
        }
    }
}
