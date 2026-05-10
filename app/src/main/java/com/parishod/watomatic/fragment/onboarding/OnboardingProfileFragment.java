package com.parishod.watomatic.fragment.onboarding;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.parishod.watomatic.R;
import com.parishod.watomatic.model.classifier.UserProfile;
import com.parishod.watomatic.viewmodel.OnboardingViewModel;

import java.util.Locale;

public class OnboardingProfileFragment extends OnboardingStepFragment {

    private OnboardingViewModel vm;
    private TextInputEditText nameInput;
    private TextInputEditText occupationInput;
    private TextView workingStart;
    private TextView workingEnd;
    private RadioGroup toneGroup;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_profile, container, false);

        vm = new ViewModelProvider(requireActivity()).get(OnboardingViewModel.class);
        UserProfile draft = vm.getDraftProfile();

        nameInput = root.findViewById(R.id.onboarding_name);
        occupationInput = root.findViewById(R.id.onboarding_occupation);
        workingStart = root.findViewById(R.id.onboarding_work_start);
        workingEnd = root.findViewById(R.id.onboarding_work_end);
        toneGroup = root.findViewById(R.id.onboarding_tone_group);

        nameInput.setText(draft.getDisplayName());
        occupationInput.setText(draft.getOccupation());
        renderTime(workingStart, draft.getWorkingHoursStartMinuteOfDay());
        renderTime(workingEnd, draft.getWorkingHoursEndMinuteOfDay());

        switch (draft.getTone() == null ? UserProfile.Tone.CASUAL : draft.getTone()) {
            case PROFESSIONAL: toneGroup.check(R.id.onboarding_tone_professional); break;
            case BRIEF: toneGroup.check(R.id.onboarding_tone_brief); break;
            case CASUAL:
            default: toneGroup.check(R.id.onboarding_tone_casual); break;
        }

        workingStart.setOnClickListener(v -> pickTime(true));
        workingEnd.setOnClickListener(v -> pickTime(false));

        return root;
    }

    @Override
    public boolean onContinue() {
        commitDraft();
        return true;
    }

    private void commitDraft() {
        UserProfile p = vm.getDraftProfile();
        p.setDisplayName(textOf(nameInput));
        p.setOccupation(textOf(occupationInput));
        int checked = toneGroup.getCheckedRadioButtonId();
        if (checked == R.id.onboarding_tone_professional) p.setTone(UserProfile.Tone.PROFESSIONAL);
        else if (checked == R.id.onboarding_tone_brief) p.setTone(UserProfile.Tone.BRIEF);
        else p.setTone(UserProfile.Tone.CASUAL);
    }

    private void pickTime(boolean isStart) {
        UserProfile p = vm.getDraftProfile();
        int current = isStart ? p.getWorkingHoursStartMinuteOfDay() : p.getWorkingHoursEndMinuteOfDay();
        new TimePickerDialog(requireContext(),
                (view, hourOfDay, minute) -> {
                    int picked = hourOfDay * 60 + minute;
                    if (isStart) {
                        p.setWorkingHoursStartMinuteOfDay(picked);
                        renderTime(workingStart, picked);
                    } else {
                        p.setWorkingHoursEndMinuteOfDay(picked);
                        renderTime(workingEnd, picked);
                    }
                }, current / 60, current % 60, DateFormat.is24HourFormat(requireContext())).show();
    }

    private static void renderTime(TextView t, int minOfDay) {
        t.setText(String.format(Locale.getDefault(), "%02d:%02d", minOfDay / 60, minOfDay % 60));
    }

    private static String textOf(TextInputEditText input) {
        return (input == null || input.getText() == null) ? "" : input.getText().toString().trim();
    }
}
