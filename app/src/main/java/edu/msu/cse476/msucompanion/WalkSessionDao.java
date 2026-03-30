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

    // Reserved if needed may be needed when syncing
    // Get a session by its Firestore remote ID
    // Used when syncing updates from Firestore back to local Room

    //@Query("SELECT * FROM walk_sessions WHERE remoteId = :remoteId")
    //WalkSession getSessionByRemoteId(String remoteId);


    // Clears all local sessions for a user before re-fetching from Firestore on login
    // Prevents duplicate sessions from appearing if the user logs in multiple times
    @Query("DELETE FROM walk_sessions WHERE userId = :userId")
    void deleteAllByUserId(String userId);
}
