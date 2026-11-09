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
    public void localBackupRestoresListsTemplatesLinksAndReceiptFiles() throws Exception {
        long listId = db.createList("Крамниця");
        db.add(listId, "Молоко", "2 л");
        long itemId = db.getItems(listId).get(0).id;
        db.addStoreLink(listId, itemId, "Сільпо", "https://silpo.ua/milk");
        db.setItemBarcode(listId, itemId, "012345678905");
        db.saveTemplate(listId, "Щотижневі");
        db.archive(listId, true);
        String name = java.util.UUID.randomUUID() + ".png";
        java.io.File directory = new java.io.File(context.getFilesDir(), "receipts");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        java.io.File receipt = new java.io.File(directory, name);
        byte[] original = new byte[] {1, 2, 3, 4};
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(receipt)) {
            output.write(original);
        }
        db.addReceipt(listId, name, "image/png");
        java.io.File backup = new java.io.File(context.getCacheDir(), file + ".zip");
        try {
            com.example.shoppinglist.data.LocalBackup storage =
                    new com.example.shoppinglist.data.LocalBackup(context, db);
            storage.exportTo(android.net.Uri.fromFile(backup));
            db.createList("Не повинно залишитися");
            storage.importFrom(android.net.Uri.fromFile(backup));
            assertEquals(0, db.getLists(false).size());
            assertEquals(1, db.getLists(true).size());
            assertEquals("Крамниця", db.getList(listId).name);
            assertEquals("Молоко", db.getItems(listId).get(0).name);
            assertEquals("012345678905", db.getItems(listId).get(0).barcode);
            assertEquals(1, db.getStoreLinks(listId).size());
            assertEquals("https://silpo.ua/milk", db.getStoreLinks(listId).get(0).url);
            assertEquals("Щотижневі", db.getTemplates().get(0).name);
            assertEquals(1, db.getReceipts(listId).size());
            java.io.File restored = new java.io.File(
                    directory, db.getReceipts(listId).get(0).fileName);
            assertTrue(restored.isFile());
            assertArrayEquals(
                    original, java.nio.file.Files.readAllBytes(restored.toPath()));
            assertFalse("Old attachment should be deleted after successful restore",
                    receipt.exists());
            restored.delete();
        } finally {
            backup.delete();
            receipt.delete();
        }
    }

    @Test
    public void malformedBackupMustNotReplaceExistingLists() throws Exception {
        long id = db.createList("Залишається");
        java.io.File invalid = new java.io.File(context.getCacheDir(), file + "-invalid.zip");
        try {
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(invalid)) {
                output.write(new byte[] {1, 2, 3, 4});
            }
            try {
                new com.example.shoppinglist.data.LocalBackup(context, db)
                        .importFrom(android.net.Uri.fromFile(invalid));
                fail("Invalid backup must be rejected");
            } catch (java.io.IOException expected) {
                assertEquals("Залишається", db.getList(id).name);
            }
        } finally {
            invalid.delete();
        }
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
        assertEquals(9, db.getReadableDatabase().getVersion());
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
        assertEquals(9, db.getReadableDatabase().getVersion());
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
        assertEquals(9, db.getReadableDatabase().getVersion());
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
        assertEquals(9, db.getReadableDatabase().getVersion());
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
            old.execSQL(
                    "INSERT INTO shopping_lists VALUES (1, 'Завершений', 100, 200, 200, 'shopping', 'receipt.img')");
            old.execSQL(
                    "INSERT INTO items VALUES (3, 1, 'Кава', '2', 1, 'https://example.com/item')");
            old.setVersion(5);
        }
        db = new ShoppingDatabase(context, file);
        assertEquals(9, db.getReadableDatabase().getVersion());
        assertEquals("receipt.img", db.getList(1).receiptFile);
        ShoppingItem item = db.getItems(1).get(0);
        assertEquals(3L, item.id);
        assertEquals("https://example.com/item", item.url);
        assertNull(item.barcode);
        assertTrue(item.purchased);
    }

    @Test
    public void upgradeV6PreservesExistingReceiptAndMakesItFirstAttachment() {
        db.close();
        try (SQLiteDatabase old = context.openOrCreateDatabase(file, 0, null)) {
            old.execSQL(
                    "CREATE TABLE shopping_lists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, archivedAt INTEGER, type TEXT NOT NULL DEFAULT 'shopping', receiptFile TEXT)");
            old.execSQL(
                    "CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, listId INTEGER NOT NULL REFERENCES shopping_lists(id), name TEXT NOT NULL, quantity TEXT NOT NULL, purchased INTEGER NOT NULL, url TEXT, barcode TEXT)");
            old.execSQL(
                    "INSERT INTO shopping_lists VALUES (42, 'Старий чек', 10, 20, 20, 'shopping', 'existing.img')");
            old.execSQL("INSERT INTO items VALUES (12, 42, 'Кава', '1', 1, NULL, '012345678905')");
            old.setVersion(6);
        }
        db = new ShoppingDatabase(context, file);
        assertEquals(9, db.getReadableDatabase().getVersion());
        assertEquals("existing.img", db.getList(42).receiptFile);
        assertEquals(1, db.getReceipts(42).size());
        assertEquals("existing.img", db.getReceipts(42).get(0).fileName);
        assertEquals("012345678905", db.getItems(42).get(0).barcode);
    }

    @Test
    public void addAndDeleteSpecificReceiptPreservesOtherAttachments() {
        long id = db.createList("Завершений");
        db.archive(id, false);
        db.addReceipts(
                id,
                java.util.Arrays.asList(
                        new com.example.shoppinglist.data.ReceiptAttachment(
                                0, id, "first.img", "image/png"),
                        new com.example.shoppinglist.data.ReceiptAttachment(
                                0, id, "second.pdf", "application/pdf")));
        assertEquals(2, db.getReceipts(id).size());
        assertEquals("first.img", db.getList(id).receiptFile);
        long first = db.getReceipts(id).get(0).id;
        assertEquals("first.img", db.removeReceipt(id, first));
        assertEquals(1, db.getReceipts(id).size());
        assertEquals("second.pdf", db.getList(id).receiptFile);
        db.close();
        db = new ShoppingDatabase(context, file);
        assertEquals("second.pdf", db.getReceipts(id).get(0).fileName);
        db.restore(id);
        assertRejected(() ->
                db.addReceipts(
                        id,
                        java.util.Collections.singletonList(
                                new com.example.shoppinglist.data.ReceiptAttachment(
                                        0, id, "third.img", "image/png"))));
    }

    @Test
    public void multipleStoreLinksSurviveRestartAndArchiveAndDeleteIndependently() {
        long listId = db.createList("Покупки");
        db.add(listId, "Молоко", "2 л");
        long itemId = db.getItems(listId).get(0).id;
        db.addStoreLink(listId, itemId, "Сільпо", "https://silpo.ua/milk");
        db.addStoreLink(listId, itemId, "АТБ", "https://atbmarket.com/milk");
        assertEquals(2, db.getStoreLinks(listId).size());
        assertEquals("https://silpo.ua/milk", db.getItems(listId).get(0).url);
        long secondId = db.getStoreLinks(listId).get(1).id;
        db.updateStoreLink(listId, secondId, "Метро", "https://metro.ua/milk");
        assertEquals("Метро", db.getStoreLinks(listId).get(1).storeName);
        assertEquals("https://metro.ua/milk", db.getStoreLinks(listId).get(1).url);
        db.close();
        db = new ShoppingDatabase(context, file);
        assertEquals(2, db.getStoreLinks(listId).size());
        db.archive(listId, true);
        assertRejected(() -> db.addStoreLink(listId, itemId, "Інший", "https://example.org"));
        assertRejected(() -> db.deleteStoreLink(listId, secondId));
        db.restore(listId);
        db.setItemUrl(listId, itemId, "https://fresh.example.com/new");
        assertEquals(2, db.getStoreLinks(listId).size());
        assertEquals("https://fresh.example.com/new", db.getItems(listId).get(0).url);
        db.deleteStoreLink(listId, db.getStoreLinks(listId).get(0).id);
        assertEquals(1, db.getStoreLinks(listId).size());
        assertEquals("https://metro.ua/milk", db.getItems(listId).get(0).url);
        db.delete(listId, itemId);
        assertTrue(db.getStoreLinks(listId).isEmpty());
    }

    @Test
    public void storeLinksCannotBeChangedAcrossListsOrDuplicated() {
        long first = db.createList("Перший");
        long second = db.createList("Другий");
        db.add(first, "Сир", "1");
        long item = db.getItems(first).get(0).id;
        db.addStoreLink(first, item, "Магазин", "https://shop.example.ua/cheese");
        long link = db.getStoreLinks(first).get(0).id;
        assertRejected(() -> db.addStoreLink(second, item, "Чужий", "https://example.org"));
        assertRejected(() -> db.deleteStoreLink(second, link));
        assertRejected(() -> db.updateStoreLink(second, link, "Чужий", "https://example.org"));
        assertRejected(
                () -> db.addStoreLink(first, item, "Дубль", "https://shop.example.ua/cheese"));
        assertEquals(1, db.getStoreLinks(first).size());
        assertEquals("Магазин", db.getStoreLinks(first).get(0).storeName);
    }

    @Test
    public void migrationFromVersionSevenPreservesLinksBarcodeAndReceipts() {
        db.close();
        try (SQLiteDatabase old = context.openOrCreateDatabase(file, 0, null)) {
            old.execSQL(
                    "CREATE TABLE shopping_lists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, archivedAt INTEGER, type TEXT NOT NULL DEFAULT 'shopping', receiptFile TEXT)");
            old.execSQL(
                    "CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, listId INTEGER NOT NULL REFERENCES shopping_lists(id), name TEXT NOT NULL, quantity TEXT NOT NULL, purchased INTEGER NOT NULL, url TEXT, barcode TEXT)");
            old.execSQL(
                    "CREATE TABLE receipt_attachments (id INTEGER PRIMARY KEY AUTOINCREMENT, listId INTEGER NOT NULL REFERENCES shopping_lists(id), fileName TEXT NOT NULL UNIQUE, mimeType TEXT NOT NULL, createdAt INTEGER NOT NULL)");
            old.execSQL(
                    "INSERT INTO shopping_lists VALUES (7, 'Архів', 100, 120, 120, 'shopping', 'existing.img')");
            old.execSQL(
                    "INSERT INTO items VALUES (17, 7, 'Кава', '2', 1, 'https://shop.example.ua/coffee', '012345678905')");
            old.execSQL(
                    "INSERT INTO receipt_attachments VALUES (1, 7, 'existing.img', 'image/jpeg', 120)");
            old.setVersion(7);
        }
        db = new ShoppingDatabase(context, file);
        assertEquals(9, db.getReadableDatabase().getVersion());
        assertEquals("https://shop.example.ua/coffee", db.getItems(7).get(0).url);
        assertEquals("012345678905", db.getItems(7).get(0).barcode);
        assertEquals("existing.img", db.getReceipts(7).get(0).fileName);
        assertEquals(1, db.getStoreLinks(7).size());
        assertEquals("Без назви магазину", db.getStoreLinks(7).get(0).storeName);
        assertEquals("https://shop.example.ua/coffee", db.getStoreLinks(7).get(0).url);
        assertNotNull(db.getList(7).archivedAt);
    }

    @Test
    public void voiceBatchInsertsAllItemsAndTouchOnce() {
        long id = db.createList("Продукти");
        db.addItems(id, java.util.Arrays.asList("Молоко", "Хліб", "Яблука"));
        List<ShoppingItem> items = db.getItems(id);
        assertEquals(3, items.size());
        assertEquals("Молоко", items.get(0).name);
        assertEquals("Хліб", items.get(1).name);
        assertEquals("Яблука", items.get(2).name);
        assertEquals(3, db.getList(id).total);
        assertEquals("", items.get(1).quantity);
        assertFalse(items.get(2).purchased);
    }

    @Test
    public void voiceBatchRejectsInvalidInputWithoutPartialInsertion() {
        long id = db.createList("Продукти");
        try {
            db.addItems(id, java.util.Arrays.asList("Молоко", " ", "Яблука"));
            fail("Invalid batch must not partially save");
        } catch (IllegalArgumentException expected) {
            assertTrue(db.getItems(id).isEmpty());
        }
        db.archive(id, false);
        assertRejected(() -> db.addItems(id, java.util.Arrays.asList("Сир", "Хліб")));
        assertTrue(db.getItems(id).isEmpty());
    }

    @Test
    public void recurringTemplateCopiesItemsLinksAndBarcodeWithoutPurchasedOrReceipts() {
        long source = db.createList("Продукти");
        db.add(source, "Молоко", "2 л");
        db.add(source, "Хліб", "1");
        long milk = db.getItems(source).get(0).id;
        db.setItemBarcode(source, milk, "012345678905");
        db.addStoreLink(source, milk, "Сільпо", "https://silpo.ua/milk");
        db.addStoreLink(source, milk, "АТБ", "https://atbmarket.com/milk");
        db.setPurchased(source, milk, true);
        db.archive(source, true);
        long templateId = db.saveTemplate(source, "Щотижневі покупки");
        assertEquals(1, db.getTemplates().size());
        assertEquals(2, db.getTemplates().get(0).itemCount);
        long created = db.createFromTemplate(templateId);
        assertNotEquals(source, created);
        assertNull(db.getList(created).archivedAt);
        assertEquals(0, db.getList(created).purchased);
        assertTrue(db.getList(created).name.matches("Щотижневі покупки \\d{2}\\.\\d{2}\\.\\d{4}"));
        assertNull(db.getList(created).receiptFile);
        assertEquals(2, db.getItems(created).size());
        assertEquals("2 л", db.getItems(created).get(0).quantity);
        assertNull(db.getItems(created).get(0).barcode);
        assertNull(db.getItems(created).get(0).url);
        assertFalse(db.getItems(created).get(0).purchased);
        assertTrue(db.getStoreLinks(created).isEmpty());
        db.deleteTemplate(templateId);
        assertTrue(db.getTemplates().isEmpty());
        assertEquals(2, db.getItems(created).size());
        assertNotNull(db.getList(source).archivedAt);
        assertTrue(
                db.getItems(source).stream().anyMatch(item -> item.id == milk && item.purchased));
        assertEquals("Молоко", db.getItems(created).get(0).name);
        assertEquals("Хліб", db.getItems(created).get(1).name);
    }

    @Test
    public void editingTemplateUpdatesFutureListsOnly() {
        long source = db.createList("Original");
        db.add(source, "Milk", "2");
        long template = db.saveTemplate(source, "Weekly");
        long before = db.createFromTemplate(template);
        db.updateTemplate(
                template,
                "Updated",
                java.util.Arrays.asList(new String[] {"Bread", "3"}, new String[] {"Eggs", "6"}));
        long after = db.createFromTemplate(template);
        assertEquals("Updated", db.getTemplates().get(0).name);
        assertEquals(2, db.getTemplates().get(0).itemCount);
        assertEquals("Milk", db.getItems(before).get(0).name);
        assertEquals("Milk", db.getItems(source).get(0).name);
        assertEquals(2, db.getItems(after).size());
        assertEquals("Bread", db.getItems(after).get(0).name);
        assertEquals("3", db.getItems(after).get(0).quantity);
    }

    @Test
    public void editedTemplatePreservesOptionalEmptyQuantity() {
        long source = db.createList("Original");
        db.add(source, "Milk", "");
        long template = db.saveTemplate(source, "Weekly");
        db.updateTemplate(
                template,
                "Weekly",
                java.util.Collections.singletonList(new String[] {"Bread", ""}));
        long list = db.createFromTemplate(template);
        assertEquals("", db.getItems(list).get(0).quantity);
    }

    @Test
    public void recurringTemplateCanBeReusedWithoutCouplingCreatedLists() {
        long source = db.createList("Постійні покупки");
        db.add(source, "Кава", "1");
        long template = db.saveTemplate(source, "Тиждень");
        long first = db.createFromTemplate(template);
        long second = db.createFromTemplate(template);
        long firstItem = db.getItems(first).get(0).id;
        db.delete(first, firstItem);
        assertTrue(db.getItems(first).isEmpty());
        assertEquals(1, db.getItems(second).size());
        assertEquals(1, db.getItems(source).size());
        assertEquals(1, db.getTemplates().get(0).itemCount);
        db.close();
        db = new ShoppingDatabase(context, file);
        assertEquals(9, db.getReadableDatabase().getVersion());
        assertEquals(1, db.getTemplates().size());
        assertEquals(1, db.getItems(second).size());
    }

    @Test
    public void recurringTemplateRejectsEmptyAndWishSourcesWithoutCreatingRecords() {
        long empty = db.createList("Порожній");
        try {
            db.saveTemplate(empty, "Шаблон");
            fail("Empty source should be rejected");
        } catch (SQLiteException expected) {
            assertTrue(db.getTemplates().isEmpty());
        }
        long wish = db.createList("Бажання", ShoppingList.TYPE_WISH);
        db.add(wish, "Книга", "1");
        try {
            db.saveTemplate(wish, "Шаблон");
            fail("Wishlist source should be rejected");
        } catch (SQLiteException expected) {
            assertTrue(db.getTemplates().isEmpty());
        }
    }

    @Test
    public void upgradeV8PreservesExistingDataAndAddsTemplateTables() {
        db.close();
        try (SQLiteDatabase old = context.openOrCreateDatabase(file, 0, null)) {
            old.execSQL(
                    "CREATE TABLE shopping_lists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, archivedAt INTEGER, type TEXT NOT NULL DEFAULT 'shopping', receiptFile TEXT)");
            old.execSQL(
                    "CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, listId INTEGER NOT NULL REFERENCES shopping_lists(id), name TEXT NOT NULL, quantity TEXT NOT NULL, purchased INTEGER NOT NULL, url TEXT, barcode TEXT)");
            old.execSQL(
                    "CREATE TABLE receipt_attachments (id INTEGER PRIMARY KEY AUTOINCREMENT, listId INTEGER NOT NULL REFERENCES shopping_lists(id), fileName TEXT NOT NULL UNIQUE, mimeType TEXT NOT NULL, createdAt INTEGER NOT NULL)");
            old.execSQL(
                    "CREATE TABLE item_links (id INTEGER PRIMARY KEY AUTOINCREMENT, itemId INTEGER NOT NULL REFERENCES items(id), storeName TEXT NOT NULL, url TEXT NOT NULL)");
            old.execSQL(
                    "INSERT INTO shopping_lists VALUES (4, 'Архів', 100, 120, 120, 'shopping', 'legacy.img')");
            old.execSQL(
                    "INSERT INTO items VALUES (7, 4, 'Кава', '2', 1, 'https://example.com/coffee', '012345678905')");
            old.execSQL(
                    "INSERT INTO receipt_attachments VALUES (8, 4, 'legacy.img', 'image/jpeg', 120)");
            old.execSQL(
                    "INSERT INTO item_links VALUES (9, 7, 'Сільпо', 'https://example.com/coffee')");
            old.setVersion(8);
        }
        db = new ShoppingDatabase(context, file);
        assertEquals(9, db.getReadableDatabase().getVersion());
        assertEquals("legacy.img", db.getReceipts(4).get(0).fileName);
        assertEquals("012345678905", db.getItems(4).get(0).barcode);
        assertEquals(1, db.getStoreLinks(4).size());
        long template = db.saveTemplate(4, "Повторити");
        assertEquals(1, db.getTemplates().size());
        assertEquals(1, db.getItems(db.createFromTemplate(template)).size());
    }

    private void assertRejected(Runnable action) {
        try {
            action.run();
            fail("Operation should fail");
        } catch (SQLiteException expected) {
        }
    }
}
