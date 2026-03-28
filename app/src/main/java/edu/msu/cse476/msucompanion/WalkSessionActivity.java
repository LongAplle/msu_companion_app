package edu.msu.cse476.msucompanion;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * This activity manages the user's walking session. It tracks the user's
 * GPS location in real time and compares it with the selected destination.
 */
public class WalkSessionActivity extends AppCompatActivity {

    // UI components used to display information to the user
    private TextView tvCurrentLocation;
    private TextView tvDistance;
    private TextView tvStatus;
    private Button btnToggleWalk;

    // Helper class used to manage GPS/location services
    private LocationHelper locationHelper;

    // Destination selected by the user
    private Destination destination;


    // Distance threshold used to determine arrival (in meters)
    private static final float ARRIVAL_THRESHOLD_METERS = 200.0f;

    // Session states
    private boolean walkSessionActive = false;
    private boolean arrivalAlreadyHandled = false;

    // Remote user ID
    private String currUserId;

    // Firestore session document ID — used to update the session on end
    private String currentSessionId;


    // Firestore instance for saving session and ping data
    private FirebaseFirestore db;

    // Handler for periodic location pings to Firestore
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable sendLocationUpdateRunnable;
    private Location lastKnownLocation;

    // All trusted contact phone numbers (to be fetched at start)
    private List<String> contactPhones = new ArrayList<>();

    // Session start location
    private Location startLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walk_session);

        // Get current user ID from SharedPreferences
        // Note: userId is a String (Firestore auto-generated ID)
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        currUserId = prefs.getString("userId", null);
        if (currUserId == null) {
            finish();
            return;
        }

        // Initialize Firestore instance
        db = FirebaseFirestore.getInstance();

        // Initialize UI components
        TextView tvDestination = findViewById(R.id.tvDestination);
        btnToggleWalk = findViewById(R.id.btnToggleWalk);
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        tvDistance = findViewById(R.id.tvDistance);
        tvStatus = findViewById(R.id.tvStatus);

        // Initialize GPS helper
        locationHelper = new LocationHelper(this);

        String destinationName = getIntent().getStringExtra("destination_name");
        double destinationLat = getIntent().getDoubleExtra("destination_lat", 0.0);
        double destinationLng = getIntent().getDoubleExtra("destination_lng", 0.0);

        /*
         * Temporary fallback in case the destination name was not passed.
         * This prevents the app from crashing during testing.
         */
        if (destinationName == null) {
            destinationName = "Test Destination";
        }

        // Create a Destination object using the provided data
        destination = new Destination(destinationName, destinationLat, destinationLng);

        // Display the selected destination on screen
        tvDestination.setText(getString(R.string.tvDestinationText, destination.getName()));
        tvCurrentLocation.setText(getString(R.string.tvCurrentLocationText, "None"));
        tvDistance.setText(getString(R.string.tvDistanceText, Float.NaN));
        tvStatus.setText(getString(R.string.tvStatusText, "Waiting"));
    }

    /*
     * Starts tracking the user's location in real time.
     * If location permission is not granted, it requests permission first.
     */
    private void startWalkSession() {
        // Guard against starting an already active session
        if (walkSessionActive) {
            return;
        }

        // Check if the app has location permission
        if (!locationHelper.hasLocationPermission()) {
            locationHelper.requestLocationPermission(this);
            return;
        }

        // Fetch all trusted contacts from local database
        new Thread(() -> {
            List<String> phones = AppDatabase.getInstance(this)
                    .contactDao()
                    .getAllPhoneNumber(currUserId);
            runOnUiThread(() -> {
                contactPhones = phones;

                // Send initial SMS
                sendNotification("I'm starting a walk to " + destination.getName() + ".");
            });
        }).start();

        arrivalAlreadyHandled = false;
        walkSessionActive = true;
        Date sessionStartTime = new Date();
        tvStatus.setText(getString(R.string.tvStatusText, "Walk session active"));

        // Create a new session document in Firestore when the walk starts
        Map<String, Object> session = new HashMap<>();
        session.put("userId", currUserId);
        session.put("destinationName", destination.getName());
        session.put("destinationLat", destination.getLatitude());
        session.put("destinationLng", destination.getLongitude());
        session.put("startTime", sessionStartTime);
        session.put("endTime", null);
        session.put("status", "active");

        db.collection("sessions")
                .add(session)
                .addOnSuccessListener(documentReference -> {
                    // Save the session ID so we can update it when the walk ends
                    currentSessionId = documentReference.getId();

                    // Update start location if it is available
                    if (startLocation != null) {
                        updateSessionStartLocation();
                    }
                })
                .addOnFailureListener(e ->
                    Toast.makeText(this, "Failed to start session: " + e.getMessage(), Toast.LENGTH_SHORT).show());

        // Start GPS updates
        locationHelper.startLocationUpdates(new LocationHelper.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(Location location) {
                handleLocationUpdate(location);
            }

            @Override
            public void onLocationError(String message) {
                Toast.makeText(WalkSessionActivity.this, message, Toast.LENGTH_SHORT).show();
                tvStatus.setText(getString(R.string.tvStatusText, "Error - " + message));
            }
        });

        // Ping location to Firestore every 5 minutes so contacts can track progress
        sendLocationUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (walkSessionActive && lastKnownLocation != null) {
                    double lat = lastKnownLocation.getLatitude();
                    double lng = lastKnownLocation.getLongitude();

                    // Send SMS notification with maps link
                    String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;
                    sendNotification("My current location: " + mapsLink);

                    // Also ping location to Firestore under the current session
                    addLocationPing(lat, lng);
                }
                if (walkSessionActive) {
                    handler.postDelayed(this, 5 * 60 * 1000); // 5 minutes
                }
            }
        };
        handler.post(sendLocationUpdateRunnable);
    }

    /*
     * Writes a location ping to Firestore as a sub-collection under the session.
     * Called every 5 minutes and on session end.
     */
    private void addLocationPing(double lat, double lng) {
        // Don't ping if there's no active session in Firestore yet
        if (currentSessionId == null) return;

        Map<String, Object> ping = new HashMap<>();
        ping.put("lat", lat);
        ping.put("lng", lng);
        ping.put("timestamp", new Date());

        // Pings are stored as a sub-collection under the session document
        db.collection("sessions")
                .document(currentSessionId)
                .collection("pings")
                .add(ping);
    }

    /*
     * Stops tracking the user's location and ends the walk session.
     */
    private void stopWalkSession(boolean arrived) {
        if (!walkSessionActive) {
            return;
        }

        walkSessionActive = false;
        locationHelper.stopLocationUpdates();
        tvStatus.setText(getString(R.string.tvStatusText, "Walk session stopped"));
        handler.removeCallbacks(sendLocationUpdateRunnable);

        // Write a final location ping before closing the session
        if (lastKnownLocation != null) {
            addLocationPing(
                    lastKnownLocation.getLatitude(),
                    lastKnownLocation.getLongitude()
            );
        }

        // TODO: Add walk session to local database (userId, startTime, endTime, startLat, startLng, destinationName, destinationLat, destinationLng, status)

        // Update the session document in Firestore with end time and final status
        if (currentSessionId != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("endTime", new Date());
            updates.put("status", arrived ? "completed" : "stopped");

            db.collection("sessions")
                    .document(currentSessionId)
                    .update(updates);
        }

        if (arrived) {
            sendNotification("I have arrived at " + destination.getName() + ".");
        } else if (lastKnownLocation != null) {
            double lat = lastKnownLocation.getLatitude();
            double lng = lastKnownLocation.getLongitude();
            String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;
            sendNotification("I stopped the walk. My final location: " + mapsLink);
        }

        // Close the activity so a new session must be started from the destination picker
        finish();
    }

    /*
     * Called every time the user's GPS location changes.
     * Updates the UI and checks whether the destination has been reached.
     */
    private void handleLocationUpdate(Location location) {
        if (!walkSessionActive || location == null || destination == null) {
            return;
        }
        lastKnownLocation = location;

        // Initialize start location if not yet
        if (startLocation == null) {
            startLocation = lastKnownLocation;

            if (currentSessionId != null) {
                updateSessionStartLocation();
            }
        }

        // Retrieve the user's current coordinates
        double lat = location.getLatitude();
        double lng = location.getLongitude();

        // Display current GPS location on screen
        tvCurrentLocation.setText(getString(R.string.tvCurrentLocationText, lat + ", " + lng));

        float distance = LocationUtility.distanceInMeters(
                lat,
                lng,
                destination.getLatitude(),
                destination.getLongitude()
        );
        tvDistance.setText(getString(R.string.tvDistanceText, distance));

        // Arrival detection
        if (!arrivalAlreadyHandled &&
                LocationUtility.hasArrived(location, destination, ARRIVAL_THRESHOLD_METERS)) {
            arrivalAlreadyHandled = true;
            onArrivalDetected();
        }
    }

    private void updateSessionStartLocation(){
        if (currentSessionId == null || startLocation == null) return;

        double startLat = startLocation.getLatitude();
        double startLng = startLocation.getLongitude();

        Map<String, Object> updates = new HashMap<>();
        updates.put("startLat", startLat);
        updates.put("startLng", startLng);
        db.collection("sessions")
                .document(currentSessionId)
                .update(updates)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update start location", Toast.LENGTH_SHORT).show());
    }

    /*
     * Called once the user is within the arrival threshold distance.
     * Triggers safe arrival notification.
     */
    private void onArrivalDetected() {
        tvStatus.setText(getString(R.string.tvStatusText, "Arrived safely"));
        Toast.makeText(this, "Safe arrival detected!", Toast.LENGTH_LONG).show();
        stopWalkSession(true);
    }

    /*
     * Send a notification to all trusted contacts
     */
    private void sendNotification(String message) {
        for (String phoneNumber : contactPhones) {
            // TODO: Send SMS message to the phoneNumber
        }
    }

    /*
     * Toggle the start/stop button
     */
    public void onToggleStartStop(View view) {
        if (!walkSessionActive) {
            startWalkSession();
            btnToggleWalk.setText(getString(R.string.endWalkText));
        } else {
            stopWalkSession(false);
        }
    }

    /*
     * Ensures location tracking stops when the activity is destroyed.
     * This prevents unnecessary GPS usage and battery drain.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // TODO: implement a foreground service so the walk can survive app closure and system kills

        stopWalkSession(false);
    }

    /*
     * Handles the result of the location permission request.
     * If permission is granted, the walk session begins automatically.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LocationHelper.LOCATION_PERMISSION_REQUEST_CODE) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }

            if (granted) {
                startWalkSession();
            } else {
                Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show();
                tvStatus.setText(getString(R.string.tvStatusText, "Permission denied"));
            }
        }
    }
}