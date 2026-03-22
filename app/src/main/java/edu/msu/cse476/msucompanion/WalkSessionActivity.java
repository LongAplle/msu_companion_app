package edu.msu.cse476.msucompanion;

import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/*
 * WalkSessionActivity
 *
 * This activity manages the user's walking session. It tracks the user's
 * GPS location in real time and compares it with the selected destination.
 *
 * Responsibilities of this class:
 * 1. Start and stop walk sessions
 * 2. Continuously track the user's GPS location
 * 3. Display current location and distance to destination
 * 4. Detect when the user reaches the destination
 * 5. Trigger a safe-arrival event
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
    private static final float ARRIVAL_THRESHOLD_METERS = 50.0f;

    // Tracks whether a walk session is currently active
    private boolean walkSessionActive = false;

    // Prevents multiple arrival triggers once destination is reached
    private boolean arrivalAlreadyHandled = false;


    // Handler for periodic SMS updates
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable sendLocationUpdateRunnable;
    private Location lastKnownLocation;


    // Store contact phone numbers (to be fetched at start)
    private List<String> contactPhones = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walk_session);

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

        btnToggleWalk.setOnClickListener(v -> toggleStartStop());
    }

    /*
     * Starts tracking the user's location in real time.
     * If location permission is not granted, it requests permission first.
     */
    private void startWalkSession() {
        // Check if the app has location permission
        if (!locationHelper.hasLocationPermission()) {
            locationHelper.requestLocationPermission(this);
            return;
        }

        // TODO: Fetch trusted contacts from Room

        sendNotification("I'm starting a walk to " + destination.getName() + ".");

        arrivalAlreadyHandled = false;
        walkSessionActive = true;
        tvStatus.setText(getString(R.string.tvStatusText, "Walk session active"));

        // Start GPS updates
        locationHelper.startLocationUpdates(new LocationHelper.LocationUpdateListener() {

            @Override
            public void onLocationUpdated(Location location) {
                handleLocationUpdate(location);
            }

            @Override
            public void onLocationError(String message) {
                Toast.makeText(WalkSessionActivity.this, message, Toast.LENGTH_SHORT).show();
                tvStatus.setText(getString(R.string.tvStatusText,"Error - " + message));
            }
        });

        // Start periodic location SMS every (5) minutes
        sendLocationUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (walkSessionActive && lastKnownLocation != null) {
                    double lat = lastKnownLocation.getLatitude();
                    double lng = lastKnownLocation.getLongitude();
                    String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;
                    sendNotification("My current location: " + mapsLink);
                }
                if (walkSessionActive) {
                    handler.postDelayed(this, 5 * 60 * 1000); // 5 minutes
                }
            }
        };
        handler.post(sendLocationUpdateRunnable);
    }

    /*
     * Stops tracking the user's location and ends the walk session.
     */
    private void stopWalkSession(boolean arrived) {
        walkSessionActive = false;
        locationHelper.stopLocationUpdates();
        tvStatus.setText(getString(R.string.tvStatusText,"Walk session stopped"));
        handler.removeCallbacks(sendLocationUpdateRunnable);

        // TODO: Save walk session summary to local and remote database (startTime, endTime, destination, status)

        if (arrived) {
            sendNotification("I have arrived at " + destination.getName() + ".");
        }
        else {
            double lat = lastKnownLocation.getLatitude();
            double lng = lastKnownLocation.getLongitude();
            String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;
            sendNotification("I stopped the walk. My final destination: " + mapsLink);
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
        tvDistance.setText(getString(R.string.tvDistanceText,distance));

        // Arrival detection
        if (!arrivalAlreadyHandled &&
                LocationUtility.hasArrived(location, destination, ARRIVAL_THRESHOLD_METERS)) {

            arrivalAlreadyHandled = true;
            onArrivalDetected();
        }
    }

    /*
     * Called once the user is within the arrival threshold distance.
     * Triggers safe arrival notification.
     */
    private void onArrivalDetected() {
        tvStatus.setText(getString(R.string.tvStatusText,"Arrived safely"));

        Toast.makeText(this, "Safe arrival detected!", Toast.LENGTH_LONG).show();
        stopWalkSession(true);
    }

    /*
     * Send a notification to all trusted contacts
     */
    private void sendNotification(String message) {
        // TODO: Send SMS message to all trusted contacts
    }

    /*
     * Toggle the start/stop button
     */
    private void toggleStartStop(){
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

        if (locationHelper != null) {
            locationHelper.stopLocationUpdates();
        }
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
                tvStatus.setText(getString(R.string.tvStatusText,"Permission denied"));
            }
        }
    }
}