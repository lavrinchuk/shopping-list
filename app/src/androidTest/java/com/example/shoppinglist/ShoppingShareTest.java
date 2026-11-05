package com.example.shoppinglist;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ShoppingShareTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final BlockingQueue<Intent> sent = new LinkedBlockingQueue<>();
    private TestShoppingApplication app;
    private ShoppingRepository repository;
    private ActivityScenario<MainActivity> screen;
    private Instrumentation.ActivityMonitor monitor;
    private IdlingResource idle;
    private long listId;

    @Before
    public void setup() throws Exception {
        app = (TestShoppingApplication) instrumentation.getTargetContext().getApplicationContext();
        instrumentation.runOnMainSync(() -> repository = app.repository());
        enqueue(() -> {
            listId = app.database.createList("Сільпо");
            app.database.add(listId, "Хліб", "1 шт.");
            app.database.setPurchased(listId, app.database.getItems(listId).get(0).id, true);
            app.database.add(listId, "Молоко", "2 л");
        });
        IdlingResource.ResourceCallback[] callback = new IdlingResource.ResourceCallback[1];
        instrumentation.runOnMainSync(() ->
                repository.setIdleListener(() -> {
                    if (callback[0] != null) {
                        callback[0].onTransitionToIdle();
                    }
                }));
        idle =
                new IdlingResource() {
                    @Override
                    public String getName() {
                        return "Share snapshot queue";
                    }

                    @Override
                    public boolean isIdleNow() {
                        return repository.isIdle();
                    }

                    @Override
                    public void registerIdleTransitionCallback(ResourceCallback listener) {
                        callback[0] = listener;
                    }
                };
        IdlingRegistry.getInstance().register(idle);
        monitor =
                new Instrumentation.ActivityMonitor() {
                    @Override
                    public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                        if (!Intent.ACTION_CHOOSER.equals(intent.getAction())) {
                            return null;
                        }
                        sent.add(intent);
                        return new Instrumentation.ActivityResult(Activity.RESULT_OK, null);
                    }
                };
        instrumentation.addMonitor(monitor);
    }

    @After
    public void cleanup() {
        if (app != null && app.database.allowWrite != null) {
            app.database.allowWrite.countDown();
        }
        if (screen != null) {
            screen.close();
        }
        if (monitor != null) {
            instrumentation.removeMonitor(monitor);
        }
        if (idle != null) {
            IdlingRegistry.getInstance().unregister(idle);
        }
        if (repository != null) {
            instrumentation.runOnMainSync(() -> repository.setIdleListener(null));
        }
    }

    private void enqueue(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        instrumentation.runOnMainSync(() ->
                repository.execute(
                        action,
                        new ShoppingRepository.Result() {
                            @Override
                            public void written() {
                                done.countDown();
                            }

                            @Override
                            public void loaded(List<ShoppingItem> ignored) {}

                            @Override
                            public void failed(boolean reading) {
                                throw new AssertionError("Database operation failed");
                            }
                        }));
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }

    private void open() {
        screen =
                ActivityScenario.launch(
                        new Intent(app, MainActivity.class).putExtra("listId", listId));
        onView(withContentDescription(R.string.share_list)).check(matches(isEnabled()));
    }

    @SuppressWarnings("deprecation")
    private String exportedText() throws Exception {
        Intent chooser = sent.poll(10, TimeUnit.SECONDS);
        assertNotNull("Sharesheet was not opened", chooser);
        Intent send = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertNotNull(send);
        assertEquals(Intent.ACTION_SEND, send.getAction());
        assertEquals("text/plain", send.getType());
        assertNull(send.getPackage());
        assertNull(send.getComponent());
        return send.getStringExtra(Intent.EXTRA_TEXT);
    }

    private void verifyShareDoesNotWrite(boolean archived) throws Exception {
        if (archived) {
            enqueue(() -> app.database.archive(listId, true));
        }
        ShoppingList[] before = new ShoppingList[1];
        enqueue(() -> before[0] = app.database.getList(listId));
        open();
        onView(withContentDescription(R.string.share_list)).perform(click());
        assertEquals("Сільпо\n\n☐ Молоко — 2 л\n☑ Хліб — 1 шт.", exportedText());
        enqueue(() -> {
            ShoppingList after = app.database.getList(listId);
            assertEquals(before[0].updatedAt, after.updatedAt);
            assertEquals(before[0].archivedAt, after.archivedAt);
            assertEquals(2, after.total);
            assertEquals(1, after.purchased);
        });
        assertTrue(sent.isEmpty());
    }

    @Test
    public void activeListSharesPlainTextWithoutChangingData() throws Exception {
        verifyShareDoesNotWrite(false);
    }

    @Test
    public void archivedListCanShareWithoutChangingData() throws Exception {
        verifyShareDoesNotWrite(true);
    }

    @Test
    public void pendingWriteAndRotationProduceFreshSnapshotOnce() throws Exception {
        open();
        app.database.pauseNextWrite();
        screen.onActivity(activity -> {
            activity.model.draft("Кава", "");
            activity.model.add();
            MaterialToolbar toolbar = activity.findViewById(R.id.heading);
            toolbar.getMenu().performIdentifierAction(R.id.share_list, 0);
        });
        assertTrue(app.database.writeStarted.await(5, TimeUnit.SECONDS));
        screen.recreate();
        assertTrue(sent.isEmpty());
        app.database.allowWrite.countDown();
        assertEquals("Сільпо\n\n☐ Молоко — 2 л\n☐ Кава\n☑ Хліб — 1 шт.", exportedText());
        onView(withContentDescription(R.string.share_list)).check(matches(isEnabled()));
        screen.recreate();
        onView(withContentDescription(R.string.share_list)).check(matches(isEnabled()));
        assertTrue(sent.isEmpty());
    }

    @Test
    public void failedSnapshotDoesNotShareStaleDataAndCanRetry() throws Exception {
        open();
        app.database.failNextRead = true;
        onView(withContentDescription(R.string.share_list)).perform(click());
        onView(withContentDescription(R.string.share_list)).check(matches(isEnabled()));
        assertTrue(sent.isEmpty());
        onView(withContentDescription(R.string.share_list)).perform(click());
        assertEquals("Сільпо\n\n☐ Молоко — 2 л\n☑ Хліб — 1 шт.", exportedText());
    }
}
