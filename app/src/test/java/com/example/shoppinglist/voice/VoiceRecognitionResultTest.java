package com.example.shoppinglist.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class VoiceRecognitionResultTest {
    @Test
    public void usesFirstNonBlankCandidate() {
        assertEquals(
                "молоко",
                VoiceRecognitionResult.firstNonBlank(Arrays.asList(" ", " молоко ", "хліб")));
    }

    @Test
    public void preservesUkrainianText() {
        assertEquals(
                "олія соняшникова",
                VoiceRecognitionResult.firstNonBlank(
                        Collections.singletonList("олія соняшникова")));
    }

    @Test
    public void ignoresMissingCandidates() {
        assertNull(VoiceRecognitionResult.firstNonBlank(null));
        assertNull(VoiceRecognitionResult.firstNonBlank(Collections.emptyList()));
        assertNull(VoiceRecognitionResult.firstNonBlank(Arrays.asList(null, "", "  ")));
    }
}
