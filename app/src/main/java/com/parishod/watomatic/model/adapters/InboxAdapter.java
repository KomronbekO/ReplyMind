package com.parishod.watomatic.model.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.parishod.watomatic.R;
import com.parishod.watomatic.model.classifier.CategoryAction;
import com.parishod.watomatic.model.classifier.ClassificationCategory;
import com.parishod.watomatic.model.logs.MessageLog;
import com.parishod.watomatic.model.preferences.PreferencesManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * RecyclerView adapter for the Inbox screen. Each row shows the category-coloured emoji
 * badge, sender, optional snippet, relative timestamp, and the action ReplyMind took.
 */
public class InboxAdapter extends RecyclerView.Adapter<InboxAdapter.MessageVH> {

    public interface OnMessageClickListener {
        void onClick(MessageLog log);
    }

    private final List<MessageLog> items = new ArrayList<>();
    private final OnMessageClickListener clickListener;

    /**
     * Lookup keyed by category id → category, refreshed when the user edits the category list.
     * Avoids walking {@link PreferencesManager#getClassificationCategories()} per row.
     */
    private final Map<String, ClassificationCategory> categoryIndex = new HashMap<>();

    public InboxAdapter(@NonNull Context context, OnMessageClickListener listener) {
        this.clickListener = listener;
        rebuildCategoryIndex(context);
    }

    /** Call when categories may have changed (e.g., user navigated back from CategoriesActivity). */
    public void rebuildCategoryIndex(@NonNull Context context) {
        categoryIndex.clear();
        List<ClassificationCategory> cats = PreferencesManager.getPreferencesInstance(context)
                .getClassificationCategories();
        for (ClassificationCategory c : cats) {
            if (c == null || c.getId() == null) continue;
            categoryIndex.put(c.getId().toLowerCase(), c);
        }
    }

    public void submitList(List<MessageLog> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inbox_message, parent, false);
        return new MessageVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageVH h, int position) {
        MessageLog log = items.get(position);
        Context ctx = h.itemView.getContext();

        ClassificationCategory cat = (log.getCategory() != null)
                ? categoryIndex.get(log.getCategory().toLowerCase())
                : null;

        // Colored circle background + emoji
        h.emoji.setText(cat != null ? cat.getEmoji() : "💬");
        int color;
        try {
            color = Color.parseColor(cat != null ? cat.getColorHex() : "#757575");
        } catch (Exception e) {
            color = Color.parseColor("#757575");
        }
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        h.emojiBg.setBackground(circle);

        h.sender.setText(log.getNotifTitle() != null ? log.getNotifTitle() : "(unknown)");

        String snippet = log.getNotifBodySnippet();
        if (snippet != null && !snippet.isEmpty()) {
            h.snippet.setText(snippet);
            h.snippet.setVisibility(View.VISIBLE);
        } else {
            h.snippet.setVisibility(View.GONE);
        }

        h.time.setText(relativeTime(ctx, log.getNotifArrivedTime()));

        h.action.setText(actionLabel(ctx, log));

        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(log);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String actionLabel(Context ctx, MessageLog log) {
        if (log.getActionTaken() == null) {
            return ctx.getString(R.string.inbox_action_default);
        }
        try {
            CategoryAction a = CategoryAction.valueOf(log.getActionTaken());
            switch (a) {
                case SUPPRESS: return ctx.getString(R.string.inbox_action_suppressed);
                case ESCALATE: return ctx.getString(R.string.inbox_action_escalated);
                case REPLY_TEMPLATE: return ctx.getString(R.string.inbox_action_template);
                case VIP_ONLY_BYPASS:
                case REPLY_DEFAULT:
                default:
                    return log.isNotifIsReplied()
                            ? ctx.getString(R.string.inbox_action_replied)
                            : ctx.getString(R.string.inbox_action_suppressed);
            }
        } catch (IllegalArgumentException e) {
            return ctx.getString(R.string.inbox_action_default);
        }
    }

    private static String relativeTime(Context ctx, long thenMs) {
        long deltaMs = System.currentTimeMillis() - thenMs;
        if (deltaMs < TimeUnit.MINUTES.toMillis(1)) {
            return ctx.getString(R.string.inbox_relative_now);
        }
        long minutes = TimeUnit.MILLISECONDS.toMinutes(deltaMs);
        if (minutes < 60) {
            return ctx.getString(R.string.inbox_relative_min, minutes);
        }
        long hours = TimeUnit.MILLISECONDS.toHours(deltaMs);
        if (hours < 48) {
            return ctx.getString(R.string.inbox_relative_hour, hours);
        }
        long days = TimeUnit.MILLISECONDS.toDays(deltaMs);
        return ctx.getString(R.string.inbox_relative_day, days);
    }

    static class MessageVH extends RecyclerView.ViewHolder {
        final View emojiBg;
        final TextView emoji;
        final TextView sender;
        final TextView snippet;
        final TextView time;
        final TextView action;

        MessageVH(@NonNull View itemView) {
            super(itemView);
            emojiBg = itemView.findViewById(R.id.inbox_item_emoji_bg);
            emoji = itemView.findViewById(R.id.inbox_item_emoji);
            sender = itemView.findViewById(R.id.inbox_item_sender);
            snippet = itemView.findViewById(R.id.inbox_item_snippet);
            time = itemView.findViewById(R.id.inbox_item_time);
            action = itemView.findViewById(R.id.inbox_item_action);
        }
    }
}
