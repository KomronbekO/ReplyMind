package com.parishod.watomatic.activity.inbox;

import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.parishod.watomatic.R;
import com.parishod.watomatic.activity.BaseActivity;
import com.parishod.watomatic.model.classifier.CategoryAction;
import com.parishod.watomatic.model.classifier.ClassificationCategory;
import com.parishod.watomatic.model.preferences.PreferencesManager;

import java.util.Date;

/**
 * Read-only detail view for one classified message. Shows the classifier's verdict (category +
 * confidence + reasoning) and the action that was taken. The "Move to category" affordance is
 * deferred to a follow-up — the underlying DAO update is already in place via
 * {@code InboxViewModel.moveToCategory}.
 */
public class MessageDetailActivity extends BaseActivity {

    public static final String EXTRA_MESSAGE_ID = "msg_id";
    public static final String EXTRA_SENDER = "sender";
    public static final String EXTRA_BODY = "body";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_CONFIDENCE = "confidence";
    public static final String EXTRA_ACTION = "action";
    public static final String EXTRA_REPLY = "reply";
    public static final String EXTRA_ARRIVED = "arrived";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_detail);

        Toolbar toolbar = findViewById(R.id.detail_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ActionBar bar = getSupportActionBar();
            if (bar != null) {
                bar.setDisplayHomeAsUpEnabled(true);
                bar.setTitle(R.string.message_detail_title);
            }
        }

        Bundle in = getIntent().getExtras();
        if (in == null) {
            finish();
            return;
        }

        TextView senderView = findViewById(R.id.detail_sender);
        TextView bodyView = findViewById(R.id.detail_body);
        TextView noSnippetView = findViewById(R.id.detail_no_snippet);
        TextView arrivedView = findViewById(R.id.detail_arrived);
        TextView categoryView = findViewById(R.id.detail_category);
        TextView confidenceView = findViewById(R.id.detail_confidence);
        TextView reasoningView = findViewById(R.id.detail_reasoning);
        TextView actionView = findViewById(R.id.detail_action);
        TextView replyHeader = findViewById(R.id.detail_reply_header);
        TextView replyView = findViewById(R.id.detail_reply);
        LinearLayout categoryBadge = findViewById(R.id.detail_category_badge);

        senderView.setText(in.getString(EXTRA_SENDER, "(unknown)"));

        String body = in.getString(EXTRA_BODY);
        if (body != null && !body.isEmpty()) {
            bodyView.setText(body);
            bodyView.setVisibility(View.VISIBLE);
            noSnippetView.setVisibility(View.GONE);
        } else {
            bodyView.setVisibility(View.GONE);
            noSnippetView.setVisibility(View.VISIBLE);
        }

        long arrived = in.getLong(EXTRA_ARRIVED, 0L);
        if (arrived > 0) {
            arrivedView.setText(DateFormat.format("MMM d, h:mm a", new Date(arrived)));
        } else {
            arrivedView.setVisibility(View.GONE);
        }

        String catId = in.getString(EXTRA_CATEGORY);
        ClassificationCategory cat = (catId != null)
                ? PreferencesManager.getPreferencesInstance(this).findCategoryById(catId)
                : null;
        if (cat != null) {
            categoryView.setText(cat.getEmoji() + " " + cat.getName());
            try {
                int color = Color.parseColor(cat.getColorHex());
                categoryBadge.setBackgroundColor(adjustAlpha(color, 0.15f));
                categoryView.setTextColor(color);
            } catch (Exception ignored) { }
        } else {
            categoryView.setText(R.string.inbox_unclassified);
        }

        float conf = in.getFloat(EXTRA_CONFIDENCE, -1f);
        if (conf >= 0f) {
            int pct = Math.max(0, Math.min(100, Math.round(conf * 100f)));
            confidenceView.setText(getString(R.string.message_detail_confidence, pct));
        } else {
            confidenceView.setVisibility(View.GONE);
        }

        // Reasoning isn't persisted on MessageLog today (we kept the schema lean); when present
        // in a future revision it'll show here. For now hide it.
        reasoningView.setVisibility(View.GONE);

        String actionName = in.getString(EXTRA_ACTION);
        actionView.setText(actionLabel(actionName));

        String reply = in.getString(EXTRA_REPLY);
        if (reply != null && !reply.isEmpty()) {
            replyView.setText(reply);
            replyView.setVisibility(View.VISIBLE);
            replyHeader.setVisibility(View.VISIBLE);
        } else {
            replyView.setVisibility(View.GONE);
            replyHeader.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String actionLabel(String name) {
        if (name == null) return getString(R.string.inbox_action_default);
        try {
            CategoryAction a = CategoryAction.valueOf(name);
            switch (a) {
                case SUPPRESS: return getString(R.string.inbox_action_suppressed);
                case ESCALATE: return getString(R.string.inbox_action_escalated);
                case REPLY_TEMPLATE: return getString(R.string.inbox_action_template);
                default: return getString(R.string.inbox_action_replied);
            }
        } catch (IllegalArgumentException e) {
            return getString(R.string.inbox_action_default);
        }
    }

    private static int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
