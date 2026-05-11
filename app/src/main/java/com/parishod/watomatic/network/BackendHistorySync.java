package com.parishod.watomatic.network;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parishod.watomatic.model.logs.MessageLog;
import com.parishod.watomatic.model.logs.MessageLogsDB;
import com.parishod.watomatic.model.preferences.PreferencesManager;
import com.parishod.watomatic.network.model.backend.HistorySyncRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * One-shot push of recent {@link MessageLog} rows into the backend's vector store
 * so retrieval-augmented inference has prior context from message #1, not just
 * after the user has built up history through the app.
 *
 * <p>Intentionally simple: runs on a fixed background executor, batches up to
 * {@code limit} rows, fires one POST. No WorkManager queue — for the local-demo
 * use case that's overkill; the user can re-tap "Sync history" if the network
 * blips.</p>
 */
public class BackendHistorySync {

    private static final String TAG = "BackendHistorySync";
    private static final int DEFAULT_LIMIT = 500;

    public interface Callback {
        void onSynced(int rowsSent);
        void onFailure(@NonNull String reason);
    }

    private static final Executor IO = Executors.newSingleThreadExecutor();

    public static void runOnce(@NonNull Context context, @Nullable Callback cb) {
        runOnce(context, DEFAULT_LIMIT, cb);
    }

    public static void runOnce(@NonNull Context context, int limit, @Nullable Callback cb) {
        final Context appCtx = context.getApplicationContext();
        IO.execute(() -> {
            try {
                PreferencesManager prefs = PreferencesManager.getPreferencesInstance(appCtx);
                AtomaticBackendGateway gateway = new AtomaticBackendGateway(prefs);
                if (!gateway.isConfigured()) {
                    if (cb != null) cb.onFailure("backend_not_configured");
                    return;
                }
                AtomaticBackendService service = gateway.service();
                if (service == null) {
                    if (cb != null) cb.onFailure("backend_misconfigured");
                    return;
                }

                List<MessageLog> rows = MessageLogsDB.getInstance(appCtx).logsDao()
                        .recentSync(limit);
                if (rows == null || rows.isEmpty()) {
                    if (cb != null) cb.onSynced(0);
                    return;
                }

                HistorySyncRequest req = new HistorySyncRequest();
                req.userId = prefs.getBackendUserId();
                req.items = new ArrayList<>(rows.size());
                for (MessageLog row : rows) {
                    HistorySyncRequest.HistoryItem item = new HistorySyncRequest.HistoryItem();
                    item.messageId = "log-" + row.getId();
                    item.sender = nullSafe(row.getNotifTitle());
                    // notifId is the only thing on the row that often encodes the package; if it's
                    // missing, leave package blank — the backend uses sender+user_id as the key.
                    item.pkg = nullSafe(row.getNotifId());
                    item.snippet = nullSafe(row.getNotifBodySnippet());
                    item.timestampMs = row.getNotifArrivedTime();
                    item.category = row.getCategory();
                    req.items.add(item);
                }

                Call<Void> call = service.historySync(gateway.bearerHeader(), req);
                call.enqueue(new retrofit2.Callback<Void>() {
                    @Override public void onResponse(@NonNull Call<Void> call,
                                                     @NonNull Response<Void> response) {
                        if (cb == null) return;
                        if (response.isSuccessful()) cb.onSynced(req.items.size());
                        else cb.onFailure("http_" + response.code());
                    }

                    @Override public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        if (cb == null) return;
                        cb.onFailure(t.getMessage() != null ? t.getMessage() : "network_error");
                    }
                });
            } catch (Throwable t) {
                Log.e(TAG, "history sync crashed", t);
                if (cb != null) cb.onFailure(t.getMessage() != null ? t.getMessage() : "unknown");
            }
        });
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}
