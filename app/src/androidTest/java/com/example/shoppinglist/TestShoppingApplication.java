package com.example.shoppinglist;

import android.database.sqlite.SQLiteException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestShoppingApplication extends ShoppingApplication {
    final String testDatabaseName = "instrumentation-" + java.util.UUID.randomUUID() + ".db";
    ControlledDatabase database;
    long listId;

    @Override ShoppingRepository createRepository() {
        database = new ControlledDatabase(this, testDatabaseName);
        listId = database.createList("Тестовий список");
        return new ShoppingRepository(database);
    }
    static final class ControlledDatabase extends ShoppingDatabase {
        volatile CountDownLatch writeStarted;
        volatile CountDownLatch allowWrite;
        volatile boolean failNextRead;
        volatile boolean failNextWrite;

        ControlledDatabase(android.content.Context context, String name) { super(context, name); }
        void pauseNextWrite() {
            writeStarted = new CountDownLatch(1);
            allowWrite = new CountDownLatch(1);
        }
        @Override void add(long listId, String name, String quantity) {
            CountDownLatch gate = allowWrite;
            if (gate != null) {
                writeStarted.countDown();
                try {
                    if (!gate.await(15, TimeUnit.SECONDS)) throw new AssertionError("Write gate timed out");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
                allowWrite = null;
            }
            if (failNextWrite) {
                failNextWrite = false;
                throw new SQLiteException("Injected write failure");
            }
            super.add(listId, name, quantity);
        }
        @Override List<ShoppingItem> getItems(long listId) {
            if (failNextRead) {
                failNextRead = false;
                throw new SQLiteException("Injected read failure");
            }
            return super.getItems(listId);
        }
    }
}
