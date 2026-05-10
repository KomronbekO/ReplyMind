package com.parishod.watomatic.model.classifier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class PromptBuilderTest {

    @Test
    public void systemPrompt_includesAllEnabledCategoryIds() {
        PromptBuilder pb = new PromptBuilder();
        UserProfile profile = new UserProfile();
        profile.setDisplayName("Komron");
        profile.setOccupation("Software engineer");

        String prompt = pb.buildClassificationSystemPrompt(profile, ClassificationCategory.defaults());

        assertTrue("expected urgent id", prompt.contains("\"urgent\""));
        assertTrue("expected work id", prompt.contains("\"work\""));
        assertTrue("expected family_friends id", prompt.contains("\"family_friends\""));
        assertTrue("expected promotional id", prompt.contains("\"promotional\""));
        assertTrue("expected spam id", prompt.contains("\"spam\""));
        assertTrue("expected other id", prompt.contains("\"other\""));
    }

    @Test
    public void systemPrompt_skipsDisabledCategories() {
        PromptBuilder pb = new PromptBuilder();
        ClassificationCategory disabled = new ClassificationCategory(
                "spam", "Spam", "desc", "🚫", "#000000",
                CategoryAction.SUPPRESS, true);
        disabled.setEnabled(false);
        ClassificationCategory enabled = new ClassificationCategory(
                "work", "Work", "desc", "💼", "#1E88E5",
                CategoryAction.REPLY_DEFAULT, true);

        String prompt = pb.buildClassificationSystemPrompt(new UserProfile(),
                Arrays.asList(disabled, enabled));

        assertFalse("disabled spam id should not be sent", prompt.contains("\"spam\""));
        assertTrue("enabled work id should be sent", prompt.contains("\"work\""));
    }

    @Test
    public void systemPrompt_includesUserProfileJson() {
        PromptBuilder pb = new PromptBuilder();
        UserProfile profile = new UserProfile();
        profile.setDisplayName("Komron");
        profile.setOccupation("Final-year CS student");
        profile.setTone(UserProfile.Tone.BRIEF);

        String prompt = pb.buildClassificationSystemPrompt(profile, ClassificationCategory.defaults());

        assertTrue(prompt.contains("Komron"));
        assertTrue(prompt.contains("Final-year CS student"));
        assertTrue(prompt.contains("BRIEF"));
    }

    @Test
    public void systemPrompt_handlesNullProfile() {
        PromptBuilder pb = new PromptBuilder();
        String prompt = pb.buildClassificationSystemPrompt(null, ClassificationCategory.defaults());
        assertTrue(prompt.contains("USER PROFILE"));
        assertTrue("empty JSON for null profile", prompt.contains("{}"));
    }

    @Test
    public void systemPrompt_demandsJsonOnlyOutput() {
        PromptBuilder pb = new PromptBuilder();
        String prompt = pb.buildClassificationSystemPrompt(new UserProfile(),
                ClassificationCategory.defaults());
        // Anchor key instructions so we catch accidental prompt regressions in code review.
        assertTrue(prompt.contains("\"category\""));
        assertTrue(prompt.contains("\"confidence\""));
        assertTrue(prompt.contains("\"reasoning\""));
        assertTrue("must instruct JSON-only", prompt.contains("Output only the JSON object"));
    }

    @Test
    public void userMessage_includesSenderAndBody() {
        PromptBuilder pb = new PromptBuilder();
        String msg = pb.buildClassificationUserMessage("Boss", "We need this by 3pm.");
        assertTrue(msg.contains("Boss"));
        assertTrue(msg.contains("We need this by 3pm."));
    }

    @Test
    public void userMessage_handlesNullSender() {
        PromptBuilder pb = new PromptBuilder();
        String msg = pb.buildClassificationUserMessage(null, "hello");
        assertTrue(msg.contains("(unknown)"));
        assertTrue(msg.contains("hello"));
    }
}
