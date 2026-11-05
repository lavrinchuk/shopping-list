package com.example.shoppinglist.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ReceiptStoreTest {
    private Context context;
    private ShoppingDatabase database;
    private ReceiptStore receipts;
    private File source;
    private long listId;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = new ShoppingDatabase(context, "receipts-test-" + UUID.randomUUID() + ".db");
        receipts = new ReceiptStore(context);
        listId = database.createList("Їжа");
        database.archive(listId, false);
        source = new File(context.getCacheDir(), UUID.randomUUID() + ".png");
        Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        try (FileOutputStream output = new FileOutputStream(source)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            bitmap.recycle();
        }
    }

    @After
    public void tearDown() {
        String receipt = database.getList(listId).receiptFile;
        if (receipt != null) {
            receipts.remove(database, listId);
        }
        String filename = database.getDatabaseName();
        database.close();
        context.deleteDatabase(filename);
        source.delete();
    }

    @Test
    public void attachReplacePreviewAndRemoveWithoutLeavingOldFile() {
        receipts.attach(database, listId, Uri.fromFile(source));
        String first = database.getList(listId).receiptFile;
        assertNotNull(first);
        File old = new File(new File(context.getFilesDir(), "receipts"), first);
        assertTrue(old.isFile());
        assertNotNull(receipts.preview(first));
        receipts.attach(database, listId, Uri.fromFile(source));
        String second = database.getList(listId).receiptFile;
        assertFalse(first.equals(second));
        assertFalse(old.exists());
        assertNotNull(receipts.preview(second));
        receipts.remove(database, listId);
        assertNull(database.getList(listId).receiptFile);
        assertNull(receipts.preview(second));
    }

    @Test
    public void invalidPhotoDoesNotReplaceExistingReceipt() throws IOException {
        receipts.attach(database, listId, Uri.fromFile(source));
        String first = database.getList(listId).receiptFile;
        File invalid = new File(context.getCacheDir(), UUID.randomUUID() + ".txt");
        try (FileOutputStream output = new FileOutputStream(invalid)) {
            output.write("not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        try {
            receipts.attach(database, listId, Uri.fromFile(invalid));
            fail("Invalid image must be rejected");
        } catch (android.database.sqlite.SQLiteException expected) {
            assertEquals(first, database.getList(listId).receiptFile);
        } finally {
            invalid.delete();
        }
    }
}
