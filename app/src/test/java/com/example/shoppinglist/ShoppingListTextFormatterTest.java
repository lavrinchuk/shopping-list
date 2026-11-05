package com.example.shoppinglist;

import com.example.shoppinglist.model.ShoppingItem;
import com.example.shoppinglist.model.ShoppingList;
import com.example.shoppinglist.share.ShoppingListTextFormatter;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ShoppingListTextFormatterTest {
    private ShoppingItem item(long id, String name, String quantity, boolean purchased) {
        return new ShoppingItem(id, 9, name, quantity, purchased);
    }

    @Test
    public void mixedStatusesAndQuantities() {
        assertEquals(
                "Сільпо\n\n☐ Молоко — 2 л\n☑ Хліб — 1 шт.",
                ShoppingListTextFormatter.format(
                        "Сільпо",
                        Arrays.asList(
                                item(1, "Молоко", "2 л", false), item(2, "Хліб", "1 шт.", true))));
    }

    @Test
    public void noQuantityOmitsDash() {
        assertEquals(
                "Покупки\n\n☑ Кава",
                ShoppingListTextFormatter.format(
                        "Покупки", Collections.singletonList(item(1, "Кава", "  ", true))));
    }

    @Test
    public void emptyList() {
        assertEquals(
                "Сільпо\n\nСписок порожній",
                ShoppingListTextFormatter.format("Сільпо", Collections.emptyList()));
    }

    @Test
    public void longNamesAreNotTruncated() {
        String name = String.join("", Collections.nCopies(500, "Довга назва ")).trim();
        assertEquals(
                "Покупки\n\n☐ " + name,
                ShoppingListTextFormatter.format(
                        "Покупки", Collections.singletonList(item(1, name, "", false))));
    }

    @Test
    public void ukrainianAndSpecialCharactersArePreserved() {
        assertEquals(
                "Їжа & дім <3\n\n☐ Печиво «Сім’я» & чай — ½ кг + 2 шт.",
                ShoppingListTextFormatter.format(
                        "Їжа & дім <3",
                        Collections.singletonList(
                                item(1, "Печиво «Сім’я» & чай", "½ кг + 2 шт.", false))));
    }

    @Test
    public void stablePartitionMatchesDisplayOrder() {
        assertEquals(
                "Список\n\n☐ Б\n☐ Г\n☑ А\n☑ В",
                ShoppingListTextFormatter.format(
                        "Список",
                        Arrays.asList(
                                item(1, "А", "", true),
                                item(2, "Б", "", false),
                                item(3, "В", "", true),
                                item(4, "Г", "", false))));
    }

    @Test
    public void formattingDoesNotMutateInputOrMetadata() {
        ShoppingList list = new ShoppingList(9, "Архів", 10, 20, 30L, 2, 1);
        List<ShoppingItem> items =
                new ArrayList<>(
                        Arrays.asList(item(8, "Кава", "1", true), item(7, "Чай", "2", false)));
        List<ShoppingItem> before = new ArrayList<>(items);
        String first =
                ShoppingListTextFormatter.format(list.name, Collections.unmodifiableList(items));
        assertEquals(first, ShoppingListTextFormatter.format(list.name, items));
        assertEquals(before, items);
        assertEquals(8, items.get(0).id);
        assertEquals(9, items.get(0).listId);
        assertEquals("Кава", items.get(0).name);
        assertEquals("1", items.get(0).quantity);
        assertTrue(items.get(0).purchased);
        assertEquals(20, list.updatedAt);
        assertEquals(Long.valueOf(30), list.archivedAt);
    }
}
