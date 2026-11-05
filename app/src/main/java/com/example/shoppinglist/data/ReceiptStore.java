package com.example.shoppinglist.data;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
            throw new IllegalStateException("Cannot create receipt directory");
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
            throw new IllegalStateException("Receipt import failed", error);
        } finally {
            if (!committed) {
                target.delete();
            }
            temporary.delete();
        }
    }

    public void remove(ShoppingDatabase database, long listId) {
        String old = database.setReceipt(listId, null);
        deleteOld(old);
    }

    private void deleteOld(String name) {
        if (name != null && name.matches("[0-9a-f-]{36}\\.img")) {
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
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }
}
