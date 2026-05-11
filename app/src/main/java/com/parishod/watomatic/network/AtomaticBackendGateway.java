package com.parishod.watomatic.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parishod.watomatic.model.preferences.PreferencesManager;

import retrofit2.Retrofit;

/**
 * Thin factory for {@link AtomaticBackendService}. The base URL is read from
 * preferences so the user can point at their laptop (emulator → 10.0.2.2, real
 * device → LAN IP) without recompiling. Reuses the Retrofit + OkHttp client
 * config from {@link RetrofitInstance#getOpenAIRetrofitInstance(String)} since
 * the timeouts and converters match what we need.
 */
public class AtomaticBackendGateway {

    private final PreferencesManager prefs;

    public AtomaticBackendGateway(PreferencesManager prefs) {
        this.prefs = prefs;
    }

    /** True when the user has entered both a URL and a token AND the toggle is on. */
    public boolean isConfigured() {
        return prefs.isBackendEnabled()
                && !isBlank(prefs.getBackendUrl())
                && !isBlank(prefs.getBackendToken());
    }

    @Nullable
    public AtomaticBackendService service() {
        String url = prefs.getBackendUrl();
        if (isBlank(url)) return null;
        // Retrofit base URL must end with '/'.
        if (!url.endsWith("/")) url = url + "/";
        Retrofit r = RetrofitInstance.getOpenAIRetrofitInstance(url);
        return r.create(AtomaticBackendService.class);
    }

    /** Returns a Service for a one-off connectivity probe — does not require the toggle ON. */
    @Nullable
    public AtomaticBackendService serviceForUrl(@NonNull String url) {
        if (isBlank(url)) return null;
        String base = url.endsWith("/") ? url : url + "/";
        Retrofit r = RetrofitInstance.getOpenAIRetrofitInstance(base);
        return r.create(AtomaticBackendService.class);
    }

    public String bearerHeader() {
        String token = prefs.getBackendToken();
        return token == null ? "" : "Bearer " + token;
    }

    public String bearerHeaderFor(@NonNull String token) {
        return "Bearer " + token;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
