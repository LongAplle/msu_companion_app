package edu.msu.cse476.msucompanion;

import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class WalkSessionActivity extends AppCompatActivity {

    private TextView tvDestination;
    private TextView tvCurrentLocation;
    private TextView tvDistance;
    private TextView tvStatus;
    private Button btnStartWalk;
    private Button btnStopWalk;

    private LocationHelper locationHelper;
    private Destination destination;

    private String typedDestination;
    private String buddyName;
    private String buddyPhone;

    private static final float ARRIVAL_THRESHOLD_METERS = 200.0f;    //within 0.1 mile

    private boolean walkSessionActive = false;
    private boolean arrivalAlreadyHandled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walk_session);

        tvDestination = findViewById(R.id.tvDestination);
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        tvDistance = findViewById(R.id.tvDistance);
        tvStatus = findViewById(R.id.tvStatus);
        btnStartWalk = findViewById(R.id.btnStartWalk);
        btnStopWalk = findViewById(R.id.btnStopWalk);

        locationHelper = new LocationHelper(this);

        // Destination selected from DestinationPickerActivity
        String destinationName = getIntent().getStringExtra("destination_name");
        double destinationLat = getIntent().getDoubleExtra("destination_lat", 0.0);
        double destinationLng = getIntent().getDoubleExtra("destination_lng", 0.0);

        // Session/contact data from SessionActivity
        typedDestination = getIntent().getStringExtra("typed_destination");
        buddyName = getIntent().getStringExtra("buddyName");
        buddyPhone = getIntent().getStringExtra("buddyPhone");

        if (destinationName == null) {
            destinationName = "Test Destination";
        }

        destination = new Destination(destinationName, destinationLat, destinationLng);

        tvDestination.setText("Destination: " + destination.getName());
        tvStatus.setText("Status: Waiting");

        btnStartWalk.setOnClickListener(v -> startWalkSession());
        btnStopWalk.setOnClickListener(v -> stopWalkSession());
    }

    private void startWalkSession() {
        if (!locationHelper.hasLocationPermission()) {
            locationHelper.requestLocationPermission(this);
            return;
        }

        arrivalAlreadyHandled = false;
        walkSessionActive = true;
        tvStatus.setText("Status: Walk session active");

        locationHelper.startLocationUpdates(new LocationHelper.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(Location location) {
                handleLocationUpdate(location);
            }

            @Override
            public void onLocationError(String message) {
                Toast.makeText(WalkSessionActivity.this, message, Toast.LENGTH_SHORT).show();
                tvStatus.setText("Status: Error - " + message);
            }
        });
    }

    private void stopWalkSession() {
        walkSessionActive = false;
        locationHelper.stopLocationUpdates();
        tvStatus.setText("Status: Walk session stopped");
    }

    private void handleLocationUpdate(Location location) {
        if (!walkSessionActive || location == null || destination == null) {
            return;
        }

        double lat = location.getLatitude();
        double lng = location.getLongitude();

        tvCurrentLocation.setText("Current Location: " + lat + ", " + lng);

        float distance = LocationUtility.distanceInMeters(
                lat,
                lng,
                destination.getLatitude(),
                destination.getLongitude()
        );

        tvDistance.setText("Distance to destination: " + String.format("%.2f", distance) + " meters");

        sendLocationUpdateToServerOrContact(lat, lng, distance);

        if (!arrivalAlreadyHandled &&
                LocationUtility.hasArrived(location, destination, ARRIVAL_THRESHOLD_METERS)) {
            arrivalAlreadyHandled = true;
            onArrivalDetected();
        }
    }

    private void onArrivalDetected() {
        tvStatus.setText("Status: Arrived safely");
        Toast.makeText(this, "Safe arrival detected!", Toast.LENGTH_LONG).show();

        sendArrivalNotification();

        stopWalkSession();
    }

    private void sendLocationUpdateToServerOrContact(double lat, double lng, float distance) {
        // Placeholder for future backend/contact integration
        // Example:
        // 1. Send live location updates to backend
        // 2. Notify trusted buddy of progress
        // 3. Save walk history
    }

    private void sendArrivalNotification() {
        String message;

        if (buddyName != null && !buddyName.isEmpty()) {
            message = "Arrival notification would be sent to " + buddyName;
        } else {
            message = "Arrival notification placeholder";
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationHelper != null) {
            locationHelper.stopLocationUpdates();
        }
    }

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
                tvStatus.setText("Status: Permission denied");
            }
        }
    }
}