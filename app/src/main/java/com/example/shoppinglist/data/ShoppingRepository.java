package com.example.shoppinglist.data;

import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.shoppinglist.model.ShoppingItem;
import com.example.shoppinglist.model.ShoppingList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One application-owned queue orders writes and their subsequent reads, across Activities. */
public class ShoppingRepository {
    public interface Result {
        void written();

        void loaded(List<ShoppingItem> items);

        void failed(boolean reading);

        default void listLoaded(ShoppingList list) {}

        default void receiptsLoaded(List<ReceiptAttachment> attachments) {}

        default void storeLinksLoaded(List<StoreLink> links) {}

        default void created(long id) {}

        default void listsLoaded(List<ShoppingList> lists) {}

        default void templatesLoaded(List<ShoppingTemplate> templates) {}

        default void confirmationRequired() {
            failed(false);
        }
    }

    private final ShoppingDatabase database;
    private final ReceiptStore receipts;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int pending;
    private Runnable idleListener;

    public ShoppingRepository(ShoppingDatabase database) {
        this.database = database;
        this.receipts = new ReceiptStore(database.context());
    }

    public interface BackupResult {
        void completed();

        void failed(String reason);
    }

    /** Share the same queue with every database mutation to obtain a consistent snapshot. */
    public void exportBackup(Uri uri, BackupResult result) {
        backup(() -> new LocalBackup(database.context(), database).exportTo(uri), result);
    }

    public void importBackup(Uri uri, BackupResult result) {
        backup(() -> new LocalBackup(database.context(), database).importFrom(uri), result);
    }

    private interface BackupOperation {
        void run() throws java.io.IOException;
    }

    private void backup(BackupOperation operation, BackupResult callback) {
        pending++;
        worker.execute(() -> {
            try {
                operation.run();
                main.post(callback::completed);
            } catch (java.io.IOException | RuntimeException error) {
                Log.e("ShoppingRepository", "Backup operation failed", error);
                main.post(() -> callback.failed(error.getMessage()));
            } finally {
                main.post(() -> {
                    pending--;
                    if (isIdle() && idleListener != null) {
                        idleListener.run();
                    }
                });
            }
        });
    }

    public void load(long listId, Result result) {
        run(null, () -> readItems(listId, result), result);
    }

    public void add(long listId, String name, String quantity, Result result) {
        run(() -> database.add(listId, name, quantity), () -> readItems(listId, result), result);
    }

    public void addItems(long listId, List<String> names, Result result) {
        run(() -> database.addItems(listId, names), () -> readItems(listId, result), result);
    }

    public void purchased(long listId, long id, boolean checked, Result result) {
        run(
                () -> database.setPurchased(listId, id, checked),
                () -> readItems(listId, result),
                result);
    }

    public void setItemUrl(long listId, long id, String url, Result result) {
        run(() -> database.setItemUrl(listId, id, url), () -> readItems(listId, result), result);
    }

    public void addStoreLink(
            long listId, long itemId, String storeName, String url, Result result) {
        run(
                () -> database.addStoreLink(listId, itemId, storeName, url),
                () -> readItems(listId, result),
                result);
    }

    public void updateStoreLink(
            long listId, long linkId, String storeName, String url, Result result) {
        run(
                () -> database.updateStoreLink(listId, linkId, storeName, url),
                () -> readItems(listId, result),
                result);
    }

    public void deleteStoreLink(long listId, long linkId, Result result) {
        run(
                () -> database.deleteStoreLink(listId, linkId),
                () -> readItems(listId, result),
                result);
    }

    public void setItemBarcode(long listId, long id, String code, Result result) {
        run(
                () -> database.setItemBarcode(listId, id, code),
                () -> readItems(listId, result),
                result);
    }

    public void delete(long listId, long id, Result result) {
        run(() -> database.delete(listId, id), () -> readItems(listId, result), result);
    }

    public void attachReceipt(long listId, Uri photo, Result result) {
        run(
                () -> receipts.attach(database, listId, photo),
                () -> readItems(listId, result),
                result);
    }

    public void addReceipts(long listId, List<Uri> sources, Result result) {
        run(() -> receipts.add(database, listId, sources), () -> readItems(listId, result), result);
    }

    public void removeReceipt(long listId, long receiptId, Result result) {
        run(
                () -> receipts.remove(database, listId, receiptId),
                () -> readItems(listId, result),
                result);
    }

    public interface ReceiptAttachmentsResult {
        void loaded(List<ReceiptAttachment> attachments);
    }

    public void receiptAttachments(long listId, ReceiptAttachmentsResult callback) {
        worker.execute(() -> {
            List<ReceiptAttachment> list = database.getReceipts(listId);
            main.post(() -> callback.loaded(list));
        });
    }

    public void previewReceipt(ReceiptAttachment item, ReceiptPreview callback) {
        worker.execute(() -> {
            Bitmap bitmap = receipts.preview(item);
            main.post(() -> callback.loaded(bitmap));
        });
    }

    public void removeReceipt(long listId, Result result) {
        run(() -> receipts.remove(database, listId), () -> readItems(listId, result), result);
    }

    public interface ReceiptPreview {
        void loaded(Bitmap bitmap);
    }

    public void previewReceipt(long listId, ReceiptPreview callback) {
        worker.execute(() -> {
            Bitmap preview = null;
            try {
                ShoppingList list = database.getList(listId);
                preview = receipts.preview(list.receiptFile);
            } catch (RuntimeException error) {
                Log.e("ShoppingRepository", "Receipt preview failed", error);
            }
            Bitmap result = preview;
            main.post(() -> callback.loaded(result));
        });
    }

    private void readItems(long listId, Result result) {
        ShoppingList list = database.getList(listId);
        List<ShoppingItem> items = database.getItems(listId);
        List<ReceiptAttachment> attachments = database.getReceipts(listId);
        List<StoreLink> links = database.getStoreLinks(listId);
        main.post(() -> {
            result.storeLinksLoaded(links);
            result.receiptsLoaded(attachments);
            result.listLoaded(list);
            result.loaded(items);
        });
    }

    public void lists(boolean archived, Result result) {
        run(null, () -> readLists(archived, result), result);
    }

    public void saveTemplate(long sourceListId, String name, boolean archived, Result result) {
        run(
                () -> database.saveTemplate(sourceListId, name),
                () -> readLists(archived, result),
                result);
    }

    public void createFromTemplate(long templateId, Result result) {
        run(
                () -> {
                    long created = database.createFromTemplate(templateId);
                    main.post(() -> result.created(created));
                },
                () -> readLists(false, result),
                result);
    }

    public interface TemplateItemsResult {
        void loaded(List<String[]> items);

        void failed();
    }

    public void templateItems(long templateId, TemplateItemsResult callback) {
        pending++;
        worker.execute(() -> {
            try {
                List<String[]> items = database.getTemplateItems(templateId);
                main.post(() -> callback.loaded(items));
            } catch (RuntimeException error) {
                Log.e("ShoppingRepository", "Cannot load template", error);
                main.post(callback::failed);
            } finally {
                main.post(() -> {
                    pending--;
                    if (isIdle() && idleListener != null) {
                        idleListener.run();
                    }
                });
            }
        });
    }

    public void updateTemplate(long id, String name, List<String[]> items, Result result) {
        run(() -> database.updateTemplate(id, name, items), () -> readLists(false, result), result);
    }

    public void deleteTemplate(long templateId, Result result) {
        run(() -> database.deleteTemplate(templateId), () -> readLists(false, result), result);
    }

    public void create(String name, Result result) {
        create(name, ShoppingList.TYPE_SHOPPING, result);
    }

    public void create(String name, String type, Result result) {
        run(
                () -> {
                    long id = database.createList(name, type);
                    main.post(() -> result.created(id));
                },
                () -> readLists(false, result),
                result);
    }

    public void deleteEmptyShoppingList(long id, Result result) {
        run(() -> database.deleteEmptyShoppingList(id), () -> {}, result);
    }

    public void completeShoppingList(long id, boolean confirmed, Result result) {
        run(() -> database.archive(id, confirmed), () -> {}, result);
    }

    public void rename(long id, String name, Result result) {
        run(() -> database.rename(id, name), () -> readLists(false, result), result);
    }

    public void archive(long id, boolean confirmed, Result result) {
        run(() -> database.archive(id, confirmed), () -> readLists(false, result), result);
    }

    public void restore(long id, Result result) {
        run(() -> database.restore(id), () -> readLists(true, result), result);
    }

    private void readLists(boolean archived, Result result) {
        List<ShoppingList> lists = database.getLists(archived);
        List<ShoppingTemplate> templates =
                archived ? java.util.Collections.emptyList() : database.getTemplates();
        main.post(() -> {
            result.templatesLoaded(templates);
            result.listsLoaded(lists);
        });
    }

    // Exposed for isolated instrumentation setup; production uses scoped methods above.
    public void execute(Runnable write, Result result) {
        run(write, () -> {}, result);
    }

    private void run(Runnable write, Runnable read, Result result) {
        pending++;
        worker.execute(() -> {
            try {
                if (write != null) {
                    try {
                        write.run();
                    } catch (ShoppingDatabase.IncompleteListException error) {
                        main.post(result::confirmationRequired);
                        return;
                    } catch (SQLiteException error) {
                        Log.e("ShoppingRepository", "SQLite write failed", error);
                        main.post(() -> result.failed(false));
                        return;
                    }
                    // A committed write remains successful even if the following read fails.
                    main.post(result::written);
                }
                try {
                    read.run();
                } catch (SQLiteException error) {
                    Log.e("ShoppingRepository", "SQLite read failed", error);
                    main.post(() -> result.failed(true));
                }
            } catch (RuntimeException error) {
                Log.e("ShoppingRepository", "Unexpected programming failure", error);
                throw error;
            } finally {
                // Programmer errors are deliberately not disguised as storage errors.
                main.post(() -> {
                    pending--;
                    if (isIdle() && idleListener != null) {
                        idleListener.run();
                    }
                });
            }
        });
    }

    // Main-thread diagnostics also allow instrumentation to synchronize without sleeps.
    public boolean isIdle() {
        return pending == 0;
    }

    public void setIdleListener(Runnable listener) {
        idleListener = listener;
    }
}
