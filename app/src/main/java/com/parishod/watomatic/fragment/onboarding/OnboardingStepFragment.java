package com.parishod.watomatic.fragment.onboarding;

import androidx.fragment.app.Fragment;

import com.parishod.watomatic.activity.onboarding.OnboardingActivity;

/**
 * Base for onboarding step fragments. Subclasses can override {@link #onContinue()} to gate
 * progression (e.g., the permission step can return false until notification access is granted).
 */
public abstract class OnboardingStepFragment extends Fragment {

    /** @return true to allow advancing, false to block (no toast — step UI explains why). */
    public boolean onContinue() {
        return true;
    }

    protected OnboardingActivity host() {
        return (OnboardingActivity) requireActivity();
    }
}
