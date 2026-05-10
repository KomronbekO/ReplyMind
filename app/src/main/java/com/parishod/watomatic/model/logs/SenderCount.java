package com.parishod.watomatic.model.logs;

import androidx.room.ColumnInfo;

/** Aggregation row used by Insights screen. */
public class SenderCount {
    @ColumnInfo(name = "sender")
    public String sender;

    @ColumnInfo(name = "count")
    public int count;
}
