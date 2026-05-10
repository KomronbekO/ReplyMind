package com.parishod.watomatic.fragment.onboarding;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parishod.watomatic.R;
import com.parishod.watomatic.model.classifier.ClassificationCategory;
import com.parishod.watomatic.model.preferences.PreferencesManager;

import java.util.List;

public class OnboardingCategoriesFragment extends OnboardingStepFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_categories, container, false);

        LinearLayout list = root.findViewById(R.id.onboarding_category_list);
        List<ClassificationCategory> cats = PreferencesManager.getPreferencesInstance(
                requireContext()).getClassificationCategories();

        LayoutInflater li = LayoutInflater.from(requireContext());
        for (ClassificationCategory c : cats) {
            View row = li.inflate(R.layout.item_onboarding_category, list, false);
            View bg = row.findViewById(R.id.onboarding_cat_emoji_bg);
            TextView emoji = row.findViewById(R.id.onboarding_cat_emoji);
            TextView name = row.findViewById(R.id.onboarding_cat_name);
            TextView desc = row.findViewById(R.id.onboarding_cat_desc);

            emoji.setText(c.getEmoji());
            name.setText(c.getName());
            desc.setText(c.getDescription());

            int color;
            try {
                color = Color.parseColor(c.getColorHex());
            } catch (Exception e) {
                color = Color.parseColor("#757575");
            }
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(color);
            bg.setBackground(circle);

            list.addView(row);
        }

        return root;
    }
}
