package com.parishod.watomatic.network.model.backend;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class HistorySyncRequest {
    @SerializedName("user_id") public String userId;
    @SerializedName("items") public List<HistoryItem> items;

    public static class HistoryItem {
        @SerializedName("message_id") public String messageId;
        @SerializedName("sender") public String sender = "";
        @SerializedName("package") public String pkg = "";
        @SerializedName("snippet") public String snippet = "";
        @SerializedName("timestamp_ms") public long timestampMs = 0L;
        @SerializedName("category") public String category;
    }
}
