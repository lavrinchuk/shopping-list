package com.example.shoppinglist.model;

/** A barcode is an identifier, not a number: leading zeroes must be retained. */
public final class ProductBarcode {
    private ProductBarcode() {}

    public static String normalize(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String value = input.trim();
        if (value.length() > 256 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Invalid barcode");
        }
        return value;
    }
}
