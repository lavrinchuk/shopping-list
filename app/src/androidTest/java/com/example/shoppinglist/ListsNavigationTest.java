package com.example.shoppinglist;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ListsNavigationTest {
    private TestShoppingApplication app;
    private ShoppingRepository repository;
    private ActivityScenario<ListsActivity> home;
    private NavigationIdle idle;
    @Before public void setup() {
        app = (TestShoppingApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            repository = app.repository();
            repository.execute(() -> {
                app.database.getWritableDatabase().execSQL("DELETE FROM items");
                app.database.getWritableDatabase().execSQL("DELETE FROM shopping_lists WHERE id != ?", new Object[]{app.listId});
                app.database.restore(app.listId);
            }, new ShoppingRepository.Result() {
                @Override public void written() { }
                @Override public void loaded(java.util.List<ShoppingItem> ignored) { }
                @Override public void failed(boolean reading) { throw new AssertionError("Test setup failed"); }
            });
        });
        idle = new NavigationIdle(); app.registerActivityLifecycleCallbacks(idle);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> repository.setIdleListener(idle::signal));
        IdlingRegistry.getInstance().register(idle);
        home = ActivityScenario.launch(ListsActivity.class);
        onView(withText("Тестовий список")).check(matches(isDisplayed()));
    }
    @After public void cleanup() {
        if (home != null) home.close();
        if (idle != null) {
            app.unregisterActivityLifecycleCallbacks(idle); IdlingRegistry.getInstance().unregister(idle);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { repository.setIdleListener(null); idle.detach(); });
        }
    }
    private void create(String name) {
        onView(withId(R.id.create_list)).perform(click());
        onView(withId(R.id.list_name_input)).perform(replaceText(name), closeSoftKeyboard());
        onView(withText(R.string.save)).perform(click());
        onView(withText(name)).check(matches(isDisplayed()));
    }
    private void menu(String name, int action) {
        onView(withContentDescription(app.getString(R.string.list_actions, name))).perform(click());
        onView(withText(action)).perform(click());
    }
    private void back() { onView(withContentDescription(R.string.back)).perform(click()); }
    @Test public void createRotateRenameAndKeepListsIndependent() throws Exception {
        onView(withId(R.id.create_list)).perform(click());
        onView(withText(R.string.save)).perform(click());
        onView(withText(R.string.list_name_required)).check(matches(isDisplayed()));
        onView(withId(R.id.list_name_input)).perform(replaceText("Сільпо"), closeSoftKeyboard());
        home.recreate();
        onView(withId(R.id.list_name_input)).check(matches(withText("Сільпо")));
        onView(withText(R.string.save)).perform(click());
        onView(withText("Сільпо")).perform(click());
        onView(withId(R.id.name)).perform(replaceText("Молоко"), closeSoftKeyboard());
        onView(withId(R.id.add)).perform(click());
        onView(withText("Молоко")).check(matches(isDisplayed())); back();
        home.onActivity(activity -> {
            ShoppingList list = activity.model.lists.stream().filter(value -> value.name.equals("Сільпо")).findFirst().get();
            assertEquals(1, list.total); assertEquals(0, list.purchased);
        });
        create("Одяг"); onView(withText("Одяг")).perform(click());
        onView(withText("Молоко")).check(doesNotExist());
        onView(withText(R.string.empty_title)).check(matches(isDisplayed())); back();
        menu("Одяг", R.string.rename_list);
        onView(withId(R.id.list_name_input)).perform(replaceText("Епіцентр"), closeSoftKeyboard());
        onView(withText(R.string.save)).perform(click());
        onView(withText("Епіцентр")).check(matches(isDisplayed()));
        home.recreate(); onView(withText("Епіцентр")).check(matches(isDisplayed()));
        capture("active-lists");
    }
    @Test public void archiveConfirmationReadOnlyAndRestoreNavigation() throws Exception {
        create("Покупки"); onView(withText("Покупки")).perform(click());
        onView(withId(R.id.name)).perform(replaceText("Хліб"), closeSoftKeyboard());
        onView(withId(R.id.add)).perform(click()); onView(withText("Хліб")).check(matches(isDisplayed())); back();
        menu("Покупки", R.string.finish_shopping);
        onView(withText(R.string.incomplete_confirmation)).check(matches(isDisplayed()));
        onView(withText(R.string.cancel)).perform(click());
        onView(withText("Покупки")).check(matches(isDisplayed()));
        menu("Покупки", R.string.finish_shopping);
        home.recreate(); onView(withText(R.string.incomplete_confirmation)).check(matches(isDisplayed()));
        onView(withId(android.R.id.button1)).perform(click());
        onView(withText("Покупки")).check(doesNotExist());
        onView(withId(R.id.open_archive)).perform(click());
        capture("archive");
        onView(withText("Покупки")).perform(click());
        onView(withId(R.id.form_scroll)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        onView(withId(R.id.item_name)).check(matches(isNotEnabled()));
        onView(withId(R.id.item_name)).check(matches(isNotChecked()));
        onView(withId(R.id.delete_item)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        capture("archived-items"); back();
        menu("Покупки", R.string.restore_list);
        onView(withText(R.string.no_archive)).check(matches(isDisplayed())); back();
        onView(withText("Покупки")).perform(click());
        onView(withId(R.id.item_name)).check(matches(isEnabled()));
        onView(withText("Хліб")).check(matches(isDisplayed())); back();
    }
    private void capture(String name) throws Exception {
        java.util.concurrent.CountDownLatch drawn = new java.util.concurrent.CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            android.view.View root = idle.current.getWindow().getDecorView();
            root.postOnAnimation(() -> root.postOnAnimation(drawn::countDown));
        });
        assertTrue(drawn.await(10, java.util.concurrent.TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        java.io.File directory = new java.io.File(app.getFilesDir(), "v2-previews");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        android.graphics.Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
    /** Tracks the resumed screen, repository callbacks, list diffs and item animations. */
    private final class NavigationIdle implements IdlingResource, Application.ActivityLifecycleCallbacks {
        Activity current;
        RecyclerView list;
        ResourceCallback callback;
        final RecyclerView.AdapterDataObserver observer = new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { postSignal(); }
            @Override public void onItemRangeInserted(int start, int count) { postSignal(); }
            @Override public void onItemRangeRemoved(int start, int count) { postSignal(); }
            @Override public void onItemRangeChanged(int start, int count, Object payload) { postSignal(); }
            @Override public void onItemRangeMoved(int from, int to, int count) { postSignal(); }
        };
        void postSignal() { if (list != null) list.post(this::signal); }
        void detach() {
            if (list != null && list.getAdapter() != null) list.getAdapter().unregisterAdapterDataObserver(observer);
            list = null; current = null;
        }
        void signal() { if (callback != null && isIdleNow()) callback.onTransitionToIdle(); }
        @Override public String getName() { return "Lists navigation and SQLite"; }
        @Override public boolean isIdleNow() {
            if (!repository.isIdle()) return false;
            if (list == null) return true;
            ListAdapter<?, ?> adapter = (ListAdapter<?, ?>) list.getAdapter();
            java.util.List<?> desired = current instanceof ListsActivity ? ((ListsActivity) current).model.lists : ((MainActivity) current).model.items;
            if (!adapter.getCurrentList().equals(desired)) return false;
            if (list.getItemAnimator() != null && list.getItemAnimator().isRunning()) {
                list.getItemAnimator().isRunning(this::signal); return false;
            }
            return true;
        }
        @Override public void registerIdleTransitionCallback(ResourceCallback callback) { this.callback = callback; }
        @Override public void onActivityResumed(Activity activity) {
            detach(); current = activity;
            if (activity instanceof ListsActivity) list = activity.findViewById(R.id.lists);
            else if (activity instanceof MainActivity) list = activity.findViewById(R.id.items);
            if (list != null) { list.getAdapter().registerAdapterDataObserver(observer); postSignal(); }
        }
        @Override public void onActivityDestroyed(Activity activity) { if (current == activity) detach(); }
        @Override public void onActivityCreated(Activity a, Bundle b) { }
        @Override public void onActivityStarted(Activity a) { }
        @Override public void onActivityPaused(Activity a) { }
        @Override public void onActivityStopped(Activity a) { }
        @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
    }
}
