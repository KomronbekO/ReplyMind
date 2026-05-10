package com.parishod.watomatic.activity.categories;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.parishod.watomatic.R;
import com.parishod.watomatic.activity.BaseActivity;
import com.parishod.watomatic.model.adapters.CategoriesAdapter;
import com.parishod.watomatic.model.preferences.PreferencesManager;

public class CategoriesActivity extends BaseActivity {

    private CategoriesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_categories);

        Toolbar toolbar = findViewById(R.id.categories_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ActionBar bar = getSupportActionBar();
            if (bar != null) {
                bar.setDisplayHomeAsUpEnabled(true);
                bar.setTitle(R.string.manage_categories_title);
            }
        }

        RecyclerView recycler = findViewById(R.id.categories_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new CategoriesAdapter(category -> {
            Intent i = new Intent(this, CategoryEditorActivity.class);
            i.putExtra(CategoryEditorActivity.EXTRA_CATEGORY_ID, category.getId());
            startActivity(i);
        });
        recycler.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload from prefs so any edits made in the editor are reflected immediately.
        adapter.submit(PreferencesManager.getPreferencesInstance(this).getClassificationCategories());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
