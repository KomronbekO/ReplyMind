package com.parishod.watomatic.fragment.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.parishod.watomatic.R;
import com.parishod.watomatic.model.preferences.PreferencesManager;
import com.parishod.watomatic.model.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class OnboardingAiPickerFragment extends OnboardingStepFragment {

    private AutoCompleteTextView providerDropdown;
    private TextInputEditText keyInput;
    private TextView savedLabel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_ai_picker, container, false);

        providerDropdown = root.findViewById(R.id.onboarding_ai_provider);
        keyInput = root.findViewById(R.id.onboarding_ai_key);
        savedLabel = root.findViewById(R.id.onboarding_ai_saved_label);
        MaterialButton saveButton = root.findViewById(R.id.onboarding_ai_save_button);

        List<String> providers = new ArrayList<>(Constants.INSTANCE.getPROVIDER_URLS().keySet());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, providers);
        providerDropdown.setAdapter(adapter);

        PreferencesManager prefs = PreferencesManager.getPreferencesInstance(requireContext());
        String current = prefs.getOpenApiSource();
        if (current == null || current.isEmpty()) current = "OpenAI";
        providerDropdown.setText(current, false);

        String existingKey = prefs.getOpenAIApiKey();
        if (existingKey != null && !existingKey.isEmpty()) {
            savedLabel.setVisibility(View.VISIBLE);
        }

        saveButton.setOnClickListener(v -> {
            String provider = providerDropdown.getText() != null
                    ? providerDropdown.getText().toString().trim() : "OpenAI";
            String key = keyInput.getText() != null ? keyInput.getText().toString().trim() : "";
            if (key.isEmpty()) return;
            PreferencesManager pm = PreferencesManager.getPreferencesInstance(requireContext());
            pm.saveOpenApiSource(provider);
            pm.saveOpenAIApiKey(key);
            pm.setEnableByokReplies(true);
            // Enable Smart Classification now that BYOK is configured. The user can disable
            // it from Settings any time.
            pm.setClassificationEnabled(true);
            savedLabel.setVisibility(View.VISIBLE);
            keyInput.setText("");
        });

        return root;
    }
}
