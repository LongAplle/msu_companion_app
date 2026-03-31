package edu.msu.cse476.msucompanion;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Query;

import java.util.List;

@Dao
public interface WalkSessionDao {
    @Insert
    long insert(WalkSession session);

    @Update
    void update(WalkSession session);

    @Query("SELECT * FROM walk_sessions WHERE id = :id")
    WalkSession getSessionById(long id);

    @Query("SELECT * FROM walk_sessions WHERE userId = :userId ORDER BY startTime DESC")
    List<WalkSession> getSessionsForUser(String userId);

    @Query("DELETE FROM walk_sessions WHERE userId = :userId")
    void deleteAllByUserId(String userId);
}
