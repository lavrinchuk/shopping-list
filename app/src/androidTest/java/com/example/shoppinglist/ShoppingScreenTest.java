package com.example.shoppinglist;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.TimeUnit;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ShoppingScreenTest {
    private TestShoppingApplication app;
    private ShoppingRepository repository;
    private ActivityScenario<MainActivity> screen;
    private ScreenIdle idle;

    private void main(Runnable task) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
    }
    private void open() {
        screen = ActivityScenario.launch(MainActivity.class);
        screen.onActivity(activity -> idle.attach(activity));
    }
    @Before public void setup() {
        app = (TestShoppingApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        main(() -> {
            repository = app.repository();
            // Enqueue cleanup before the new ViewModel's first load.
            repository.execute(() -> {
                for (ShoppingItem item : app.database.getItems()) app.database.delete(item.id);
            }, noResult());
        });
        idle = new ScreenIdle(repository);
        main(() -> repository.setIdleListener(idle::changed));
        IdlingRegistry.getInstance().register(idle);
        open();
        onView(withId(R.id.add)).check(matches(isEnabled()));
    }
    private static ShoppingRepository.Result noResult() {
        return new ShoppingRepository.Result() {
            @Override public void written() { }
            @Override public void loaded(java.util.List<ShoppingItem> items) { }
            @Override public void failed(boolean reading) { throw new AssertionError("Unexpected database error"); }
        };
    }
    @After public void cleanup() {
        if (app != null && app.database.allowWrite != null) app.database.allowWrite.countDown();
        if (screen != null) screen.close();
        if (idle != null) IdlingRegistry.getInstance().unregister(idle);
        if (repository != null) main(() -> repository.setIdleListener(null));
    }
    private void add(String name) {
        onView(withId(R.id.name)).perform(replaceText(name), closeSoftKeyboard());
        onView(withId(R.id.add)).perform(click());
        onView(withText(name)).check(matches(isDisplayed()));
    }
    @Test public void addCheckReopenAndDelete() {
        onView(withId(R.id.add)).perform(click());
        screen.onActivity(activity -> assertEquals(app.getString(R.string.name_required),
                ((com.google.android.material.textfield.TextInputLayout) activity.findViewById(R.id.name_layout)).getError()));
        onView(withId(R.id.name)).perform(replaceText("Яблука"));
        onView(withId(R.id.quantity)).perform(replaceText("2 кг"), closeSoftKeyboard());
        onView(withId(R.id.add)).perform(click());
        onView(withText("2 кг")).check(matches(isDisplayed()));
        onView(withId(R.id.item_name)).perform(click());
        onView(withId(R.id.item_name)).check(matches(isChecked()));
        screen.close();
        open();
        onView(withId(R.id.item_name)).check(matches(isChecked()));
        onView(withId(R.id.delete_item)).perform(click());
        onView(withText(R.string.empty_title)).check(matches(isDisplayed()));
    }
    @Test public void rotationDuringWriteKeepsOperationAndNewDraft() throws Exception {
        app.database.pauseNextWrite();
        // Deliberately interact while the operation is paused, using the main thread rather
        // than Espresso (which correctly waits until all repository operations are idle).
        screen.onActivity(activity -> {
            ((android.widget.EditText) activity.findViewById(R.id.name)).setText("Молоко");
            activity.findViewById(R.id.add).performClick();
            ((android.widget.EditText) activity.findViewById(R.id.name)).setText("Хліб");
            ((android.widget.EditText) activity.findViewById(R.id.quantity)).setText("2 шт");
            activity.findViewById(R.id.add).performClick(); // ignored while adding
        });
        assertTrue(app.database.writeStarted.await(5, TimeUnit.SECONDS));
        screen.recreate();
        screen.onActivity(activity -> {
            idle.attach(activity);
            assertEquals("Хліб", ((android.widget.EditText) activity.findViewById(R.id.name)).getText().toString());
            assertFalse(activity.findViewById(R.id.add).isEnabled());
            com.google.android.material.button.MaterialButton button = activity.findViewById(R.id.add);
            assertEquals(activity.getString(R.string.add_item), button.getContentDescription());
            assertEquals(Math.round(56 * activity.getResources().getDisplayMetrics().density), button.getWidth());
            assertEquals(button.getWidth(), button.getHeight());
            assertNotEquals(button.getIconTint().getColorForState(new int[]{android.R.attr.state_enabled}, 0),
                    button.getIconTint().getColorForState(new int[]{-android.R.attr.state_enabled}, 0));
        });
        app.database.allowWrite.countDown();
        onView(withText("Молоко")).check(matches(isDisplayed()));
        onView(withId(R.id.name)).check(matches(withText("Хліб")));
        onView(withId(R.id.quantity)).check(matches(withText("2 шт")));
        screen.onActivity(activity -> assertEquals(1, activity.adapter.getItemCount()));
    }

    @Test public void reopeningActivityDuringWriteLoadsCommittedResult() throws Exception {
        app.database.pauseNextWrite();
        screen.onActivity(activity -> {
            ((android.widget.EditText) activity.findViewById(R.id.name)).setText("Кава");
            activity.findViewById(R.id.add).performClick();
        });
        assertTrue(app.database.writeStarted.await(5, TimeUnit.SECONDS));
        screen.close();
        open();
        app.database.allowWrite.countDown();
        onView(withText("Кава")).check(matches(isDisplayed()));
        screen.onActivity(activity -> assertEquals(1, activity.adapter.getItemCount()));
    }
    @Test public void successfulWriteFailedReadCanReloadWithoutDuplicate() {
        app.database.failNextRead = true;
        onView(withId(R.id.name)).perform(replaceText("Сир"), closeSoftKeyboard());
        onView(withId(R.id.add)).perform(click());
        onView(withId(R.id.name)).check(matches(withText("")));
        onView(withText(R.string.read_error)).check(matches(isDisplayed()));
        onView(withText(R.string.retry)).perform(click());
        onView(withText("Сир")).check(matches(isDisplayed()));
        screen.onActivity(activity -> assertEquals(1, activity.adapter.getItemCount()));
    }
    @Test public void failedWriteKeepsDraftAndCanRetry() {
        app.database.failNextWrite = true;
        onView(withId(R.id.name)).perform(replaceText("Груші"), closeSoftKeyboard());
        onView(withId(R.id.add)).perform(click());
        onView(withId(R.id.name)).check(matches(withText("Груші")));
        onView(withId(R.id.add)).check(matches(isEnabled()));
        screen.onActivity(activity -> assertTrue(activity.model.items.isEmpty()));
        onView(withId(R.id.add)).perform(click());
        onView(withId(R.id.name)).check(matches(withText("")));
        screen.onActivity(activity -> assertEquals(1, activity.adapter.getItemCount()));
    }
    @Test public void rotationDuringWriteClearsOnlySubmittedDraft() throws Exception {
        app.database.pauseNextWrite();
        screen.onActivity(activity -> {
            ((android.widget.EditText) activity.findViewById(R.id.name)).setText("Олія");
            activity.findViewById(R.id.add).performClick();
        });
        assertTrue(app.database.writeStarted.await(5, TimeUnit.SECONDS));
        screen.recreate();
        screen.onActivity(activity -> idle.attach(activity));
        app.database.allowWrite.countDown();
        onView(withText("Олія")).check(matches(isDisplayed()));
        onView(withId(R.id.name)).check(matches(withText("")));
        screen.onActivity(activity -> assertEquals(1, activity.adapter.getItemCount()));
    }
    @Test public void largeListDiffKeepsOrderAndUpdatesStatusAndDeletion() {
        main(() -> repository.execute(() -> {
            for (int index = 0; index < 500; index++) app.database.add("Товар " + index, "");
        }, noResult()));
        screen.onActivity(activity -> activity.model.reload());
        onView(withId(R.id.items)).check(matches(isDisplayed()));
        screen.onActivity(activity -> {
            assertEquals(500, activity.adapter.getItemCount());
            ShoppingItem first = activity.model.items.get(0);
            activity.model.purchased(first, true);
            activity.model.delete(activity.model.items.get(1));
        });
        onView(withId(R.id.items)).check(matches(isDisplayed()));
        screen.onActivity(activity -> {
            assertEquals(499, activity.adapter.getItemCount());
            assertEquals("Товар 2", activity.adapter.getCurrentList().get(0).name);
            assertEquals("Товар 0", activity.adapter.getCurrentList().get(498).name);
            assertTrue(activity.adapter.getCurrentList().get(498).purchased);
        });
    }
    @Test public void rapidStatusChangesKeepFinalValueAndOrder() {
        add("Молоко");
        add("Хліб");
        screen.onActivity(activity -> {
            ShoppingItem milk = activity.model.items.get(0);
            activity.model.purchased(milk, true);
            activity.model.purchased(milk, false);
            activity.model.purchased(milk, true);
        });
        onView(withText("Хліб")).check(matches(isDisplayed()));
        screen.onActivity(activity -> {
            assertEquals("Хліб", activity.adapter.getCurrentList().get(0).name);
            assertEquals("Молоко", activity.adapter.getCurrentList().get(1).name);
            assertTrue(activity.adapter.getCurrentList().get(1).purchased);
        });
    }
    @Test public void testApplicationUsesOnlyIsolatedDatabase() {
        assertTrue(app.testDatabaseName.startsWith("instrumentation-"));
        assertNotEquals(app.getDatabasePath("shopping.db"), app.getDatabasePath(app.testDatabaseName));
        assertEquals(app.testDatabaseName, app.database.getDatabaseName());
        add("Ізольований товар");
    }
    @Test public void launcherIconHasAdaptiveAndThemedLayers() {
        android.graphics.drawable.Drawable drawable = app.getDrawable(R.mipmap.ic_launcher);
        assertTrue(drawable instanceof android.graphics.drawable.AdaptiveIconDrawable);
        android.graphics.drawable.AdaptiveIconDrawable icon = (android.graphics.drawable.AdaptiveIconDrawable) drawable;
        assertNotNull(icon.getBackground());
        assertNotNull(icon.getForeground());
        if (android.os.Build.VERSION.SDK_INT >= 33) assertNotNull(icon.getMonochrome());
        assertTrue(app.getDrawable(R.mipmap.ic_launcher_round)
                instanceof android.graphics.drawable.AdaptiveIconDrawable);
    }
    @Test public void materialAppearanceAndKeyboard() throws Exception {
        setNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        onView(withText(R.string.empty_title)).check(matches(isDisplayed()));
        screenshot("empty-light");
        main(() -> repository.execute(() -> {
            app.database.add("Молоко", "2 л");
            app.database.add("Яблука", "1 кг");
            app.database.add("Цільнозерновий хліб із насінням соняшника та гарбуза", "1 шт");
            app.database.add("Натуральний йогурт", "");
            app.database.setPurchased(app.database.getItems().get(1).id, true);
        }, noResult()));
        screen.onActivity(activity -> activity.model.reload());
        onView(withText("Молоко")).check(matches(isDisplayed()));
        screen.onActivity(activity -> {
            assertTrue(activity.findViewById(R.id.items).getHeight() > 0);
            android.view.View add = activity.findViewById(R.id.add);
            assertEquals(Math.round(56 * activity.getResources().getDisplayMetrics().density), add.getWidth());
            assertEquals(add.getWidth(), add.getHeight());
        });
        screenshot("list-light");
        setNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        onView(withText("Молоко")).check(matches(isDisplayed()));
        screenshot("list-dark");
        setNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        onView(withId(R.id.name)).perform(scrollTo(), click(), replaceText("Чернетка товару"));
        onView(withId(R.id.quantity)).perform(scrollTo(), click(), replaceText("2 кг"));
        screenshot("keyboard");
        screen.recreate();
        screen.onActivity(activity -> idle.attach(activity));
        onView(withId(R.id.name)).check(matches(withText("Чернетка товару")));
        onView(withId(R.id.quantity)).check(matches(withText("2 кг")));
        onView(withId(R.id.quantity)).perform(scrollTo(), pressImeActionButton());
        onView(withId(R.id.name)).check(matches(withText("")));
        screen.onActivity(activity -> assertEquals(5, activity.adapter.getItemCount()));
    }
    private void setNightMode(int mode) {
        screen.onActivity(activity -> activity.getDelegate().setLocalNightMode(mode));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        screen.onActivity(activity -> idle.attach(activity));
        onView(withId(R.id.items)).check(matches(isDisplayed()));
    }
    private void screenshot(String name) throws Exception {
        // Capture a fully drawn frame, including completion of RecyclerView item animations.
        java.util.concurrent.CountDownLatch drawn = new java.util.concurrent.CountDownLatch(1);
        screen.onActivity(activity -> {
            android.view.View root = activity.findViewById(R.id.main);
            root.postOnAnimation(() -> root.postOnAnimation(() -> {
                Runnable finished = () -> root.postOnAnimation(() -> root.postOnAnimation(drawn::countDown));
                androidx.recyclerview.widget.RecyclerView list = activity.findViewById(R.id.items);
                if (list.getItemAnimator() != null && list.getItemAnimator().isRunning()) {
                    list.getItemAnimator().isRunning(finished::run);
                } else finished.run();
            }));
        });
        assertTrue("Screenshot frame did not finish", drawn.await(10, java.util.concurrent.TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        String prefix = InstrumentationRegistry.getArguments().getString("previewPrefix", "default");
        java.io.File directory = new java.io.File(app.getFilesDir(), "ui-previews");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        android.graphics.Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(
                new java.io.File(directory, prefix.replaceAll("[^a-zA-Z0-9_-]", "") + "-" + name + ".png"))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
    private final class ScreenIdle implements IdlingResource {
        final ShoppingRepository repository;
        ShoppingAdapter adapter;
        ResourceCallback callback;
        androidx.recyclerview.widget.RecyclerView list;
        void attach(MainActivity activity) {
            adapter = activity.adapter;
            list = activity.findViewById(R.id.items);
        }
        ScreenIdle(ShoppingRepository repository) { this.repository = repository; }
        @Override public String getName() { return "Shopping database and list diff"; }
        @Override public boolean isIdleNow() {
            if (adapter != null) adapter.setIdleListener(this::changed);
            if (!repository.isIdle() || (adapter != null && !adapter.isIdle())) return false;
            androidx.recyclerview.widget.RecyclerView.ItemAnimator animator = list == null ? null : list.getItemAnimator();
            if (animator != null && animator.isRunning()) {
                animator.isRunning(this::changed);
                return false;
            }
            return true;
        }
        void changed() { if (isIdleNow() && callback != null) callback.onTransitionToIdle(); }
        @Override public void registerIdleTransitionCallback(ResourceCallback callback) { this.callback = callback; }
    }
}
