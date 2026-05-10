package com.parishod.watomatic.model.classifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Pure unit tests for the static parse helpers in {@link LlmMessageClassifier}. No Robolectric
 * needed — the parse logic doesn't touch Android.
 */
public class LlmMessageClassifierParseTest {

    private final List<ClassificationCategory> defaults = ClassificationCategory.defaults();

    @Test
    public void parse_plainJson_succeeds() {
        String raw = "{\"category\":\"work\",\"confidence\":0.92,\"reasoning\":\"meeting at 3pm\"}";
        ClassificationResult r = LlmMessageClassifier.parseOrFallback(raw, defaults);
        assertEquals("work", r.getCategoryId());
        assertEquals(0.92f, r.getConfidence(), 0.001f);
        assertEquals("meeting at 3pm", r.getReasoning());
        assertFalse(r.isFallback());
    }

    @Test
    public void parse_codeFenceWrapped_succeeds() {
        String raw = "```json\n{\"category\":\"urgent\",\"confidence\":0.8,\"reasoning\":\"deadline\"}\n```";
        ClassificationResult r = LlmMessageClassifier.parseOrFallback(raw, defaults);
        assertEquals("urgent", r.getCategoryId());
        assertFalse(r.isFallback());
    }

    @Test
    public void parse_leadingProse_succeeds() {
        String raw = "Sure, here is my classification: {\"category\":\"spam\",\"confidence\":0.99,\"reasoning\":\"obvious phishing\"} hope it helps.";
        ClassificationResult r = LlmMessageClassifier.parseOrFallback(raw, defaults);
        assertEquals("spam", r.getCategoryId());
        assertFalse(r.isFallback());
    }

    @Test
    public void parse_unknownCategory_fallsBack() {
        String raw = "{\"category\":\"banana\",\"confidence\":0.5,\"reasoning\":\"made up\"}";
        ClassificationResult r = LlmMessageClassifier.parseOrFallback(raw, defaults);
        assertTrue(r.isFallback());
        assertEquals(ClassificationCategory.ID_OTHER, r.getCategoryId());
        assertTrue(r.getReasoning().contains("unknown_category"));
    }

    @Test
    public void parse_garbage_fallsBack() {
        ClassificationResult r = LlmMessageClassifier.parseOrFallback("not json at all", defaults);
        assertTrue(r.isFallback());
        assertTrue(r.getReasoning().contains("parse_error") || r.getReasoning().contains("no JSON"));
    }

    @Test
    public void parse_null_fallsBack() {
        ClassificationResult r = LlmMessageClassifier.parseOrFallback(null, defaults);
        assertTrue(r.isFallback());
    }

    @Test
    public void parse_missingConfidence_usesDefault() {
        // A model that returns category + reasoning but forgets confidence — accept it.
        String raw = "{\"category\":\"family_friends\",\"reasoning\":\"close friend\"}";
        ClassificationResult r = LlmMessageClassifier.parseOrFallback(raw, defaults);
        assertFalse(r.isFallback());
        assertEquals("family_friends", r.getCategoryId());
        assertEquals(0.5f, r.getConfidence(), 0.001f);
    }

    @Test
    public void parse_caseInsensitiveCategoryId() {
        String raw = "{\"category\":\"WORK\",\"confidence\":0.7,\"reasoning\":\"upper case\"}";
        ClassificationResult r = LlmMessageClassifier.parseOrFallback(raw, defaults);
        assertFalse(r.isFallback());
        assertEquals("work", r.getCategoryId());
    }

    @Test
    public void extractJsonObject_handlesBalancedBraces() {
        String raw = "{\"a\":{\"b\":1}}";
        String json = LlmMessageClassifier.extractJsonObject(raw);
        assertEquals("{\"a\":{\"b\":1}}", json);
    }
}
