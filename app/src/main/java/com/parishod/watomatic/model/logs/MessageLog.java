package com.parishod.watomatic.model.logs;

import androidx.annotation.NonNull;
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
    @ColumnInfo(name = "notif_id")
    private String notifId;
    @ColumnInfo(name = "notif_title")
    private String notifTitle;
    @ColumnInfo(name = "notif_arrived_time")
    private long notifArrivedTime;
    @ColumnInfo(name = "notif_is_replied")
    private boolean notifIsReplied;
    @ColumnInfo(name = "notif_replied_msg")
    private String notifRepliedMsg;
    @ColumnInfo(name = "notif_reply_time")
    private long notifReplyTime;

    // ReplyMind v3 schema additions — all nullable so the v2 → v3 ALTER TABLE migration succeeds.
    @ColumnInfo(name = "category")
    private String category;
    @ColumnInfo(name = "confidence")
    private Float confidence;
    @ColumnInfo(name = "action_taken")
    private String actionTaken;
    @ColumnInfo(name = "notif_body_snippet")
    private String notifBodySnippet;
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

    public String getNotifId() {
        return notifId;
    }

    public void setNotifId(String notifId) {
        this.notifId = notifId;
    }

    public String getNotifTitle() {
        return notifTitle;
    }

    public void setNotifTitle(String notifTitle) {
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

    public String getNotifRepliedMsg() {
        return notifRepliedMsg;
    }

    public void setNotifRepliedMsg(String notifRepliedMsg) {
        this.notifRepliedMsg = notifRepliedMsg;
    }

    public long getNotifReplyTime() {
        return notifReplyTime;
    }

    public void setNotifReplyTime(long notifReplyTime) {
        this.notifReplyTime = notifReplyTime;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Float getConfidence() { return confidence; }
    public void setConfidence(Float confidence) { this.confidence = confidence; }

    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }

    public String getNotifBodySnippet() { return notifBodySnippet; }
    public void setNotifBodySnippet(String notifBodySnippet) { this.notifBodySnippet = notifBodySnippet; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
}
