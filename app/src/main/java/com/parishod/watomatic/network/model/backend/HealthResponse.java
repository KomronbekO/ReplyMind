package com.parishod.watomatic.network.model.backend;

import com.google.gson.annotations.SerializedName;

public class HealthResponse {
    @SerializedName("ok") public boolean ok;
    @SerializedName("model_loaded") public boolean modelLoaded;
    @SerializedName("chroma_ready") public boolean chromaReady;
}
