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
                "Молоко",
                VoiceRecognitionResult.firstNonBlank(Arrays.asList(" ", " молоко ", "хліб")));
    }

    @Test
    public void preservesUkrainianText() {
        assertEquals(
                "Олія соняшникова",
                VoiceRecognitionResult.firstNonBlank(
                        Collections.singletonList("олія соняшникова")));
    }

    @Test
    public void keepsOtherLettersAndUppercasesUkrainianLetters() {
        assertEquals(
                "Сік ABC",
                VoiceRecognitionResult.firstNonBlank(Collections.singletonList("сік ABC")));
        assertEquals(
                "Ґудзики",
                VoiceRecognitionResult.firstNonBlank(Collections.singletonList("Ґудзики")));
        assertEquals("Їжа", VoiceRecognitionResult.firstNonBlank(Collections.singletonList("їжа")));
    }

    @Test
    public void parsesExplicitDelimitersAndCapitalizesEachName() {
        assertEquals(
                Arrays.asList("Молоко", "Хліб", "Яблука"),
                VoiceRecognitionResult.items(" молоко, хліб; яблука "));
        assertEquals(Arrays.asList("Сир", "Олія"), VoiceRecognitionResult.items("сир\n\n олія"));
    }

    @Test
    public void supportsSpokenCommaWhenRecognizerOmitsPunctuation() {
        assertEquals(
                Arrays.asList("Молоко", "Хліб", "Яблука"),
                VoiceRecognitionResult.items("молоко кома хліб кома яблука"));
    }

    @Test
    public void doesNotGuessNaturalLanguageQuantityOrConjunctions() {
        assertEquals(
                Collections.singletonList("Два літри молока й три батони"),
                VoiceRecognitionResult.items("два літри молока й три батони"));
    }

    @Test
    public void ignoresBlankPartsWithoutRemovingIntentionalDuplicates() {
        assertEquals(Arrays.asList("Хліб", "Хліб"), VoiceRecognitionResult.items("хліб,,, хліб; "));
        assertEquals(Collections.emptyList(), VoiceRecognitionResult.items(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void limitsVoiceBatchToTwentyItems() {
        VoiceRecognitionResult.items("хліб,".repeat(21));
    }

    @Test
    public void ignoresMissingCandidates() {
        assertNull(VoiceRecognitionResult.firstNonBlank(null));
        assertNull(VoiceRecognitionResult.firstNonBlank(Collections.emptyList()));
        assertNull(VoiceRecognitionResult.firstNonBlank(Arrays.asList(null, "", "  ")));
    }
}
