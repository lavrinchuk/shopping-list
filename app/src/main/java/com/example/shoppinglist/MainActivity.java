package com.example.shoppinglist;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
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
import com.example.shoppinglist.data.ReceiptAttachment;
import com.example.shoppinglist.data.ShoppingRepository;
import com.example.shoppinglist.model.ItemLink;
import com.example.shoppinglist.model.ProductBarcode;
import com.example.shoppinglist.model.ShoppingItem;
import com.example.shoppinglist.ui.AdaptiveFormLayoutController;
import com.example.shoppinglist.voice.VoiceRecognitionResult;
import com.example.shoppinglist.web.ProductSearchUrl;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    static final String EXTRA_LIST_ID = "listId";
    static final String EXTRA_OFFER_RECEIPT = "offerReceipt";
    private static final String SEARCH_PREFERENCES = "purchase_search";
    private static final String SEARCH_REGION_KEY = "last_selected_region";
    ShoppingViewModel viewModel;
    ShoppingAdapter adapter;
    private Snackbar retry;
    private boolean rendering;
    private boolean scanningBarcode;
    private androidx.appcompat.app.AlertDialog receiptOfferDialog;
    private final ActivityResultLauncher<PickVisualMediaRequest> receiptPicker =
            registerForActivityResult(
                    new ActivityResultContracts.PickMultipleVisualMedia(10),
                    uris -> {
                        if (uris != null && !uris.isEmpty() && viewModel != null) {
                            viewModel.attachReceipts(uris);
                        }
                    });
    private final ActivityResultLauncher<String[]> receiptPdfPicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null && viewModel != null) {
                            viewModel.attachReceipts(Collections.singletonList(uri));
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
        if (getIntent().getBooleanExtra(EXTRA_OFFER_RECEIPT, false) && savedInstanceState == null) {
            getIntent().removeExtra(EXTRA_OFFER_RECEIPT);
            viewModel.requestReceiptOffer();
        }
        observeLifecycleForSharing();
        configureItemsList();
        configureItemForm();
        configureFinishAction();
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
            return false;
        });
    }

    private void openReceiptPicker() {
        if (viewModel.list == null || viewModel.list.archivedAt == null || viewModel.receiptBusy) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.receipt_add_title)
                .setItems(
                        new String[] {
                            getString(R.string.receipt_choose_photos),
                            getString(R.string.receipt_choose_pdf)
                        },
                        (dialog, which) -> {
                            if (which == 0) {
                                receiptPicker.launch(
                                        new PickVisualMediaRequest.Builder()
                                                .setMediaType(
                                                        ActivityResultContracts.PickVisualMedia
                                                                .ImageOnly.INSTANCE)
                                                .build());
                            } else {
                                receiptPdfPicker.launch(new String[] {"application/pdf"});
                            }
                        })
                .show();
    }

    private void confirmReceiptRemoval(ReceiptAttachment receipt) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.receipt_remove_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.receipt_remove,
                        (dialog, which) -> viewModel.removeReceipt(receipt))
                .show();
    }

    private void showReceiptOfferIfNeeded() {
        if (!viewModel.receiptOfferPending
                || viewModel.list == null
                || viewModel.list.archivedAt == null
                || receiptOfferDialog != null
                || isFinishing()) {
            return;
        }
        receiptOfferDialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.receipt_offer_title)
                        .setMessage(R.string.receipt_offer_message)
                        .setNegativeButton(
                                R.string.receipt_offer_skip,
                                (dialog, which) -> {
                                    viewModel.consumeReceiptOffer();
                                    finish();
                                })
                        .setPositiveButton(
                                R.string.receipt_attach,
                                (dialog, which) -> {
                                    viewModel.consumeReceiptOffer();
                                    openReceiptPicker();
                                })
                        .create();
        receiptOfferDialog.setOnCancelListener(dialog -> {
            viewModel.consumeReceiptOffer();
            finish();
        });
        receiptOfferDialog.setOnDismissListener(dialog -> receiptOfferDialog = null);
        receiptOfferDialog.show();
    }

    private int receiptPixels(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void showReceiptGallery() {
        List<ReceiptAttachment> receipts = viewModel.receiptAttachments;
        if (receipts.isEmpty()) {
            openReceiptPicker();
            return;
        }
        if (receipts.size() == 1) {
            showReceiptPreview(receipts.get(0));
            return;
        }
        LinearLayout gallery = new LinearLayout(this);
        gallery.setOrientation(LinearLayout.VERTICAL);
        gallery.setPadding(receiptPixels(16), receiptPixels(8), receiptPixels(16), 0);
        for (int i = 0; i < receipts.size(); i++) {
            ReceiptAttachment attachment = receipts.get(i);
            LinearLayout entry = new LinearLayout(this);
            entry.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(this);
            title.setText(
                    getString(
                            attachment.isPdf()
                                    ? R.string.receipt_pdf_number
                                    : R.string.receipt_photo_number,
                            i + 1));
            entry.addView(title);
            ImageView thumbnail = new ImageView(this);
            thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumbnail.setContentDescription(title.getText());
            entry.addView(
                    thumbnail,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, receiptPixels(108)));
            MaterialButton open = new MaterialButton(this);
            open.setText(R.string.receipt_view);
            entry.addView(open);
            gallery.addView(entry);
            viewModel.previewReceipt(
                    attachment,
                    bitmap -> {
                        if (!isDestroyed() && thumbnail.isAttachedToWindow() && bitmap != null) {
                            thumbnail.setImageBitmap(bitmap);
                        } else if (bitmap != null) {
                            bitmap.recycle();
                        }
                    });
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(gallery);
        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.receipt_gallery_title)
                        .setView(scroll)
                        .setNegativeButton(R.string.back, null)
                        .setPositiveButton(
                                R.string.receipt_add_more, (d, which) -> openReceiptPicker())
                        .create();
        for (int i = 0; i < receipts.size(); i++) {
            ReceiptAttachment current = receipts.get(i);
            LinearLayout entry = (LinearLayout) gallery.getChildAt(i);
            MaterialButton open = (MaterialButton) entry.getChildAt(2);
            open.setOnClickListener(view -> {
                dialog.dismiss();
                showReceiptPreview(current);
            });
        }
        dialog.show();
    }

    void showReceiptPreview(ReceiptAttachment receipt) {
        ImageView image = new ImageView(this);
        int padding = receiptPixels(16);
        image.setPadding(padding, padding, padding, padding);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // The scroll area has a measured height inside a vertical container.
        // The fixed action panel is a sibling, so a tall image cannot push it off screen.
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, 0, padding, padding);

        ScrollView photoScroll = new ScrollView(this);
        photoScroll.setFillViewport(false);
        photoScroll.addView(
                image,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT));
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int previewHeight = Math.min((int) (screenHeight * 0.38f), receiptPixels(360));
        content.addView(
                photoScroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, previewHeight));

        MaterialButton more = new MaterialButton(this);
        more.setId(R.id.receipt_preview_more);
        more.setText(receipt.isPdf() ? R.string.receipt_open_pdf : R.string.receipt_add_more);
        content.addView(
                more,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton remove = new MaterialButton(this);
        remove.setId(R.id.receipt_preview_remove);
        remove.setText(R.string.receipt_remove);
        actions.addView(
                remove,
                new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton back = new MaterialButton(this);
        back.setId(R.id.receipt_preview_back);
        back.setText(R.string.back);
        actions.addView(
                back,
                new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(actions);

        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(
                                receipt.isPdf()
                                        ? R.string.receipt_pdf_preview
                                        : R.string.receipt_title)
                        .setView(content)
                        .create();
        back.setOnClickListener(view -> dialog.dismiss());
        remove.setOnClickListener(view -> {
            dialog.dismiss();
            confirmReceiptRemoval(receipt);
        });
        more.setOnClickListener(view -> {
            dialog.dismiss();
            if (receipt.isPdf()) {
                openReceiptPdf(receipt);
            } else {
                openReceiptPicker();
            }
        });
        dialog.show();
        viewModel.previewReceipt(
                receipt,
                bitmap -> {
                    if (!dialog.isShowing() || isDestroyed()) {
                        if (bitmap != null) {
                            bitmap.recycle();
                        }
                    } else if (bitmap != null) {
                        image.setImageBitmap(bitmap);
                    } else {
                        Toast.makeText(this, R.string.receipt_unavailable, Toast.LENGTH_LONG)
                                .show();
                        dialog.dismiss();
                    }
                });
    }

    private void openReceiptPdf(ReceiptAttachment receipt) {
        if (!receipt.isPdf() || !receipt.fileName.matches("[0-9a-f-]{36}\\.pdf")) {
            return;
        }
        java.io.File file =
                new java.io.File(new java.io.File(getFilesDir(), "receipts"), receipt.fileName);
        Uri uri =
                androidx.core.content.FileProvider.getUriForFile(
                        this, getPackageName() + ".receipts", file);
        Intent intent =
                new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/pdf")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, R.string.receipt_pdf_unavailable, Toast.LENGTH_LONG).show();
        }
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
                            public void barcode(ShoppingItem item) {
                                if (item.barcode == null) {
                                    scanBarcode(item);
                                } else {
                                    showSavedBarcode(item);
                                }
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

    private void showSavedBarcode(ShoppingItem item) {
        String current = item.barcode;
        if (current == null) {
            return;
        }
        boolean archived = viewModel.list == null || viewModel.list.archivedAt != null;
        View actions = getLayoutInflater().inflate(R.layout.dialog_barcode_actions, null);
        com.google.android.material.button.MaterialButton save =
                actions.findViewById(R.id.barcode_dialog_save);
        com.google.android.material.button.MaterialButton copy =
                actions.findViewById(R.id.barcode_dialog_copy);
        com.google.android.material.button.MaterialButton search =
                actions.findViewById(R.id.barcode_dialog_search);
        com.google.android.material.button.MaterialButton rescan =
                actions.findViewById(R.id.barcode_dialog_rescan);
        save.setVisibility(View.GONE);
        rescan.setVisibility(archived ? View.GONE : View.VISIBLE);
        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.barcode_code_title, current))
                        .setView(actions)
                        .setNegativeButton(R.string.back, null)
                        .create();
        copy.setOnClickListener(view -> {
            dialog.dismiss();
            copyBarcode(current);
        });
        search.setOnClickListener(view -> {
            dialog.dismiss();
            openBarcodeSearch(current);
        });
        rescan.setOnClickListener(view -> {
            dialog.dismiss();
            scanBarcode(item);
        });
        dialog.show();
    }

    private void copyBarcode(String code) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.barcode_label), code));
            Toast.makeText(this, R.string.barcode_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void scanBarcode(ShoppingItem item) {
        if (scanningBarcode || viewModel.list == null || viewModel.list.archivedAt != null) {
            return;
        }
        scanningBarcode = true;
        GmsBarcodeScanner scanner =
                GmsBarcodeScanning.getClient(
                        this, new GmsBarcodeScannerOptions.Builder().enableAutoZoom().build());
        scanner.startScan()
                .addOnSuccessListener(barcode -> scannedBarcode(item, barcode.getRawValue()))
                .addOnCanceledListener(() -> scanningBarcode = false)
                .addOnFailureListener(error -> barcodeScanFailed());
    }

    private void scannedBarcode(ShoppingItem item, String rawValue) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        scanningBarcode = false;
        String value;
        try {
            value = ProductBarcode.normalize(rawValue);
        } catch (IllegalArgumentException error) {
            value = null;
        }
        if (value == null) {
            Toast.makeText(this, R.string.barcode_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        confirmScannedBarcode(item, value);
    }

    private void barcodeScanFailed() {
        if (!isFinishing() && !isDestroyed()) {
            scanningBarcode = false;
            Toast.makeText(this, R.string.barcode_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    void confirmScannedBarcode(ShoppingItem item, String value) {
        if (viewModel.list == null || viewModel.list.archivedAt != null) {
            return;
        }
        View actions = getLayoutInflater().inflate(R.layout.dialog_barcode_actions, null);
        com.google.android.material.button.MaterialButton save =
                actions.findViewById(R.id.barcode_dialog_save);
        com.google.android.material.button.MaterialButton copy =
                actions.findViewById(R.id.barcode_dialog_copy);
        com.google.android.material.button.MaterialButton search =
                actions.findViewById(R.id.barcode_dialog_search);
        actions.findViewById(R.id.barcode_dialog_rescan).setVisibility(View.GONE);
        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.barcode_scanned_code_title, value))
                        .setView(actions)
                        .setNegativeButton(R.string.cancel, null)
                        .create();
        save.setOnClickListener(view -> {
            dialog.dismiss();
            viewModel.updateItemBarcode(item, value);
        });
        copy.setOnClickListener(view -> {
            dialog.dismiss();
            copyBarcode(value);
        });
        search.setOnClickListener(view -> {
            dialog.dismiss();
            openBarcodeSearch(value);
        });
        dialog.show();
    }

    private void openBarcodeSearch(String code) {
        Intent search =
                new Intent(Intent.ACTION_VIEW, Uri.parse(ProductSearchUrl.forBarcode(code)));
        try {
            startActivity(search);
            copyBarcode(code);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, R.string.search_unavailable, Toast.LENGTH_LONG).show();
        }
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

    private void configureFinishAction() {
        findViewById(R.id.finish_list).setOnClickListener(view -> onFinishClicked());
        findViewById(R.id.receipt_bottom_action).setOnClickListener(view -> onReceiptClicked());
    }

    private void onReceiptClicked() {
        if (viewModel.list == null || viewModel.receiptBusy) {
            return;
        }
        showReceiptGallery();
    }

    private void onFinishClicked() {
        if (viewModel.list == null || viewModel.finishing) {
            return;
        }
        if (viewModel.list.total == 0) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_empty_list)
                    .setMessage(R.string.delete_empty_list_confirmation)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(
                            R.string.delete_empty_list,
                            (dialog, which) -> viewModel.finishShopping(false, true))
                    .show();
        } else if (viewModel.list.purchased < viewModel.list.total) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.finish_shopping)
                    .setMessage(R.string.incomplete_confirmation)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(
                            R.string.finish_shopping,
                            (dialog, which) -> viewModel.finishShopping(true, false))
                    .show();
        } else {
            viewModel.finishShopping(false, false);
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
        if (viewModel.closeAfterFinish) {
            finish();
            return;
        }
        EditText name = findViewById(R.id.name);
        EditText quantity = findViewById(R.id.quantity);
        renderDraft(name, quantity);
        findViewById(R.id.add).setEnabled(!viewModel.adding);
        renderCurrentList();
        renderItems();
        showPendingError();
        synchronizeReadError();
        showReceiptOfferIfNeeded();
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
            com.google.android.material.button.MaterialButton finishButton =
                    findViewById(R.id.finish_list);
            finishButton.setVisibility(
                    !readOnly && !viewModel.list.isWish() ? View.VISIBLE : View.GONE);
            finishButton.setText(
                    viewModel.list.total == 0
                            ? R.string.delete_empty_list
                            : R.string.finish_shopping);
            finishButton.setEnabled(
                    !viewModel.finishing && !viewModel.adding && !viewModel.receiptOfferPending);
            com.google.android.material.button.MaterialButton receiptButton =
                    findViewById(R.id.receipt_bottom_action);
            receiptButton.setVisibility(readOnly ? View.VISIBLE : View.GONE);
            int receiptCount = viewModel.receiptAttachments.size();
            receiptButton.setText(
                    receiptCount == 0
                            ? getString(R.string.receipt_attach)
                            : getString(R.string.receipt_count_button, receiptCount));
            receiptButton.setEnabled(!viewModel.receiptBusy);
        }
        toolbar.getMenu().findItem(R.id.share_list).setEnabled(!viewModel.sharing);
        android.view.MenuItem receipt = toolbar.getMenu().findItem(R.id.receipt_actions);
        receipt.setVisible(false);
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
        if (error == R.string.finish_confirmation_needed) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        }
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
        if (receiptOfferDialog != null) {
            receiptOfferDialog.setOnCancelListener(null);
            receiptOfferDialog.setOnDismissListener(null);
            receiptOfferDialog.dismiss();
            receiptOfferDialog = null;
        }
        if (retry != null) {
            retry.dismiss();
        }
        super.onDestroy();
    }
}
