package com.parishod.watomatic.model.classifier;

import androidx.annotation.NonNull;

import com.parishod.watomatic.model.preferences.PreferencesManager;

/**
 * Pure-logic router: maps a {@link ClassificationResult} to one of four action callbacks the
 * caller (e.g., NotificationService) implements. No I/O — fully unit-testable with a mocked
 * {@link PreferencesManager}.
 */
public class CategoryActionRouter {

    public interface RoutingCallbacks {
        void doDefaultReplyFlow(@NonNull ClassificationResult result);

        void sendTemplateReply(@NonNull String templateText, @NonNull ClassificationResult result);

        void suppressReply(@NonNull ClassificationResult result);

        void escalate(@NonNull ClassificationResult result);
    }

    private final PreferencesManager prefs;

    public CategoryActionRouter(@NonNull PreferencesManager prefs) {
        this.prefs = prefs;
    }

    public void route(@NonNull ClassificationResult result,
                      @NonNull String senderTitle,
                      @NonNull RoutingCallbacks cb) {
        CategoryAction action = prefs.getCategoryAction(result.getCategoryId());

        switch (action) {
            case SUPPRESS:
                cb.suppressReply(result);
                return;
            case ESCALATE:
                cb.escalate(result);
                return;
            case VIP_ONLY_BYPASS:
                if (prefs.isVipSender(senderTitle)) {
                    cb.doDefaultReplyFlow(result);
                } else {
                    cb.suppressReply(result);
                }
                return;
            case REPLY_TEMPLATE:
                String tpl = prefs.getCategoryTemplate(result.getCategoryId());
                if (tpl == null || tpl.trim().isEmpty()) {
                    cb.doDefaultReplyFlow(result);
                } else {
                    cb.sendTemplateReply(tpl, result);
                }
                return;
            case REPLY_DEFAULT:
            default:
                cb.doDefaultReplyFlow(result);
        }
    }
}
