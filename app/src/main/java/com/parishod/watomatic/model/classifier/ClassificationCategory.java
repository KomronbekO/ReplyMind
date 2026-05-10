package com.parishod.watomatic.model.classifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User-configurable classification category. Persisted as a JSON list in PreferencesManager
 * so users can rename, recolor, or add custom categories at runtime.
 *
 * <p>Built-in categories are seeded via {@link #defaults()}. They can be edited but not deleted
 * (the {@code builtIn} flag is enforced in the management UI).</p>
 */
public class ClassificationCategory {
    public static final String ID_URGENT = "urgent";
    public static final String ID_WORK = "work";
    public static final String ID_FAMILY_FRIENDS = "family_friends";
    public static final String ID_PROMOTIONAL = "promotional";
    public static final String ID_SPAM = "spam";
    public static final String ID_OTHER = "other";

    private String id;
    private String name;
    private String description;
    private String emoji;
    private String colorHex;
    private CategoryAction defaultAction;
    private boolean builtIn;
    private boolean enabled;

    public ClassificationCategory() {
        this.enabled = true;
    }

    public ClassificationCategory(String id, String name, String description, String emoji,
                                  String colorHex, CategoryAction defaultAction, boolean builtIn) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.emoji = emoji;
        this.colorHex = colorHex;
        this.defaultAction = defaultAction;
        this.builtIn = builtIn;
        this.enabled = true;
    }

    public static List<ClassificationCategory> defaults() {
        return new ArrayList<>(Arrays.asList(
                new ClassificationCategory(
                        ID_URGENT,
                        "Urgent",
                        "Time-sensitive messages needing immediate attention: emergencies, deadlines today, urgent requests from boss/family.",
                        "🚨",
                        "#E53935",
                        CategoryAction.ESCALATE,
                        true),
                new ClassificationCategory(
                        ID_WORK,
                        "Work",
                        "Messages from colleagues, clients, or about work projects, meetings, professional matters.",
                        "💼",
                        "#1E88E5",
                        CategoryAction.REPLY_TEMPLATE,
                        true),
                new ClassificationCategory(
                        ID_FAMILY_FRIENDS,
                        "Family & Friends",
                        "Personal messages from close relationships: family members, close friends, partner.",
                        "❤️",
                        "#43A047",
                        CategoryAction.REPLY_DEFAULT,
                        true),
                new ClassificationCategory(
                        ID_PROMOTIONAL,
                        "Promotional",
                        "Marketing messages, deals, newsletters, OTPs, transactional notifications, automated content.",
                        "📢",
                        "#FB8C00",
                        CategoryAction.SUPPRESS,
                        true),
                new ClassificationCategory(
                        ID_SPAM,
                        "Spam",
                        "Suspicious, scam, phishing, or unsolicited messages from unknown senders.",
                        "🚫",
                        "#8E24AA",
                        CategoryAction.SUPPRESS,
                        true),
                new ClassificationCategory(
                        ID_OTHER,
                        "Other",
                        "Messages that don't clearly fit the above categories.",
                        "💬",
                        "#757575",
                        CategoryAction.REPLY_DEFAULT,
                        true)
        ));
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    public CategoryAction getDefaultAction() { return defaultAction; }
    public void setDefaultAction(CategoryAction defaultAction) { this.defaultAction = defaultAction; }

    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
