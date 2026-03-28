package edu.msu.cse476.msucompanion;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "contacts")
public class Contact {
    @PrimaryKey(autoGenerate = true)
    public long id;  // local ID of contact (will reset once logged out)
    private String remoteId;    // firestore document ID
    public String userId;  // firestore user ID

    public String name;
    public String phoneNumber;

    public Contact() {}

    public Contact(String remoteId, String userId, String name, String phoneNumber) {
        this.remoteId = remoteId;
        this.userId = userId;
        this.name = name;
        this.phoneNumber = phoneNumber;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getRemoteId() { return remoteId; }
    public void setRemoteId(String remoteId) { this.remoteId = remoteId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}
