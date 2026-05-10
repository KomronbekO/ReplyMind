package com.parishod.watomatic.activity.insights;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.parishod.watomatic.R;
import com.parishod.watomatic.activity.BaseActivity;
import com.parishod.watomatic.model.classifier.ClassificationCategory;
import com.parishod.watomatic.model.logs.CategoryCount;
import com.parishod.watomatic.model.logs.SenderCount;
import com.parishod.watomatic.model.preferences.PreferencesManager;
import com.parishod.watomatic.viewmodel.InsightsViewModel;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hand-rolled Insights view: hero card + horizontal category bars + top senders list.
 *
 * <p>We deliberately don't pull in MPAndroidChart (the plan called it out as a JitPack/Play
 * flavor risk). Bars are LinearLayouts with relative widths — simple and dependency-free.</p>
 */
public class InsightsActivity extends BaseActivity {

    private InsightsViewModel viewModel;
    private TextView heroText;
    private LinearLayout categoryBarsContainer;
    private LinearLayout topSendersContainer;
    private TextView emptyState;
    private MaterialButtonToggleGroup rangeGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insights);

        Toolbar toolbar = findViewById(R.id.insights_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ActionBar bar = getSupportActionBar();
            if (bar != null) {
                bar.setDisplayHomeAsUpEnabled(true);
                bar.setTitle(R.string.insights_title);
            }
        }

        heroText = findViewById(R.id.insights_hero_text);
        categoryBarsContainer = findViewById(R.id.insights_category_bars);
        topSendersContainer = findViewById(R.id.insights_top_senders);
        emptyState = findViewById(R.id.insights_empty_state);
        rangeGroup = findViewById(R.id.insights_range_group);

        viewModel = new ViewModelProvider(this).get(InsightsViewModel.class);

        rangeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.range_today) viewModel.setRange(InsightsViewModel.Range.TODAY);
            else if (checkedId == R.id.range_month) viewModel.setRange(InsightsViewModel.Range.MONTH);
            else viewModel.setRange(InsightsViewModel.Range.WEEK);
        });

        viewModel.getRepliesCount().observe(this, this::renderHero);
        viewModel.getCategoryCounts().observe(this, this::renderCategoryBars);
        viewModel.getTopSenders().observe(this, this::renderTopSenders);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void renderHero(Integer repliesCount) {
        int count = repliesCount == null ? 0 : repliesCount;
        long savedMs = count * InsightsViewModel.AVG_REPLY_TIME_SAVED_MS;
        heroText.setText(getString(R.string.insights_hero_format, count, formatDuration(savedMs)));

        boolean hasData = count > 0;
        emptyState.setVisibility(hasData ? View.GONE : View.VISIBLE);
    }

    private void renderCategoryBars(List<CategoryCount> counts) {
        categoryBarsContainer.removeAllViews();
        if (counts == null || counts.isEmpty()) return;

        // Find max for relative scaling; build a category-id → metadata map for emoji/color/name.
        int max = 1;
        for (CategoryCount cc : counts) {
            if (cc != null && cc.count > max) max = cc.count;
        }
        Map<String, ClassificationCategory> catIndex = new HashMap<>();
        for (ClassificationCategory c : PreferencesManager.getPreferencesInstance(this).getClassificationCategories()) {
            if (c == null || c.getId() == null) continue;
            catIndex.put(c.getId().toLowerCase(), c);
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (CategoryCount cc : counts) {
            if (cc == null) continue;
            View row = inflater.inflate(R.layout.item_insights_bar, categoryBarsContainer, false);
            TextView label = row.findViewById(R.id.bar_label);
            TextView countView = row.findViewById(R.id.bar_count);
            View fill = row.findViewById(R.id.bar_fill);

            ClassificationCategory cat = (cc.category != null) ? catIndex.get(cc.category.toLowerCase()) : null;
            String prettyLabel = (cat != null)
                    ? (cat.getEmoji() != null ? cat.getEmoji() + " " : "") + cat.getName()
                    : (cc.category != null ? cc.category : getString(R.string.inbox_unclassified));
            label.setText(prettyLabel);
            countView.setText(String.valueOf(cc.count));

            int color;
            try {
                color = Color.parseColor(cat != null ? cat.getColorHex() : "#757575");
            } catch (Exception e) {
                color = Color.parseColor("#757575");
            }
            fill.setBackgroundColor(color);

            // Set width via layout weight: fraction of max becomes the weight.
            ViewGroup.LayoutParams lp = fill.getLayoutParams();
            if (lp instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams) lp;
                llp.weight = Math.max(0.04f, (float) cc.count / (float) max);
                fill.setLayoutParams(llp);
            }

            // Spacer weight = remaining
            View spacer = row.findViewById(R.id.bar_spacer);
            ViewGroup.LayoutParams sp = spacer.getLayoutParams();
            if (sp instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams) sp;
                llp.weight = Math.max(0.001f, 1f - (float) cc.count / (float) max);
                spacer.setLayoutParams(llp);
            }

            categoryBarsContainer.addView(row);
        }
    }

    private void renderTopSenders(List<SenderCount> senders) {
        topSendersContainer.removeAllViews();
        if (senders == null || senders.isEmpty()) return;

        LayoutInflater inflater = LayoutInflater.from(this);
        for (SenderCount sc : senders) {
            if (sc == null) continue;
            View row = inflater.inflate(R.layout.item_insights_sender, topSendersContainer, false);
            TextView name = row.findViewById(R.id.sender_name);
            TextView count = row.findViewById(R.id.sender_count);
            name.setText(sc.sender != null ? sc.sender : "(unknown)");
            count.setText(String.valueOf(sc.count));
            topSendersContainer.addView(row);
        }
    }

    private String formatDuration(long ms) {
        long minutes = ms / 60_000L;
        if (minutes < 60) {
            return getString(R.string.insights_minutes_short, minutes);
        }
        float hours = minutes / 60f;
        return String.format(Locale.getDefault(), getString(R.string.insights_hours_short), hours);
    }
}
