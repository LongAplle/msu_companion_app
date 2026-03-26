package edu.msu.cse476.msucompanion;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

/*
 * LocationHelper
 *
 * This class manages all GPS/location functionality for the app.
 * It handles:
 *  - Checking location permissions
 *  - Requesting location permissions
 *  - Retrieving the user's current location
 *  - Starting and stopping continuous location tracking
 *
 * It uses Google's FusedLocationProviderClient, which combines GPS,
 * Wi-Fi, and cellular signals to provide accurate and efficient location updates.
 */
public class LocationHelper {

    /*
     * Interface used to send location updates back to the activity.
     * Activities implementing this listener receive:
     *  - new location updates
     *  - error messages if something goes wrong
     */
    public interface LocationUpdateListener {
        void onLocationUpdated(Location location);
        void onLocationError(String message);
    }

    // Request code used when asking the user for location permission
    public static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // Application context
    private final Context context;

    // Google Play Services client used for retrieving device location
    private final FusedLocationProviderClient fusedLocationClient;

    // Callback used to receive location updates
    private LocationCallback locationCallback;

    // Tracks whether location updates are currently active
    private boolean isTracking = false;

    /*
     * Constructor
     *
     * Initializes the location helper and prepares the fused location provider.
     */
    public LocationHelper(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    /*
     * Checks whether the app currently has location permission.
     * The app can operate with either fine location (GPS) or coarse location.
     */
    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /*
     * Requests location permission from the user.
     * This will trigger the Android permission dialog.
     */
    public void requestLocationPermission(Activity activity) {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    /*
     * Retrieves the device's last known location.
     * This is useful for quickly getting an approximate position
     * before continuous updates begin.
     */
    public void getLastKnownLocation(LocationUpdateListener listener) {

        // If permission is not granted, notify the listener
        if (!hasLocationPermission()) {
            listener.onLocationError("Location permission not granted.");
            return;
        }

        try {
            // Request the last known location from the fused provider
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {

                        // If location exists, return it to the listener
                        if (location != null) {
                            listener.onLocationUpdated(location);
                        } else {
                            listener.onLocationError("Could not get last known location.");
                        }
                    })
                    .addOnFailureListener(e ->
                            listener.onLocationError("Error getting location: " + e.getMessage()));
        } catch (SecurityException e) {
            listener.onLocationError("Location permission was revoked.");
        }


    }

    /*
     * Starts continuous location tracking.
     * The app will receive periodic location updates while tracking is active.
     */
    public void startLocationUpdates(LocationUpdateListener listener) {

        // Ensure location permission exists before starting tracking
        if (!hasLocationPermission()) {
            listener.onLocationError("Location permission not granted.");
            return;
        }

        // Prevent starting multiple tracking sessions
        if (isTracking) {
            return;
        }

        /*
         * Create a location request configuration.
         * - High accuracy mode uses GPS when available
         * - Updates requested every ~5 seconds
         * - Minimum update interval set to ~3 seconds
         */
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000
        )
                .setMinUpdateIntervalMillis(3000)
                .setWaitForAccurateLocation(true)
                .build();

        /*
         * LocationCallback receives location updates from the system.
         * Each time the device location changes, this callback is triggered.
         */
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {

                // Get the most recent location from the result
                Location location = locationResult.getLastLocation();

                if (location != null) {
                    listener.onLocationUpdated(location);
                }
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
            );
            isTracking = true;
        } catch (SecurityException e) {
            listener.onLocationError("Location permission was revoked.");
        }
    }

    /*
     * Stops continuous location tracking.
     * Called when the walk session ends or the activity closes.
     */
    public void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            isTracking = false;
        }
    }

    /*
     * Returns whether location tracking is currently active.
     */
    public boolean isTracking() {
        return isTracking;
    }
}