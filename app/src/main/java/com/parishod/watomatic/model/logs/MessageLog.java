package com.parishod.watomatic.model.logs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "message_logs",
        foreignKeys = {@ForeignKey(
                entity = AppPackage.class,
                parentColumns = "index",
                childColumns = "index",
                onDelete = ForeignKey.CASCADE
        )},
        indices = {
                @Index(value = "index")
        })
public class MessageLog {
    @PrimaryKey(autoGenerate = true)
    @NonNull
    private int id;
    @NonNull
    private int index;
    @Nullable
    @ColumnInfo(name = "notif_id")
    private String notifId;
    @Nullable
    @ColumnInfo(name = "notif_title")
    private String notifTitle;
    @ColumnInfo(name = "notif_arrived_time")
    private long notifArrivedTime;
    @ColumnInfo(name = "notif_is_replied")
    private boolean notifIsReplied;
    @Nullable
    @ColumnInfo(name = "notif_replied_msg")
    private String notifRepliedMsg;
    @ColumnInfo(name = "notif_reply_time")
    private long notifReplyTime;

    // ReplyMind v3 schema additions — all nullable so the v2 → v3 ALTER TABLE migration succeeds.
    @Nullable
    @ColumnInfo(name = "category")
    private String category;
    @Nullable
    @ColumnInfo(name = "confidence")
    private Float confidence;
    @Nullable
    @ColumnInfo(name = "action_taken")
    private String actionTaken;
    @Nullable
    @ColumnInfo(name = "notif_body_snippet")
    private String notifBodySnippet;
    @Nullable
    @ColumnInfo(name = "sentiment")
    private String sentiment;

    public MessageLog(int index,
                      String notifTitle,
                      long notifArrivedTime,
                      String notifRepliedMsg,
                      long notifReplyTime
    ) {
        this.index = index;
        this.notifId = java.util.UUID.randomUUID().toString();
        this.notifTitle = notifTitle;
        this.notifArrivedTime = notifArrivedTime;
        this.notifRepliedMsg = notifRepliedMsg;
        this.notifReplyTime = notifReplyTime;
        this.notifIsReplied = true;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    @Nullable
    public String getNotifId() {
        return notifId;
    }

    public void setNotifId(@Nullable String notifId) {
        this.notifId = notifId;
    }

    @Nullable
    public String getNotifTitle() {
        return notifTitle;
    }

    public void setNotifTitle(@Nullable String notifTitle) {
        this.notifTitle = notifTitle;
    }

    public long getNotifArrivedTime() {
        return notifArrivedTime;
    }

    public void setNotifArrivedTime(long notifArrivedTime) {
        this.notifArrivedTime = notifArrivedTime;
    }

    public boolean isNotifIsReplied() {
        return notifIsReplied;
    }

    public void setNotifIsReplied(boolean notifIsReplied) {
        this.notifIsReplied = notifIsReplied;
    }

    @Nullable
    public String getNotifRepliedMsg() {
        return notifRepliedMsg;
    }

    public void setNotifRepliedMsg(@Nullable String notifRepliedMsg) {
        this.notifRepliedMsg = notifRepliedMsg;
    }

    public long getNotifReplyTime() {
        return notifReplyTime;
    }

    public void setNotifReplyTime(long notifReplyTime) {
        this.notifReplyTime = notifReplyTime;
    }

    @Nullable
    public String getCategory() { return category; }
    public void setCategory(@Nullable String category) { this.category = category; }

    @Nullable
    public Float getConfidence() { return confidence; }
    public void setConfidence(@Nullable Float confidence) { this.confidence = confidence; }

    @Nullable
    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(@Nullable String actionTaken) { this.actionTaken = actionTaken; }

    @Nullable
    public String getNotifBodySnippet() { return notifBodySnippet; }
    public void setNotifBodySnippet(@Nullable String notifBodySnippet) { this.notifBodySnippet = notifBodySnippet; }

    @Nullable
    public String getSentiment() { return sentiment; }
    public void setSentiment(@Nullable String sentiment) { this.sentiment = sentiment; }
}
