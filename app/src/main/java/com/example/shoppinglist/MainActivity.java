package com.example.shoppinglist;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.view.inputmethod.EditorInfo;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.textfield.TextInputLayout;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {
    ShoppingViewModel model;
    ShoppingAdapter adapter;
    private Snackbar retry;
    private boolean rendering;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        boolean dark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.core.view.WindowInsetsControllerCompat systemBarController = androidx.core.view.WindowCompat
                .getInsetsController(getWindow(), getWindow().getDecorView());
        systemBarController.setAppearanceLightStatusBars(!dark);
        systemBarController.setAppearanceLightNavigationBars(!dark);
        setContentView(R.layout.activity_main);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.heading), true);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        long listId = getIntent().getLongExtra("listId", -1);
        if (listId <= 0) {
            startActivity(new android.content.Intent(this, ListsActivity.class)); finish(); return;
        }
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.heading);
        toolbar.setNavigationOnClickListener(view -> finish());
        ShoppingRepository repository = ((ShoppingApplication) getApplication()).repository();
        model = new ViewModelProvider(this, new AbstractSavedStateViewModelFactory(this, savedInstanceState) {
            @NonNull @Override protected <T extends ViewModel> T create(@NonNull String key,
                    @NonNull Class<T> type, @NonNull SavedStateHandle state) {
                return type.cast(new ShoppingViewModel(repository, state, listId));
            }
        }).get(ShoppingViewModel.class);
        adapter = new ShoppingAdapter(new ShoppingAdapter.Actions() {
            @Override public void purchased(ShoppingItem item, boolean checked) { model.purchased(item, checked); }
            @Override public void delete(ShoppingItem item) { model.delete(item); }
        });
        RecyclerView list = findViewById(R.id.items);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        EditText name = findViewById(R.id.name);
        EditText quantity = findViewById(R.id.quantity);
        TextInputLayout nameLayout = findViewById(R.id.name_layout);
        configureForm();
        // Restore the draft from the retained model, not stale framework View state.
        name.setSaveEnabled(false);
        quantity.setSaveEnabled(false);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!rendering) {
                    model.draft(name.getText().toString(), quantity.getText().toString());
                    if (!name.getText().toString().trim().isEmpty()) nameLayout.setError(null);
                }
            }
            @Override public void afterTextChanged(Editable text) { }
        };
        name.addTextChangedListener(watcher);
        quantity.addTextChangedListener(watcher);
        View add = findViewById(R.id.add);
        add.setOnClickListener(view -> {
            try { model.add(); }
            catch (IllegalArgumentException error) {
                nameLayout.setError(getString(R.string.name_required));
                name.requestFocus();
            }
        });
        quantity.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            if (add.isEnabled()) add.performClick();
            return true;
        });
        model.changes.observe(this, ignored -> {
            rendering = true;
            if (!name.getText().toString().equals(model.name)) {
                boolean cleared = model.name.isEmpty() && !model.adding;
                name.setText(model.name);
                if (cleared) name.requestFocus();
            }
            if (!quantity.getText().toString().equals(model.quantity)) quantity.setText(model.quantity);
            rendering = false;
            add.setEnabled(!model.adding);
            if (model.list != null) {
                toolbar.setTitle(model.list.name);
                boolean readOnly = model.list.archivedAt != null;
                toolbar.setSubtitle(readOnly ? getString(R.string.archive) : null);
                findViewById(R.id.form_scroll).setVisibility(readOnly ? View.GONE : View.VISIBLE);
                adapter.setReadOnly(readOnly);
            }
            adapter.submitList(model.items);
            findViewById(R.id.empty).setVisibility(model.items.isEmpty() && !model.readFailed ? View.VISIBLE : View.GONE);
            int error = model.error;
            model.consumeError();
            if (error == R.string.write_error) {
                adapter.restoreChecks();
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
            if (model.readFailed && retry == null) {
                retry = Snackbar.make(list, R.string.read_error, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.retry, view -> { retry = null; model.reload(); });
                retry.show();
            } else if (!model.readFailed && retry != null) {
                retry.dismiss();
                retry = null;
            }
        });
    }
    /** Keep the form compact, but leave scrollable space for both inputs and the list. */
    private void configureForm() {
        View root = findViewById(R.id.main);
        LinearLayout actions = findViewById(R.id.form_actions);
        View quantity = findViewById(R.id.quantity_layout);
        View add = findViewById(R.id.add);
        NestedScrollView scroll = findViewById(R.id.form_scroll);
        root.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            float density = getResources().getDisplayMetrics().density;
            float fontScale = getResources().getConfiguration().fontScale;
            int width = root.getWidth() - root.getPaddingLeft() - root.getPaddingRight();
            boolean stacked = width / density < 32 + 312 * Math.max(1f, fontScale);
            int orientation = stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;
            if (actions.getOrientation() != orientation) {
                actions.setOrientation(orientation);
                quantity.setLayoutParams(new LinearLayout.LayoutParams(stacked ? -1 : 0, -2, stacked ? 0 : 1));
                int diameter = Math.round(56 * density);
                LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(diameter, diameter);
                button.setMarginStart(stacked ? 0 : Math.round(12 * density));
                button.topMargin = stacked ? Math.round(8 * density) : 0;
                add.setLayoutParams(button);
            }
            int available = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom()
                    - findViewById(R.id.heading).getHeight();
            View content = scroll.getChildAt(0);
            content.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, width), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int height = Math.min(content.getMeasuredHeight(), Math.max(0, Math.round(available * 0.60f)));
            if (scroll.getLayoutParams().height != height) {
                android.view.ViewGroup.LayoutParams params = scroll.getLayoutParams();
                params.height = height;
                scroll.setLayoutParams(params);
            }
        });
    }
    @Override protected void onResume() {
        super.onResume();
        if (model != null) model.reload();
    }
    @Override protected void onDestroy() {
        if (retry != null) retry.dismiss();
        super.onDestroy();
    }
}
