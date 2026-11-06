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

    @Test
    public void purchaseSearchIncludesBuyingIntentAndSelectedRegion() {
        assertEquals(
                "https://www.google.com/search?q=%D0%9C%D0%BE%D0%BB%D0%BE%D0%BA%D0%BE+%D0%BA%D1%83%D0%BF%D0%B8%D1%82%D0%B8+%D0%9A%D0%B8%D1%97%D0%B2%D1%81%D1%8C%D0%BA%D0%B0+%D0%BE%D0%B1%D0%BB%D0%B0%D1%81%D1%82%D1%8C",
                ProductSearchUrl.forPurchase("Молоко", "Київська область"));
    }

    @Test
    public void purchaseSearchWorksWithoutRegion() {
        assertEquals(
                "https://www.google.com/search?q=Milk+%D0%BA%D1%83%D0%BF%D0%B8%D1%82%D0%B8",
                ProductSearchUrl.forPurchase("Milk", ""));
    }

    @Test
    public void purchaseSearchTrimsNameAndRegionBeforeEncoding() {
        assertEquals(
                "https://www.google.com/search?q=%D0%9A%D0%B0%D0%B2%D0%B0+%26+%D1%87%D0%B0%D0%B9+%D0%BA%D1%83%D0%BF%D0%B8%D1%82%D0%B8+%D0%BC.+%D0%9A%D0%B8%D1%97%D0%B2",
                ProductSearchUrl.forPurchase(" Кава & чай ", " м. Київ "));
    }

    @Test
    public void purchaseSearchTreatsWhitespaceOnlyRegionAsNoRegion() {
        assertEquals(
                ProductSearchUrl.forPurchase("Молоко", ""),
                ProductSearchUrl.forPurchase("Молоко", "   "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void purchaseSearchRejectsBlankProductName() {
        ProductSearchUrl.forPurchase("  ", "Київська область");
    }

    @Test
    public void barcodeSearchPreservesLeadingZeroesAndQuotesIdentifier() {
        assertEquals(
                "https://www.google.com/search?q=%22012345678905%22+%D1%82%D0%BE%D0%B2%D0%B0%D1%80",
                ProductSearchUrl.forBarcode(" 012345678905 "));
    }

    @Test
    public void barcodeSearchEscapesSpecialCharacters() {
        assertEquals(
                "https://www.google.com/search?q=%22AB%2B12%26C%22+%D1%82%D0%BE%D0%B2%D0%B0%D1%80",
                ProductSearchUrl.forBarcode("AB+12&C"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankBarcodeSearch() {
        ProductSearchUrl.forBarcode("  ");
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
