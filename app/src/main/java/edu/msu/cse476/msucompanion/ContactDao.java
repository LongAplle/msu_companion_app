package edu.msu.cse476.msucompanion;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;

import java.util.List;

@Dao
public interface ContactDao {
    @Insert
    void insert(Contact contact);

    @Update
    void update(Contact contact);

    @Delete
    void delete(Contact contact);

    @Query("SELECT * FROM contacts WHERE id = :id")
    Contact getContactById(long id);

    @Query("SELECT * FROM contacts WHERE userId = :userId")
    List<Contact> getContactsForUser(String userId);

    @Query("SELECT * FROM contacts WHERE userId = :userId AND (phoneNumber LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%')")
    List<Contact> searchContacts(String userId, String query);

    @Query("SELECT phoneNumber FROM contacts WHERE userId = :userId")
    List<String> getAllPhoneNumber(String userId);
}
