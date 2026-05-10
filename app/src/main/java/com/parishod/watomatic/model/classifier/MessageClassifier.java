package com.parishod.watomatic.model.classifier;

import androidx.annotation.NonNull;

/**
 * Asynchronously classifies an incoming message into one of the user's configured
 * {@link ClassificationCategory} ids.
 *
 * <p>Implementations MUST always invoke {@link Callback#onResult(ClassificationResult)} exactly
 * once, even on failure (using {@link ClassificationResult#fallback(String)}). This contract
 * frees callers from defensive timeouts.</p>
 */
public interface MessageClassifier {

    interface Callback {
        void onResult(@NonNull ClassificationResult result);
    }

    /** True when the classifier is configured (BYOK + classification enabled in settings). */
    boolean isAvailable();

    void classify(@NonNull String senderTitle,
                  @NonNull String packageName,
                  @NonNull String incomingMessage,
                  @NonNull Callback callback);
}
