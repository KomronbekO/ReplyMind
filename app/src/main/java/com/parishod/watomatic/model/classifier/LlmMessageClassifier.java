package com.parishod.watomatic.model.classifier;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.parishod.watomatic.model.preferences.PreferencesManager;
import com.parishod.watomatic.network.AiCompletionGateway;

import java.util.List;

/**
 * Default {@link MessageClassifier} implementation. Runs classification through an LLM via
 * {@link AiCompletionGateway}, with sender-keyed caching to dedup re-classifications inside the
 * cache TTL window.
 */
public class LlmMessageClassifier implements MessageClassifier {

    private static final String TAG = "LlmMessageClassifier";

    private final Context appContext;
    private final PreferencesManager prefs;
    private final PromptBuilder promptBuilder;
    private final ClassificationCache cache;
    private final AiCompletionGateway gateway;

    public LlmMessageClassifier(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = PreferencesManager.getPreferencesInstance(appContext);
        this.promptBuilder = new PromptBuilder();
        this.cache = new ClassificationCache(prefs.getClassificationCacheTtlMs());
        this.gateway = new AiCompletionGateway(appContext);
    }

    /** Visible-for-testing constructor that lets us inject fakes. */
    LlmMessageClassifier(Context context,
                         PreferencesManager prefs,
                         PromptBuilder promptBuilder,
                         ClassificationCache cache,
                         AiCompletionGateway gateway) {
        this.appContext = context.getApplicationContext();
        this.prefs = prefs;
        this.promptBuilder = promptBuilder;
        this.cache = cache;
        this.gateway = gateway;
    }

    @Override
    public boolean isAvailable() {
        // Classification only works against a BYOK provider for now (Atomatic backend has no
        // /classify endpoint). The user must also have explicitly enabled it.
        return prefs.isClassificationEnabled() && prefs.isByokRepliesEnabled();
    }

    @Override
    public void classify(@NonNull String senderTitle,
                         @NonNull String packageName,
                         @NonNull String incomingMessage,
                         @NonNull final Callback callback) {

        ClassificationResult cached = cache.get(packageName, senderTitle);
        if (cached != null) {
            callback.onResult(cached);
            return;
        }

        final UserProfile profile = prefs.getUserProfile();
        final List<ClassificationCategory> categories = prefs.getClassificationCategories();
        String systemPrompt = promptBuilder.buildClassificationSystemPrompt(profile, categories);
        String userMessage = promptBuilder.buildClassificationUserMessage(senderTitle, incomingMessage);

        try {
            gateway.complete(systemPrompt, userMessage, /*forceJson=*/true,
                    new AiCompletionGateway.Callback() {
                        @Override
                        public void onSuccess(@NonNull String text) {
                            ClassificationResult r = parseOrFallback(text, categories);
                            cache.put(packageName, senderTitle, r);
                            callback.onResult(r);
                        }

                        @Override
                        public void onError(@NonNull Throwable t) {
                            Log.w(TAG, "Classifier network error", t);
                            callback.onResult(ClassificationResult.fallback(
                                    "classifier_error: " + safeMsg(t)));
                        }
                    });
        } catch (Exception e) {
            // Defensive — gateway should never throw synchronously, but keep the contract.
            Log.e(TAG, "Classifier dispatch failed", e);
            callback.onResult(ClassificationResult.fallback("dispatch_error: " + safeMsg(e)));
        }
    }

    /** Visible-for-testing parse helper. */
    static ClassificationResult parseOrFallback(String raw, List<ClassificationCategory> cats) {
        if (raw == null) return ClassificationResult.fallback("empty_response");
        try {
            String json = extractJsonObject(raw);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String catId = obj.has("category") ? obj.get("category").getAsString().trim().toLowerCase() : "";
            float conf = obj.has("confidence") ? obj.get("confidence").getAsFloat() : 0.5f;
            String reasoning = obj.has("reasoning") ? obj.get("reasoning").getAsString() : "";

            boolean known = false;
            for (ClassificationCategory c : cats) {
                if (c == null) continue;
                if (c.getId() != null && c.getId().equalsIgnoreCase(catId)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                return ClassificationResult.fallback("unknown_category:" + catId);
            }
            return new ClassificationResult(catId, conf, reasoning, System.currentTimeMillis(), false);
        } catch (Exception e) {
            return ClassificationResult.fallback("parse_error: " + safeMsg(e));
        }
    }

    /**
     * Extract the first balanced JSON object from a model response. Handles common cases:
     * leading prose, trailing prose, or {@code ```json ... ```} fences.
     */
    static String extractJsonObject(String s) {
        if (s == null) throw new IllegalStateException("null response");
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("no JSON object in response");
        }
        return s.substring(start, end + 1);
    }

    private static String safeMsg(Throwable t) {
        return (t == null || t.getMessage() == null) ? "unknown" : t.getMessage();
    }
}
