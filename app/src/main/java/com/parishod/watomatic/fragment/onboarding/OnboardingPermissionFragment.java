package com.parishod.watomatic.fragment.onboarding;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.parishod.watomatic.R;
import com.parishod.watomatic.service.NotificationService;

public class OnboardingPermissionFragment extends OnboardingStepFragment {

    private TextView statusView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_permission, container, false);
        statusView = root.findViewById(R.id.onboarding_permission_status);

        MaterialButton openButton = root.findViewById(R.id.onboarding_open_settings_button);
        openButton.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        renderStatus();
    }

    @Override
    public boolean onContinue() {
        boolean granted = isListenerEnabled();
        if (!granted) {
            renderStatus();
            return false;
        }
        return true;
    }

    private void renderStatus() {
        boolean granted = isListenerEnabled();
        statusView.setText(granted
                ? R.string.onboarding_permission_granted
                : R.string.onboarding_permission_pending);
        host().setContinueEnabled(granted);
    }

    private boolean isListenerEnabled() {
        Context ctx = requireContext();
        ComponentName cn = new ComponentName(ctx, NotificationService.class);
        String flat = Settings.Secure.getString(
                ctx.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(cn.flattenToString());
    }
}
