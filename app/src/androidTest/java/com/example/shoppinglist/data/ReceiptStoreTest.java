package com.example.shoppinglist.data;

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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
        for (ReceiptAttachment attachment : database.getReceipts(listId)) {
            receipts.remove(database, listId, attachment.id);
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
    public void multipleImagesAndPdfAreImportedAsSeparateFiles() throws Exception {
        File pdf = new File(context.getCacheDir(), UUID.randomUUID() + ".pdf");
        android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
        android.graphics.pdf.PdfDocument.Page page =
                document.startPage(
                        new android.graphics.pdf.PdfDocument.PageInfo.Builder(200, 300, 1)
                                .create());
        document.finishPage(page);
        try (FileOutputStream output = new FileOutputStream(pdf)) {
            document.writeTo(output);
        } finally {
            document.close();
        }
        try {
            receipts.add(
                    database,
                    listId,
                    java.util.Arrays.asList(
                            Uri.fromFile(source), Uri.fromFile(source), Uri.fromFile(pdf)));
            java.util.List<ReceiptAttachment> all = database.getReceipts(listId);
            assertEquals(3, all.size());
            assertEquals("application/pdf", all.get(2).mimeType);
            assertNotNull(receipts.preview(all.get(2)));
            String first = all.get(0).fileName;
            receipts.remove(database, listId, all.get(1).id);
            assertEquals(2, database.getReceipts(listId).size());
            assertEquals(first, database.getList(listId).receiptFile);
            for (ReceiptAttachment receipt : database.getReceipts(listId)) {
                receipts.remove(database, listId, receipt.id);
            }
            assertTrue(database.getReceipts(listId).isEmpty());
        } finally {
            pdf.delete();
        }
    }

    @Test
    public void failedBatchLeavesPreviousReceiptAndNoNewRows() throws IOException {
        receipts.add(database, listId, java.util.Collections.singletonList(Uri.fromFile(source)));
        String existing = database.getList(listId).receiptFile;
        File invalid = new File(context.getCacheDir(), UUID.randomUUID() + ".txt");
        try (FileOutputStream output = new FileOutputStream(invalid)) {
            output.write("invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        try {
            receipts.add(
                    database,
                    listId,
                    java.util.Arrays.asList(Uri.fromFile(source), Uri.fromFile(invalid)));
            fail("Invalid second attachment must roll back batch");
        } catch (android.database.sqlite.SQLiteException expected) {
            assertEquals(1, database.getReceipts(listId).size());
            assertEquals(existing, database.getReceipts(listId).get(0).fileName);
        } finally {
            invalid.delete();
        }
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
