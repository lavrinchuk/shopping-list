package com.example.shoppinglist;

import android.database.sqlite.SQLiteException;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One application-owned queue orders writes and their subsequent reads, across Activities. */
class ShoppingRepository {
    interface Result {
        void written();
        void loaded(List<ShoppingItem> items);
        void failed(boolean reading);
        default void listLoaded(ShoppingList list) { }
        default void listsLoaded(List<ShoppingList> lists) { }
        default void confirmationRequired() { failed(false); }
    }
    private final ShoppingDatabase database;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int pending;
    private Runnable idleListener;

    ShoppingRepository(ShoppingDatabase database) { this.database = database; }
    void load(long listId, Result result) { run(null, () -> readItems(listId, result), result); }
    void add(long listId, String name, String quantity, Result result) {
        run(() -> database.add(listId, name, quantity), () -> readItems(listId, result), result);
    }
    void purchased(long listId, long id, boolean checked, Result result) {
        run(() -> database.setPurchased(listId, id, checked), () -> readItems(listId, result), result);
    }
    void delete(long listId, long id, Result result) {
        run(() -> database.delete(listId, id), () -> readItems(listId, result), result);
    }
    private void readItems(long listId, Result result) {
        ShoppingList list = database.getList(listId);
        List<ShoppingItem> items = database.getItems(listId);
        main.post(() -> { result.listLoaded(list); result.loaded(items); });
    }
    void lists(boolean archived, Result result) { run(null, () -> readLists(archived, result), result); }
    void create(String name, Result result) { run(() -> database.createList(name), () -> readLists(false, result), result); }
    void rename(long id, String name, Result result) { run(() -> database.rename(id, name), () -> readLists(false, result), result); }
    void archive(long id, boolean confirmed, Result result) {
        run(() -> database.archive(id, confirmed), () -> readLists(false, result), result);
    }
    void restore(long id, Result result) { run(() -> database.restore(id), () -> readLists(true, result), result); }
    private void readLists(boolean archived, Result result) {
        List<ShoppingList> lists = database.getLists(archived);
        main.post(() -> result.listsLoaded(lists));
    }
    // Package-visible for isolated instrumentation setup; production uses scoped methods above.
    void execute(Runnable write, Result result) { run(write, () -> { }, result); }
    private void run(Runnable write, Runnable read, Result result) {
        pending++;
        worker.execute(() -> {
            try {
                if (write != null) {
                    try { write.run(); }
                    catch (ShoppingDatabase.IncompleteListException error) {
                        main.post(result::confirmationRequired);
                        return;
                    }
                    catch (SQLiteException error) {
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
                    if (isIdle() && idleListener != null) idleListener.run();
                });
            }
        });
    }
    // Main-thread diagnostics also allow instrumentation to synchronize without sleeps.
    boolean isIdle() { return pending == 0; }
    void setIdleListener(Runnable listener) { idleListener = listener; }

}
