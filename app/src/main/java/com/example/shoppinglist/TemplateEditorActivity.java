package com.example.shoppinglist;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.shoppinglist.data.ShoppingRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;

/** Edits a detached template snapshot; normal shopping lists are never modified here. */
public final class TemplateEditorActivity extends AppCompatActivity {
    public static final String EXTRA_TEMPLATE_ID = "templateId";
    public static final String EXTRA_TEMPLATE_NAME = "templateName";

    private static final String STATE_READY = "ready";
    private static final String STATE_NAME = "name";
    private static final String STATE_ITEMS = "items";
    private static final String STATE_DRAFT_NAME = "draftName";
    private static final String STATE_DRAFT_QUANTITY = "draftQuantity";
    private static final String STATE_EDITING_INDEX = "editingIndex";

    private final ArrayList<String[]> products = new ArrayList<>();
    private long templateId;
    private boolean ready;
    private boolean saving;
    private int editingIndex = -1;
    private ShoppingRepository repository;
    private EditText nameInput;
    private EditText itemInput;
    private EditText quantityInput;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_template_editor);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.template_editor_root),
                (view, insets) -> {
                    androidx.core.graphics.Insets padding =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.ime());
                    view.setPadding(padding.left, padding.top, padding.right, padding.bottom);
                    return insets;
                });

        templateId = getIntent().getLongExtra(EXTRA_TEMPLATE_ID, -1L);
        if (templateId <= 0) {
            finish();
            return;
        }
        repository = ((ShoppingApplication) getApplication()).repository();
        nameInput = findViewById(R.id.template_name_input);
        itemInput = findViewById(R.id.template_product_input);
        quantityInput = findViewById(R.id.template_quantity_input);

        MaterialToolbar toolbar = findViewById(R.id.template_editor_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        findViewById(R.id.template_add_item).setOnClickListener(view -> applyItem());
        findViewById(R.id.template_save).setOnClickListener(view -> save());

        if (state != null && state.getBoolean(STATE_READY)) {
            ready = true;
            nameInput.setText(state.getString(STATE_NAME, ""));
            ArrayList<String> encoded = state.getStringArrayList(STATE_ITEMS);
            if (encoded != null) {
                for (int index = 0; index + 1 < encoded.size(); index += 2) {
                    products.add(new String[] {encoded.get(index), encoded.get(index + 1)});
                }
            }
            itemInput.setText(state.getString(STATE_DRAFT_NAME, ""));
            quantityInput.setText(state.getString(STATE_DRAFT_QUANTITY, ""));
            editingIndex = state.getInt(STATE_EDITING_INDEX, -1);
            showReady();
        } else {
            nameInput.setText(getIntent().getStringExtra(EXTRA_TEMPLATE_NAME));
            loadTemplate();
        }
    }

    private void loadTemplate() {
        repository.templateItems(
                templateId,
                new ShoppingRepository.TemplateItemsResult() {
                    @Override
                    public void loaded(List<String[]> items) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        products.clear();
                        for (String[] item : items) {
                            products.add(new String[] {item[0], item[1]});
                        }
                        ready = true;
                        showReady();
                    }

                    @Override
                    public void failed() {
                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(
                                            TemplateEditorActivity.this,
                                            R.string.read_error,
                                            Toast.LENGTH_LONG)
                                    .show();
                            finish();
                        }
                    }
                });
    }

    private void showReady() {
        findViewById(R.id.template_editor_loading).setVisibility(View.GONE);
        findViewById(R.id.template_save).setEnabled(!saving);
        findViewById(R.id.template_add_item).setEnabled(!saving);
        renderProducts();
    }

    private void renderProducts() {
        LinearLayout container = findViewById(R.id.template_editor_products);
        container.removeAllViews();
        int iconSize = Math.round(48 * getResources().getDisplayMetrics().density);
        for (int index = 0; index < products.size(); index++) {
            final int position = index;
            String[] item = products.get(index);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(iconSize + iconSize / 3);
            TextView label = new TextView(this);
            label.setText(item[1].isEmpty() ? item[0] : item[0] + " · " + item[1]);
            label.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(
                    label,
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageButton edit =
                    action(
                            R.drawable.ic_edit,
                            getString(R.string.template_edit_item_action, item[0]));
            ImageButton delete =
                    action(
                            R.drawable.ic_delete,
                            getString(R.string.template_delete_item_action, item[0]));
            edit.setEnabled(!saving);
            delete.setEnabled(!saving);
            row.addView(edit, new LinearLayout.LayoutParams(iconSize, iconSize));
            row.addView(delete, new LinearLayout.LayoutParams(iconSize, iconSize));
            edit.setOnClickListener(view -> {
                editingIndex = position;
                itemInput.setText(item[0]);
                quantityInput.setText(item[1]);
                itemInput.requestFocus();
            });
            delete.setOnClickListener(view -> {
                products.remove(position);
                if (editingIndex == position) {
                    editingIndex = -1;
                    itemInput.setText("");
                    quantityInput.setText("");
                } else if (editingIndex > position) {
                    editingIndex--;
                }
                renderProducts();
            });
            container.addView(row);
        }
        if (products.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.template_requires_items);
            empty.setPadding(0, iconSize / 2, 0, iconSize / 2);
            container.addView(empty);
        }
    }

    private ImageButton action(int drawable, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable);
        button.setContentDescription(description);
        android.util.TypedValue ripple = new android.util.TypedValue();
        if (getTheme()
                        .resolveAttribute(
                                android.R.attr.selectableItemBackgroundBorderless, ripple, true)
                && ripple.resourceId != 0) {
            button.setBackgroundResource(ripple.resourceId);
        }
        button.setImageTintList(
                android.content.res.ColorStateList.valueOf(
                        com.google.android.material.color.MaterialColors.getColor(
                                button, com.google.android.material.R.attr.colorOnSurface)));
        return button;
    }

    private void applyItem() {
        if (!ready || saving) {
            return;
        }
        String name = itemInput.getText().toString().trim();
        if (name.isEmpty()) {
            itemInput.setError(getString(R.string.list_name_required));
            return;
        }
        String quantity = quantityInput.getText().toString().trim();
        if (editingIndex >= 0 && editingIndex < products.size()) {
            products.set(editingIndex, new String[] {name, quantity});
        } else {
            products.add(new String[] {name, quantity});
        }
        editingIndex = -1;
        itemInput.setText("");
        quantityInput.setText("");
        renderProducts();
    }

    private void save() {
        if (!ready || saving) {
            return;
        }
        String title = nameInput.getText().toString().trim();
        if (title.isEmpty()) {
            nameInput.setError(getString(R.string.list_name_required));
            return;
        }
        if (products.isEmpty()) {
            Toast.makeText(this, R.string.template_requires_items, Toast.LENGTH_SHORT).show();
            return;
        }
        if (editingIndex >= 0 || !itemInput.getText().toString().trim().isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.template_unsaved_item)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.template_discard_item,
                            (dialog, which) -> persist(title))
                    .show();
            return;
        }
        persist(title);
    }

    private void persist(String title) {
        saving = true;
        findViewById(R.id.template_save).setEnabled(false);
        findViewById(R.id.template_add_item).setEnabled(false);
        renderProducts();
        List<String[]> snapshot = new ArrayList<>();
        for (String[] item : products) {
            snapshot.add(new String[] {item[0], item[1]});
        }
        repository.updateTemplate(
                templateId, title, snapshot,
                new ShoppingRepository.Result() {
                    @Override
                    public void written() {
                        if (!isFinishing() && !isDestroyed()) {
                            setResult(RESULT_OK);
                            finish();
                        }
                    }

                    @Override
                    public void loaded(List<com.example.shoppinglist.model.ShoppingItem> ignored) {}

                    @Override
                    public void failed(boolean reading) {
                        if (!isFinishing() && !isDestroyed()) {
                            saving = false;
                            showReady();
                            Toast.makeText(
                                            TemplateEditorActivity.this,
                                            reading ? R.string.read_error : R.string.write_error,
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    }
                });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(STATE_READY, ready);
        if (ready) {
            outState.putString(STATE_NAME, nameInput.getText().toString());
            ArrayList<String> encoded = new ArrayList<>();
            for (String[] item : products) {
                encoded.add(item[0]);
                encoded.add(item[1]);
            }
            outState.putStringArrayList(STATE_ITEMS, encoded);
            outState.putString(STATE_DRAFT_NAME, itemInput.getText().toString());
            outState.putString(STATE_DRAFT_QUANTITY, quantityInput.getText().toString());
            outState.putInt(STATE_EDITING_INDEX, editingIndex);
        }
        super.onSaveInstanceState(outState);
    }
}
