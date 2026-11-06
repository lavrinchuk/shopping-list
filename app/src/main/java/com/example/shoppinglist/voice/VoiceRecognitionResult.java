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
                return capitalizeFirst(candidate);
            }
        }
        return null;
    }

    /**
     * Splits only explicit separators; quantity and natural-language parsing belongs to VOICE-03.
     */
    public static List<String> items(String recognized) {
        if (recognized == null || recognized.isBlank()) {
            return java.util.Collections.emptyList();
        }
        List<String> names = new java.util.ArrayList<>();
        // Some speech engines transcribe spoken punctuation as a literal word.
        String explicitSeparators = recognized.replaceAll("(?iu)\\s+кома\\s+", ",");
        for (String part : explicitSeparators.split("[,;\\n]+")) {
            if (!part.isBlank()) {
                names.add(capitalizeFirst(part));
            }
            if (names.size() > 20) {
                throw new IllegalArgumentException("Too many spoken items");
            }
        }
        return names;
    }

    public static String capitalizeFirst(String value) {
        String name = value.trim();
        if (name.isEmpty()) {
            return name;
        }
        int firstCharacterEnd = name.offsetByCodePoints(0, 1);
        return name.substring(0, firstCharacterEnd).toUpperCase(Locale.forLanguageTag("uk"))
                + name.substring(firstCharacterEnd);
    }
}
