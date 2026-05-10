package com.parishod.watomatic.activity.vip;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.parishod.watomatic.R;
import com.parishod.watomatic.activity.BaseActivity;
import com.parishod.watomatic.model.preferences.PreferencesManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight VIP contacts editor: free-form name list, no Contacts permission required.
 * The classifier's VIP_ONLY_BYPASS action calls
 * {@link PreferencesManager#isVipSender(String)} which does case-insensitive exact match
 * against the names stored here.
 */
public class VipContactsActivity extends BaseActivity {

    private VipAdapter adapter;
    private TextView emptyState;
    private RecyclerView recycler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vip_contacts);

        Toolbar toolbar = findViewById(R.id.vip_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ActionBar bar = getSupportActionBar();
            if (bar != null) {
                bar.setDisplayHomeAsUpEnabled(true);
                bar.setTitle(R.string.vip_contacts_title);
            }
        }

        emptyState = findViewById(R.id.vip_empty_state);
        recycler = findViewById(R.id.vip_recycler);
        TextInputEditText input = findViewById(R.id.vip_add_input);
        MaterialButton addButton = findViewById(R.id.vip_add_button);

        adapter = new VipAdapter(this::removeVip);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        addButton.setOnClickListener(v -> {
            String name = input.getText() != null ? input.getText().toString().trim() : "";
            if (name.isEmpty()) return;
            addVip(name);
            input.setText("");
        });

        renderList();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void addVip(String name) {
        Set<String> set = new HashSet<>(PreferencesManager.getPreferencesInstance(this).getVipContacts());
        set.add(name);
        PreferencesManager.getPreferencesInstance(this).setVipContacts(set);
        renderList();
    }

    private void removeVip(String name) {
        Set<String> set = new HashSet<>(PreferencesManager.getPreferencesInstance(this).getVipContacts());
        set.remove(name);
        PreferencesManager.getPreferencesInstance(this).setVipContacts(set);
        renderList();
    }

    private void renderList() {
        List<String> sorted = new ArrayList<>(
                PreferencesManager.getPreferencesInstance(this).getVipContacts());
        java.util.Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        adapter.submit(sorted);

        boolean empty = sorted.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private static class VipAdapter extends RecyclerView.Adapter<VipAdapter.VH> {
        interface OnRemoveListener { void onRemove(String name); }

        private final List<String> items = new ArrayList<>();
        private final OnRemoveListener removeListener;

        VipAdapter(OnRemoveListener l) { this.removeListener = l; }

        void submit(List<String> names) {
            items.clear();
            if (names != null) items.addAll(names);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_vip_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            String name = items.get(position);
            h.name.setText(name);
            h.remove.setOnClickListener(v -> {
                if (removeListener != null) removeListener.onRemove(name);
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView remove;

            VH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.vip_row_name);
                remove = itemView.findViewById(R.id.vip_row_remove);
            }
        }
    }
}
