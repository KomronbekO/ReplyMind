package com.parishod.watomatic.model.classifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;

import com.parishod.watomatic.model.preferences.PreferencesManager;

import org.junit.Before;
import org.junit.Test;

/**
 * Pure-logic tests for the routing decision tree. Stubs PreferencesManager via Mockito so we can
 * exercise every {@link CategoryAction} branch deterministically.
 */
public class CategoryActionRouterTest {

    private PreferencesManager prefs;
    private CategoryActionRouter router;

    /** Trivial spy that records which callback fired. */
    private static class Recorder implements CategoryActionRouter.RoutingCallbacks {
        String fired = null;
        String templateText = null;
        ClassificationResult result = null;

        @Override public void doDefaultReplyFlow(@NonNull ClassificationResult r) {
            fired = "default"; result = r;
        }
        @Override public void sendTemplateReply(@NonNull String t, @NonNull ClassificationResult r) {
            fired = "template"; templateText = t; result = r;
        }
        @Override public void suppressReply(@NonNull ClassificationResult r) {
            fired = "suppress"; result = r;
        }
        @Override public void escalate(@NonNull ClassificationResult r) {
            fired = "escalate"; result = r;
        }
    }

    private static ClassificationResult resultFor(String catId) {
        return new ClassificationResult(catId, 0.9f, "test", 0L, false);
    }

    @Before
    public void setUp() {
        prefs = mock(PreferencesManager.class);
        router = new CategoryActionRouter(prefs);
    }

    @Test
    public void replyDefault_goesToDefaultFlow() {
        when(prefs.getCategoryAction("urgent")).thenReturn(CategoryAction.REPLY_DEFAULT);
        Recorder r = new Recorder();
        router.route(resultFor("urgent"), "Alice", r);
        assertEquals("default", r.fired);
    }

    @Test
    public void replyTemplate_withTemplate_sendsTemplate() {
        when(prefs.getCategoryAction("work")).thenReturn(CategoryAction.REPLY_TEMPLATE);
        when(prefs.getCategoryTemplate("work")).thenReturn("In a meeting, will reply later.");
        Recorder r = new Recorder();
        router.route(resultFor("work"), "Boss", r);
        assertEquals("template", r.fired);
        assertEquals("In a meeting, will reply later.", r.templateText);
    }

    @Test
    public void replyTemplate_missingTemplate_fallsBackToDefault() {
        when(prefs.getCategoryAction("work")).thenReturn(CategoryAction.REPLY_TEMPLATE);
        when(prefs.getCategoryTemplate("work")).thenReturn(null);
        Recorder r = new Recorder();
        router.route(resultFor("work"), "Boss", r);
        assertEquals("default", r.fired);
    }

    @Test
    public void replyTemplate_blankTemplate_fallsBackToDefault() {
        when(prefs.getCategoryAction("work")).thenReturn(CategoryAction.REPLY_TEMPLATE);
        when(prefs.getCategoryTemplate("work")).thenReturn("   ");
        Recorder r = new Recorder();
        router.route(resultFor("work"), "Boss", r);
        assertEquals("default", r.fired);
    }

    @Test
    public void suppress_suppresses() {
        when(prefs.getCategoryAction("spam")).thenReturn(CategoryAction.SUPPRESS);
        Recorder r = new Recorder();
        router.route(resultFor("spam"), "Spammer", r);
        assertEquals("suppress", r.fired);
    }

    @Test
    public void escalate_escalates() {
        when(prefs.getCategoryAction("urgent")).thenReturn(CategoryAction.ESCALATE);
        Recorder r = new Recorder();
        router.route(resultFor("urgent"), "Boss", r);
        assertEquals("escalate", r.fired);
    }

    @Test
    public void vipBypass_whenVip_fallsToDefault() {
        when(prefs.getCategoryAction("promotional")).thenReturn(CategoryAction.VIP_ONLY_BYPASS);
        when(prefs.isVipSender("MyVIP")).thenReturn(true);
        Recorder r = new Recorder();
        router.route(resultFor("promotional"), "MyVIP", r);
        assertEquals("default", r.fired);
    }

    @Test
    public void vipBypass_whenNotVip_suppresses() {
        when(prefs.getCategoryAction("promotional")).thenReturn(CategoryAction.VIP_ONLY_BYPASS);
        when(prefs.isVipSender("Stranger")).thenReturn(false);
        Recorder r = new Recorder();
        router.route(resultFor("promotional"), "Stranger", r);
        assertEquals("suppress", r.fired);
    }

    @Test
    public void resultIsForwardedToCallback() {
        when(prefs.getCategoryAction("work")).thenReturn(CategoryAction.REPLY_DEFAULT);
        Recorder r = new Recorder();
        ClassificationResult input = resultFor("work");
        router.route(input, "Anyone", r);
        assertNotNull(r.result);
        assertEquals("work", r.result.getCategoryId());
        // For the default branch, no template text should be stashed.
        assertNull(r.templateText);
    }
}
