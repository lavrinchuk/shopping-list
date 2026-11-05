package com.example.shoppinglist.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** HTTP(S) link validation shared by the editor and the persistence layer. */
public final class ItemLink {
    private ItemLink() {}

    /** Returns null for an empty optional link; rejects unsupported or malformed URLs. */
    public static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String url = value.trim();
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.toLowerCase(Locale.ROOT).equals("https")
                            || scheme.toLowerCase(Locale.ROOT).equals("http"))
                    || uri.getHost() == null
                    || uri.getHost().isEmpty()
                    || uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException("Use a complete HTTP(S) URL");
            }
            return url;
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Use a complete HTTP(S) URL", error);
        }
    }
}
