package edu.msu.cse476.msucompanion;

import androidx.room.Dao;
import androidx.room.Query;

/**
 * A general DAO for the app database
 */
@Dao
public interface DatabaseDao {
    // Reset the auto increment sequence
    @Query("DELETE FROM sqlite_sequence")
    void resetSequence();
}
