package com.parishod.watomatic.model.logs;

import androidx.room.ColumnInfo;

/** Aggregation row used by Insights screen. */
public class CategoryCount {
    @ColumnInfo(name = "category")
    public String category;

    @ColumnInfo(name = "count")
    public int count;
}
