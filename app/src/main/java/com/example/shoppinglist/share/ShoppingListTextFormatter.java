package com.example.shoppinglist.share;

import com.example.shoppinglist.model.ShoppingItem;
import java.util.List;

/** Read-only text export; preserves display order within each purchase status. */
public final class ShoppingListTextFormatter {
    private ShoppingListTextFormatter() {}

    public static String format(String title, List<ShoppingItem> items) {
        StringBuilder text = new StringBuilder(title).append("\n\n");
        if (items.isEmpty()) {
            return text.append("Список порожній").toString();
        }
        appendItems(text, items, false);
        appendItems(text, items, true);
        return text.substring(0, text.length() - 1);
    }

    private static void appendItems(
            StringBuilder text, List<ShoppingItem> items, boolean purchased) {
        for (ShoppingItem item : items) {
            if (item.purchased != purchased) {
                continue;
            }
            text.append(purchased ? "☑ " : "☐ ").append(item.name);
            if (!item.quantity.isEmpty()) {
                text.append(" — ").append(item.quantity);
            }
            text.append('\n');
        }
    }
}
