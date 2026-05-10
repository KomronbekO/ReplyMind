package com.parishod.watomatic.activity.inbox;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.parishod.watomatic.R;
import com.parishod.watomatic.activity.BaseActivity;
import com.parishod.watomatic.model.adapters.InboxAdapter;
import com.parishod.watomatic.model.classifier.ClassificationCategory;
import com.parishod.watomatic.model.preferences.PreferencesManager;
import com.parishod.watomatic.viewmodel.InboxViewModel;

import java.util.List;

/**
 * Inbox: chronological list of every classified message, with category filter chips and an
 * empty state when nothing has been classified yet.
 */
public class InboxActivity extends BaseActivity {

    private InboxViewModel viewModel;
    private InboxAdapter adapter;
    private View emptyState;
    private RecyclerView recycler;
    private ChipGroup chipGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        Toolbar toolbar = findViewById(R.id.inbox_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ActionBar bar = getSupportActionBar();
            if (bar != null) {
                bar.setDisplayHomeAsUpEnabled(true);
                bar.setTitle(R.string.inbox_screen_title);
            }
        }

        emptyState = findViewById(R.id.inbox_empty_state);
        recycler = findViewById(R.id.inbox_recycler);
        chipGroup = findViewById(R.id.inbox_chip_group);

        adapter = new InboxAdapter(this, log -> {
            Intent i = new Intent(this, MessageDetailActivity.class);
            i.putExtra(MessageDetailActivity.EXTRA_MESSAGE_ID, log.getId());
            i.putExtra(MessageDetailActivity.EXTRA_SENDER, log.getNotifTitle());
            i.putExtra(MessageDetailActivity.EXTRA_BODY, log.getNotifBodySnippet());
            i.putExtra(MessageDetailActivity.EXTRA_CATEGORY, log.getCategory());
            i.putExtra(MessageDetailActivity.EXTRA_CONFIDENCE,
                    log.getConfidence() == null ? -1f : log.getConfidence());
            i.putExtra(MessageDetailActivity.EXTRA_ACTION, log.getActionTaken());
            i.putExtra(MessageDetailActivity.EXTRA_REPLY, log.getNotifRepliedMsg());
            i.putExtra(MessageDetailActivity.EXTRA_ARRIVED, log.getNotifArrivedTime());
            startActivity(i);
        });
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        buildFilterChips();

        viewModel = new ViewModelProvider(this).get(InboxViewModel.class);
        viewModel.getMessages().observe(this, this::onMessagesUpdated);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have edited categories elsewhere — refresh the lookup so emojis/colors
        // reflect any changes the next time we render.
        adapter.rebuildCategoryIndex(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void buildFilterChips() {
        chipGroup.removeAllViews();
        chipGroup.setSingleSelection(true);

        Chip allChip = makeChip(getString(R.string.inbox_filter_all), null, "#3F51B5");
        allChip.setChecked(true);
        chipGroup.addView(allChip);

        List<ClassificationCategory> cats = PreferencesManager.getPreferencesInstance(this)
                .getClassificationCategories();
        for (ClassificationCategory c : cats) {
            if (c == null || !c.isEnabled()) continue;
            String label = (c.getEmoji() != null ? c.getEmoji() + " " : "") + c.getName();
            chipGroup.addView(makeChip(label, c.getId(), c.getColorHex()));
        }
    }

    private Chip makeChip(String label, String categoryId, String colorHex) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setClickable(true);
        try {
            int color = Color.parseColor(colorHex);
            chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(color));
            chip.setChipStrokeWidth(2f);
        } catch (Exception ignored) {
            // fall back to default stroke
        }
        chip.setOnClickListener(v -> {
            if (chip.isChecked()) {
                viewModel.setSelectedCategory(categoryId);
            }
        });
        return chip;
    }

    private void onMessagesUpdated(List<?> messages) {
        boolean empty = messages == null || messages.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        @SuppressWarnings("unchecked")
        List<com.parishod.watomatic.model.logs.MessageLog> typed =
                (List<com.parishod.watomatic.model.logs.MessageLog>) messages;
        adapter.submitList(typed);
    }
}
