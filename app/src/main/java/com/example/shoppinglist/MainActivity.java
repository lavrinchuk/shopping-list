package com.example.shoppinglist;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
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
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ShoppingRepository repository = ((ShoppingApplication) getApplication()).repository();
        model = new ViewModelProvider(this, new AbstractSavedStateViewModelFactory(this, savedInstanceState) {
            @NonNull @Override protected <T extends ViewModel> T create(@NonNull String key,
                    @NonNull Class<T> type, @NonNull SavedStateHandle state) {
                return type.cast(new ShoppingViewModel(repository, state));
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
        // Restore the draft from the retained model, not stale framework View state.
        name.setSaveEnabled(false);
        quantity.setSaveEnabled(false);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!rendering) model.draft(name.getText().toString(), quantity.getText().toString());
            }
            @Override public void afterTextChanged(Editable text) { }
        };
        name.addTextChangedListener(watcher);
        quantity.addTextChangedListener(watcher);
        View add = findViewById(R.id.add);
        add.setOnClickListener(view -> {
            try { model.add(); }
            catch (IllegalArgumentException error) {
                name.setError(getString(R.string.name_required));
                name.requestFocus();
            }
        });
        model.changes.observe(this, ignored -> {
            rendering = true;
            if (!name.getText().toString().equals(model.name)) name.setText(model.name);
            if (!quantity.getText().toString().equals(model.quantity)) quantity.setText(model.quantity);
            rendering = false;
            add.setEnabled(!model.adding);
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
    @Override protected void onDestroy() {
        if (retry != null) retry.dismiss();
        super.onDestroy();
    }
}
