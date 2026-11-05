package com.example.shoppinglist;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.shoppinglist.data.ShoppingRepository;
import com.example.shoppinglist.model.ShoppingList;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ListsActivity extends AppCompatActivity {
    private static final String EXTRA_ARCHIVED = "archived";
    ListsViewModel viewModel;
    private ListsAdapter adapter;
    private AlertDialog nameDialog;
    private AlertDialog confirmationDialog;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_lists);
        applyWindowInsets();

        boolean archived = getIntent().getBooleanExtra(EXTRA_ARCHIVED, false);
        initializeViewModel(state, archived);
        configureToolbar(archived);
        configureActions(archived);
        configureLists();
        viewModel.changes.observe(this, ignored -> render());
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.lists_root),
                (view, insets) -> {
                    androidx.core.graphics.Insets bars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.ime());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }

    private void initializeViewModel(Bundle state, boolean archived) {
        ShoppingRepository repository = ((ShoppingApplication) getApplication()).repository();
        viewModel =
                new ViewModelProvider(
                                this,
                                new AbstractSavedStateViewModelFactory(this, state) {
                                    @NonNull
                                    @Override
                                    protected <T extends ViewModel> T create(
                                            @NonNull String key,
                                            @NonNull Class<T> type,
                                            @NonNull SavedStateHandle saved) {
                                        return type.cast(
                                                new ListsViewModel(repository, saved, archived));
                                    }
                                })
                        .get(ListsViewModel.class);
    }

    private void configureToolbar(boolean archived) {
        MaterialToolbar toolbar = findViewById(R.id.lists_toolbar);
        toolbar.setTitle(archived ? R.string.archive : R.string.all_lists);
        if (archived) {
            toolbar.setNavigationIcon(R.drawable.ic_back);
            toolbar.setNavigationContentDescription(R.string.back);
            toolbar.setNavigationOnClickListener(view -> finish());
        }
    }

    private void configureActions(boolean archived) {
        findViewById(R.id.open_archive).setVisibility(archived ? View.GONE : View.VISIBLE);
        findViewById(R.id.open_archive)
                .setOnClickListener(view ->
                        startActivity(
                                new Intent(this, ListsActivity.class)
                                        .putExtra(EXTRA_ARCHIVED, true)));
        findViewById(R.id.create_list).setVisibility(archived ? View.GONE : View.VISIBLE);
        findViewById(R.id.create_list).setOnClickListener(view -> edit(null));
        findViewById(R.id.retry_lists).setOnClickListener(view -> viewModel.reload());
    }

    private void configureLists() {
        adapter =
                new ListsAdapter(
                        new ListsAdapter.Actions() {
                            @Override
                            public void open(ShoppingList list) {
                                startActivity(
                                        new Intent(ListsActivity.this, MainActivity.class)
                                                .putExtra(MainActivity.EXTRA_LIST_ID, list.id));
                            }

                            @Override
                            public void menu(View anchor, ShoppingList list) {
                                showMenu(anchor, list);
                            }
                        });
        RecyclerView lists = findViewById(R.id.lists);
        lists.setLayoutManager(new LinearLayoutManager(this));
        lists.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.reload();
        }
    }

    private void edit(ShoppingList list) {
        if (viewModel.busy) {
            return;
        }
        viewModel.saved.set(ListsViewModel.STATE_EDIT_ID, list == null ? 0L : list.id);
        viewModel.saved.set(ListsViewModel.STATE_EDIT_NAME, list == null ? "" : list.name);
        viewModel.saved.set(
                ListsViewModel.STATE_EDIT_TYPE,
                list == null ? ShoppingList.TYPE_SHOPPING : list.type);
        viewModel.saved.set(ListsViewModel.STATE_EDITING, true);
        viewModel.notifyStateChanged();
    }

    private void showMenu(View anchor, ShoppingList list) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (viewModel.archived) {
            menu.getMenu()
                    .add(R.string.restore_list)
                    .setOnMenuItemClickListener(item -> {
                        viewModel.restore(list.id);
                        return true;
                    });
        } else {
            menu.getMenu()
                    .add(R.string.rename_list)
                    .setOnMenuItemClickListener(item -> {
                        edit(list);
                        return true;
                    });
            menu.getMenu()
                    .add(list.isWish() ? R.string.finish_wish_list : R.string.finish_shopping)
                    .setOnMenuItemClickListener(item -> {
                        viewModel.archive(list);
                        return true;
                    });
        }
        for (int i = 0; i < menu.getMenu().size(); i++) {
            menu.getMenu().getItem(i).setEnabled(!viewModel.busy);
        }
        menu.show();
    }

    private void render() {
        renderListState();
        showErrorIfNeeded();
        synchronizeNameDialog();
        showArchiveConfirmationIfNeeded();
    }

    private void renderListState() {
        adapter.submitList(viewModel.lists);
        findViewById(R.id.create_list).setEnabled(!viewModel.busy);
        findViewById(R.id.lists_loading)
                .setVisibility(viewModel.loading ? View.VISIBLE : View.GONE);
        findViewById(R.id.retry_lists)
                .setVisibility(viewModel.readFailed ? View.VISIBLE : View.GONE);
        android.widget.TextView empty = findViewById(R.id.lists_empty);
        empty.setText(viewModel.archived ? R.string.no_archive : R.string.no_lists);
        empty.setVisibility(
                !viewModel.loading && !viewModel.readFailed && viewModel.lists.isEmpty()
                        ? View.VISIBLE
                        : View.GONE);
    }

    private void showErrorIfNeeded() {
        if (viewModel.error != 0) {
            Toast.makeText(this, viewModel.error, Toast.LENGTH_LONG).show();
            viewModel.error = 0;
        }
    }

    private void synchronizeNameDialog() {
        boolean editing = Boolean.TRUE.equals(viewModel.saved.get(ListsViewModel.STATE_EDITING));
        if (editing && nameDialog == null) {
            showNameDialog();
        }
        if (!editing && nameDialog != null) {
            nameDialog.dismiss();
            nameDialog = null;
        }
        if (nameDialog != null) {
            nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!viewModel.busy);
        }
    }

    private void showArchiveConfirmationIfNeeded() {
        if (viewModel.confirmation != null && confirmationDialog == null) {
            long id = viewModel.confirmation;
            confirmationDialog =
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.finish_shopping)
                            .setMessage(R.string.incomplete_confirmation)
                            .setNegativeButton(
                                    R.string.cancel,
                                    (dialog, which) -> viewModel.confirmation = null)
                            .setPositiveButton(
                                    R.string.finish_shopping,
                                    (dialog, which) -> viewModel.archive(id, true))
                            .create();
            confirmationDialog.setOnCancelListener(dialog -> viewModel.confirmation = null);
            confirmationDialog.setOnDismissListener(dialog -> confirmationDialog = null);
            confirmationDialog.show();
        }
    }

    private void showNameDialog() {
        TextInputLayout field = new TextInputLayout(this);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        field.setPadding(pad, 0, pad, 0);
        field.setHint(getString(R.string.list_name_hint));
        TextInputEditText input = new TextInputEditText(field.getContext());
        input.setSingleLine(true);
        input.setId(R.id.list_name_input);
        field.addView(input);
        input.setText((String) viewModel.saved.get(ListsViewModel.STATE_EDIT_NAME));
        input.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        viewModel.saved.set(ListsViewModel.STATE_EDIT_NAME, s.toString());
                        field.setError(null);
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });
        Long editId = viewModel.saved.get(ListsViewModel.STATE_EDIT_ID);
        nameDialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(
                                editId != null && editId != 0
                                        ? R.string.rename_list
                                        : R.string.new_list)
                        .setView(field)
                        .setNegativeButton(
                                R.string.cancel,
                                (dialog, which) ->
                                        viewModel.saved.set(ListsViewModel.STATE_EDITING, false))
                        .setPositiveButton(R.string.save, null)
                        .create();
        nameDialog.setOnCancelListener(
                dialog -> viewModel.saved.set(ListsViewModel.STATE_EDITING, false));
        nameDialog.setOnDismissListener(dialog -> nameDialog = null);
        nameDialog.setOnShowListener(dialog ->
                nameDialog
                        .getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            try {
                                Editable text = input.getText();
                                viewModel.saveName(
                                        editId == null ? 0 : editId,
                                        text == null ? "" : text.toString());
                            } catch (IllegalArgumentException error) {
                                field.setError(getString(R.string.list_name_required));
                            }
                        }));
        nameDialog.show();
    }

    @Override
    protected void onDestroy() {
        // Detach listeners before dismissing: configuration changes must retain dialog state.
        if (nameDialog != null) {
            nameDialog.setOnDismissListener(null);
            nameDialog.dismiss();
        }
        if (confirmationDialog != null) {
            confirmationDialog.setOnDismissListener(null);
            confirmationDialog.dismiss();
        }
        super.onDestroy();
    }
}
