package com.example.shoppinglist.web;

import com.example.shoppinglist.model.ShoppingItem;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Builds an encoded browser search URL; no network access or store API is required. */
public final class ProductSearchUrl {
    private static final String SEARCH_ENDPOINT = "https://www.google.com/search?q=";

    private ProductSearchUrl() {}

    public static String forName(String productName) {
        try {
            return SEARCH_ENDPOINT
                    + URLEncoder.encode(
                            ShoppingItem.cleanName(productName), StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException error) {
            throw new AssertionError("UTF-8 is always supported", error);
        }
    }
}
