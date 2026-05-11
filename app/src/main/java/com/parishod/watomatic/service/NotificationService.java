package com.parishod.watomatic.service;

import static com.parishod.watomatic.model.utils.Constants.DEFAULT_LLM_MODEL;
import static com.parishod.watomatic.model.utils.Constants.DEFAULT_LLM_PROMPT;

import android.app.PendingIntent;
import android.content.res.Resources;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
// import Constants.kt
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import com.parishod.watomatic.model.utils.Constants;


import androidx.annotation.NonNull;
import androidx.core.app.RemoteInput;

import com.parishod.watomatic.NotificationWear;
import com.parishod.watomatic.R;
import com.parishod.watomatic.model.CustomRepliesData;
import com.parishod.watomatic.model.classifier.CategoryAction;
import com.parishod.watomatic.model.classifier.CategoryActionRouter;
import com.parishod.watomatic.model.classifier.ClassificationResult;
import com.parishod.watomatic.model.classifier.LlmMessageClassifier;
import com.parishod.watomatic.model.classifier.MessageClassifier;
import com.parishod.watomatic.network.AtomaticAIService;
import com.parishod.watomatic.network.OpenAIService;
import com.parishod.watomatic.network.RetrofitInstance;
import com.parishod.watomatic.network.model.atomatic.AtomaticAIErrorResponse;
import com.parishod.watomatic.network.model.atomatic.AtomaticAIRequest;
import com.parishod.watomatic.network.model.atomatic.AtomaticAIResponse;
import com.parishod.watomatic.network.model.openai.Message;
import com.parishod.watomatic.network.model.openai.OpenAIRequest;
import com.parishod.watomatic.network.model.openai.OpenAIResponse;
import com.parishod.watomatic.model.preferences.PreferencesManager;
import com.parishod.watomatic.model.utils.ContactsHelper;
import com.parishod.watomatic.model.utils.DbUtils;
import com.parishod.watomatic.model.utils.NotificationHelper;
import com.parishod.watomatic.model.utils.NotificationUtils;
import com.parishod.watomatic.utils.FirebaseTokenRefresher;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class NotificationService extends NotificationListenerService {
    private final String TAG = NotificationService.class.getSimpleName();
    private DbUtils dbUtils;
    private NotificationReplyDecider replyDecider;
    private MessageClassifier classifier;
    private CategoryActionRouter router;

    private NotificationReplyDecider getReplyDecider() {
        if (replyDecider == null) {
            if (dbUtils == null) {
                dbUtils = new DbUtils(getApplicationContext());
            }
            replyDecider = new NotificationReplyDecider(
                    PreferencesManager.getPreferencesInstance(this),
                    dbUtils,
                    ContactsHelper.Companion.getInstance(this));
        }
        return replyDecider;
    }

    private MessageClassifier getClassifier() {
        if (classifier == null) {
            // Cascade: local backend → BYOK LLM → template-only.
            MessageClassifier templateLast =
                    new com.parishod.watomatic.model.classifier.TemplateClassifier();
            MessageClassifier llmThenTemplate = new ChainingClassifier(
                    new LlmMessageClassifier(getApplicationContext()), templateLast);
            classifier = new com.parishod.watomatic.model.classifier.BackendMessageClassifier(
                    getApplicationContext(), llmThenTemplate);
        }
        return classifier;
    }

    /**
     * Tiny adapter that lets {@link LlmMessageClassifier} fall back to a template-only
     * classifier when BYOK isn't configured. {@link LlmMessageClassifier#isAvailable()} is
     * the gate; if false we hop straight to the fallback.
     */
    private static final class ChainingClassifier implements MessageClassifier {
        private final MessageClassifier primary;
        private final MessageClassifier fallback;

        ChainingClassifier(MessageClassifier primary, MessageClassifier fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }

        @Override public boolean isAvailable() {
            return primary.isAvailable() || (fallback != null && fallback.isAvailable());
        }

        @Override public void classify(@androidx.annotation.NonNull String senderTitle,
                                       @androidx.annotation.NonNull String packageName,
                                       @androidx.annotation.NonNull String incomingMessage,
                                       @androidx.annotation.NonNull Callback callback) {
            if (primary.isAvailable()) {
                primary.classify(senderTitle, packageName, incomingMessage, callback);
            } else if (fallback != null) {
                fallback.classify(senderTitle, packageName, incomingMessage, callback);
            } else {
                callback.onResult(com.parishod.watomatic.model.classifier.ClassificationResult
                        .fallback("no_classifier_available"));
            }
        }
    }

    private CategoryActionRouter getRouter() {
        if (router == null) {
            router = new CategoryActionRouter(PreferencesManager.getPreferencesInstance(this));
        }
        return router;
    }

    private DbUtils getDbUtils() {
        if (dbUtils == null) {
            dbUtils = new DbUtils(getApplicationContext());
        }
        return dbUtils;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        NotificationReplyDecider decider = getReplyDecider();
        if (decider.canReply(sbn) && decider.shouldReply(sbn)) {
            sendReply(sbn);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        //START_STICKY  to order the system to restart your service as soon as possible when it was killed.
        return START_STICKY;
    }

    private void sendActualReply(StatusBarNotification sbn, NotificationWear notificationWear, String replyText) {
        // customRepliesData = CustomRepliesData.getInstance(this); // Initialize if other methods from it are needed beyond replyText

        RemoteInput finalRemoteIn = null;
        Intent localIntent = new Intent();
        localIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        Bundle localBundle = new Bundle(); // notificationWear.bundle;
        for (RemoteInput remoteIn : notificationWear.getRemoteInputs()) {
            if(remoteIn.getAllowFreeFormInput()) {
                finalRemoteIn = remoteIn;
                localBundle.putCharSequence(finalRemoteIn.getResultKey(), replyText);
                break;
            }
        }

        if(finalRemoteIn == null) return;

        RemoteInput.addResultsToIntent(new RemoteInput[]{ finalRemoteIn }, localIntent, localBundle);
        try {
            if (notificationWear.getPendingIntent() != null) {
                if (dbUtils == null) {
                    dbUtils = new DbUtils(getApplicationContext());
                }
                dbUtils.logReply(sbn, NotificationUtils.getTitle(sbn));
                
                // Use ReplyService to send the reply in foreground
                /*Intent replyServiceIntent = new Intent(this, ReplyService.class);
                replyServiceIntent.putExtra("pendingIntent", notificationWear.getPendingIntent());
                replyServiceIntent.putExtra("fillInIntent", localIntent);
                
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(replyServiceIntent);
                    } else {
                        startService(replyServiceIntent);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start ReplyService: " + e.getMessage());
                    // Fallback to direct send if service start fails (though likely to fail for FB)
                    notificationWear.getPendingIntent().send(this, 0, localIntent);
                }*/

                notificationWear.getPendingIntent().send(this, 0, localIntent);
                if (PreferencesManager.getPreferencesInstance(this).isShowNotificationEnabled()) {
                    NotificationHelper.getInstance(getApplicationContext()).sendNotification(sbn.getNotification().extras.getString("android.title"), sbn.getNotification().extras.getString("android.text"), sbn.getPackageName());
                }
                cancelNotification(sbn.getKey());
                if (canPurgeMessages()) {
                    dbUtils.purgeMessageLogs();
                    PreferencesManager.getPreferencesInstance(this).setPurgeMessageTime(System.currentTimeMillis());
                }
            }
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "sendActualReply error: " + e.getLocalizedMessage());
        }
    }

    private void sendReply(StatusBarNotification sbn) {
        final NotificationWear notificationWear = NotificationUtils.extractWearNotification(sbn);
        if (notificationWear.getRemoteInputs().isEmpty()) {
            return;
        }

        PreferencesManager preferencesManager = PreferencesManager.getPreferencesInstance(this);
        CustomRepliesData customRepliesData = CustomRepliesData.getInstance(this); // For fallback
        String replyText = customRepliesData.getTextToSendOrElse();
        if(preferencesManager.isAnyAiRepliesEnabled()){
            try {
                replyText = getString(R.string.auto_reply_default_message);
            } catch (Resources.NotFoundException e) {
                replyText = "I am currently busy. Will reply later.";
            }
        }
        final String fallbackReplyText = replyText; // needs to be final to access in inner class hence one more variable

        CharSequence incomingMessageChars = sbn.getNotification().extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
        final String incomingMessage = (incomingMessageChars != null) ? incomingMessageChars.toString() : null;

        // -------------------------------------------------------------------
        // ReplyMind: Vacation Mode short-circuit. If the user is on vacation, skip the
        // classifier entirely and send the configured vacation message. Saves LLM calls and
        // guarantees consistent vacation behavior regardless of how a message is classified.
        // -------------------------------------------------------------------
        if (preferencesManager.isVacationModeActive()) {
            String vacationMsg = preferencesManager.getVacationMessage();
            if (vacationMsg == null || vacationMsg.trim().isEmpty()) {
                vacationMsg = fallbackReplyText;
            }
            Log.i(TAG, "Vacation Mode active — using vacation reply.");
            // We don't have a classification here; persist as a default-replied row with no
            // category (Inbox will render it as Unclassified with action "Replied").
            sendActualReplyWithMeta(sbn, notificationWear, vacationMsg, null, null, incomingMessage);
            return;
        }

        // -------------------------------------------------------------------
        // ReplyMind: classification gate. If the user has classification on AND has BYOK
        // configured AND we have a non-empty body, classify first and route via the user's
        // per-category action. Otherwise fall through to the legacy reply flow unchanged.
        // -------------------------------------------------------------------
        final MessageClassifier mc = getClassifier();
        final boolean canClassify = mc.isAvailable()
                && incomingMessage != null
                && !incomingMessage.trim().isEmpty();

        if (!canClassify) {
            proceedWithLegacyReplyFlow(sbn, notificationWear, incomingMessage, fallbackReplyText, null);
            return;
        }

        final String senderTitle = NotificationUtils.getTitle(sbn);
        mc.classify(senderTitle, sbn.getPackageName(), incomingMessage, new MessageClassifier.Callback() {
            @Override
            public void onResult(@NonNull final ClassificationResult result) {
                Log.d(TAG, "Classification: " + result.getCategoryId()
                        + " conf=" + result.getConfidence()
                        + (result.isFallback() ? " [fallback]" : "")
                        + " — " + result.getReasoning());

                getRouter().route(result, senderTitle, new CategoryActionRouter.RoutingCallbacks() {
                    @Override
                    public void doDefaultReplyFlow(@NonNull ClassificationResult r) {
                        proceedWithLegacyReplyFlow(sbn, notificationWear, incomingMessage, fallbackReplyText, r);
                    }

                    @Override
                    public void sendTemplateReply(@NonNull String templateText, @NonNull ClassificationResult r) {
                        String finalText = maybePrependOutOfHoursPrefix(templateText);
                        sendActualReplyWithMeta(sbn, notificationWear, finalText,
                                r, CategoryAction.REPLY_TEMPLATE, incomingMessage);
                    }

                    @Override
                    public void suppressReply(@NonNull ClassificationResult r) {
                        Log.i(TAG, "Suppressing reply for category " + r.getCategoryId());
                        logSuppressed(sbn, r, CategoryAction.SUPPRESS, incomingMessage);
                    }

                    @Override
                    public void escalate(@NonNull ClassificationResult r) {
                        Log.i(TAG, "Escalating: leaving original notification visible. Category=" + r.getCategoryId());
                        logSuppressed(sbn, r, CategoryAction.ESCALATE, incomingMessage);
                    }
                });
            }
        });
    }

    /** Run the original (non-classified) reply path: AI or canned reply. */
    private void proceedWithLegacyReplyFlow(StatusBarNotification sbn,
                                            NotificationWear notificationWear,
                                            String incomingMessage,
                                            String fallbackReplyText,
                                            ClassificationResult classification) {
        PreferencesManager preferencesManager = PreferencesManager.getPreferencesInstance(this);

        // 1. "Automatic AI" mode = use the reply the local backend already
        //    generated as part of the classify call. No subscription, no
        //    external LLM call. Falls through if no suggestion is available.
        if (preferencesManager.isAutomaticAiRepliesEnabled()
                && classification != null
                && classification.getSuggestedReply() != null
                && !classification.getSuggestedReply().trim().isEmpty()) {
            Log.d(TAG, "Automatic AI: using backend-suggested reply");
            sendActualReplyWithMeta(sbn, notificationWear,
                    classification.getSuggestedReply(),
                    classification, CategoryAction.REPLY_DEFAULT, incomingMessage);
            return;
        }

        // 2. BYOK still works — for users who provided their own OpenAI-
        //    compatible key in Settings → Other AI.
        if (preferencesManager.isByokRepliesEnabled()) {
            String apiKey = preferencesManager.getOpenAIApiKey();
            boolean haveKey = apiKey != null && !apiKey.trim().isEmpty()
                    && !apiKey.equals("PENDING_CONFIGURATION");
            boolean haveBody = incomingMessage != null && !incomingMessage.trim().isEmpty();
            Log.d(TAG, "BYOK mode - API key configured: " + haveKey);
            if (haveKey && haveBody) {
                fetchAiReply(sbn, notificationWear, incomingMessage, fallbackReplyText);
                return;
            }
        }

        // 3. Plain canned reply, with classifier metadata persisted if we have it.
        Log.d(TAG, "AI conditions not met. Using default reply.");
        if (classification != null) {
            sendActualReplyWithMeta(sbn, notificationWear, fallbackReplyText,
                    classification, CategoryAction.REPLY_DEFAULT, incomingMessage);
        } else {
            sendActualReply(sbn, notificationWear, fallbackReplyText);
        }
    }

    /**
     * Send reply, persisting classifier metadata on the message log row.
     * Used for category-template replies and (in the absence of an AI fetch) the default reply.
     */
    private void sendActualReplyWithMeta(StatusBarNotification sbn,
                                         NotificationWear notificationWear,
                                         String replyText,
                                         ClassificationResult result,
                                         CategoryAction action,
                                         String incomingBody) {
        RemoteInput finalRemoteIn = null;
        Intent localIntent = new Intent();
        localIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        Bundle localBundle = new Bundle();
        for (RemoteInput remoteIn : notificationWear.getRemoteInputs()) {
            if (remoteIn.getAllowFreeFormInput()) {
                finalRemoteIn = remoteIn;
                localBundle.putCharSequence(finalRemoteIn.getResultKey(), replyText);
                break;
            }
        }
        if (finalRemoteIn == null) return;

        RemoteInput.addResultsToIntent(new RemoteInput[]{finalRemoteIn}, localIntent, localBundle);
        try {
            if (notificationWear.getPendingIntent() != null) {
                getDbUtils().logReply(sbn, NotificationUtils.getTitle(sbn),
                        replyText, result, action, true, incomingBody);

                notificationWear.getPendingIntent().send(this, 0, localIntent);
                if (PreferencesManager.getPreferencesInstance(this).isShowNotificationEnabled()) {
                    NotificationHelper.getInstance(getApplicationContext()).sendNotification(
                            sbn.getNotification().extras.getString("android.title"),
                            sbn.getNotification().extras.getString("android.text"),
                            sbn.getPackageName());
                }
                cancelNotification(sbn.getKey());
                if (canPurgeMessages()) {
                    getDbUtils().purgeMessageLogs();
                    PreferencesManager.getPreferencesInstance(this).setPurgeMessageTime(System.currentTimeMillis());
                }
            }
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "sendActualReplyWithMeta error: " + e.getLocalizedMessage());
        }
    }

    /**
     * If the user has set working hours and the current local time falls outside them,
     * prepend an "[Outside my working hours]" tag to the reply text. Skips when the user
     * hasn't configured a sensible window (start == end).
     */
    private String maybePrependOutOfHoursPrefix(String replyText) {
        if (replyText == null) return null;
        com.parishod.watomatic.model.classifier.UserProfile profile =
                PreferencesManager.getPreferencesInstance(this).getUserProfile();
        if (profile == null) return replyText;
        int start = profile.getWorkingHoursStartMinuteOfDay();
        int end = profile.getWorkingHoursEndMinuteOfDay();
        if (start == end) return replyText;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
        boolean isInsideHours;
        if (start < end) {
            isInsideHours = nowMin >= start && nowMin < end;
        } else {
            // Wraps midnight (e.g., 22:00 → 06:00 night shift).
            isInsideHours = nowMin >= start || nowMin < end;
        }
        if (isInsideHours) return replyText;
        try {
            return getString(R.string.ooh_prefix) + replyText;
        } catch (Resources.NotFoundException e) {
            return "[Outside my working hours] " + replyText;
        }
    }

    /** Persist a "no reply sent" log row for SUPPRESS/ESCALATE so Inbox can render it. */
    private void logSuppressed(StatusBarNotification sbn,
                               ClassificationResult result,
                               CategoryAction action,
                               String incomingBody) {
        getDbUtils().logReply(sbn,
                NotificationUtils.getTitle(sbn),
                /*repliedMsg=*/ null,
                result,
                action,
                /*wasReplied=*/ false,
                incomingBody);
    }

    private void fetchAiReply(StatusBarNotification sbn, NotificationWear notificationWear, String incomingMessage, String fallbackReplyText) {
        PreferencesManager prefs = PreferencesManager.getPreferencesInstance(this);

        // Determine which backend API to use based on the selected reply method
        String apiKey;
        String provider;
        String baseUrl;

        if (prefs.isAutomaticAiRepliesEnabled()) {
            // Automatic AI: Use server-side API (Atomatic backend)
            Log.d(TAG, "Using Automatic AI (server-based) backend");
            fetchAtomaticAiReply(sbn, notificationWear, incomingMessage, fallbackReplyText);
            return;
        } else if (prefs.isByokRepliesEnabled()) {
            // BYOK: Use user's own API key with configured provider
            Log.d(TAG, "Using BYOK (client-based) backend");
            apiKey = prefs.getOpenAIApiKey();
            provider = prefs.getOpenApiSource();
            if (provider == null) provider = "OpenAI";
        } else {
            // No AI mode enabled, use fallback
            sendActualReply(sbn, notificationWear, fallbackReplyText);
            return;
        }

        // BYOK flow continues here
        String model = prefs.getSelectedOpenAIModel();
        String systemPrompt = prefs.getOpenAICustomPrompt();
        if (systemPrompt == null || systemPrompt.trim().isEmpty()) systemPrompt = DEFAULT_LLM_PROMPT;
        if (model == null || model.isEmpty()) model = DEFAULT_LLM_MODEL;

        // Mix in the user's profile (display name, occupation, tone, relationships)
        // so the LLM speaks as them in first person and recognises VIP senders.
        systemPrompt = enrichSystemPromptWithProfile(systemPrompt, prefs);

        // Prepend the sender's name to the message so the model can reference
        // who's writing — "Message from Mum: …" beats raw "…" for context.
        String senderTitle = NotificationUtils.getTitle(sbn);
        String enrichedIncoming = (senderTitle == null || senderTitle.isEmpty())
                ? incomingMessage
                : "Message from " + senderTitle + ":\n" + incomingMessage;

        baseUrl = Constants.INSTANCE.getPROVIDER_URLS().get(provider);
        if ("Custom".equals(provider)) {
            baseUrl = prefs.getCustomOpenAIApiUrl();
        }
        if (baseUrl == null) baseUrl = "https://api.openai.com/";

        if (!baseUrl.endsWith("/")) baseUrl += "/";

        OpenAIService service = RetrofitInstance.getOpenAIRetrofitInstance(baseUrl).create(OpenAIService.class);

        if ("Claude".equals(provider)) {
            fetchClaudeReply(service, baseUrl, apiKey, model, systemPrompt, enrichedIncoming, sbn, notificationWear, fallbackReplyText);
        } else if ("Gemini".equals(provider)) {
            fetchGeminiReply(service, baseUrl, apiKey, model, systemPrompt, enrichedIncoming, sbn, notificationWear, fallbackReplyText);
        } else {
            // OpenAI, Grok, DeepSeek, Mistral, Custom
            fetchOpenAiCompatibleReply(service, apiKey, model, systemPrompt, enrichedIncoming, sbn, notificationWear, fallbackReplyText);
        }
    }

    /** Append a "You are <name>" preamble built from the user's profile to the
     *  base system prompt. Falls back to the raw prompt when the profile is
     *  empty. Kept inline so the existing fetch helpers don't need re-plumbing. */
    private static String enrichSystemPromptWithProfile(String basePrompt,
                                                        PreferencesManager prefs) {
        com.parishod.watomatic.model.classifier.UserProfile p;
        try {
            p = prefs.getUserProfile();
        } catch (Throwable t) {
            return basePrompt;
        }
        if (p == null) return basePrompt;
        StringBuilder sb = new StringBuilder();
        String name = p.getDisplayName();
        if (name != null && !name.trim().isEmpty()) {
            sb.append("You are ").append(name.trim()).append(". ");
        }
        String occ = p.getOccupation();
        if (occ != null && !occ.trim().isEmpty()) {
            sb.append("Your occupation: ").append(occ.trim()).append(". ");
        }
        com.parishod.watomatic.model.classifier.UserProfile.Tone tone = p.getTone();
        if (tone != null) {
            String hint;
            switch (tone.name()) {
                case "PROFESSIONAL": hint = "Keep replies polite and professional."; break;
                case "BRIEF":        hint = "Keep replies very short — 1 short sentence."; break;
                case "CASUAL":
                default:             hint = "Keep replies warm and casual, like a friend."; break;
            }
            sb.append(hint).append(" ");
        }
        java.util.List<com.parishod.watomatic.model.classifier.KeyRelationship> rels =
                p.getKeyRelationships();
        if (rels != null && !rels.isEmpty()) {
            sb.append("Key people in your life: ");
            for (int i = 0; i < rels.size() && i < 6; i++) {
                com.parishod.watomatic.model.classifier.KeyRelationship r = rels.get(i);
                if (r == null) continue;
                if (i > 0) sb.append(", ");
                sb.append(r.getName()).append(" (").append(r.getRole()).append(")");
            }
            sb.append(". ");
        }
        String ctx = p.getAdditionalContext();
        if (ctx != null && !ctx.trim().isEmpty()) {
            sb.append("Extra context: ").append(ctx.trim()).append(" ");
        }
        if (sb.length() == 0) return basePrompt;
        sb.append("\n\n").append(basePrompt);
        return sb.toString();
    }

    private void fetchAtomaticAiReply(StatusBarNotification sbn, NotificationWear notificationWear, String incomingMessage, String fallbackReplyText) {
        // Call the method without retry flag (first attempt)
        fetchAtomaticAiReplyInternal(sbn, notificationWear, incomingMessage, fallbackReplyText, false);
    }

    private void fetchAtomaticAiReplyInternal(StatusBarNotification sbn, NotificationWear notificationWear, String incomingMessage, String defaultReply, boolean isRetryAfterTokenRefresh) {
        PreferencesManager prefs = PreferencesManager.getPreferencesInstance(this);

        String fallbackReply;
        if(!TextUtils.isEmpty(prefs.getFallbackMessage())){
            fallbackReply = prefs.getFallbackMessage();
        } else {
            fallbackReply = defaultReply;
        }
        // Get Firebase ID token
        String firebaseToken = prefs.getFirebaseToken();

        if (firebaseToken == null || firebaseToken.trim().isEmpty()) {
            Log.e(TAG, "Firebase token not available, falling back to default reply");
            sendActualReply(sbn, notificationWear, fallbackReply);
            return;
        }

        // Create the request
        AtomaticAIRequest request = new AtomaticAIRequest(incomingMessage, prefs.getAtomaticAICustomPrompt());

        // Create the service
        AtomaticAIService service = RetrofitInstance.getAtomaticAIRetrofitInstance()
                .create(AtomaticAIService.class);

        // Make the API call
        String authHeader = "Bearer " + firebaseToken;
        Log.d(TAG, "Atomatic AI API call - isRetry: " + isRetryAfterTokenRefresh);

        service.getAIReply(authHeader, "application/json", request).enqueue(new Callback<AtomaticAIResponse>() {
            @Override
            public void onResponse(@NonNull Call<AtomaticAIResponse> call, @NonNull Response<AtomaticAIResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AtomaticAIResponse aiResponse = response.body();
                    String reply = aiResponse.getReply();
                    int remainingAtoms = aiResponse.getRemainingAtoms();

                    if (reply != null && !reply.trim().isEmpty()) {
                        Log.i(TAG, "Atomatic AI successful response. Remaining atoms: " + remainingAtoms);
                        prefs.setRemainingAtoms(remainingAtoms);
                        checkAndNotifyQuotaExhausted(remainingAtoms);
                        sendActualReply(sbn, notificationWear, reply);
                    } else {
                        Log.e(TAG, "Atomatic AI returned empty reply, using fallback");
                        sendActualReply(sbn, notificationWear, fallbackReply);
                    }
                } else {
                    // Check if this is an authentication error (401 or 403)
                    boolean isAuthError = response.code() == 401 || response.code() == 403;

                    // Try to parse error response
                    if (!isAuthError && response.errorBody() != null) {
                        AtomaticAIErrorResponse errorResponse = RetrofitInstance.parseAtomaticAIError(response);
                        if (errorResponse != null) {
                            isAuthError = errorResponse.isAuthError();
                            Log.e(TAG, "Atomatic AI error: " + errorResponse.getMessage());
                        }
                    }

                    // If it's an auth error and we haven't retried yet, refresh token and retry
                    if (isAuthError && !isRetryAfterTokenRefresh) {
                        Log.w(TAG, "Atomatic AI authentication failed (code: " + response.code() + "). Attempting token refresh...");
                        handleTokenExpirationAndRetry(sbn, notificationWear, incomingMessage, fallbackReply);
                    } else {
                        // Either not an auth error, or already retried - use fallback
                        if (isRetryAfterTokenRefresh) {
                            Log.e(TAG, "Atomatic AI still failed after token refresh, using fallback");
                        } else {
                            Log.e(TAG, "Atomatic AI API failed: " + response.code() + " " + response.message());
                        }
                        sendActualReply(sbn, notificationWear, fallbackReply);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<AtomaticAIResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Atomatic AI API network error", t);
                sendActualReply(sbn, notificationWear, defaultReply);
            }
        });
    }

    private void handleTokenExpirationAndRetry(StatusBarNotification sbn, NotificationWear notificationWear, String incomingMessage, String fallbackReplyText) {
        Log.i(TAG, "Handling token expiration - refreshing Firebase token...");

        // Refresh token asynchronously to avoid blocking the main thread
        FirebaseTokenRefresher.refreshTokenAsync(this, new FirebaseTokenRefresher.TokenRefreshCallback() {
            @Override
            public void onSuccess(String newToken) {
                Log.i(TAG, "Token refresh successful. Retrying Atomatic AI request...");
                // Retry the request with the new token (pass true to indicate this is a retry)
                fetchAtomaticAiReplyInternal(sbn, notificationWear, incomingMessage, fallbackReplyText, true);
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Token refresh failed: " + error + ". Using fallback reply.");
                sendActualReply(sbn, notificationWear, fallbackReplyText);
            }
        });
    }

    private void fetchClaudeReply(OpenAIService service, String baseUrl, String apiKey, String model, String systemPrompt, String incomingMessage, StatusBarNotification sbn, NotificationWear notificationWear, String fallbackReplyText) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("max_tokens", 1024);
        requestBody.addProperty("system", systemPrompt);

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", incomingMessage);
        messages.add(userMessage);
        requestBody.add("messages", messages);

        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", "2023-06-01");
        headers.put("content-type", "application/json");

        String url = baseUrl + "v1/messages";

        service.getClaudeCompletion(url, headers, requestBody).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JsonArray content = response.body().getAsJsonArray("content");
                        if (content != null && content.size() > 0) {
                            String reply = content.get(0).getAsJsonObject().get("text").getAsString();
                            sendActualReply(sbn, notificationWear, reply);
                            return;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing Claude response", e);
                    }
                }
                Log.e(TAG, "Claude API failed: " + response.code() + " " + response.message());
                sendActualReply(sbn, notificationWear, fallbackReplyText);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "Claude API network error", t);
                sendActualReply(sbn, notificationWear, fallbackReplyText);
            }
        });
    }

    private void fetchGeminiReply(OpenAIService service, String baseUrl, String apiKey, String model, String systemPrompt, String incomingMessage, StatusBarNotification sbn, NotificationWear notificationWear, String fallbackReplyText) {
        JsonObject requestBody = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        // Combine system prompt and user message for simplicity
        part.addProperty("text", systemPrompt + "\n\nUser: " + incomingMessage);
        parts.add(part);
        contentObj.add("parts", parts);
        contents.add(contentObj);
        requestBody.add("contents", contents);

        String url = baseUrl + "v1beta/models/" + model + ":generateContent";

        service.getGeminiCompletion(url, apiKey, requestBody).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JsonArray candidates = response.body().getAsJsonArray("candidates");
                        if (candidates != null && candidates.size() > 0) {
                            JsonObject candidate = candidates.get(0).getAsJsonObject();
                            JsonObject content = candidate.getAsJsonObject("content");
                            JsonArray parts = content.getAsJsonArray("parts");
                            if (parts != null && parts.size() > 0) {
                                String reply = parts.get(0).getAsJsonObject().get("text").getAsString();
                                sendActualReply(sbn, notificationWear, reply);
                                return;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing Gemini response", e);
                    }
                }
                Log.e(TAG, "Gemini API failed: " + response.code() + " " + response.message());
                sendActualReply(sbn, notificationWear, fallbackReplyText);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "Gemini API network error", t);
                sendActualReply(sbn, notificationWear, fallbackReplyText);
            }
        });
    }

    private void fetchOpenAiCompatibleReply(OpenAIService service, String apiKey, String model, String systemPrompt, String incomingMessage, StatusBarNotification sbn, NotificationWear notificationWear, String fallbackReplyText) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt));
        messages.add(new Message("user", incomingMessage));

        OpenAIRequest request = new OpenAIRequest(model, messages);
        String bearerToken = "Bearer " + apiKey;

        service.getChatCompletion(bearerToken, request).enqueue(new Callback<OpenAIResponse>() {
            @Override
            public void onResponse(@NonNull Call<OpenAIResponse> call, @NonNull Response<OpenAIResponse> response) {
                if (response.isSuccessful() && response.body() != null &&
                    response.body().getChoices() != null && !response.body().getChoices().isEmpty() &&
                    response.body().getChoices().get(0).getMessage() != null &&
                    response.body().getChoices().get(0).getMessage().getContent() != null) {

                    String aiReply = response.body().getChoices().get(0).getMessage().getContent().trim();
                    Log.i(TAG, "OpenAI/Compatible successful response: " + aiReply);
                    sendActualReply(sbn, notificationWear, aiReply);
                } else {
                    Log.e(TAG, "OpenAI/Compatible API failed: " + response.code() + " " + response.message());
                    // Fallback to default reply
                    sendActualReply(sbn, notificationWear, fallbackReplyText);
                }
            }

            @Override
            public void onFailure(@NonNull Call<OpenAIResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "OpenAI/Compatible API network error", t);
                sendActualReply(sbn, notificationWear, fallbackReplyText);
            }
        });
    }

    private boolean canPurgeMessages() {
        //Added L to avoid numeric overflow expression
        //https://stackoverflow.com/questions/43801874/numeric-overflow-in-expression-manipulating-timestamps
        long daysBeforePurgeInMS = 30 * 24 * 60 * 60 * 1000L;
        return (System.currentTimeMillis() - PreferencesManager.getPreferencesInstance(this).getLastPurgedTime()) > daysBeforePurgeInMS;
    }

    // Decision logic (canReply, shouldReply, isSupportedPackage, canSendReplyNow,
    // isGroupMessageAndReplyAllowed, isServiceEnabled) is in NotificationReplyDecider.

    /**
     * Check if the user's quota is exhausted and show a notification if needed.
     * Rate-limited to at most once every 24 hours.
     */
    private void checkAndNotifyQuotaExhausted(int remainingAtoms) {
        Log.d("QuotaExhaustedChecker", "Remaining atoms: " + remainingAtoms);
        if (remainingAtoms > 0) return;

        PreferencesManager prefs = PreferencesManager.getPreferencesInstance(this);
        long lastShown = prefs.getQuotaNotificationLastShown();
        long now = System.currentTimeMillis();
        long TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L;

        if (lastShown == 0 || (now - lastShown) >= TWENTY_FOUR_HOURS_MS) {
            prefs.setQuotaNotificationLastShown(now);

            // Determine if user is on highest plan (pro)
            boolean isHighestPlan = isOnHighestPlan(prefs);
            String renewalDate = null;
            if (isHighestPlan) {
                long expiryTime = prefs.getSubscriptionExpiryTime();
                if (expiryTime > 0) {
                    renewalDate = new java.text.SimpleDateFormat(
                            "MMMM dd, yyyy", java.util.Locale.getDefault()
                    ).format(new java.util.Date(expiryTime));
                }
            }
            NotificationUtils.showQuotaExhaustedNotification(this, isHighestPlan, renewalDate);
        }
    }

    /**
     * Check if the user is on the highest subscription plan (pro).
     */
    private boolean isOnHighestPlan(PreferencesManager prefs) {
        String productId = prefs.getSubscriptionProductId();
        return productId != null && productId.toLowerCase().contains("pro");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "Listener disconnected! Requesting rebind...");
        ComponentName componentName = new ComponentName(this, NotificationService.class);
        requestRebind(componentName);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Toast.makeText(getApplicationContext(), "Listener connected!", Toast.LENGTH_SHORT).show();
    }

}
