package com.parishod.watomatic.activity.categories;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.parishod.watomatic.R;
import com.parishod.watomatic.activity.BaseActivity;
import com.parishod.watomatic.model.classifier.CategoryAction;
import com.parishod.watomatic.model.classifier.ClassificationCategory;
import com.parishod.watomatic.model.preferences.PreferencesManager;

public class CategoryEditorActivity extends BaseActivity {

    public static final String EXTRA_CATEGORY_ID = "category_id";

    private String categoryId;
    private RadioGroup actionGroup;
    private TextInputEditText templateInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_editor);

        Toolbar toolbar = findViewById(R.id.category_editor_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ActionBar bar = getSupportActionBar();
            if (bar != null) {
                bar.setDisplayHomeAsUpEnabled(true);
                bar.setTitle(R.string.category_editor_title);
            }
        }

        categoryId = getIntent().getStringExtra(EXTRA_CATEGORY_ID);
        PreferencesManager prefs = PreferencesManager.getPreferencesInstance(this);
        ClassificationCategory category = prefs.findCategoryById(categoryId);
        if (category == null) {
            finish();
            return;
        }

        TextView nameLabel = findViewById(R.id.category_editor_name);
        TextView descLabel = findViewById(R.id.category_editor_description);
        actionGroup = findViewById(R.id.category_action_group);
        templateInput = findViewById(R.id.category_template_input);
        MaterialButton saveButton = findViewById(R.id.category_save_button);

        nameLabel.setText((category.getEmoji() != null ? category.getEmoji() + "  " : "")
                + category.getName());
        descLabel.setText(category.getDescription());

        // Pre-select the user's current action choice (or the category default).
        CategoryAction effective = prefs.getCategoryAction(categoryId);
        actionGroup.check(idForAction(effective));

        // Pre-fill template if any.
        templateInput.setText(prefs.getCategoryTemplate(categoryId));

        saveButton.setOnClickListener(v -> save());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void save() {
        PreferencesManager prefs = PreferencesManager.getPreferencesInstance(this);
        prefs.setCategoryAction(categoryId, actionForId(actionGroup.getCheckedRadioButtonId()));
        String template = templateInput.getText() != null ? templateInput.getText().toString().trim() : "";
        prefs.setCategoryTemplate(categoryId, template);

        Toast.makeText(this, R.string.category_saved_toast, Toast.LENGTH_SHORT).show();
        finish();
    }

    private int idForAction(CategoryAction action) {
        if (action == null) return R.id.action_reply_default;
        switch (action) {
            case REPLY_TEMPLATE: return R.id.action_reply_template;
            case SUPPRESS: return R.id.action_suppress;
            case ESCALATE: return R.id.action_escalate;
            case VIP_ONLY_BYPASS: return R.id.action_vip_only_bypass;
            case REPLY_DEFAULT:
            default: return R.id.action_reply_default;
        }
    }

    private CategoryAction actionForId(int radioId) {
        if (radioId == R.id.action_reply_template) return CategoryAction.REPLY_TEMPLATE;
        if (radioId == R.id.action_suppress) return CategoryAction.SUPPRESS;
        if (radioId == R.id.action_escalate) return CategoryAction.ESCALATE;
        if (radioId == R.id.action_vip_only_bypass) return CategoryAction.VIP_ONLY_BYPASS;
        return CategoryAction.REPLY_DEFAULT;
    }
}
