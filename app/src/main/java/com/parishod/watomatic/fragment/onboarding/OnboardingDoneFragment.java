package com.parishod.watomatic.fragment.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.parishod.watomatic.R;
import com.parishod.watomatic.viewmodel.OnboardingViewModel;

public class OnboardingDoneFragment extends OnboardingStepFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding_done, container, false);
    }

    @Override
    public boolean onContinue() {
        // Commit the draft profile + flip the onboarding_complete flag.
        OnboardingViewModel vm = new ViewModelProvider(requireActivity()).get(OnboardingViewModel.class);
        vm.saveProfileAndComplete();
        return true;
    }
}
