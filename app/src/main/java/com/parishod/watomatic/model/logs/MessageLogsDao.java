package com.parishod.watomatic.model.logs;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MessageLogsDao {
    @Query("SELECT message_logs.notif_reply_time FROM MESSAGE_LOGS " +
            "INNER JOIN app_packages ON app_packages.`index` = message_logs.`index` " +
            "WHERE app_packages.package_name=:packageName AND message_logs.notif_title=:title ORDER BY notif_reply_time DESC LIMIT 1"
    )
    long getLastReplyTimeStamp(String title, String packageName);

    @Insert
    void logReply(MessageLog log);

    @Query("SELECT COUNT(id) FROM MESSAGE_LOGS")
    long getNumReplies();

    //https://stackoverflow.com/questions/11771580/deleting-android-sqlite-rows-older-than-x-days
    @Query("DELETE FROM message_logs WHERE notif_reply_time <= strftime('%s', datetime('now', '-30 days'));")
    void purgeMessageLogs();

    @Query("SELECT notif_reply_time FROM MESSAGE_LOGS ORDER BY notif_reply_time DESC LIMIT 1")
    long getFirstRepliedTime();

    // ---- ReplyMind: Inbox + Insights queries ----

    @Query("SELECT * FROM message_logs ORDER BY notif_arrived_time DESC LIMIT 500")
    LiveData<List<MessageLog>> observeRecent();

    @Query("SELECT * FROM message_logs WHERE category = :categoryId ORDER BY notif_arrived_time DESC LIMIT 500")
    LiveData<List<MessageLog>> observeByCategory(String categoryId);

    @Query("UPDATE message_logs SET category = :newCategoryId WHERE id = :id")
    void updateCategory(int id, String newCategoryId);

    @Query("SELECT category, COUNT(*) AS count FROM message_logs " +
            "WHERE notif_arrived_time >= :sinceMs AND category IS NOT NULL GROUP BY category")
    LiveData<List<CategoryCount>> categoryCounts(long sinceMs);

    @Query("SELECT notif_title AS sender, COUNT(*) AS count FROM message_logs " +
            "WHERE notif_arrived_time >= :sinceMs AND notif_title IS NOT NULL " +
            "GROUP BY notif_title ORDER BY count DESC LIMIT 5")
    LiveData<List<SenderCount>> topSenders(long sinceMs);

    @Query("SELECT COUNT(*) FROM message_logs WHERE notif_is_replied = 1 AND notif_reply_time >= :sinceMs")
    LiveData<Integer> repliesCount(long sinceMs);
}
