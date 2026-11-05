package com.example.shoppinglist;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.shoppinglist.data.ShoppingRepository;
import com.example.shoppinglist.model.ShoppingItem;
import com.example.shoppinglist.model.ShoppingList;
import com.example.shoppinglist.share.ShoppingListTextFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ShoppingViewModel extends ViewModel {
    private static final String STATE_ITEM_NAME = "name";
    private static final String STATE_ITEM_QUANTITY = "quantity";
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
    private long draftEditRevision;
    private boolean cleared;

    ShoppingViewModel(ShoppingRepository repository, SavedStateHandle saved, long listId) {
        this.repository = repository;
        this.listId = listId;
        this.saved = saved;
        name = saved.contains(STATE_ITEM_NAME) ? saved.get(STATE_ITEM_NAME) : "";
        quantity = saved.contains(STATE_ITEM_QUANTITY) ? saved.get(STATE_ITEM_QUANTITY) : "";
        reload();
    }

    void draft(String name, String quantity) {
        if (!this.name.equals(name) || !this.quantity.equals(quantity)) {
            draftEditRevision++;
        }
        this.name = name;
        this.quantity = quantity;
        saved.set(STATE_ITEM_NAME, name);
        saved.set(STATE_ITEM_QUANTITY, quantity);
    }

    void add() {
        if (adding || list == null || list.archivedAt != null) {
            return;
        }
        String clean = ShoppingItem.cleanName(name);
        long submittedDraftRevision = draftEditRevision;
        adding = true;
        notifyStateChanged();
        repository.add(
                listId, clean, quantity, createRepositoryCallback(true, submittedDraftRevision));
    }

    void purchased(ShoppingItem item, boolean checked) {
        repository.purchased(listId, item.id, checked, createRepositoryCallback(false, 0));
    }

    void delete(ShoppingItem item) {
        repository.delete(listId, item.id, createRepositoryCallback(false, 0));
    }

    void updateItemUrl(ShoppingItem item, String url) {
        repository.setItemUrl(listId, item.id, url, createRepositoryCallback(false, 0));
    }

    void reload() {
        repository.load(listId, createRepositoryCallback(false, 0));
    }

    // A fresh read on the same queue follows all writes submitted before this action.
    void share() {
        if (sharing || shareText != null) {
            return;
        }
        sharing = true;
        notifyStateChanged();
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
                        notifyStateChanged();
                    }

                    @Override
                    public void failed(boolean reading) {
                        if (cleared) {
                            return;
                        }
                        sharing = false;
                        error = R.string.share_error;
                        notifyStateChanged();
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

    private ShoppingRepository.Result createRepositoryCallback(boolean add, long revision) {
        return new ShoppingRepository.Result() {
            @Override
            public void written() {
                if (cleared) {
                    return;
                }
                if (add) {
                    adding = false;
                    if (draftEditRevision == revision) {
                        draft("", "");
                    }
                }
                notifyStateChanged();
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
                notifyStateChanged();
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
                notifyStateChanged();
            }
        };
    }

    private void notifyStateChanged() {
        Integer current = changes.getValue();
        changes.setValue(current == null ? 1 : current + 1);
    }

    @Override
    protected void onCleared() {
        cleared = true;
    }
}
