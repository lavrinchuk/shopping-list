package com.example.shoppinglist;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import java.util.ArrayList;
import java.util.List;

public final class ShoppingViewModel extends ViewModel {
    final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    private final ShoppingRepository repository;
    private final SavedStateHandle saved;
    final long listId;
    ShoppingList list;
    List<ShoppingItem> items = new ArrayList<>();
    String name = "";
    String quantity = "";
    boolean adding;
    boolean sharing;
    String shareText;
    boolean readFailed;
    int error;
    private long draftRevision;
    private boolean cleared;

    ShoppingViewModel(ShoppingRepository repository, SavedStateHandle saved, long listId) {
        this.repository = repository;
        this.listId = listId;
        this.saved = saved;
        name = saved.contains("name") ? saved.get("name") : "";
        quantity = saved.contains("quantity") ? saved.get("quantity") : "";
        reload();
    }

    void draft(String name, String quantity) {
        if (!this.name.equals(name) || !this.quantity.equals(quantity)) {
            draftRevision++;
        }
        this.name = name;
        this.quantity = quantity;
        saved.set("name", name);
        saved.set("quantity", quantity);
    }

    void add() {
        if (adding || list == null || list.archivedAt != null) {
            return;
        }
        String clean = ShoppingItem.cleanName(name);
        long submittedRevision = draftRevision;
        adding = true;
        publish();
        repository.add(listId, clean, quantity, result(true, submittedRevision));
    }

    void purchased(ShoppingItem item, boolean checked) {
        repository.purchased(listId, item.id, checked, result(false, 0));
    }

    void delete(ShoppingItem item) {
        repository.delete(listId, item.id, result(false, 0));
    }

    void reload() {
        repository.load(listId, result(false, 0));
    }

    // A fresh read on the same queue follows all writes submitted before this action.
    void share() {
        if (sharing || shareText != null) {
            return;
        }
        sharing = true;
        publish();
        repository.load(
                listId,
                new ShoppingRepository.Result() {
                    private ShoppingList snapshot;

                    @Override
                    public void written() {}

                    @Override
                    public void listLoaded(ShoppingList updated) {
                        snapshot = updated;
                    }

                    @Override
                    public void loaded(List<ShoppingItem> updated) {
                        if (cleared) {
                            return;
                        }
                        shareText = ShoppingListTextFormatter.format(snapshot.name, updated);
                        sharing = false;
                        publish();
                    }

                    @Override
                    public void failed(boolean reading) {
                        if (cleared) {
                            return;
                        }
                        sharing = false;
                        error = R.string.share_error;
                        publish();
                    }
                });
    }

    String consumeShareText() {
        String text = shareText;
        shareText = null;
        return text;
    }

    void consumeError() {
        error = 0;
    }

    private ShoppingRepository.Result result(boolean add, long revision) {
        return new ShoppingRepository.Result() {
            @Override
            public void written() {
                if (cleared) {
                    return;
                }
                if (add) {
                    adding = false;
                    if (draftRevision == revision) {
                        draft("", "");
                    }
                }
                publish();
            }

            @Override
            public void listLoaded(ShoppingList updated) {
                list = updated;
            }

            @Override
            public void loaded(List<ShoppingItem> updated) {
                if (cleared) {
                    return;
                }
                items = updated;
                readFailed = false;
                publish();
            }

            @Override
            public void failed(boolean reading) {
                if (cleared) {
                    return;
                }
                if (add && !reading) {
                    adding = false;
                }
                if (reading) {
                    readFailed = true;
                }
                error = reading ? R.string.read_error : R.string.write_error;
                publish();
            }
        };
    }

    private void publish() {
        Integer current = changes.getValue();
        changes.setValue(current == null ? 1 : current + 1);
    }

    @Override
    protected void onCleared() {
        cleared = true;
    }
}
