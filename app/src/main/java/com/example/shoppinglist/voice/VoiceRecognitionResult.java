package com.example.shoppinglist.voice;

import java.util.List;
import java.util.Locale;

public final class VoiceRecognitionResult {
    private VoiceRecognitionResult() {}

    public static String firstNonBlank(List<String> matches) {
        if (matches == null) {
            return null;
        }
        for (String candidate : matches) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                String name = candidate.trim();
                int firstCharacterEnd = name.offsetByCodePoints(0, 1);
                return name.substring(0, firstCharacterEnd).toUpperCase(Locale.forLanguageTag("uk"))
                        + name.substring(firstCharacterEnd);
            }
        }
        return null;
    }
}
