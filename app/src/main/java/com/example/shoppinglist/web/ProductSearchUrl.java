package com.example.shoppinglist.web;

import com.example.shoppinglist.model.ShoppingItem;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Builds encoded HTTPS search URLs; no network access or store API is required. */
public final class ProductSearchUrl {
    private static final String SEARCH_ENDPOINT = "https://www.google.com/search?q=";

    private ProductSearchUrl() {}

    public static String forName(String productName) {
        return encodeQuery(ShoppingItem.cleanName(productName));
    }

    /** Adds a buying intent and an optional, manually selected Ukrainian search region. */
    public static String forPurchase(String productName, String region) {
        String query = ShoppingItem.cleanName(productName) + " купити";
        if (region != null && !region.trim().isEmpty()) {
            query += " " + region.trim();
        }
        return encodeQuery(query);
    }

    private static String encodeQuery(String query) {
        try {
            return SEARCH_ENDPOINT + URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException error) {
            throw new AssertionError("UTF-8 is always supported", error);
        }
    }
}
