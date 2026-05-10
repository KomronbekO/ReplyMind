package com.parishod.watomatic.activity.profile;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.MenuItem;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.parishod.watomatic.R;
import com.parishod.watomatic.activity.BaseActivity;
import com.parishod.watomatic.model.classifier.UserProfile;
import com.parishod.watomatic.model.preferences.PreferencesManager;

import java.util.Locale;

/**
 * Minimal user-profile editor: name, occupation, working hours, communication tone, optional
 * style + extra context. Persisted via {@link PreferencesManager#saveUserProfile(UserProfile)}
 * (stored in EncryptedSharedPreferences).
 *
 * <p>This is the v1 entry point used by Settings → "Your profile". A full ViewPager-based
 * onboarding wizard is on the roadmap and will reuse the same persistence.
 */
public class ProfileActivity extends BaseActivity {

    private TextInputEditText displayNameInput;
    private TextInputEditText occupationInput;
    private TextInputEditText styleInput;
    private TextInputEditText extraContextInput;
    private TextView workingStartLabel;
    private TextView workingEndLabel;
    private RadioGroup toneGroup;

    private int workingStartMin = 9 * 60;
    private int workingEndMin = 18 * 60;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        Toolbar toolbar = findViewById(R.id.profile_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ActionBar bar = getSupportActionBar();
            if (bar != null) {
                bar.setDisplayHomeAsUpEnabled(true);
                bar.setTitle(R.string.profile_screen_title);
            }
        }

        displayNameInput = findViewById(R.id.profile_display_name);
        occupationInput = findViewById(R.id.profile_occupation);
        styleInput = findViewById(R.id.profile_style);
        extraContextInput = findViewById(R.id.profile_extra_context);
        workingStartLabel = findViewById(R.id.profile_working_start_label);
        workingEndLabel = findViewById(R.id.profile_working_end_label);
        toneGroup = findViewById(R.id.profile_tone_group);

        UserProfile existing = PreferencesManager.getPreferencesInstance(this).getUserProfile();
        displayNameInput.setText(existing.getDisplayName());
        occupationInput.setText(existing.getOccupation());
        styleInput.setText(existing.getCommunicationStyle());
        extraContextInput.setText(existing.getAdditionalContext());
        workingStartMin = existing.getWorkingHoursStartMinuteOfDay();
        workingEndMin = existing.getWorkingHoursEndMinuteOfDay();
        renderWorkingHours();

        switch (existing.getTone() == null ? UserProfile.Tone.CASUAL : existing.getTone()) {
            case PROFESSIONAL:
                toneGroup.check(R.id.profile_tone_professional);
                break;
            case BRIEF:
                toneGroup.check(R.id.profile_tone_brief);
                break;
            case CASUAL:
            default:
                toneGroup.check(R.id.profile_tone_casual);
                break;
        }

        findViewById(R.id.profile_working_start_label).setOnClickListener(v -> pickTime(true));
        findViewById(R.id.profile_working_end_label).setOnClickListener(v -> pickTime(false));

        MaterialButton saveButton = findViewById(R.id.profile_save_button);
        saveButton.setOnClickListener(v -> save());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void pickTime(boolean isStart) {
        int currentMin = isStart ? workingStartMin : workingEndMin;
        int hour = currentMin / 60;
        int minute = currentMin % 60;
        new TimePickerDialog(this,
                (view, hourOfDay, mm) -> {
                    int picked = hourOfDay * 60 + mm;
                    if (isStart) workingStartMin = picked; else workingEndMin = picked;
                    renderWorkingHours();
                },
                hour, minute, DateFormat.is24HourFormat(this)).show();
    }

    private void renderWorkingHours() {
        workingStartLabel.setText(formatTime(workingStartMin));
        workingEndLabel.setText(formatTime(workingEndMin));
    }

    private String formatTime(int minOfDay) {
        int h = minOfDay / 60;
        int m = minOfDay % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", h, m);
    }

    private void save() {
        UserProfile profile = new UserProfile();
        profile.setDisplayName(textOf(displayNameInput));
        profile.setOccupation(textOf(occupationInput));
        profile.setCommunicationStyle(textOf(styleInput));
        profile.setAdditionalContext(textOf(extraContextInput));
        profile.setWorkingHoursStartMinuteOfDay(workingStartMin);
        profile.setWorkingHoursEndMinuteOfDay(workingEndMin);

        int checked = toneGroup.getCheckedRadioButtonId();
        if (checked == R.id.profile_tone_professional) profile.setTone(UserProfile.Tone.PROFESSIONAL);
        else if (checked == R.id.profile_tone_brief) profile.setTone(UserProfile.Tone.BRIEF);
        else profile.setTone(UserProfile.Tone.CASUAL);

        PreferencesManager.getPreferencesInstance(this).saveUserProfile(profile);
        Toast.makeText(this, R.string.profile_saved_toast, Toast.LENGTH_SHORT).show();
        finish();
    }

    private static String textOf(TextInputEditText input) {
        if (input == null || input.getText() == null) return "";
        return input.getText().toString().trim();
    }
}
