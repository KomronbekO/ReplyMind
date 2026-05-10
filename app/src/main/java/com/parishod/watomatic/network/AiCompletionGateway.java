package com.parishod.watomatic.network;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.parishod.watomatic.model.preferences.PreferencesManager;
import com.parishod.watomatic.model.utils.Constants;
import com.parishod.watomatic.network.model.openai.Message;
import com.parishod.watomatic.network.model.openai.OpenAIRequest;
import com.parishod.watomatic.network.model.openai.OpenAIResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Single entry point for asynchronous LLM completions used by ReplyMind features that need raw
 * text out (classification, smart suggestions, future Insights summaries).
 *
 * <p>Routes through the user's chosen BYOK provider, mirroring the per-provider request shapes
 * already used by {@code NotificationService} for reply generation. Atomatic backend is not
 * supported here — that flow has special Firebase-token retry logic and lives in NotificationService.
 *
 * <p>Always invokes {@link Callback#onError(Throwable)} on failure so callers don't need to guard
 * against silent drops. Retrofit dispatches callbacks on its own executor, off the listener thread.
 */
public class AiCompletionGateway {

    public interface Callback {
        void onSuccess(@NonNull String text);

        void onError(@NonNull Throwable t);
    }

    private static final String TAG = "AiCompletionGateway";
    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/";

    private final Context appContext;

    public AiCompletionGateway(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Issue a chat completion against the user's configured BYOK provider.
     *
     * @param forceJson when true, OpenAI-compatible providers receive a {@code response_format}
     *                  hint requesting strict JSON. Claude/Gemini ignore this — the prompt itself
     *                  must instruct JSON-only output.
     */
    public void complete(@NonNull String systemPrompt,
                         @NonNull String userMessage,
                         boolean forceJson,
                         @NonNull Callback callback) {
        PreferencesManager prefs = PreferencesManager.getPreferencesInstance(appContext);

        if (!prefs.isByokRepliesEnabled()) {
            callback.onError(new IllegalStateException(
                    "AiCompletionGateway requires BYOK to be enabled (no Automatic AI route here)"));
            return;
        }

        String apiKey = prefs.getOpenAIApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onError(new IllegalStateException("BYOK API key not configured"));
            return;
        }

        String provider = prefs.getOpenApiSource();
        if (provider == null || provider.trim().isEmpty()) provider = "OpenAI";

        String baseUrl = Constants.INSTANCE.getPROVIDER_URLS().get(provider);
        if ("Custom".equals(provider)) {
            baseUrl = prefs.getCustomOpenAIApiUrl();
        }
        if (baseUrl == null) baseUrl = DEFAULT_OPENAI_BASE_URL;
        if (!baseUrl.endsWith("/")) baseUrl += "/";

        String model = prefs.getSelectedOpenAIModel();
        if (model == null || model.isEmpty()) model = Constants.DEFAULT_LLM_MODEL;

        OpenAIService service = RetrofitInstance.getOpenAIRetrofitInstance(baseUrl)
                .create(OpenAIService.class);

        if ("Claude".equals(provider)) {
            callClaude(service, baseUrl, apiKey, model, systemPrompt, userMessage, callback);
        } else if ("Gemini".equals(provider)) {
            callGemini(service, baseUrl, apiKey, model, systemPrompt, userMessage, callback);
        } else {
            // OpenAI / Grok / DeepSeek / Mistral / Custom — all OpenAI-compatible
            callOpenAiCompatible(service, apiKey, model, systemPrompt, userMessage, forceJson, callback);
        }
    }

    private void callOpenAiCompatible(OpenAIService service,
                                      String apiKey,
                                      String model,
                                      String systemPrompt,
                                      String userMessage,
                                      boolean forceJson,
                                      Callback callback) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt));
        messages.add(new Message("user", userMessage));

        OpenAIRequest request = new OpenAIRequest(model, messages);
        if (forceJson) {
            request.setResponseFormatJsonObject();
        }

        String bearer = "Bearer " + apiKey;
        service.getChatCompletion(bearer, request).enqueue(new retrofit2.Callback<OpenAIResponse>() {
            @Override
            public void onResponse(@NonNull Call<OpenAIResponse> call,
                                   @NonNull Response<OpenAIResponse> response) {
                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().getChoices() != null
                        && !response.body().getChoices().isEmpty()
                        && response.body().getChoices().get(0).getMessage() != null
                        && response.body().getChoices().get(0).getMessage().getContent() != null) {
                    String text = response.body().getChoices().get(0).getMessage().getContent().trim();
                    callback.onSuccess(text);
                } else {
                    Log.e(TAG, "OpenAI-compatible API failed: "
                            + response.code() + " " + response.message());
                    callback.onError(new RuntimeException("HTTP " + response.code()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<OpenAIResponse> call, @NonNull Throwable t) {
                callback.onError(t);
            }
        });
    }

    private void callClaude(OpenAIService service,
                            String baseUrl,
                            String apiKey,
                            String model,
                            String systemPrompt,
                            String userMessage,
                            Callback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 1024);
        body.addProperty("system", systemPrompt);

        JsonArray messages = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userMessage);
        messages.add(user);
        body.add("messages", messages);

        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", "2023-06-01");
        headers.put("content-type", "application/json");

        String url = baseUrl + "v1/messages";
        service.getClaudeCompletion(url, headers, body).enqueue(new retrofit2.Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call,
                                   @NonNull Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JsonArray content = response.body().getAsJsonArray("content");
                        if (content != null && content.size() > 0) {
                            String text = content.get(0).getAsJsonObject().get("text").getAsString();
                            callback.onSuccess(text);
                            return;
                        }
                    } catch (Exception e) {
                        callback.onError(e);
                        return;
                    }
                }
                callback.onError(new RuntimeException(
                        "Claude HTTP " + response.code() + " " + response.message()));
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                callback.onError(t);
            }
        });
    }

    private void callGemini(OpenAIService service,
                            String baseUrl,
                            String apiKey,
                            String model,
                            String systemPrompt,
                            String userMessage,
                            Callback callback) {
        JsonObject body = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        // Match the existing NotificationService.fetchGeminiReply convention: combine prompts.
        part.addProperty("text", systemPrompt + "\n\nUser: " + userMessage);
        parts.add(part);
        contentObj.add("parts", parts);
        contents.add(contentObj);
        body.add("contents", contents);

        String url = baseUrl + "v1beta/models/" + model + ":generateContent";
        service.getGeminiCompletion(url, apiKey, body).enqueue(new retrofit2.Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call,
                                   @NonNull Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JsonArray candidates = response.body().getAsJsonArray("candidates");
                        if (candidates != null && candidates.size() > 0) {
                            JsonObject candidate = candidates.get(0).getAsJsonObject();
                            JsonObject content = candidate.getAsJsonObject("content");
                            JsonArray pParts = content.getAsJsonArray("parts");
                            if (pParts != null && pParts.size() > 0) {
                                String text = pParts.get(0).getAsJsonObject().get("text").getAsString();
                                callback.onSuccess(text);
                                return;
                            }
                        }
                    } catch (Exception e) {
                        callback.onError(e);
                        return;
                    }
                }
                callback.onError(new RuntimeException(
                        "Gemini HTTP " + response.code() + " " + response.message()));
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                callback.onError(t);
            }
        });
    }
}
