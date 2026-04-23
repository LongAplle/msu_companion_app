package edu.msu.cse476.msucompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
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

    // Permission request code for SMS (will be handled in activity)
    public static final int SMS_PERMISSION_REQUEST_CODE = 2001;

    // Action constants for communication with the service
    public static final String ACTION_START_SESSION = "START_SESSION";
    public static final String ACTION_STOP_SESSION = "STOP_SESSION";

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

    // Contact selection
    private boolean useAllContacts = true;
    private long[] selectedContactIds = new long[0];

    // Helper class used to manage GPS/location services
    private LocationHelper locationHelper;

    // // Handler for main thread, used for updating UI and periodic location pings
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable sendLocationUpdateRunnable;
    private Location lastKnownLocation;

    // Distance threshold used to determine arrival (in meters)
    private static final float ARRIVAL_THRESHOLD_METERS = 50.0f;

    // SMS / Firestore default ping interval (in minutes)
    public static final int NOTIFY_INTERVAL_MINUTES_DEFAULT = 5;
    private long notifyIntervalMs = (long) NOTIFY_INTERVAL_MINUTES_DEFAULT * 60 * 1000;

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
                case ACTION_START_SESSION:
                    // Extract destination and other data from intent
                    String destName = intent.getStringExtra(Keys.EXTRA_DESTINATION_NAME);
                    double destLat = intent.getDoubleExtra(Keys.EXTRA_DESTINATION_LAT, 0.0);
                    double destLng = intent.getDoubleExtra(Keys.EXTRA_DESTINATION_LNG, 0.0);
                    destination = new Destination(destName, destLat, destLng);

                    useAllContacts = intent.getBooleanExtra(Keys.EXTRA_USE_ALL_CONTACTS, true);
                    selectedContactIds = intent.getLongArrayExtra(Keys.EXTRA_SELECTED_CONTACT_IDS);
                    if (selectedContactIds == null) {
                        selectedContactIds = new long[0];
                    }

                    notifyIntervalMs = (long) intent.getIntExtra(Keys.EXTRA_NOTIFY_INTERVAL_MINUTES, NOTIFY_INTERVAL_MINUTES_DEFAULT) * 60 * 1000;

                    // Get current user ID
                    SharedPreferences prefs = getSharedPreferences(Keys.PREF_USER, Context.MODE_PRIVATE);
                    currUserId = prefs.getString(Keys.PREF_USER_ID, null);
                    if (currUserId != null) {
                        startWalkSession();
                    }
                    break;

                case ACTION_STOP_SESSION:
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
        Intent notifIntent = new Intent(this, WalkSessionActivity.class);
        notifIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        notifIntent.putExtra(Keys.EXTRA_DESTINATION_NAME, destination != null ? destination.getName() : "");
        notifIntent.putExtra(Keys.EXTRA_DESTINATION_LAT, destination != null ? destination.getLatitude() : 0.0);
        notifIntent.putExtra(Keys.EXTRA_DESTINATION_LNG, destination != null ? destination.getLongitude() : 0.0);
        notifIntent.putExtra(Keys.EXTRA_START_NEW_SESSION, false);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notifIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MSU Companion")
                .setContentText("Walking session active")
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
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
        if (walkSessionActive) return;

        // Check if the app has location permission
        if (!locationHelper.hasLocationPermission()) {
            return;  // The activity will request permission and restart the service
        }

        addFirestore(new Date());

        // Send initial SMS
        sendSMSMessageToChosenContacts("I'm starting a walk to " + destination.getName() + ".");
    }

    private void stopWalkSession(boolean arrived) {
        if (!walkSessionActive) return;

        // Notify contacts
        if (arrived) {
            sendSMSMessageToChosenContacts("I have arrived at " + destination.getName() + ".");
        } else if (lastKnownLocation != null) {
            double lat = lastKnownLocation.getLatitude();
            double lng = lastKnownLocation.getLongitude();
            String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;
            sendSMSMessageToChosenContacts("I stopped the walk. My final location: " + mapsLink);
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

        updateFirestoreFinal(endTime, arrived);
        updateRoomFinal(endTime, arrived);
        ActiveSessionRepository.clearActiveSession();

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

        updateFirestoreStartLocation();
        updateRoomStartLocation();
    }

    private void addLocationPing(double lat, double lng) {
        if (currentSessionId == null) return;

        Map<String, Object> ping = new HashMap<>();
        ping.put("lat", lat);
        ping.put("lng", lng);
        ping.put("timestamp", new Date());

        // Pings are stored as a sub-collection under the session document
        firestoreDb.collection(Keys.COLLECTION_SESSIONS)
                .document(currentSessionId)
                .collection(Keys.COLLECTION_PINGS)
                .add(ping);
    }

    private void onArrivalDetected() {
        Toast.makeText(this, "Safe arrival detected!", Toast.LENGTH_SHORT).show();
        stopWalkSession(true);
    }

    /**
     * Send a notification to all chosen contacts
     */
    private void sendSMSMessageToChosenContacts(String message) {
        new Thread(() -> {
            try {
                List<String> contactPhones;

                if (useAllContacts) {
                    contactPhones = db.contactDao().getAllPhoneNumber(currUserId);
                } else {
                    if (selectedContactIds == null || selectedContactIds.length == 0) return;
                    contactPhones = db.contactDao().getPhoneNumbersForSelectedContacts(currUserId, selectedContactIds);
                }

                for (String phoneNumber : contactPhones) {
                    sendSMSMessage(phoneNumber, message);
                }

            } catch (Exception e) {
                handler.post(() ->
                        Toast.makeText(this, "Failed to load contacts: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    // Async helpers
    /**
     * Create a new session document in Firestore
     */
    private void addFirestore(Date sessionStartTime) {
        Map<String, Object> session = new HashMap<>();
        session.put(Keys.FIELD_USER_ID, currUserId);
        session.put(Keys.FIELD_SESSION_DESTINATION_NAME, destination.getName());
        session.put(Keys.FIELD_SESSION_DESTINATION_LAT, destination.getLatitude());
        session.put(Keys.FIELD_SESSION_DESTINATION_LNG, destination.getLongitude());
        session.put(Keys.FIELD_SESSION_START_TIME, sessionStartTime);
        session.put(Keys.FIELD_SESSION_END_TIME, null);
        session.put(Keys.FIELD_SESSION_STATUS, "active");

        firestoreDb.collection(Keys.COLLECTION_SESSIONS)
                .add(session)
                .addOnSuccessListener(documentReference -> {
                    // Save the session ID so we can update it when the walk ends
                    currentSessionId = documentReference.getId();
                    addRoomInitial(sessionStartTime);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to start session: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    /**
     * Update the start location in Firestore
     */
    private void updateFirestoreStartLocation() {
        if (currentSessionId == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put(Keys.FIELD_SESSION_START_LAT, startLocation.getLatitude());
        updates.put(Keys.FIELD_SESSION_START_LNG, startLocation.getLongitude());
        firestoreDb.collection(Keys.COLLECTION_SESSIONS)
                .document(currentSessionId)
                .update(updates)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update start location remotely: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    /**
     * Update the session document in Firestore with end time and final status
     */
    private void updateFirestoreFinal(Date endTime, boolean arrived) {
        if (currentSessionId == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put(Keys.FIELD_SESSION_END_TIME, endTime);
        updates.put(Keys.FIELD_SESSION_STATUS, arrived ? "completed" : "stopped");

        firestoreDb.collection(Keys.COLLECTION_SESSIONS)
                .document(currentSessionId)
                .update(updates);
    }

    /**
     * Add initial data of the session to the local Room database
     */
    private void addRoomInitial(Date sessionStartTime) {
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

            startGPSUpdate();
        }).start();
    }

    /**
     * Update the start location in Room database
     */
    private void updateRoomStartLocation() {
        if (localSessionId == -1) return;

        new Thread(() -> {
            WalkSession session = db.walkSessionDao().getSessionById(localSessionId);
            if (session != null) {
                session.setStartLat(startLocation.getLatitude());
                session.setStartLng(startLocation.getLongitude());
                db.walkSessionDao().update(session);
            }
        }).start();
    }

    /**
     * Update the session document in Room database with end time and final status
     */
    private void updateRoomFinal(Date endTime, boolean arrived) {
        if (localSessionId == -1) return;

        new Thread(() -> {
            WalkSession session = db.walkSessionDao().getSessionById(localSessionId);
            if (session != null) {
                session.setEndTime(endTime);
                session.setStatus(arrived ? "completed" : "stopped");
                db.walkSessionDao().update(session);
            }
        }).start();

    }

    /**
     * Start GPS updates and notify all bound activities
     */
    private void startGPSUpdate() {
        handler.post(() -> {
            // Start GPS updates
            walkSessionActive = true;
            locationHelper.startLocationUpdates(WalkSessionService.this);

            startPing();

            // Live data for the active session
            ActiveSessionRepository.setActiveSession(destination);

            // Notify all listeners that the session has started
            for (SessionListener listener : listeners) {
                listener.onSessionStarted();
            }

            Toast.makeText(this, "Walk session started", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Start periodic location pings to Firestore
     */
    private void startPing() {
        sendLocationUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (walkSessionActive && lastKnownLocation != null) {
                    double lat = lastKnownLocation.getLatitude();
                    double lng = lastKnownLocation.getLongitude();

                    // Send SMS notification with maps link
                    String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;
                    sendSMSMessageToChosenContacts("My current location: " + mapsLink);

                    // Also ping location to Firestore under the current session
                    addLocationPing(lat, lng);
                }
                if (walkSessionActive) {
                    handler.postDelayed(this, notifyIntervalMs); // 5 minutes
                }
            }
        };
        handler.post(sendLocationUpdateRunnable);
    }

    private void sendSMSMessage(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
        }
        catch (Exception e) {
            handler.post(() ->
                    Toast.makeText(this, "Failed to send SMS: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
        }
    }
}
