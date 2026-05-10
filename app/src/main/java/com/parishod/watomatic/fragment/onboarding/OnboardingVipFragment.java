package com.parishod.watomatic.fragment.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.parishod.watomatic.R;
import com.parishod.watomatic.model.preferences.PreferencesManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Optional onboarding step: VIP names. Persists directly to PreferencesManager so users can
 * back-navigate without losing entries (same store as the VIP contacts settings screen).
 */
public class OnboardingVipFragment extends OnboardingStepFragment {

    private TextInputEditText input;
    private LinearLayout chipContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_vip, container, false);
        input = root.findViewById(R.id.onboarding_vip_input);
        chipContainer = root.findViewById(R.id.onboarding_vip_chips);
        MaterialButton addButton = root.findViewById(R.id.onboarding_vip_add);

        addButton.setOnClickListener(v -> {
            String name = input.getText() != null ? input.getText().toString().trim() : "";
            if (name.isEmpty()) return;
            Set<String> set = new HashSet<>(
                    PreferencesManager.getPreferencesInstance(requireContext()).getVipContacts());
            set.add(name);
            PreferencesManager.getPreferencesInstance(requireContext()).setVipContacts(set);
            input.setText("");
            renderChips();
        });

        renderChips();
        return root;
    }

    private void renderChips() {
        chipContainer.removeAllViews();
        List<String> sorted = new ArrayList<>(
                PreferencesManager.getPreferencesInstance(requireContext()).getVipContacts());
        java.util.Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (String n : sorted) {
            TextView t = (TextView) inflater.inflate(
                    R.layout.item_onboarding_vip_chip, chipContainer, false);
            t.setText("⭐ " + n);
            t.setOnClickListener(v -> {
                Set<String> set = new HashSet<>(
                        PreferencesManager.getPreferencesInstance(requireContext()).getVipContacts());
                set.remove(n);
                PreferencesManager.getPreferencesInstance(requireContext()).setVipContacts(set);
                renderChips();
            });
            chipContainer.addView(t);
        }
    }
}
