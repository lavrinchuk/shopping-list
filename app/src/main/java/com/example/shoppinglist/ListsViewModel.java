package com.example.shoppinglist;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import java.util.ArrayList;
import java.util.List;

final class ListsViewModel extends ViewModel {
    final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    final boolean archived;
    final SavedStateHandle saved;
    private final ShoppingRepository repository;
    List<ShoppingList> lists = new ArrayList<>();
    boolean busy;
    boolean loading = true;
    boolean readFailed;
    int error;
    boolean cleared;
    Long confirmation;

    ListsViewModel(ShoppingRepository repository, SavedStateHandle saved, boolean archived) {
        this.repository = repository; this.saved = saved; this.archived = archived;
    }
    void reload() { loading = true; publish(); repository.lists(archived, result(false)); }
    void saveName(long id, String name) {
        if (busy) return;
        String clean = ShoppingList.cleanName(name);
        busy = true; publish();
        if (id == 0) repository.create(clean, result(true));
        else repository.rename(id, clean, result(true));
    }
    void archive(ShoppingList list) {
        if (busy) return;
        if (list.total > list.purchased) { confirmation = list.id; publish(); }
        else archive(list.id, false);
    }
    void archive(long id, boolean confirmed) {
        if (busy) return;
        confirmation = null; busy = true; publish();
        repository.archive(id, confirmed, new ShoppingRepository.Result() {
            final ShoppingRepository.Result delegate = result(false);
            @Override public void written() { delegate.written(); }
            @Override public void loaded(List<ShoppingItem> items) { }
            @Override public void listsLoaded(List<ShoppingList> lists) { delegate.listsLoaded(lists); }
            @Override public void failed(boolean reading) { delegate.failed(reading); }
            @Override public void confirmationRequired() {
                if (cleared) return;
                busy = false; confirmation = id; publish();
            }
        });
    }
    void restore(long id) {
        if (busy) return;
        busy = true; publish(); repository.restore(id, result(false));
    }
    private ShoppingRepository.Result result(boolean naming) {
        return new ShoppingRepository.Result() {
            @Override public void written() {
                if (cleared) return;
                busy = false;
                if (naming) saved.set("editing", false);
                publish();
            }
            @Override public void loaded(List<ShoppingItem> ignored) { }
            @Override public void listsLoaded(List<ShoppingList> updated) {
                if (cleared) return;
                lists = updated; loading = false; readFailed = false; publish();
            }
            @Override public void failed(boolean reading) {
                if (cleared) return;
                busy = false; loading = false;
                if (reading) readFailed = true;
                error = reading ? R.string.read_error : R.string.write_error; publish();
            }
        };
    }
    void publish() { changes.setValue(changes.getValue() + 1); }
    @Override protected void onCleared() { cleared = true; }
}
