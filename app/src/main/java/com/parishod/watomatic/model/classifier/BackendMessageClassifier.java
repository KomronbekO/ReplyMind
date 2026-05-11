package com.parishod.watomatic.model.classifier;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.parishod.watomatic.model.preferences.PreferencesManager;
import com.parishod.watomatic.network.AtomaticBackendGateway;
import com.parishod.watomatic.network.AtomaticBackendService;
import com.parishod.watomatic.network.model.backend.ClassifyRequest;
import com.parishod.watomatic.network.model.backend.ClassifyResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Primary classifier in the chain. Calls the local ReplyMind backend's /classify
 * endpoint. On error (network, timeout, 5xx, unknown category) defers to the
 * supplied {@code fallback} classifier so the chain can degrade gracefully:
 *
 * <pre>
 *   BackendMessageClassifier
 *     ↓ on error
 *   LlmMessageClassifier (BYOK)
 *     ↓ on error
 *   TemplateClassifier (always returns fallback)
 * </pre>
 */
public class BackendMessageClassifier implements MessageClassifier {

    private static final String TAG = "BackendClassifier";

    private final Context appContext;
    private final PreferencesManager prefs;
    private final AtomaticBackendGateway gateway;
    private final MessageClassifier fallback;

    public BackendMessageClassifier(Context context, MessageClassifier fallback) {
        this(context, PreferencesManager.getPreferencesInstance(context.getApplicationContext()),
                new AtomaticBackendGateway(PreferencesManager.getPreferencesInstance(context.getApplicationContext())),
                fallback);
    }

    /** Visible-for-testing constructor. */
    BackendMessageClassifier(Context context,
                             PreferencesManager prefs,
                             AtomaticBackendGateway gateway,
                             MessageClassifier fallback) {
        this.appContext = context.getApplicationContext();
        this.prefs = prefs;
        this.gateway = gateway;
        this.fallback = fallback;
    }

    @Override
    public boolean isAvailable() {
        // The chain is available if any link in it is. We never disable the chain
        // entirely — the template classifier always works.
        return prefs.isClassificationEnabled()
                && (gateway.isConfigured() || (fallback != null && fallback.isAvailable()));
    }

    @Override
    public void classify(@NonNull String senderTitle,
                         @NonNull String packageName,
                         @NonNull String incomingMessage,
                         @NonNull Callback callback) {

        if (!gateway.isConfigured()) {
            delegateToFallback(senderTitle, packageName, incomingMessage, callback,
                    "backend_disabled");
            return;
        }

        AtomaticBackendService service = gateway.service();
        if (service == null) {
            delegateToFallback(senderTitle, packageName, incomingMessage, callback,
                    "backend_misconfigured");
            return;
        }

        ClassifyRequest req = new ClassifyRequest();
        req.userId = prefs.getBackendUserId();
        req.sender = senderTitle;
        req.pkg = packageName;
        req.message = incomingMessage;
        req.profile = ClassifyRequest.ProfilePayload.from(prefs.getUserProfile());
        List<ClassificationCategory> cats = prefs.getClassificationCategories();
        req.categories = ClassifyRequest.categoriesFrom(cats);

        Call<ClassifyResponse> call = service.classify(gateway.bearerHeader(), req);
        call.enqueue(new retrofit2.Callback<ClassifyResponse>() {
            @Override
            public void onResponse(@NonNull Call<ClassifyResponse> call,
                                   @NonNull Response<ClassifyResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG, "backend non-2xx: " + response.code());
                    delegateToFallback(senderTitle, packageName, incomingMessage, callback,
                            "backend_http_" + response.code());
                    return;
                }
                ClassifyResponse body = response.body();
                if (!isKnownCategory(body.categoryId, cats)) {
                    Log.w(TAG, "backend returned unknown category: " + body.categoryId);
                    delegateToFallback(senderTitle, packageName, incomingMessage, callback,
                            "backend_unknown_category:" + body.categoryId);
                    return;
                }
                callback.onResult(new ClassificationResult(
                        body.categoryId,
                        body.confidence,
                        body.reasoning != null ? body.reasoning : "",
                        System.currentTimeMillis(),
                        /*fallback=*/false));
            }

            @Override
            public void onFailure(@NonNull Call<ClassifyResponse> call, @NonNull Throwable t) {
                Log.w(TAG, "backend call failed: " + t.getMessage());
                delegateToFallback(senderTitle, packageName, incomingMessage, callback,
                        "backend_error:" + safeMsg(t));
            }
        });
    }

    private void delegateToFallback(String sender, String pkg, String msg,
                                    @NonNull Callback finalCb, String reason) {
        if (fallback == null || !fallback.isAvailable()) {
            finalCb.onResult(ClassificationResult.fallback(reason));
            return;
        }
        fallback.classify(sender, pkg, msg, finalCb);
    }

    private static boolean isKnownCategory(String id, List<ClassificationCategory> cats) {
        if (id == null) return false;
        for (ClassificationCategory c : cats) {
            if (c != null && id.equalsIgnoreCase(c.getId())) return true;
        }
        return false;
    }

    private static String safeMsg(Throwable t) {
        return (t == null || t.getMessage() == null) ? "unknown" : t.getMessage();
    }
}
