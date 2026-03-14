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

    @Query("DELETE FROM contacts WHERE userId = :userId")
    void deleteContactsForUser(String userId);

    @Query("SELECT * FROM contacts WHERE id = :id")
   Contact getContactById(int id);

    @Query("SELECT * FROM contacts WHERE userId = :userId")
    List<Contact> getContactsForUser(int userId);

    @Query("SELECT * FROM contacts WHERE userId = :userId AND phoneNumber LIKE '%' || :phone || '%'")
    List<Contact> getContactsByPhone(String phone, int userId);

    @Query("SELECT * FROM contacts WHERE userId = :userId AND name LIKE '%' || :name || '%'")
    List<Contact> getContactsByName(String name, int userId);

    @Query("SELECT * FROM contacts WHERE userId = :userId AND phoneNumber LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%'")
    List<Contact> searchContacts(int userId, String query);
}
