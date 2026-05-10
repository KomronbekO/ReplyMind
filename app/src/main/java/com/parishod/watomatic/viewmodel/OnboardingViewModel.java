package com.parishod.watomatic.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.parishod.watomatic.model.classifier.UserProfile;
import com.parishod.watomatic.model.preferences.PreferencesManager;

/**
 * Holds the in-progress onboarding state so config changes (rotation, back-stack) don't lose
 * what the user has typed so far. Persists to {@link PreferencesManager} only when the user
 * advances past the final step.
 */
public class OnboardingViewModel extends AndroidViewModel {

    private final UserProfile draftProfile;

    public OnboardingViewModel(@NonNull Application application) {
        super(application);
        // Start from whatever the user already has (handles re-entry from Settings).
        this.draftProfile = PreferencesManager.getPreferencesInstance(application).getUserProfile();
    }

    public UserProfile getDraftProfile() {
        return draftProfile;
    }

    public void saveProfileAndComplete() {
        PreferencesManager prefs = PreferencesManager.getPreferencesInstance(getApplication());
        prefs.saveUserProfile(draftProfile);
        prefs.setOnboardingComplete(true);
    }
}
