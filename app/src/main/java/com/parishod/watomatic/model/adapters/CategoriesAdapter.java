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
import com.parishod.watomatic.model.preferences.PreferencesManager;

import java.util.ArrayList;
import java.util.List;

public class CategoriesAdapter extends RecyclerView.Adapter<CategoriesAdapter.VH> {

    public interface OnCategoryClickListener {
        void onClick(ClassificationCategory category);
    }

    private final List<ClassificationCategory> items = new ArrayList<>();
    private final OnCategoryClickListener listener;

    public CategoriesAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<ClassificationCategory> categories) {
        items.clear();
        if (categories != null) items.addAll(categories);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_management, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Context ctx = h.itemView.getContext();
        ClassificationCategory c = items.get(position);

        h.emoji.setText(c.getEmoji() != null ? c.getEmoji() : "💬");
        int color;
        try {
            color = Color.parseColor(c.getColorHex() != null ? c.getColorHex() : "#757575");
        } catch (Exception e) {
            color = Color.parseColor("#757575");
        }
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        h.emojiBg.setBackground(circle);

        h.name.setText(c.getName());

        // Show the *effective* action — honoring any per-category override the user has set.
        CategoryAction effective = PreferencesManager.getPreferencesInstance(ctx)
                .getCategoryAction(c.getId());
        h.action.setText(actionLabel(ctx, effective));

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(c);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String actionLabel(Context ctx, CategoryAction a) {
        if (a == null) return ctx.getString(R.string.category_action_reply_default);
        switch (a) {
            case REPLY_TEMPLATE: return ctx.getString(R.string.category_action_reply_template);
            case SUPPRESS: return ctx.getString(R.string.category_action_suppress);
            case ESCALATE: return ctx.getString(R.string.category_action_escalate);
            case VIP_ONLY_BYPASS: return ctx.getString(R.string.category_action_vip_only_bypass);
            case REPLY_DEFAULT:
            default:
                return ctx.getString(R.string.category_action_reply_default);
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final View emojiBg;
        final TextView emoji;
        final TextView name;
        final TextView action;

        VH(@NonNull View v) {
            super(v);
            emojiBg = v.findViewById(R.id.cat_emoji_bg);
            emoji = v.findViewById(R.id.cat_emoji);
            name = v.findViewById(R.id.cat_name);
            action = v.findViewById(R.id.cat_action_label);
        }
    }
}
