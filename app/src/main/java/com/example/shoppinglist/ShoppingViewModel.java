package com.example.shoppinglist;

import android.net.Uri;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.shoppinglist.data.ShoppingRepository;
import com.example.shoppinglist.data.ReceiptAttachment;
import com.example.shoppinglist.model.ShoppingItem;
import com.example.shoppinglist.model.ShoppingList;
import com.example.shoppinglist.share.ShoppingListTextFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ShoppingViewModel extends ViewModel {
    private static final String STATE_ITEM_NAME = "name";
    private static final String STATE_ITEM_QUANTITY = "quantity";
    private static final String STATE_RECEIPT_OFFER = "receiptOffer";
    final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    private final ShoppingRepository repository;
    private final SavedStateHandle saved;
    final long listId;
    ShoppingList list;
    List<ShoppingItem> items = new ArrayList<>();
    List<ReceiptAttachment> receiptAttachments = new ArrayList<>();
    String name = "";
    String quantity = "";
    boolean adding;
    boolean sharing;
    boolean receiptBusy;
    boolean finishing;
    boolean closeAfterFinish;
    boolean receiptOfferPending;
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
        receiptOfferPending = Boolean.TRUE.equals(saved.get(STATE_RECEIPT_OFFER));
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

    void updateItemBarcode(ShoppingItem item, String barcode) {
        if (list == null || list.archivedAt != null) {
            return;
        }
        repository.setItemBarcode(listId, item.id, barcode, createRepositoryCallback(false, 0));
    }

    void updateItemUrl(ShoppingItem item, String url) {
        repository.setItemUrl(listId, item.id, url, createRepositoryCallback(false, 0));
    }

    void requestReceiptOffer() {
        receiptOfferPending = true;
        saved.set(STATE_RECEIPT_OFFER, true);
        notifyStateChanged();
    }

    void consumeReceiptOffer() {
        receiptOfferPending = false;
        saved.set(STATE_RECEIPT_OFFER, false);
    }

    void finishShopping(boolean confirmed, boolean deleteEmpty) {
        if (finishing
                || receiptOfferPending
                || list == null
                || list.archivedAt != null
                || list.isWish()) {
            return;
        }
        finishing = true;
        notifyStateChanged();
        ShoppingRepository.Result callback =
                new ShoppingRepository.Result() {
                    @Override
                    public void written() {
                        if (!cleared) {
                            if (deleteEmpty) {
                                closeAfterFinish = true;
                            } else {
                                finishing = false;
                                requestReceiptOffer();
                                reload();
                            }
                            notifyStateChanged();
                        }
                    }

                    @Override
                    public void loaded(List<ShoppingItem> ignored) {}

                    @Override
                    public void failed(boolean reading) {
                        if (!cleared) {
                            finishing = false;
                            error = R.string.write_error;
                            notifyStateChanged();
                        }
                    }

                    @Override
                    public void confirmationRequired() {
                        if (!cleared) {
                            finishing = false;
                            error = R.string.finish_confirmation_needed;
                            notifyStateChanged();
                        }
                    }
                };
        if (deleteEmpty) {
            repository.deleteEmptyShoppingList(listId, callback);
        } else {
            repository.completeShoppingList(listId, confirmed, callback);
        }
    }

    void attachReceipts(List<Uri> sources) {
        if (receiptBusy || sources == null || sources.isEmpty()
                || list == null || list.archivedAt == null) {
            return;
        }
        receiptBusy = true;
        notifyStateChanged();
        repository.addReceipts(listId, sources, receiptCallback());
    }

    void removeReceipt(ReceiptAttachment attachment) {
        if (receiptBusy || attachment == null || list == null || list.archivedAt == null) {
            return;
        }
        receiptBusy = true;
        notifyStateChanged();
        repository.removeReceipt(listId, attachment.id, receiptCallback());
    }

    void previewReceipt(ReceiptAttachment item, ShoppingRepository.ReceiptPreview callback) {
        repository.previewReceipt(item, callback);
    }

    void attachReceipt(Uri photo) {
        if (receiptBusy || photo == null || list == null || list.archivedAt == null) {
            return;
        }
        receiptBusy = true;
        notifyStateChanged();
        repository.attachReceipt(listId, photo, receiptCallback());
    }

    void removeReceipt() {
        if (receiptBusy || list == null || list.archivedAt == null || list.receiptFile == null) {
            return;
        }
        receiptBusy = true;
        notifyStateChanged();
        repository.removeReceipt(listId, receiptCallback());
    }

    void previewReceipt(ShoppingRepository.ReceiptPreview callback) {
        repository.previewReceipt(listId, callback);
    }

    private ShoppingRepository.Result receiptCallback() {
        return new ShoppingRepository.Result() {
            @Override
            public void written() {}

            @Override
            public void loaded(List<ShoppingItem> updated) {
                if (!cleared) {
                    receiptBusy = false;
                    items = updated;
                    notifyStateChanged();
                }
            }

            @Override
            public void listLoaded(ShoppingList updated) {
                if (!cleared) {
                    list = updated;
                }
            }

            @Override
            public void receiptsLoaded(List<ReceiptAttachment> attachments) {
                if (!cleared) {
                    receiptAttachments = attachments;
                }
            }

            @Override
            public void failed(boolean reading) {
                if (!cleared) {
                    receiptBusy = false;
                    error = R.string.receipt_error;
                    notifyStateChanged();
                }
            }
        };
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
