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
    }
    private final ShoppingDatabase database;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int pending;
    private Runnable idleListener;

    ShoppingRepository(ShoppingDatabase database) { this.database = database; }
    void load(Result result) { execute(null, result); }
    void add(String name, String quantity, Result result) {
        execute(() -> database.add(name, quantity), result);
    }
    void purchased(long id, boolean checked, Result result) {
        execute(() -> database.setPurchased(id, checked), result);
    }
    void delete(long id, Result result) { execute(() -> database.delete(id), result); }

    void execute(Runnable write, Result result) {
        pending++;
        worker.execute(() -> {
            try {
                if (write != null) {
                    try { write.run(); }
                    catch (SQLiteException error) {
                        Log.e("ShoppingRepository", "SQLite write failed", error);
                        main.post(() -> result.failed(false));
                        return;
                    }
                    // A committed write remains successful even if the following read fails.
                    main.post(result::written);
                }
                try {
                    List<ShoppingItem> items = database.getItems();
                    main.post(() -> result.loaded(items));
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
