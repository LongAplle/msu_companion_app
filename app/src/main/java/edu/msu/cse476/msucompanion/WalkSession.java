package edu.msu.cse476.msucompanion;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.Date;

@Entity(tableName = "walk_sessions")
public class WalkSession {
    @PrimaryKey(autoGenerate = true)
    public long id;                 // local ID of walk session
    public String userId;             // Firestore user ID
    public Date startTime;
    public Date endTime;           // nullable
    public Double startLat;        // initially nullable
    public Double startLng;        // initially nullable
    public String destinationName;
    public Double destinationLat;
    public Double destinationLng;
    public String status;          // "active", "completed", "stopped"

    public WalkSession() {}

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public Double getStartLat() { return startLat; }
    public void setStartLat(Double startLat) { this.startLat = startLat; }

    public Double getStartLng() { return startLng; }
    public void setStartLng(Double startLng) { this.startLng = startLng; }

    public String getDestinationName() { return destinationName; }
    public void setDestinationName(String destinationName) { this.destinationName = destinationName; }

    public Double getDestinationLat() { return destinationLat; }
    public void setDestinationLat(Double destinationLat) { this.destinationLat = destinationLat; }

    public Double getDestinationLng() { return destinationLng; }
    public void setDestinationLng(Double destinationLng) { this.destinationLng = destinationLng; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}