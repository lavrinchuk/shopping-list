package com.example.shoppinglist.web;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ProductSearchUrlTest {
    @Test
    public void encodesUkrainianProductName() {
        assertEquals(
                "https://www.google.com/search?q=%D0%9C%D0%BE%D0%BB%D0%BE%D0%BA%D0%BE",
                ProductSearchUrl.forName("Молоко"));
    }

    @Test
    public void trimsNameAndEscapesSpacesAndSpecialCharacters() {
        assertEquals(
                "https://www.google.com/search?q=%D0%9A%D0%B0%D0%B2%D0%B0+%26+%D1%87%D0%B0%D0%B9",
                ProductSearchUrl.forName("  Кава & чай  "));
    }

    @Test
    public void doesNotIncludeQuantityOrUnrelatedMetadata() {
        assertEquals("https://www.google.com/search?q=Milk", ProductSearchUrl.forName("Milk"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankName() {
        ProductSearchUrl.forName("  ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullName() {
        ProductSearchUrl.forName(null);
    }
}
