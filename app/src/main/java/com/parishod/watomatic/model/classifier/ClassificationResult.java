package com.parishod.watomatic.model.classifier;

/**
 * Outcome of classifying one incoming message. Always non-null; {@link #fallback(String)} is used
 * when classification fails so callers can route via {@link CategoryAction#REPLY_DEFAULT} and the
 * user still gets a reply.
 */
public class ClassificationResult {
    private final String categoryId;
    private final float confidence;
    private final String reasoning;
    private final long classifiedAtMs;
    private final boolean fallback;

    public ClassificationResult(String categoryId, float confidence, String reasoning,
                                long classifiedAtMs, boolean fallback) {
        this.categoryId = categoryId;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.classifiedAtMs = classifiedAtMs;
        this.fallback = fallback;
    }

    public static ClassificationResult fallback(String reason) {
        return new ClassificationResult(
                ClassificationCategory.ID_OTHER, 0f, reason, System.currentTimeMillis(), true);
    }

    public String getCategoryId() { return categoryId; }
    public float getConfidence() { return confidence; }
    public String getReasoning() { return reasoning; }
    public long getClassifiedAtMs() { return classifiedAtMs; }
    public boolean isFallback() { return fallback; }
}
