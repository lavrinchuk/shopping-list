package com.example.shoppinglist.data;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Owns private receipt images. No external URI is persisted. */
public final class ReceiptStore {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final Context context;
    private final File directory;

    ReceiptStore(Context context) {
        this.context = context.getApplicationContext();
        directory = new File(context.getFilesDir(), "receipts");
    }

    public void attach(ShoppingDatabase database, long listId, Uri source) {
        if (database.getList(listId).archivedAt == null) {
            throw new SQLiteException("Only archived lists can have receipts");
        }
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new SQLiteException("Cannot create receipt directory");
        }
        String name = UUID.randomUUID().toString() + ".img";
        File target = new File(directory, name);
        File temporary = new File(directory, name + ".tmp");
        boolean committed = false;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(source);
                    FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) {
                    throw new IOException("Image unavailable");
                }
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_BYTES) {
                        throw new IOException("Receipt is too large");
                    }
                    output.write(buffer, 0, count);
                }
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temporary.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw new IOException("Not a supported image");
            }
            if (!temporary.renameTo(target)) {
                throw new IOException("Could not store receipt");
            }
            String old = database.setReceipt(listId, name);
            committed = true;
            deleteOld(old);
        } catch (IOException | SecurityException error) {
            SQLiteException failure = new SQLiteException("Receipt import failed");
            failure.initCause(error);
            throw failure;
        } finally {
            if (!committed) {
                target.delete();
            }
            temporary.delete();
        }
    }

    /** Copy a selection into private files, then commit every reference in one DB transaction. */
    public void add(ShoppingDatabase database, long listId, List<Uri> sources) {
        if (sources == null || sources.isEmpty() || sources.size() > 10) {
            throw new SQLiteException("Choose between 1 and 10 receipts");
        }
        if (database.getList(listId).archivedAt == null) {
            throw new SQLiteException("Only archived lists may have receipts");
        }
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new SQLiteException("Cannot create receipt directory");
        }
        List<ReceiptAttachment> imported = new ArrayList<>();
        boolean committed = false;
        try {
            for (Uri uri : sources) {
                imported.add(importFile(listId, uri));
            }
            database.addReceipts(listId, imported);
            committed = true;
        } finally {
            if (!committed) {
                for (ReceiptAttachment attachment : imported) {
                    deleteOld(attachment.fileName);
                }
            }
        }
    }

    private ReceiptAttachment importFile(long listId, Uri uri) {
        String id = UUID.randomUUID().toString();
        File temp = new File(directory, id + ".tmp");
        File target = null;
        try {
            byte[] signature = new byte[12];
            int headerSize = 0;
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                    FileOutputStream output = new FileOutputStream(temp)) {
                if (input == null) {
                    throw new IOException("Receipt source unavailable");
                }
                byte[] buffer = new byte[8192];
                int read;
                long total = 0;
                while ((read = input.read(buffer)) != -1) {
                    if (read == 0) {
                        continue;
                    }
                    if (headerSize < signature.length) {
                        int n = Math.min(read, signature.length - headerSize);
                        System.arraycopy(buffer, 0, signature, headerSize, n);
                        headerSize += n;
                    }
                    total += read;
                    if (total > 20L * 1024 * 1024) {
                        throw new IOException("Receipt file exceeds 20 MiB");
                    }
                    output.write(buffer, 0, read);
                }
            }
            boolean pdf =
                    headerSize >= 5
                            && signature[0] == '%'
                            && signature[1] == 'P'
                            && signature[2] == 'D'
                            && signature[3] == 'F'
                            && signature[4] == '-';
            String mime;
            String extension;
            if (pdf) {
                validatePdf(temp);
                mime = "application/pdf";
                extension = ".pdf";
            } else {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(temp.getAbsolutePath(), bounds);
                if (bounds.outWidth <= 0
                        || bounds.outHeight <= 0
                        || temp.length() > 8L * 1024 * 1024) {
                    throw new IOException("Invalid or oversized receipt image");
                }
                mime = bounds.outMimeType;
                if (!"image/jpeg".equals(mime)
                        && !"image/png".equals(mime)
                        && !"image/webp".equals(mime)) {
                    throw new IOException("Unsupported receipt image format");
                }
                extension = ".img";
            }
            String fileName = id + extension;
            target = new File(directory, fileName);
            if (!temp.renameTo(target)) {
                throw new IOException("Cannot move receipt file");
            }
            return new ReceiptAttachment(0, listId, fileName, mime);
        } catch (IOException | SecurityException error) {
            SQLiteException failure = new SQLiteException("Receipt import failed");
            failure.initCause(error);
            throw failure;
        } finally {
            temp.delete();
            // Every successful rename is followed immediately by an attachment record.
            // A failed write before rename leaves only the temporary file.
        }
    }

    private void validatePdf(File file) throws IOException {
        try (ParcelFileDescriptor descriptor =
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer = new PdfRenderer(descriptor)) {
            if (renderer.getPageCount() == 0) {
                throw new IOException("PDF has no pages");
            }
        } catch (RuntimeException error) {
            throw new IOException("Invalid PDF", error);
        }
    }

    public void remove(ShoppingDatabase database, long listId, long receiptId) {
        deleteOld(database.removeReceipt(listId, receiptId));
    }

    public Bitmap preview(ReceiptAttachment receipt) {
        if (receipt == null) {
            return null;
        }
        if (!receipt.isPdf()) {
            return preview(receipt.fileName);
        }
        if (!receipt.fileName.matches("[0-9a-f-]{36}\\.pdf")) {
            return null;
        }
        File file = new File(directory, receipt.fileName);
        try (ParcelFileDescriptor descriptor =
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer = new PdfRenderer(descriptor);
                PdfRenderer.Page page = renderer.openPage(0)) {
            float scale = Math.min(1f, 1280f / Math.max(page.getWidth(), page.getHeight()));
            Bitmap bitmap =
                    Bitmap.createBitmap(
                            Math.max(1, (int) (page.getWidth() * scale)),
                            Math.max(1, (int) (page.getHeight() * scale)),
                            Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(android.graphics.Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    public void remove(ShoppingDatabase database, long listId) {
        String old = database.setReceipt(listId, null);
        deleteOld(old);
    }

    private void deleteOld(String name) {
        if (name != null && name.matches("[0-9a-f-]{36}\\.(img|pdf)")) {
            new File(directory, name).delete();
        }
    }

    public Bitmap preview(String name) {
        if (name == null || !name.matches("[0-9a-f-]{36}\\.img")) {
            return null;
        }
        File file = new File(directory, name);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 1280) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) {
            return null;
        }
        int rotation = 0;
        try {
            int orientation =
                    new ExifInterface(file.getAbsolutePath())
                            .getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                rotation = 90;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                rotation = 180;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                rotation = 270;
            }
        } catch (IOException ignored) {
            return bitmap;
        }
        if (rotation == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap corrected =
                Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (corrected != bitmap) {
            bitmap.recycle();
        }
        return corrected;
    }
}
