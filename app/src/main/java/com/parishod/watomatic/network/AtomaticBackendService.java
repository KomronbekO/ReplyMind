package com.parishod.watomatic.network;

import com.parishod.watomatic.network.model.backend.ClassifyRequest;
import com.parishod.watomatic.network.model.backend.ClassifyResponse;
import com.parishod.watomatic.network.model.backend.HealthResponse;
import com.parishod.watomatic.network.model.backend.HistorySyncRequest;
import com.parishod.watomatic.network.model.backend.ProfileRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * Retrofit interface for the local ReplyMind backend. All routes except /healthz
 * require a Bearer token configured in Settings → Local backend.
 */
public interface AtomaticBackendService {

    @GET("healthz")
    Call<HealthResponse> healthz();

    @POST("classify")
    Call<ClassifyResponse> classify(@Header("Authorization") String bearer,
                                    @Body ClassifyRequest req);

    @POST("profile")
    Call<Void> upsertProfile(@Header("Authorization") String bearer,
                             @Body ProfileRequest req);

    @POST("history/sync")
    Call<Void> historySync(@Header("Authorization") String bearer,
                           @Body HistorySyncRequest req);
}
