package com.parishod.watomatic.model.logs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.parishod.watomatic.model.utils.Constants;

@Database(entities = {MessageLog.class, AppPackage.class}, version = 3)
public abstract class MessageLogsDB extends RoomDatabase {
    private static final String DB_NAME = Constants.LOGS_DB_NAME;
    private static MessageLogsDB _instance;

    /**
     * v2 → v3: ReplyMind classifier columns. All nullable so existing rows keep their data
     * with NULL values; UI renders these as "Unclassified" / no badge.
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE message_logs ADD COLUMN category TEXT");
            db.execSQL("ALTER TABLE message_logs ADD COLUMN confidence REAL");
            db.execSQL("ALTER TABLE message_logs ADD COLUMN action_taken TEXT");
            db.execSQL("ALTER TABLE message_logs ADD COLUMN notif_body_snippet TEXT");
            db.execSQL("ALTER TABLE message_logs ADD COLUMN sentiment TEXT");
        }
    };

    public static synchronized MessageLogsDB getInstance(Context context) {
        if (_instance == null) {
            _instance = Room.databaseBuilder(context.getApplicationContext(), MessageLogsDB.class, DB_NAME)
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .allowMainThreadQueries()
                    .build();
        }
        return _instance;
    }

    public abstract MessageLogsDao logsDao();

    public abstract AppPackageDao appPackageDao();
}
