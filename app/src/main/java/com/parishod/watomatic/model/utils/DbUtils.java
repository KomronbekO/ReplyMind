package com.parishod.watomatic.model.utils;

import android.content.Context;
import android.service.notification.StatusBarNotification;

import com.parishod.watomatic.model.CustomRepliesData;
import com.parishod.watomatic.model.classifier.CategoryAction;
import com.parishod.watomatic.model.classifier.ClassificationResult;
import com.parishod.watomatic.model.logs.AppPackage;
import com.parishod.watomatic.model.logs.MessageLog;
import com.parishod.watomatic.model.logs.MessageLogsDB;
import com.parishod.watomatic.model.preferences.PreferencesManager;

public class DbUtils {
    private final Context mContext;

    public DbUtils(Context context) {
        mContext = context;
    }

    public long getNumReplies() {
        MessageLogsDB messageLogsDB = MessageLogsDB.getInstance(mContext.getApplicationContext());
        return messageLogsDB.logsDao().getNumReplies();
    }

    public void purgeMessageLogs() {
        MessageLogsDB messageLogsDB = MessageLogsDB.getInstance(mContext.getApplicationContext());
        messageLogsDB.logsDao().purgeMessageLogs();
    }

    public void logReply(StatusBarNotification sbn, String title) {
        CustomRepliesData customRepliesData = CustomRepliesData.getInstance(mContext);
        logReply(sbn, title, customRepliesData.getTextToSendOrElse(), null, null, true, null);
    }

    /**
     * ReplyMind: write a log row carrying classifier metadata.
     *
     * @param incomingBody the inbound message body, or null. Persisted only if the user has
     *                     opted in via {@link PreferencesManager#isKeepMessageSnippetsEnabled()}.
     * @param result       classifier outcome, may be null when classification is disabled
     * @param action       routing action chosen, may be null
     * @param wasReplied   true when an actual reply was sent; false for SUPPRESS / ESCALATE
     */
    public void logReply(StatusBarNotification sbn,
                         String title,
                         String repliedMsg,
                         ClassificationResult result,
                         CategoryAction action,
                         boolean wasReplied,
                         String incomingBody) {
        MessageLogsDB messageLogsDB = MessageLogsDB.getInstance(mContext.getApplicationContext());
        int packageIndex = messageLogsDB.appPackageDao().getPackageIndex(sbn.getPackageName());
        if (packageIndex <= 0) {
            AppPackage appPackage = new AppPackage(sbn.getPackageName());
            messageLogsDB.appPackageDao().insertAppPackage(appPackage);
            packageIndex = messageLogsDB.appPackageDao().getPackageIndex(sbn.getPackageName());
        }
        MessageLog log = new MessageLog(
                packageIndex,
                title,
                sbn.getNotification().when,
                repliedMsg,
                System.currentTimeMillis());
        log.setNotifIsReplied(wasReplied);

        if (result != null) {
            log.setCategory(result.getCategoryId());
            log.setConfidence(result.getConfidence());
        }
        if (action != null) {
            log.setActionTaken(action.name());
        }

        if (incomingBody != null
                && PreferencesManager.getPreferencesInstance(mContext).isKeepMessageSnippetsEnabled()) {
            log.setNotifBodySnippet(truncateSnippet(incomingBody));
        }

        messageLogsDB.logsDao().logReply(log);
    }

    private static String truncateSnippet(String s) {
        final int maxLen = 240;
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }

    public long getLastRepliedTime(String packageName, String title) {
        if (title == null) {
            return 0;
        }
        MessageLogsDB messageLogsDB = MessageLogsDB.getInstance(mContext.getApplicationContext());
        return messageLogsDB.logsDao().getLastReplyTimeStamp(title, packageName);
    }

    public long getFirstRepliedTime() {
        MessageLogsDB messageLogsDB = MessageLogsDB.getInstance(mContext.getApplicationContext());
        return messageLogsDB.logsDao().getFirstRepliedTime();
    }
}
