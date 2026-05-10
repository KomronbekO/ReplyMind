package com.parishod.watomatic.activity.onboarding;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.parishod.watomatic.R;
import com.parishod.watomatic.activity.BaseActivity;
import com.parishod.watomatic.fragment.onboarding.OnboardingAiPickerFragment;
import com.parishod.watomatic.fragment.onboarding.OnboardingCategoriesFragment;
import com.parishod.watomatic.fragment.onboarding.OnboardingDoneFragment;
import com.parishod.watomatic.fragment.onboarding.OnboardingPermissionFragment;
import com.parishod.watomatic.fragment.onboarding.OnboardingProfileFragment;
import com.parishod.watomatic.fragment.onboarding.OnboardingStepFragment;
import com.parishod.watomatic.fragment.onboarding.OnboardingVipFragment;
import com.parishod.watomatic.fragment.onboarding.OnboardingWelcomeFragment;

import java.util.Arrays;
import java.util.List;

/**
 * 7-step onboarding wizard. ViewPager2 swipe is disabled — users must use the explicit
 * Back/Continue/Skip footer buttons. Steps can opt to gate or hide the Continue button by
 * implementing the {@link OnboardingStepFragment} contract.
 */
public class OnboardingActivity extends BaseActivity {

    private ViewPager2 pager;
    private MaterialButton backButton;
    private MaterialButton continueButton;
    private MaterialButton skipButton;
    private LinearProgressIndicator progress;

    private final List<Class<? extends OnboardingStepFragment>> steps = Arrays.asList(
            OnboardingWelcomeFragment.class,
            OnboardingPermissionFragment.class,
            OnboardingProfileFragment.class,
            OnboardingVipFragment.class,
            OnboardingCategoriesFragment.class,
            OnboardingAiPickerFragment.class,
            OnboardingDoneFragment.class
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        pager = findViewById(R.id.onboarding_pager);
        backButton = findViewById(R.id.onboarding_back_button);
        continueButton = findViewById(R.id.onboarding_continue_button);
        skipButton = findViewById(R.id.onboarding_skip_button);
        progress = findViewById(R.id.onboarding_progress);

        pager.setUserInputEnabled(false);   // force explicit Continue
        pager.setOffscreenPageLimit(1);
        pager.setAdapter(new OnboardingPagerAdapter(this));
        progress.setMax(steps.size() - 1);

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                renderFooterForPage(position);
                progress.setProgressCompat(position, true);
            }
        });

        backButton.setOnClickListener(v -> {
            int cur = pager.getCurrentItem();
            if (cur > 0) pager.setCurrentItem(cur - 1, true);
        });

        continueButton.setOnClickListener(v -> advance());
        skipButton.setOnClickListener(v -> advance());

        renderFooterForPage(0);
    }

    private void advance() {
        int cur = pager.getCurrentItem();
        OnboardingStepFragment frag = currentStepFragment();
        if (frag != null && !frag.onContinue()) {
            // Step blocked progression (e.g., permission not granted) — ignore silently.
            return;
        }
        if (cur < steps.size() - 1) {
            pager.setCurrentItem(cur + 1, true);
        } else {
            finishOnboarding();
        }
    }

    private void finishOnboarding() {
        // Final commit is done by the Done fragment via the ViewModel. Defensive backup here too.
        com.parishod.watomatic.model.preferences.PreferencesManager
                .getPreferencesInstance(this)
                .setOnboardingComplete(true);
        // MainActivity finished itself when it bounced us here, so the back stack is empty.
        // Start it explicitly before finishing or the user lands on the launcher.
        android.content.Intent main = new android.content.Intent(this,
                com.parishod.watomatic.activity.main.MainActivity.class);
        main.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(main);
        finish();
    }

    private OnboardingStepFragment currentStepFragment() {
        Fragment f = getSupportFragmentManager().findFragmentByTag("f" + pager.getCurrentItem());
        return (f instanceof OnboardingStepFragment) ? (OnboardingStepFragment) f : null;
    }

    private void renderFooterForPage(int position) {
        backButton.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
        boolean isLast = position == steps.size() - 1;
        continueButton.setText(isLast ? R.string.onboarding_finish : R.string.onboarding_continue);
        // Skip is shown for optional steps (VIP, AI Picker).
        Class<? extends OnboardingStepFragment> cls = steps.get(position);
        boolean optional = cls == OnboardingVipFragment.class
                || cls == OnboardingAiPickerFragment.class;
        skipButton.setVisibility(optional ? View.VISIBLE : View.GONE);
    }

    /** Allow step fragments to enable/disable the Continue button while the user is on them. */
    public void setContinueEnabled(boolean enabled) {
        continueButton.setEnabled(enabled);
    }

    private class OnboardingPagerAdapter extends FragmentStateAdapter {

        OnboardingPagerAdapter(@NonNull FragmentActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            try {
                return steps.get(position).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Bad onboarding step", e);
            }
        }

        @Override
        public int getItemCount() {
            return steps.size();
        }
    }
}
