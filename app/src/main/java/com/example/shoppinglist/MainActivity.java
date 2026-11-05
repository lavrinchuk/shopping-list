package com.example.shoppinglist;

import android.app.Activity;
import android.widget.ImageView;
import androidx.activity.result.PickVisualMediaRequest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.example.shoppinglist.model.ItemLink;
import com.example.shoppinglist.model.ShoppingItem;
import com.example.shoppinglist.ui.AdaptiveFormLayoutController;
import com.example.shoppinglist.voice.VoiceRecognitionResult;
import com.example.shoppinglist.web.ProductSearchUrl;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    static final String EXTRA_LIST_ID = "listId";
    private static final String SEARCH_PREFERENCES = "purchase_search";
    private static final String SEARCH_REGION_KEY = "last_selected_region";
    ShoppingViewModel viewModel;
    ShoppingAdapter adapter;
    private Snackbar retry;
    private boolean rendering;
    private final ActivityResultLauncher<PickVisualMediaRequest> receiptPicker =
            registerForActivityResult(
                    new ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (uri != null && viewModel != null) {
                            viewModel.attachReceipt(uri);
                        }
                    });
    private final ActivityResultLauncher<Intent> speechRecognition =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK
                                || result.getData() == null) {
                            return;
                        }
                        ArrayList<String> matches =
                                result.getData()
                                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches == null || matches.isEmpty()) {
                            showVoiceError();
                            return;
                        }
                        String recognized = VoiceRecognitionResult.firstNonBlank(matches);
                        if (recognized == null) {
                            showVoiceError();
                            return;
                        }
                        confirmVoiceResult(recognized);
                    });

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
            if (item.getItemId() == R.id.share_list) {
                viewModel.share();
                return true;
            }
            if (item.getItemId() == R.id.receipt_actions) {
                showReceiptActions();
                return true;
            }
            return false;
        });
    }

    private void showReceiptActions() {
        if (viewModel.list == null || viewModel.list.archivedAt == null || viewModel.receiptBusy) {
            return;
        }
        boolean attached = viewModel.list.receiptFile != null;
        String[] options =
                attached
                        ? new String[] {
                            getString(R.string.receipt_view),
                            getString(R.string.receipt_replace),
                            getString(R.string.receipt_remove)
                        }
                        : new String[] {getString(R.string.receipt_attach)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.receipt_title)
                .setItems(
                        options,
                        (dialog, which) -> {
                            if (!attached || which == 1) {
                                receiptPicker.launch(
                                        new PickVisualMediaRequest.Builder()
                                                .setMediaType(
                                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                                                .INSTANCE)
                                                .build());
                            } else if (which == 0) {
                                showReceiptPreview();
                            } else if (which == 2) {
                                new MaterialAlertDialogBuilder(this)
                                        .setMessage(R.string.receipt_remove_confirmation)
                                        .setNegativeButton(R.string.cancel, null)
                                        .setPositiveButton(
                                                R.string.receipt_remove,
                                                (confirm, button) -> viewModel.removeReceipt())
                                        .show();
                            }
                        })
                .show();
    }

    private void showReceiptPreview() {
        ImageView image = new ImageView(this);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        image.setPadding(padding, padding, padding, padding);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.receipt_title)
                        .setView(image)
                        .setPositiveButton(R.string.back, null)
                        .create();
        dialog.show();
        viewModel.previewReceipt(
                bitmap -> {
                    if (!dialog.isShowing()) {
                        if (bitmap != null) {
                            bitmap.recycle();
                        }
                        return;
                    }
                    if (bitmap != null) {
                        image.setImageBitmap(bitmap);
                    } else {
                        Toast.makeText(this, R.string.receipt_unavailable, Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    }
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

                            @Override
                            public void editLink(ShoppingItem item) {
                                showLinkEditor(item);
                            }

                            @Override
                            public void openLink(ShoppingItem item) {
                                openItemLink(item);
                            }
                        });
        RecyclerView list = findViewById(R.id.items);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
    }

    private void showLinkEditor(ShoppingItem item) {
        if (viewModel.list == null || viewModel.list.archivedAt != null) {
            return;
        }
        TextInputLayout field = new TextInputLayout(this);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        field.setPadding(pad, 0, pad, 0);
        field.setHint(getString(R.string.item_link_hint));
        TextInputEditText input = new TextInputEditText(field.getContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(item.url == null ? "" : item.url);
        field.addView(input);
        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.item_link_title, item.name))
                        .setView(field)
                        .setNegativeButton(R.string.cancel, null)
                        .setNeutralButton(R.string.remove_item_link, null)
                        .setPositiveButton(R.string.save, null)
                        .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(button -> saveItemLink(item, input, field, dialog));
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(button -> {
                        viewModel.updateItemUrl(item, null);
                        dialog.dismiss();
                    });
        });
        dialog.show();
    }

    private void saveItemLink(
            ShoppingItem item,
            TextInputEditText input,
            TextInputLayout field,
            androidx.appcompat.app.AlertDialog dialog) {
        try {
            String url =
                    ItemLink.normalize(input.getText() == null ? "" : input.getText().toString());
            viewModel.updateItemUrl(item, url);
            dialog.dismiss();
        } catch (IllegalArgumentException error) {
            field.setError(getString(R.string.item_link_invalid));
        }
    }

    private void openItemLink(ShoppingItem item) {
        if (item.url == null) {
            return;
        }
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
        try {
            startActivity(open);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, R.string.item_link_unavailable, Toast.LENGTH_LONG).show();
        }
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
                .setSingleChoiceItems(
                        regions, initialSelection, (dialog, which) -> selection[0] = which)
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
        nameLayout.setEndIconOnClickListener(view -> startVoiceRecognition());
    }

    private void startVoiceRecognition() {
        if (viewModel.list == null || viewModel.list.archivedAt != null) {
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt));
        try {
            speechRecognition.launch(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void confirmVoiceResult(String recognized) {
        if (viewModel.list == null || viewModel.list.archivedAt != null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.voice_confirmation_title)
                .setMessage(recognized)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.voice_use_result,
                        (dialog, which) -> {
                            EditText name = findViewById(R.id.name);
                            name.setText(recognized);
                            name.setSelection(name.length());
                            name.requestFocus();
                        })
                .show();
    }

    private void showVoiceError() {
        Toast.makeText(this, R.string.voice_no_result, Toast.LENGTH_LONG).show();
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
            toolbar.setSubtitle(
                    readOnly
                            ? getString(R.string.archive)
                            : (viewModel.list.isWish()
                                    ? getString(R.string.wish_list_type)
                                    : null));
            findViewById(R.id.form_scroll).setVisibility(readOnly ? View.GONE : View.VISIBLE);
            adapter.setReadOnly(readOnly);
        }
        toolbar.getMenu().findItem(R.id.share_list).setEnabled(!viewModel.sharing);
        android.view.MenuItem receipt = toolbar.getMenu().findItem(R.id.receipt_actions);
        receipt.setVisible(viewModel.list != null && viewModel.list.archivedAt != null);
        receipt.setEnabled(!viewModel.receiptBusy);
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
        if (error == R.string.receipt_error) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        }
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
