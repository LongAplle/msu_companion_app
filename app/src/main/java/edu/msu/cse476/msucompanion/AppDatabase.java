package edu.msu.cse476.msucompanion;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import android.content.Context;

@Database(entities = {Contact.class, WalkSession.class}, version = 4, exportSchema = false)
@TypeConverters({DateLongConverters.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract ContactDao contactDao();
    public abstract WalkSessionDao walkSessionDao();
    public abstract DatabaseDao databaseDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "msu_companion_db"
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}