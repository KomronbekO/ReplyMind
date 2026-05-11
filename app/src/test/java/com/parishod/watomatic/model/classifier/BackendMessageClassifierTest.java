package com.parishod.watomatic.model.classifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;

import com.parishod.watomatic.model.preferences.PreferencesManager;
import com.parishod.watomatic.network.AtomaticBackendGateway;
import com.parishod.watomatic.network.AtomaticBackendService;
import com.parishod.watomatic.network.model.backend.ClassifyRequest;
import com.parishod.watomatic.network.model.backend.ClassifyResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28})
public class BackendMessageClassifierTest {

    private PreferencesManager prefs;
    private AtomaticBackendGateway gateway;
    private AtomaticBackendService service;
    private MessageClassifier fallback;

    @Before
    public void setUp() {
        prefs = mock(PreferencesManager.class);
        gateway = mock(AtomaticBackendGateway.class);
        service = mock(AtomaticBackendService.class);
        fallback = mock(MessageClassifier.class);

        // Default "happy path" wiring — individual tests override as needed.
        when(prefs.isClassificationEnabled()).thenReturn(true);
        when(prefs.getBackendUserId()).thenReturn("test-user");
        when(prefs.getUserProfile()).thenReturn(new UserProfile());
        when(prefs.getClassificationCategories()).thenReturn(
                Arrays.asList(
                        new ClassificationCategory("urgent", "Urgent", "u", "🚨", "#FF0000",
                                CategoryAction.REPLY_TEMPLATE, true),
                        new ClassificationCategory("work", "Work", "w", "💼", "#0000FF",
                                CategoryAction.REPLY_DEFAULT, true)
                ));
        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.service()).thenReturn(service);
        when(gateway.bearerHeader()).thenReturn("Bearer test-token");
    }

    /** Synthetic Retrofit Call that immediately invokes onResponse with the given body. */
    private Call<ClassifyResponse> okCall(final ClassifyResponse body) {
        Call<ClassifyResponse> call = (Call<ClassifyResponse>) mock(Call.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            Callback<ClassifyResponse> cb = invocation.getArgument(0);
            cb.onResponse(call, Response.success(body));
            return null;
        }).when(call).enqueue(any());
        return call;
    }

    private Call<ClassifyResponse> errorCall(final int code) {
        Call<ClassifyResponse> call = (Call<ClassifyResponse>) mock(Call.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            Callback<ClassifyResponse> cb = invocation.getArgument(0);
            cb.onResponse(call, Response.error(code, ResponseBody.create(
                    "boom", MediaType.parse("application/json"))));
            return null;
        }).when(call).enqueue(any());
        return call;
    }

    private Call<ClassifyResponse> failingCall(final Throwable t) {
        Call<ClassifyResponse> call = (Call<ClassifyResponse>) mock(Call.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            Callback<ClassifyResponse> cb = invocation.getArgument(0);
            cb.onFailure(call, t);
            return null;
        }).when(call).enqueue(any());
        return call;
    }

    private ClassificationResult classifyAndGet(BackendMessageClassifier c) throws Exception {
        final AtomicReference<ClassificationResult> out = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        c.classify("alice", "tg", "hello", new MessageClassifier.Callback() {
            @Override
            public void onResult(@NonNull ClassificationResult result) {
                out.set(result);
                latch.countDown();
            }
        });
        assertTrue("callback never fired", latch.await(2, TimeUnit.SECONDS));
        return out.get();
    }

    private void stubFallbackReturning(ClassificationResult r) {
        when(fallback.isAvailable()).thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            MessageClassifier.Callback cb = invocation.getArgument(3);
            cb.onResult(r);
            return null;
        }).when(fallback).classify(anyString(), anyString(), anyString(), any());
    }

    @Test
    public void happy_path_returns_parsed_result() throws Exception {
        ClassifyResponse body = new ClassifyResponse();
        body.categoryId = "urgent";
        body.confidence = 0.91f;
        body.reasoning = "fire";
        // Build the canned Call OUTSIDE the when().thenReturn() to avoid nested stubbing.
        Call<ClassifyResponse> canned = okCall(body);
        when(service.classify(anyString(), any(ClassifyRequest.class))).thenReturn(canned);

        BackendMessageClassifier c = new BackendMessageClassifier(
                RuntimeEnvironment.getApplication(), prefs, gateway, fallback);
        ClassificationResult r = classifyAndGet(c);

        assertNotNull(r);
        assertEquals("urgent", r.getCategoryId());
        assertEquals(0.91f, r.getConfidence(), 0.001f);
        assertFalse("happy path must not flag fallback", r.isFallback());
        verify(fallback, never()).classify(anyString(), anyString(), anyString(), any());
    }

    @Test
    public void backend_5xx_falls_through_to_fallback() throws Exception {
        Call<ClassifyResponse> canned = errorCall(503);
        when(service.classify(anyString(), any(ClassifyRequest.class))).thenReturn(canned);
        stubFallbackReturning(new ClassificationResult("work", 0.7f, "from fallback",
                System.currentTimeMillis(), false));

        BackendMessageClassifier c = new BackendMessageClassifier(
                RuntimeEnvironment.getApplication(), prefs, gateway, fallback);
        ClassificationResult r = classifyAndGet(c);

        assertEquals("work", r.getCategoryId());
        verify(fallback).classify(anyString(), anyString(), anyString(), any());
    }

    @Test
    public void network_failure_falls_through() throws Exception {
        Call<ClassifyResponse> canned = failingCall(new IOException("unreachable"));
        when(service.classify(anyString(), any(ClassifyRequest.class))).thenReturn(canned);
        stubFallbackReturning(ClassificationResult.fallback("template_only"));

        BackendMessageClassifier c = new BackendMessageClassifier(
                RuntimeEnvironment.getApplication(), prefs, gateway, fallback);
        ClassificationResult r = classifyAndGet(c);

        assertTrue("network error must reach fallback layer", r.isFallback() || r.getCategoryId() != null);
        verify(fallback).classify(anyString(), anyString(), anyString(), any());
    }

    @Test
    public void unknown_category_from_backend_falls_through() throws Exception {
        ClassifyResponse body = new ClassifyResponse();
        body.categoryId = "made_up_category";   // not in the configured list
        body.confidence = 0.9f;
        body.reasoning = "x";
        Call<ClassifyResponse> canned = okCall(body);
        when(service.classify(anyString(), any(ClassifyRequest.class))).thenReturn(canned);
        stubFallbackReturning(ClassificationResult.fallback("template_only"));

        BackendMessageClassifier c = new BackendMessageClassifier(
                RuntimeEnvironment.getApplication(), prefs, gateway, fallback);
        ClassificationResult r = classifyAndGet(c);

        verify(fallback).classify(anyString(), anyString(), anyString(), any());
        assertNotNull(r);
    }

    @Test
    public void unconfigured_backend_skips_to_fallback_immediately() throws Exception {
        when(gateway.isConfigured()).thenReturn(false);
        stubFallbackReturning(new ClassificationResult("work", 0.6f, "fb",
                System.currentTimeMillis(), false));

        BackendMessageClassifier c = new BackendMessageClassifier(
                RuntimeEnvironment.getApplication(), prefs, gateway, fallback);
        ClassificationResult r = classifyAndGet(c);

        verify(service, never()).classify(anyString(), any());
        verify(fallback).classify(anyString(), anyString(), anyString(), any());
        assertEquals("work", r.getCategoryId());
    }

    @Test
    public void template_classifier_always_returns_fallback() throws Exception {
        TemplateClassifier t = new TemplateClassifier();
        assertTrue(t.isAvailable());
        final AtomicReference<ClassificationResult> out = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        t.classify("a", "b", "c", r -> {
            out.set(r);
            latch.countDown();
        });
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue(out.get().isFallback());
    }
}
