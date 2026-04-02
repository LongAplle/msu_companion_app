package edu.msu.cse476.msucompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WalkSessionService extends Service implements LocationHelper.LocationUpdateListener {
    // Channel ID for notification from this app
    private static final String CHANNEL_ID = "walk_session_channel";

    // Notification ID for the foreground service, can be any number
    private static final int NOTIFICATION_ID = 2026;

    // Local Binder for communication with the service
    private final IBinder binder = new LocalBinder();

    // List of registered listeners (activities)
    private final List<SessionListener> listeners = new ArrayList<>();

    // Databases
    private AppDatabase db;  // App local database
    private FirebaseFirestore firestoreDb;  // Firestore remote database

    // Session data
    private String currUserId;
    private String currentSessionId;      // Firestore document ID
    private long localSessionId = -1;      // Room session ID
    private Destination destination;
    private Location startLocation;
    private boolean startLocationUpdated = false;
    private boolean walkSessionActive = false;
    private boolean arrivalAlreadyHandled = false;
    private List<String> contactPhones = new ArrayList<>();

    // Helper class used to manage GPS/location services
    private LocationHelper locationHelper;

    // // Handler for main thread, used for updating UI and periodic location pings
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable sendLocationUpdateRunnable;
    private Location lastKnownLocation;

    // Distance threshold used to determine arrival (in meters)
    private static final float ARRIVAL_THRESHOLD_METERS = 50.0f;

    // Permission request code for SMS (will be handled in activity)
    public static final int SMS_PERMISSION_REQUEST_CODE = 2001;

    // Interface for activities to receive updates
    public interface SessionListener {
        void onSessionStarted();
        void onSessionEnded(boolean arrived);
        void onLocationUpdated(Location location);
        void onDistanceUpdated(float distanceMeters);
        void onLocationError(String message);
    }

    // Binder class for communication with the service
    public class LocalBinder extends Binder {
        public WalkSessionService getService() {
            return WalkSessionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        locationHelper = new LocationHelper(this);
        db = AppDatabase.getInstance(this);
        firestoreDb = FirebaseFirestore.getInstance();

        // Create notification channel (for Android O+)
        createNotificationChannel();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case "START_SESSION":
                    // Extract destination and other data from intent
                    String destName = intent.getStringExtra("destination_name");
                    double destLat = intent.getDoubleExtra("destination_lat", 0.0);
                    double destLng = intent.getDoubleExtra("destination_lng", 0.0);
                    destination = new Destination(destName, destLat, destLng);

                    // Get current user ID
                    SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
                    currUserId = prefs.getString("userId", null);
                    if (currUserId != null) {
                        startWalkSession();
                    }
                    break;

                case "STOP_SESSION":
                    stopWalkSession(false);
                    break;
            }
        }

        // Make this service a foreground service with a persistent notification
        startForeground(NOTIFICATION_ID, createNotification());
        return START_NOT_STICKY; // We don't want the system to restart it if killed
    }

    // Methods for the bound activity
    public void registerListener(SessionListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    public void unregisterListener(SessionListener listener) {
        listeners.remove(listener);
    }
    public Location getLastKnownLocation() {
        return lastKnownLocation;
    }
    public boolean isWalkSessionActive() {
        return walkSessionActive;
    }

    // Foreground notification
    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Walk Session",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MSU Companion")
                .setContentText("Walking session active")
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
    }

    // LocationHelper callbacks
    @Override
    public void onLocationUpdated(Location location) {
        handleLocationUpdate(location);
    }

    @Override
    public void onLocationError(String message) {
        // Notify all bound activities
        for (SessionListener listener : listeners) {
            listener.onLocationError(message);
        }
    }

    // Session management
    private void startWalkSession() {
        // Guard against starting an already active session
        if (walkSessionActive) return;

        // Check if the app has location permission
        if (!locationHelper.hasLocationPermission()) {
            return;  // The activity will request permission and restart the service
        }

        // Fetch all trusted contacts from local database
        new Thread(() -> {
            List<String> phones = db.contactDao().getAllPhoneNumber(currUserId);

            handler.post(() -> {
                contactPhones = phones;

                // Send initial SMS
                sendSMSMessage("I'm starting a walk to " + destination.getName() + ".");
            });
        }).start();

        Date sessionStartTime = new Date();

        // Create a new session document in Firestore when the walk starts
        Map<String, Object> session = new HashMap<>();
        session.put("userId", currUserId);
        session.put("destinationName", destination.getName());
        session.put("destinationLat", destination.getLatitude());
        session.put("destinationLng", destination.getLongitude());
        session.put("startTime", sessionStartTime);
        session.put("endTime", null);
        session.put("status", "active");

        firestoreDb.collection("sessions")
                .add(session)
                .addOnSuccessListener(documentReference -> {
                    // Save the session ID so we can update it when the walk ends
                    currentSessionId = documentReference.getId();

                    // Add Session to local Room database
                    new Thread(() -> {
                        WalkSession walkSession = new WalkSession();
                        walkSession.setRemoteId(currentSessionId);
                        walkSession.setUserId(currUserId);
                        walkSession.setStartTime(sessionStartTime);
                        walkSession.setDestinationName(destination.getName());
                        walkSession.setDestinationLat(destination.getLatitude());
                        walkSession.setDestinationLng(destination.getLongitude());
                        walkSession.setStatus("active");
                        localSessionId = db.walkSessionDao().insert(walkSession);

                        handler.post(() -> {
                                    // Start GPS updates
                                    walkSessionActive = true;
                                    locationHelper.startLocationUpdates(WalkSessionService.this);

                                    // Ping location to Firestore every 5 minutes so contacts can track progress
                                    sendLocationUpdateRunnable = new Runnable() {
                                        @Override
                                        public void run() {
                                            if (walkSessionActive && lastKnownLocation != null) {
                                                double lat = lastKnownLocation.getLatitude();
                                                double lng = lastKnownLocation.getLongitude();

                                                // Send SMS notification with maps link
                                                String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;
                                                sendSMSMessage("My current location: " + mapsLink);

                                                // Also ping location to Firestore under the current session
                                                addLocationPing(lat, lng);
                                            }
                                            if (walkSessionActive) {
                                                handler.postDelayed(this, 5 * 60 * 1000); // 5 minutes
                                            }
                                        }
                                    };
                                    handler.post(sendLocationUpdateRunnable);

                                    // Notify all listeners that the session has started
                                    for (SessionListener listener : listeners) {
                                        listener.onSessionStarted();
                                    }

                                    Toast.makeText(this, "Walk session started", Toast.LENGTH_SHORT).show();
                                }
                        );
                    }).start();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to start session: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void stopWalkSession(boolean arrived) {
        if (!walkSessionActive) return;

        // Notify contacts
        if (arrived) {
            sendSMSMessage("I have arrived at " + destination.getName() + ".");
        } else if (lastKnownLocation != null) {
            double lat = lastKnownLocation.getLatitude();
            double lng = lastKnownLocation.getLongitude();
            String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;
            sendSMSMessage("I stopped the walk. My final location: " + mapsLink);
        }

        walkSessionActive = false;
        locationHelper.stopLocationUpdates();
        handler.removeCallbacks(sendLocationUpdateRunnable);

        // Final location ping
        if (lastKnownLocation != null) {
            addLocationPing(
                    lastKnownLocation.getLatitude(),
                    lastKnownLocation.getLongitude()
            );
        }

        Date endTime = new Date();

        // Update the session document in Firestore with end time and final status
        if (currentSessionId != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("endTime", endTime);
            updates.put("status", arrived ? "completed" : "stopped");

            firestoreDb.collection("sessions")
                    .document(currentSessionId)
                    .update(updates);
        }

        // Update the session in local Room database
        if (localSessionId != -1) {
            new Thread(() -> {
                WalkSession session = db.walkSessionDao().getSessionById(localSessionId);
                if (session != null) {
                    session.setEndTime(endTime);
                    session.setStatus(arrived ? "completed" : "stopped");
                    db.walkSessionDao().update(session);
                }
            }).start();
        }

        // Notify all listeners that the session ended
        for (SessionListener listener : listeners) {
            listener.onSessionEnded(arrived);
        }

        // Stop the service
        stopForeground(true);
        stopSelf();
    }

    private void handleLocationUpdate(Location location) {
        if (!walkSessionActive || location == null || destination == null) return;
        lastKnownLocation = location;

        // Initialize start location if not yet
        if (startLocation == null) {
            startLocation = lastKnownLocation;
        }

        if (!startLocationUpdated) {
            updateSessionStartLocation();
        }

        // Keep arrival detection based on direct radius
        float crowFlyDistance = LocationUtility.distanceInMeters(
                location.getLatitude(),
                location.getLongitude(),
                destination.getLatitude(),
                destination.getLongitude()
        );

        // Notify all listeners about the new location and distance
        for (SessionListener listener : listeners) {
            listener.onLocationUpdated(location);
            listener.onDistanceUpdated(crowFlyDistance);
        }

        // Arrival detection
        if (!arrivalAlreadyHandled &&
                LocationUtility.hasArrived(location, destination, ARRIVAL_THRESHOLD_METERS)) {
            arrivalAlreadyHandled = true;
            onArrivalDetected();
        }
    }

    private void updateSessionStartLocation() {
        if (currentSessionId == null || localSessionId == -1 || startLocation == null) return;
        startLocationUpdated = true;

        double startLat = startLocation.getLatitude();
        double startLng = startLocation.getLongitude();

        // Firestore update
        Map<String, Object> updates = new HashMap<>();
        updates.put("startLat", startLat);
        updates.put("startLng", startLng);
        firestoreDb.collection("sessions")
                .document(currentSessionId)
                .update(updates)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update start location remotely", Toast.LENGTH_SHORT).show());

        // Local Room update
        new Thread(() -> {
            WalkSession session = db.walkSessionDao().getSessionById(localSessionId);
            if (session != null) {
                session.setStartLat(startLat);
                session.setStartLng(startLng);
                db.walkSessionDao().update(session);
            }
        }).start();
    }

    private void addLocationPing(double lat, double lng) {
        // Don't ping if there's no active session in Firestore yet
        if (currentSessionId == null) return;

        Map<String, Object> ping = new HashMap<>();
        ping.put("lat", lat);
        ping.put("lng", lng);
        ping.put("timestamp", new Date());

        // Pings are stored as a sub-collection under the session document
        firestoreDb.collection("sessions")
                .document(currentSessionId)
                .collection("pings")
                .add(ping);
    }

    private void onArrivalDetected() {
        Toast.makeText(this, "Safe arrival detected!", Toast.LENGTH_LONG).show();
        stopWalkSession(true);
    }

    /*
     * Send a notification to all trusted contacts
     */
    private void sendSMSMessage(String message) {
        for (String phoneNumber : contactPhones) {
            // TODO: Send SMS message to the phoneNumber
        }
    }
}
