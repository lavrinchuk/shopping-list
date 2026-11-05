package com.example.shoppinglist;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.shoppinglist.data.ShoppingRepository;
import com.example.shoppinglist.model.ShoppingItem;
import com.example.shoppinglist.model.ShoppingList;
import java.util.ArrayList;
import java.util.List;

final class ListsViewModel extends ViewModel {
    static final String STATE_EDIT_ID = "editId";
    static final String STATE_EDIT_NAME = "editName";
    static final String STATE_EDIT_TYPE = "editType";
    static final String STATE_EDITING = "editing";
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
    private static final String STATE_OPEN_CREATED_ID = "openCreatedId";

    ListsViewModel(ShoppingRepository repository, SavedStateHandle saved, boolean archived) {
        this.repository = repository;
        this.saved = saved;
        this.archived = archived;
    }

    Long consumeCreatedList() {
        Long id = saved.get(STATE_OPEN_CREATED_ID);
        if (id != null) {
            saved.remove(STATE_OPEN_CREATED_ID);
        }
        return id;
    }

    void reload() {
        loading = true;
        notifyStateChanged();
        repository.lists(archived, createRepositoryCallback(false));
    }

    void saveName(long id, String name) {
        if (busy) {
            return;
        }
        String clean = ShoppingList.cleanName(name);
        busy = true;
        notifyStateChanged();
        if (id == 0) {
            String type = saved.get(STATE_EDIT_TYPE);
            ShoppingRepository.Result delegate = createRepositoryCallback(true);
            repository.create(
                    clean,
                    ShoppingList.requireType(type == null ? ShoppingList.TYPE_SHOPPING : type),
                    new ShoppingRepository.Result() {
                        private Long createdId;

                        @Override
                        public void created(long id) {
                            createdId = id;
                        }

                        @Override
                        public void written() {
                            delegate.written();
                            if (!cleared && createdId != null) {
                                saved.set(STATE_OPEN_CREATED_ID, createdId);
                                notifyStateChanged();
                            }
                        }

                        @Override
                        public void loaded(List<ShoppingItem> items) {}

                        @Override
                        public void listsLoaded(List<ShoppingList> updated) {
                            delegate.listsLoaded(updated);
                        }

                        @Override
                        public void failed(boolean reading) {
                            delegate.failed(reading);
                        }
                    });
        } else {
            repository.rename(id, clean, createRepositoryCallback(true));
        }
    }

    void archive(ShoppingList list) {
        if (busy) {
            return;
        }
        if (!list.isWish() && list.total > list.purchased) {
            confirmation = list.id;
            notifyStateChanged();
        } else {
            archive(list.id, false);
        }
    }

    void archive(long id, boolean confirmed) {
        if (busy) {
            return;
        }
        confirmation = null;
        busy = true;
        notifyStateChanged();
        repository.archive(
                id,
                confirmed,
                new ShoppingRepository.Result() {
                    final ShoppingRepository.Result delegate = createRepositoryCallback(false);

                    @Override
                    public void written() {
                        delegate.written();
                    }

                    @Override
                    public void loaded(List<ShoppingItem> items) {}

                    @Override
                    public void listsLoaded(List<ShoppingList> lists) {
                        delegate.listsLoaded(lists);
                    }

                    @Override
                    public void failed(boolean reading) {
                        delegate.failed(reading);
                    }

                    @Override
                    public void confirmationRequired() {
                        if (cleared) {
                            return;
                        }
                        busy = false;
                        confirmation = id;
                        notifyStateChanged();
                    }
                });
    }

    void deleteEmptyShoppingList(long id) {
        if (busy) {
            return;
        }
        busy = true;
        notifyStateChanged();
        repository.deleteEmptyShoppingList(
                id,
                new ShoppingRepository.Result() {
                    @Override
                    public void written() {
                        if (!cleared) {
                            busy = false;
                            reload();
                        }
                    }

                    @Override
                    public void loaded(List<ShoppingItem> ignored) {}

                    @Override
                    public void failed(boolean reading) {
                        if (!cleared) {
                            busy = false;
                            error = R.string.write_error;
                            notifyStateChanged();
                        }
                    }
                });
    }

    void restore(long id) {
        if (busy) {
            return;
        }
        busy = true;
        notifyStateChanged();
        repository.restore(id, createRepositoryCallback(false));
    }

    private ShoppingRepository.Result createRepositoryCallback(boolean naming) {
        return new ShoppingRepository.Result() {
            @Override
            public void written() {
                if (cleared) {
                    return;
                }
                busy = false;
                if (naming) {
                    saved.set(STATE_EDITING, false);
                }
                notifyStateChanged();
            }

            @Override
            public void loaded(List<ShoppingItem> ignored) {}

            @Override
            public void listsLoaded(List<ShoppingList> updated) {
                if (cleared) {
                    return;
                }
                lists = updated;
                loading = false;
                readFailed = false;
                notifyStateChanged();
            }

            @Override
            public void failed(boolean reading) {
                if (cleared) {
                    return;
                }
                busy = false;
                loading = false;
                if (reading) {
                    readFailed = true;
                }
                error = reading ? R.string.read_error : R.string.write_error;
                notifyStateChanged();
            }
        };
    }

    void notifyStateChanged() {
        Integer current = changes.getValue();
        changes.setValue(current == null ? 1 : current + 1);
    }

    @Override
    protected void onCleared() {
        cleared = true;
    }
}
