package com.parishod.watomatic.fragment;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.parishod.watomatic.R;
import com.parishod.watomatic.model.preferences.PreferencesManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Vacation Mode editor as a Material bottom sheet. Lets the user pick an end-date and a
 * vacation auto-reply message. Persists via {@link PreferencesManager#setVacationUntil(long)}
 * and {@link PreferencesManager#setVacationMessage(String)}.
 */
public class VacationModeBottomSheet extends BottomSheetDialogFragment {

    public interface OnStateChangedListener {
        void onVacationStateChanged();
    }

    private TextInputEditText messageInput;
    private MaterialButton untilButton;
    private MaterialButton actionButton;

    private long pickedEndMs;
    private OnStateChangedListener listener;

    public void setOnStateChangedListener(OnStateChangedListener l) {
        this.listener = l;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_vacation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        messageInput = view.findViewById(R.id.vacation_message_input);
        untilButton = view.findViewById(R.id.vacation_until_button);
        actionButton = view.findViewById(R.id.vacation_action_button);

        PreferencesManager prefs = PreferencesManager.getPreferencesInstance(requireContext());

        // Prefill from any existing config; default the end date to a week from now.
        long existingUntil = prefs.getVacationUntil();
        pickedEndMs = (existingUntil > System.currentTimeMillis())
                ? existingUntil
                : defaultUntilMs();
        messageInput.setText(prefs.getVacationMessage());
        renderUntil();

        untilButton.setOnClickListener(v -> showDatePicker());

        boolean alreadyOn = prefs.isVacationModeActive();
        actionButton.setText(alreadyOn ? R.string.vacation_disable : R.string.vacation_enable);

        actionButton.setOnClickListener(v -> {
            if (PreferencesManager.getPreferencesInstance(requireContext()).isVacationModeActive()) {
                disable();
            } else {
                enable();
            }
        });
    }

    private void enable() {
        String msg = (messageInput.getText() != null) ? messageInput.getText().toString().trim() : "";
        if (msg.isEmpty()) {
            Toast.makeText(requireContext(), R.string.vacation_message_required_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        if (pickedEndMs <= System.currentTimeMillis()) {
            // user kept a stale end-date — bump to default
            pickedEndMs = defaultUntilMs();
        }
        PreferencesManager prefs = PreferencesManager.getPreferencesInstance(requireContext());
        prefs.setVacationMessage(msg);
        prefs.setVacationUntil(pickedEndMs);

        Toast.makeText(requireContext(), R.string.vacation_enabled_toast, Toast.LENGTH_SHORT).show();
        if (listener != null) listener.onVacationStateChanged();
        dismiss();
    }

    private void disable() {
        PreferencesManager prefs = PreferencesManager.getPreferencesInstance(requireContext());
        prefs.setVacationUntil(0L);
        Toast.makeText(requireContext(), R.string.vacation_disabled_toast, Toast.LENGTH_SHORT).show();
        if (listener != null) listener.onVacationStateChanged();
        dismiss();
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(pickedEndMs);
        new DatePickerDialog(requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar c = Calendar.getInstance();
                    c.set(year, month, dayOfMonth, 23, 59, 59);
                    c.set(Calendar.MILLISECOND, 999);
                    pickedEndMs = c.getTimeInMillis();
                    renderUntil();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void renderUntil() {
        SimpleDateFormat df = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault());
        untilButton.setText(df.format(new Date(pickedEndMs)));
    }

    private static long defaultUntilMs() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, 7);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        return c.getTimeInMillis();
    }
}
