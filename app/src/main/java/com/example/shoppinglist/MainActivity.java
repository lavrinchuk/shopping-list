package com.example.shoppinglist;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.shoppinglist.data.ShoppingRepository;
import com.example.shoppinglist.model.ShoppingItem;
import com.example.shoppinglist.ui.AdaptiveFormLayoutController;
import com.example.shoppinglist.web.ProductSearchUrl;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity {
    static final String EXTRA_LIST_ID = "listId";
    private static final String SEARCH_PREFERENCES = "purchase_search";
    private static final String SEARCH_REGION_KEY = "last_selected_region";
    ShoppingViewModel viewModel;
    ShoppingAdapter adapter;
    private Snackbar retry;
    private boolean rendering;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        configureSystemBars();
        setContentView(R.layout.activity_main);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.heading), true);
        applyWindowInsets();
        long listId = getIntent().getLongExtra(EXTRA_LIST_ID, -1);
        if (listId <= 0) {
            startActivity(new android.content.Intent(this, ListsActivity.class));
            finish();
            return;
        }
        configureToolbar();
        initializeViewModel(savedInstanceState, listId);
        observeLifecycleForSharing();
        configureItemsList();
        configureItemForm();
        viewModel.changes.observe(this, ignored -> render());
    }

    private void configureSystemBars() {
        boolean dark =
                (getResources().getConfiguration().uiMode
                                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.core.view.WindowInsetsControllerCompat systemBarController =
                androidx.core.view.WindowCompat.getInsetsController(
                        getWindow(), getWindow().getDecorView());
        systemBarController.setAppearanceLightStatusBars(!dark);
        systemBarController.setAppearanceLightNavigationBars(!dark);
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (view, insets) -> {
                    Insets bars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.ime());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }

    private void configureToolbar() {
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.heading);
        toolbar.setNavigationOnClickListener(view -> finish());
        toolbar.inflateMenu(R.menu.shopping_list);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() != R.id.share_list) {
                return false;
            }
            viewModel.share();
            return true;
        });
    }

    private void initializeViewModel(Bundle savedInstanceState, long listId) {
        ShoppingRepository repository = ((ShoppingApplication) getApplication()).repository();
        viewModel =
                new ViewModelProvider(
                                this,
                                new AbstractSavedStateViewModelFactory(this, savedInstanceState) {
                                    @NonNull
                                    @Override
                                    protected <T extends ViewModel> T create(
                                            @NonNull String key,
                                            @NonNull Class<T> type,
                                            @NonNull SavedStateHandle state) {
                                        return type.cast(
                                                new ShoppingViewModel(repository, state, listId));
                                    }
                                })
                        .get(ShoppingViewModel.class);
    }

    private void observeLifecycleForSharing() {
        getLifecycle()
                .addObserver(
                        (androidx.lifecycle.LifecycleEventObserver)
                                (source, event) -> {
                                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                        dispatchShare();
                                    }
                                });
    }

    private void configureItemsList() {
        adapter =
                new ShoppingAdapter(
                        new ShoppingAdapter.Actions() {
                            @Override
                            public void purchased(ShoppingItem item, boolean checked) {
                                viewModel.purchased(item, checked);
                            }

                            @Override
                            public void delete(ShoppingItem item) {
                                viewModel.delete(item);
                            }

                            @Override
                            public void search(ShoppingItem item) {
                                searchInBrowser(item);
                            }
                        });
        RecyclerView list = findViewById(R.id.items);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
    }

    private void searchInBrowser(ShoppingItem item) {
        String[] regions = getResources().getStringArray(R.array.purchase_search_regions);
        String previousRegion =
                getSharedPreferences(SEARCH_PREFERENCES, MODE_PRIVATE)
                        .getString(SEARCH_REGION_KEY, "");
        int initialSelection = 0;
        for (int index = 1; index < regions.length; index++) {
            if (regions[index].equals(previousRegion)) {
                initialSelection = index;
                break;
            }
        }
        final int[] selection = {initialSelection};
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.search_region_title, item.name))
                .setSingleChoiceItems(regions, initialSelection, (dialog, which) -> selection[0] = which)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.search_purchase,
                        (dialog, which) -> {
                            String region = selection[0] == 0 ? "" : regions[selection[0]];
                            getSharedPreferences(SEARCH_PREFERENCES, MODE_PRIVATE)
                                    .edit()
                                    .putString(SEARCH_REGION_KEY, region)
                                    .apply();
                            openPurchaseSearch(item.name, region);
                        })
                .show();
    }

    private void openPurchaseSearch(String itemName, String region) {
        Intent search =
                new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(ProductSearchUrl.forPurchase(itemName, region)));
        try {
            startActivity(search);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, R.string.search_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void configureItemForm() {
        EditText name = findViewById(R.id.name);
        EditText quantity = findViewById(R.id.quantity);
        TextInputLayout nameLayout = findViewById(R.id.name_layout);
        new AdaptiveFormLayoutController(findViewById(R.id.main)).attach();
        // Restore the draft from the retained viewModel, not stale framework View state.
        name.setSaveEnabled(false);
        quantity.setSaveEnabled(false);
        installDraftListeners(name, quantity, nameLayout);
        installAddActions(name, quantity, nameLayout);
    }

    private void installDraftListeners(
            EditText name, EditText quantity, TextInputLayout nameLayout) {
        TextWatcher watcher =
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (!rendering) {
                            viewModel.draft(
                                    name.getText().toString(), quantity.getText().toString());
                            if (!name.getText().toString().trim().isEmpty()) {
                                nameLayout.setError(null);
                            }
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable text) {}
                };
        name.addTextChangedListener(watcher);
        quantity.addTextChangedListener(watcher);
    }

    private void installAddActions(EditText name, EditText quantity, TextInputLayout nameLayout) {
        View add = findViewById(R.id.add);
        add.setOnClickListener(view -> {
            try {
                viewModel.add();
            } catch (IllegalArgumentException error) {
                nameLayout.setError(getString(R.string.name_required));
                name.requestFocus();
            }
        });
        quantity.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) {
                return false;
            }
            if (add.isEnabled()) {
                add.performClick();
            }
            return true;
        });
    }

    private void render() {
        EditText name = findViewById(R.id.name);
        EditText quantity = findViewById(R.id.quantity);
        renderDraft(name, quantity);
        findViewById(R.id.add).setEnabled(!viewModel.adding);
        renderCurrentList();
        renderItems();
        showPendingError();
        synchronizeReadError();
    }

    private void renderDraft(EditText name, EditText quantity) {
        rendering = true;
        if (!name.getText().toString().equals(viewModel.name)) {
            boolean cleared = viewModel.name.isEmpty() && !viewModel.adding;
            name.setText(viewModel.name);
            if (cleared) {
                name.requestFocus();
            }
        }
        if (!quantity.getText().toString().equals(viewModel.quantity)) {
            quantity.setText(viewModel.quantity);
        }
        rendering = false;
    }

    private void renderCurrentList() {
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.heading);
        if (viewModel.list != null) {
            toolbar.setTitle(viewModel.list.name);
            boolean readOnly = viewModel.list.archivedAt != null;
            toolbar.setSubtitle(readOnly ? getString(R.string.archive) : null);
            findViewById(R.id.form_scroll).setVisibility(readOnly ? View.GONE : View.VISIBLE);
            adapter.setReadOnly(readOnly);
        }
        toolbar.getMenu().findItem(R.id.share_list).setEnabled(!viewModel.sharing);
        dispatchShare();
    }

    private void renderItems() {
        adapter.submitList(viewModel.items);
        findViewById(R.id.empty)
                .setVisibility(
                        viewModel.items.isEmpty() && !viewModel.readFailed
                                ? View.VISIBLE
                                : View.GONE);
    }

    private void showPendingError() {
        int error = viewModel.error;
        viewModel.consumeError();
        if (error == R.string.share_error) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        }
        if (error == R.string.write_error) {
            adapter.restoreChecks();
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        }
    }

    private void synchronizeReadError() {
        RecyclerView list = findViewById(R.id.items);
        if (viewModel.readFailed && retry == null) {
            retry =
                    Snackbar.make(list, R.string.read_error, Snackbar.LENGTH_INDEFINITE)
                            .setAction(
                                    R.string.retry,
                                    view -> {
                                        retry = null;
                                        viewModel.reload();
                                    });
            retry.show();
        } else if (!viewModel.readFailed && retry != null) {
            retry.dismiss();
            retry = null;
        }
    }

    private void dispatchShare() {
        if (!getLifecycle()
                .getCurrentState()
                .isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            return;
        }
        String text = viewModel.consumeShareText();
        if (text == null) {
            return;
        }
        android.content.Intent send =
                new android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, text);
        try {
            startActivity(
                    android.content.Intent.createChooser(send, getString(R.string.share_list)));
        } catch (android.content.ActivityNotFoundException error) {
            Toast.makeText(this, R.string.share_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.reload();
        }
    }

    @Override
    protected void onDestroy() {
        if (retry != null) {
            retry.dismiss();
        }
        super.onDestroy();
    }
}
