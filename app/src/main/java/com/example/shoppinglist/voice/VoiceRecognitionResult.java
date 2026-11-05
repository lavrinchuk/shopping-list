package com.example.shoppinglist.voice;

import java.util.List;

public final class VoiceRecognitionResult {
    private VoiceRecognitionResult() {}

    public static String firstNonBlank(List<String> matches) {
        if (matches == null) {
            return null;
        }
        for (String candidate : matches) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return null;
    }
}
