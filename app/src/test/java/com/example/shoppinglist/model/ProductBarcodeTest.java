package com.example.shoppinglist.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ProductBarcodeTest {
    @Test
    public void preservesLeadingZerosAndAlphanumericCodes() {
        assertEquals("012345678905", ProductBarcode.normalize(" 012345678905 "));
        assertEquals("AB-123-XY", ProductBarcode.normalize("AB-123-XY"));
    }

    @Test
    public void blankMeansNoCode() {
        assertNull(ProductBarcode.normalize(null));
        assertNull(ProductBarcode.normalize("   "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOverlongCode() {
        ProductBarcode.normalize("x".repeat(257));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMultilineCode() {
        ProductBarcode.normalize("one\ntwo");
    }
}
