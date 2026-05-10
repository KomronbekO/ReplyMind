package com.parishod.watomatic.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.parishod.watomatic.model.logs.CategoryCount;
import com.parishod.watomatic.model.logs.MessageLogsDB;
import com.parishod.watomatic.model.logs.MessageLogsDao;
import com.parishod.watomatic.model.logs.SenderCount;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Aggregates the DAO queries for the Insights screen. Time range is exposed as a
 * {@link MutableLiveData} switched between Today / 7d / 30d.
 */
public class InsightsViewModel extends AndroidViewModel {

    public enum Range { TODAY, WEEK, MONTH }

    /** Constant used to estimate time saved per auto-reply. ~30s of typing + thinking. */
    public static final long AVG_REPLY_TIME_SAVED_MS = TimeUnit.SECONDS.toMillis(30);

    private final MutableLiveData<Range> range = new MutableLiveData<>(Range.WEEK);
    private final MessageLogsDao dao;

    private final LiveData<List<CategoryCount>> categoryCounts;
    private final LiveData<List<SenderCount>> topSenders;
    private final LiveData<Integer> repliesCount;

    public InsightsViewModel(@NonNull Application application) {
        super(application);
        this.dao = MessageLogsDB.getInstance(application).logsDao();
        this.categoryCounts = Transformations.switchMap(range, r -> dao.categoryCounts(sinceMs(r)));
        this.topSenders = Transformations.switchMap(range, r -> dao.topSenders(sinceMs(r)));
        this.repliesCount = Transformations.switchMap(range, r -> dao.repliesCount(sinceMs(r)));
    }

    public LiveData<Range> getRange() { return range; }

    public LiveData<List<CategoryCount>> getCategoryCounts() { return categoryCounts; }

    public LiveData<List<SenderCount>> getTopSenders() { return topSenders; }

    public LiveData<Integer> getRepliesCount() { return repliesCount; }

    public void setRange(Range r) {
        if (r != null && r != range.getValue()) range.setValue(r);
    }

    private static long sinceMs(Range r) {
        long now = System.currentTimeMillis();
        switch (r) {
            case TODAY: return now - TimeUnit.HOURS.toMillis(24);
            case MONTH: return now - TimeUnit.DAYS.toMillis(30);
            case WEEK:
            default:    return now - TimeUnit.DAYS.toMillis(7);
        }
    }
}
