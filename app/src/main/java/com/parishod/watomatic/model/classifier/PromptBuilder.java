package com.parishod.watomatic.model.classifier;

import com.google.gson.Gson;

import java.util.List;

/**
 * Composes the classification system + user prompts.
 *
 * <p>The system prompt embeds the user profile (so the model can reason like the user) and the
 * full category list (so the model picks from a fixed vocabulary). The user prompt carries the
 * sender display name and the message body.</p>
 *
 * <p>Output JSON shape is mandated by the prompt:
 * {@code {"category": "<id>", "confidence": <0-1>, "reasoning": "<short>"}}.</p>
 */
public class PromptBuilder {

    private final Gson gson = new Gson();

    public String buildClassificationSystemPrompt(UserProfile profile,
                                                  List<ClassificationCategory> categories) {
        StringBuilder catList = new StringBuilder();
        for (ClassificationCategory c : categories) {
            if (c == null || !c.isEnabled()) continue;
            catList.append("- id: \"").append(c.getId()).append("\"")
                    .append(", name: \"").append(c.getName()).append("\"")
                    .append(", description: ").append(c.getDescription())
                    .append('\n');
        }

        String profileJson = (profile != null) ? gson.toJson(profile) : "{}";

        return String.format(CLASSIFICATION_TEMPLATE, profileJson, catList.toString().trim());
    }

    public String buildClassificationUserMessage(String sender, String body) {
        String safeSender = (sender == null || sender.trim().isEmpty()) ? "(unknown)" : sender;
        String safeBody = (body == null) ? "" : body;
        long nowMin = nowMinuteOfDay();
        return "Current local time (minutes-of-day): " + nowMin
                + "\nSender display name: " + safeSender
                + "\nMessage: " + safeBody;
    }

    /** Hook so tests can override the wall-clock dependency. */
    protected long nowMinuteOfDay() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60L + cal.get(java.util.Calendar.MINUTE);
    }

    private static final String CLASSIFICATION_TEMPLATE =
            "You are an assistant that classifies incoming chat messages on behalf of a user, "
                    + "so the user's auto-responder can pick the right action while the user is busy.\n"
                    + "\n"
                    + "USER PROFILE (JSON):\n%s\n"
                    + "\n"
                    + "Use this profile to reason like the user would: recognize names of people in "
                    + "keyRelationships, weigh urgency relative to occupation/working hours, and infer tone.\n"
                    + "\n"
                    + "CATEGORIES (you MUST pick exactly one id from this list):\n%s\n"
                    + "\n"
                    + "Respond with ONLY a single JSON object, no prose, no code fences, in this exact shape:\n"
                    + "{\"category\": \"<id>\", \"confidence\": <0.0-1.0>, \"reasoning\": \"<one sentence>\"}\n"
                    + "\n"
                    + "Rules:\n"
                    + "- \"category\" MUST be one of the ids listed above, lowercase, exact match.\n"
                    + "- If unsure, use \"other\" with low confidence.\n"
                    + "- Keep reasoning under 25 words.\n"
                    + "- Output only the JSON object — nothing before or after.";
}
