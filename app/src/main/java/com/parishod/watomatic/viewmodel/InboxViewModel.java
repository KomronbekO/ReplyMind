package com.parishod.watomatic.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.parishod.watomatic.model.logs.MessageLog;
import com.parishod.watomatic.model.logs.MessageLogsDB;
import com.parishod.watomatic.model.logs.MessageLogsDao;

import java.util.List;

/**
 * Backs the Inbox screen. Filter changes are exposed as a {@link MutableLiveData} that
 * {@link Transformations#switchMap} routes to either the "all" query or the
 * per-category query.
 *
 * <p>The constraint is intentionally minimal: an empty/null categoryId means "all" and the
 * adapter shows whatever the DAO emits.</p>
 */
public class InboxViewModel extends AndroidViewModel {

    private final MessageLogsDao dao;
    private final MutableLiveData<String> selectedCategory = new MutableLiveData<>(null);
    private final LiveData<List<MessageLog>> messages;

    public InboxViewModel(@NonNull Application application) {
        super(application);
        this.dao = MessageLogsDB.getInstance(application).logsDao();
        this.messages = Transformations.switchMap(selectedCategory, cat -> {
            if (cat == null || cat.isEmpty()) {
                return dao.observeRecent();
            }
            return dao.observeByCategory(cat);
        });
    }

    public LiveData<List<MessageLog>> getMessages() {
        return messages;
    }

    public void setSelectedCategory(@Nullable String categoryId) {
        // Avoid spurious re-emit when nothing changed.
        String current = selectedCategory.getValue();
        if ((current == null && categoryId == null)
                || (current != null && current.equals(categoryId))) return;
        selectedCategory.setValue(categoryId);
    }

    @Nullable
    public String getSelectedCategory() {
        return selectedCategory.getValue();
    }

    /** Move a single message to a different category (used by manual reclassification). */
    public void moveToCategory(int messageId, @NonNull String newCategoryId) {
        // Run off the main thread to avoid hitting the allowMainThreadQueries footgun under load.
        new Thread(() -> dao.updateCategory(messageId, newCategoryId)).start();
    }
}
