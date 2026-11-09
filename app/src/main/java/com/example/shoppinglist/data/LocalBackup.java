package com.example.shoppinglist.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Portable, offline ZIP backup of the known SQLite schema and private receipt files.
 * Imports replace current data, but write new receipt files before the SQLite transaction;
 * old files are only removed after commit. A failed validation leaves existing data intact.
 */
public final class LocalBackup {
    private static final int FORMAT_VERSION = 1;
    private static final int DATABASE_VERSION = 9;
    private static final long MAX_JSON = 16L * 1024 * 1024;
    private static final long MAX_FILE = 20L * 1024 * 1024;
    private static final long MAX_TOTAL = 256L * 1024 * 1024;
    private static final int MAX_FILES = 500;
    private static final String METADATA = "data.json";
    private static final String RECEIPTS = "receipts/";
    private static final String[] TABLES = {
        "shopping_lists",
        "items",
        "receipt_attachments",
        "item_links",
        "shopping_templates",
        "template_items",
        "template_links"
    };
    // Delete child tables before parents to respect ON DELETE RESTRICT.
    private static final String[] DELETE_ORDER = {
        "template_links", "template_items", "shopping_templates", "item_links",
        "receipt_attachments", "items", "shopping_lists"
    };

    private final Context context;
    private final ShoppingDatabase helper;

    public LocalBackup(Context context, ShoppingDatabase helper) {
        this.context = context.getApplicationContext();
        this.helper = helper;
    }

    public void exportTo(Uri target) throws IOException {
        File receipts = new File(context.getFilesDir(), "receipts");
        JSONObject data = new JSONObject();
        Set<String> names = new HashSet<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        db.beginTransactionNonExclusive();
        try {
            data.put("formatVersion", FORMAT_VERSION);
            data.put("databaseVersion", DATABASE_VERSION);
            JSONObject tables = new JSONObject();
            for (String table : TABLES) {
                JSONArray rows = new JSONArray();
                try (Cursor cursor = db.rawQuery("SELECT * FROM " + table + " ORDER BY id", null)) {
                    while (cursor.moveToNext()) {
                        JSONObject row = new JSONObject();
                        for (int i = 0; i < cursor.getColumnCount(); i++) {
                            String key = cursor.getColumnName(i);
                            if (cursor.isNull(i)) {
                                row.put(key, JSONObject.NULL);
                            } else if (cursor.getType(i) == Cursor.FIELD_TYPE_INTEGER) {
                                row.put(key, cursor.getLong(i));
                            } else {
                                row.put(key, cursor.getString(i));
                            }
                        }
                        rows.put(row);
                        if ("receipt_attachments".equals(table)) {
                            names.add(cursor.getString(cursor.getColumnIndexOrThrow("fileName")));
                        }
                        if ("shopping_lists".equals(table)) {
                            int index = cursor.getColumnIndexOrThrow("receiptFile");
                            if (!cursor.isNull(index)) {
                                names.add(cursor.getString(index));
                            }
                        }
                    }
                }
                tables.put(table, rows);
            }
            data.put("tables", tables);
            // The single-threaded repository queue prevents mutations during export.
            byte[] json = data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (json.length > MAX_JSON || names.size() > MAX_FILES) {
                throw new IOException("Backup metadata exceeds supported limits");
            }
            try (OutputStream output = requireOutput(target);
                    ZipOutputStream zip = new ZipOutputStream(output)) {
                zip.putNextEntry(new ZipEntry(METADATA));
                zip.write(json);
                zip.closeEntry();
                byte[] buffer = new byte[8192];
                long total = json.length;
                for (String name : names) {
                    if (!safeName(name)) {
                        throw new IOException("Invalid receipt file name");
                    }
                    File source = new File(receipts, name);
                    if (!source.isFile() || source.length() > MAX_FILE) {
                        throw new IOException("Missing or oversized receipt: " + name);
                    }
                    total += source.length();
                    if (total > MAX_TOTAL) {
                        throw new IOException("Backup exceeds size limit");
                    }
                    zip.putNextEntry(new ZipEntry(RECEIPTS + name));
                    try (InputStream in = new FileInputStream(source)) {
                        int n;
                        while ((n = in.read(buffer)) != -1) {
                            zip.write(buffer, 0, n);
                        }
                    }
                    zip.closeEntry();
                }
                zip.finish();
            }
            db.setTransactionSuccessful();
        } catch (JSONException error) {
            throw new IOException("Failed to serialize backup", error);
        } finally {
            db.endTransaction();
        }
    }

    public void importFrom(Uri source) throws IOException {
        File working = new File(context.getCacheDir(), "backup-" + UUID.randomUUID());
        if (!working.mkdirs()) {
            throw new IOException("Cannot stage backup");
        }
        List<File> importedFiles = new ArrayList<>();
        boolean committed = false;
        try {
            JSONObject data = null;
            Map<String, File> extracted = new HashMap<>();
            long total = 0;
            int entries = 0;
            try (InputStream input = requireInput(source);
                    ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory() || ++entries > MAX_FILES + 1) {
                        throw new IOException("Unexpected backup entry");
                    }
                    String path = entry.getName();
                    boolean manifest = METADATA.equals(path);
                    boolean receipt = path.startsWith(RECEIPTS)
                            && safeName(path.substring(RECEIPTS.length()));
                    if (!manifest && !receipt) {
                        throw new IOException("Unexpected archive path");
                    }
                    String key = manifest ? METADATA : path.substring(RECEIPTS.length());
                    if (extracted.containsKey(key)) {
                        throw new IOException("Duplicate archive entry");
                    }
                    File staged = new File(working, UUID.randomUUID().toString());
                    long size = 0;
                    try (OutputStream output = new FileOutputStream(staged)) {
                        int n;
                        while ((n = zip.read(buffer)) != -1) {
                            size += n;
                            total += n;
                            if (size > (manifest ? MAX_JSON : MAX_FILE) || total > MAX_TOTAL) {
                                throw new IOException("Backup entry exceeds size limit");
                            }
                            output.write(buffer, 0, n);
                        }
                    }
                    extracted.put(key, staged);
                    zip.closeEntry();
                }
            }
            File jsonFile = extracted.remove(METADATA);
            if (jsonFile == null) {
                throw new IOException("Missing backup metadata");
            }
            try (InputStream input = new FileInputStream(jsonFile)) {
                byte[] bytes = new byte[(int) jsonFile.length()];
                int offset = 0;
                while (offset < bytes.length) {
                    int n = input.read(bytes, offset, bytes.length - offset);
                    if (n < 0) {
                        throw new IOException("Truncated metadata");
                    }
                    offset += n;
                }
                data = new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            } catch (JSONException error) {
                throw new IOException("Malformed backup JSON", error);
            }
            if (data.optInt("formatVersion", -1) != FORMAT_VERSION
                    || data.optInt("databaseVersion", -1) != DATABASE_VERSION) {
                throw new IOException("Unsupported backup format or database version");
            }
            JSONObject tables = data.optJSONObject("tables");
            if (tables == null || tables.length() != TABLES.length) {
                throw new IOException("Incomplete backup tables");
            }
            Set<String> required = new HashSet<>();
            try {
                for (String table : TABLES) {
                    JSONArray rows = tables.getJSONArray(table);
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.getJSONObject(i);
                        if ("receipt_attachments".equals(table)) {
                            required.add(row.getString("fileName"));
                        }
                        if ("shopping_lists".equals(table) && !row.isNull("receiptFile")) {
                            required.add(row.getString("receiptFile"));
                        }
                    }
                }
            } catch (JSONException error) {
                throw new IOException("Invalid table content", error);
            }
            if (!required.equals(extracted.keySet())) {
                throw new IOException("Receipt files do not match database references");
            }
            File directory = new File(context.getFilesDir(), "receipts");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Cannot create receipt directory");
            }
            Map<String, String> renamed = new HashMap<>();
            for (String name : required) {
                if (!safeName(name)) {
                    throw new IOException("Invalid file name");
                }
                String ext = name.substring(name.lastIndexOf('.'));
                String replacement = UUID.randomUUID() + ext;
                File destination = new File(directory, replacement);
                copy(extracted.get(name), destination);
                importedFiles.add(destination);
                renamed.put(name, replacement);
            }

            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                for (String table : DELETE_ORDER) {
                    db.delete(table, null, null);
                }
                for (String table : TABLES) {
                    JSONArray rows = tables.getJSONArray(table);
                    Set<String> columns = columns(db, table);
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.getJSONObject(i);
                        if (row.length() != columns.size()) {
                            throw new IOException("Invalid columns for " + table);
                        }
                        ContentValues values = new ContentValues();
                        java.util.Iterator<String> keys = row.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            if (!columns.contains(key)) {
                                throw new IOException("Unknown column in " + table);
                            }
                            Object value = row.get(key);
                            if (JSONObject.NULL.equals(value)) {
                                values.putNull(key);
                            } else if (value instanceof Number) {
                                values.put(key, ((Number) value).longValue());
                            } else if (value instanceof String) {
                                String string = (String) value;
                                if (("fileName".equals(key) && "receipt_attachments".equals(table))
                                        || ("receiptFile".equals(key) && "shopping_lists".equals(table))) {
                                    string = renamed.get(string);
                                    if (string == null) {
                                        throw new IOException("Invalid receipt reference");
                                    }
                                }
                                values.put(key, string);
                            } else {
                                throw new IOException("Invalid column type");
                            }
                        }
                        db.insertOrThrow(table, null, values);
                    }
                }
                try (Cursor violations = db.rawQuery("PRAGMA foreign_key_check", null)) {
                    if (violations.moveToFirst()) {
                        throw new IOException("Invalid backup relationships");
                    }
                }
                db.setTransactionSuccessful();
                committed = true;
            } catch (JSONException error) {
                throw new IOException("Invalid backup rows", error);
            } finally {
                db.endTransaction();
            }
            // Old attachments are no longer referenced; leave unknown files alone.
            for (File old : directory.listFiles() == null ? new File[0] : directory.listFiles()) {
                if (old.isFile() && !importedFiles.contains(old) && safeName(old.getName())) {
                    old.delete();
                }
            }
        } catch (RuntimeException error) {
            Log.e("LocalBackup", "Backup import failed", error);
            throw new IOException("Backup cannot be imported", error);
        } finally {
            if (!committed) {
                for (File file : importedFiles) {
                    file.delete();
                }
            }
            for (File file : working.listFiles() == null ? new File[0] : working.listFiles()) {
                file.delete();
            }
            working.delete();
        }
    }

    private static Set<String> columns(SQLiteDatabase db, String table) {
        Set<String> names = new HashSet<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                names.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            }
        }
        return names;
    }

    private static boolean safeName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]+\\.(img|jpg|jpeg|png|webp|pdf)");
    }

    private static void copy(File source, File destination) throws IOException {
        try (InputStream in = new FileInputStream(source);
                OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        }
    }

    private InputStream requireInput(Uri uri) throws IOException {
        InputStream input = context.getContentResolver().openInputStream(uri);
        if (input == null) {
            throw new IOException("Cannot open backup");
        }
        return input;
    }

    private OutputStream requireOutput(Uri uri) throws IOException {
        OutputStream output = context.getContentResolver().openOutputStream(uri, "wt");
        if (output == null) {
            throw new IOException("Cannot write backup");
        }
        return output;
    }
}
