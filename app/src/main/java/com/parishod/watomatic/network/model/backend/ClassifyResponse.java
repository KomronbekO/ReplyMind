package com.parishod.watomatic.network.model.backend;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ClassifyResponse {
    @SerializedName("category_id") public String categoryId;
    @SerializedName("confidence") public float confidence;
    @SerializedName("reasoning") public String reasoning;
    @SerializedName("rag_evidence") public List<RagEvidenceItem> ragEvidence;
    @SerializedName("cold_start") public boolean coldStart;
    @SerializedName("latency_ms") public int latencyMs;
    /** Backend-generated reply text. Empty when the backend wants this message
     *  suppressed (promotional / spam / urgent) — the client should still
     *  honour its own category-action routing in that case. */
    @SerializedName("suggested_reply") public String suggestedReply;

    public static class RagEvidenceItem {
        @SerializedName("message_id") public String messageId;
        @SerializedName("sender") public String sender;
        @SerializedName("snippet") public String snippet;
        @SerializedName("category") public String category;
        @SerializedName("score") public float score;
    }
}
