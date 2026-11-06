package com.example.shoppinglist.data;

/** Metadata for a privately stored receipt image or PDF. */
public final class ReceiptAttachment {
    public final long id;
    public final long listId;
    public final String fileName;
    public final String mimeType;

    public ReceiptAttachment(long id, long listId, String fileName, String mimeType) {
        this.id = id;
        this.listId = listId;
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    public boolean isPdf() {
        return "application/pdf".equals(mimeType);
    }
}
