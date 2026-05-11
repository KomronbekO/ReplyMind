package com.parishod.watomatic.model.classifier;

import androidx.annotation.NonNull;

/**
 * Last-resort classifier. Always reports a fallback result so the calling router
 * falls into the user's default reply path. Keeps the chain total — no path
 * leaves the caller without a callback.
 */
public class TemplateClassifier implements MessageClassifier {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void classify(@NonNull String senderTitle,
                         @NonNull String packageName,
                         @NonNull String incomingMessage,
                         @NonNull Callback callback) {
        callback.onResult(ClassificationResult.fallback("template_only"));
    }
}
