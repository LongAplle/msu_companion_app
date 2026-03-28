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

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * This activity manages the user's walking session. It tracks the user's
 * GPS location in real time and compares it with the selected destination.
 */
public class WalkSessionActivity extends AppCompatActivity implements OnMapReadyCallback {

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

    // Local Room database session ID
    private long localSessionId = -1;

    // Firestore session document ID
    private String currentSessionId;

    // App local database
    private AppDatabase db;

    // Firestore instance for saving session and ping data
    private FirebaseFirestore firestoreDb;

    // Handler for periodic location pings to Firestore
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable sendLocationUpdateRunnable;
    private Location lastKnownLocation;

    // All trusted contact phone numbers (to be fetched at start)
    private List<String> contactPhones = new ArrayList<>();

    // Session start location
    private Location startLocation;

    // Flag for updating the start location
    private boolean startLocationUpdated = false;

    // Google Maps state
    private GoogleMap googleMap;
    private Marker currentLocationMarker;
    private Marker destinationMarker;
    private Polyline routeLine;
    private boolean initialCameraFramed = false;

    // Walking-route refresh control
    private boolean routeRequestInFlight = false;
    private long lastRouteFetchTimeMs = 0L;
    private LatLng lastRouteOrigin = null;

    private static final long ROUTE_REFRESH_INTERVAL_MS = 15000L;   // 15 sec
    private static final float ROUTE_REFRESH_MIN_MOVE_METERS = 25f; // refetch after meaningful movement

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

        // Initialize database
        db = AppDatabase.getInstance(this);
        firestoreDb = FirebaseFirestore.getInstance();

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

        // Create and attach the map fragment
        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.mapContainer, mapFragment)
                .commit();

        mapFragment.getMapAsync(this);
    }

    /*
     * Called when the Google Map is ready.
     */
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;

        LatLng destinationLatLng = new LatLng(destination.getLatitude(), destination.getLongitude());

        destinationMarker = googleMap.addMarker(
                new MarkerOptions()
                        .position(destinationLatLng)
                        .title(destination.getName())
        );

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng, 15f));

        try {
            if (locationHelper.hasLocationPermission()) {
                googleMap.setMyLocationEnabled(true);
            }
        } catch (SecurityException ignored) {
        }

        // If location already arrived before map was ready, draw immediately
        if (lastKnownLocation != null) {
            updateMapMarkers(lastKnownLocation);
            maybeFetchWalkingRoute(lastKnownLocation);
        }
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
        initialCameraFramed = false;
        Date sessionStartTime = new Date();
        tvStatus.setText(getString(R.string.tvStatusText, "Walk session active"));
        btnToggleWalk.setText(getString(R.string.endWalkText));

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

                        runOnUiThread(() ->
                                Toast.makeText(WalkSessionActivity.this, "Walk session started", Toast.LENGTH_SHORT).show()
                        );
                    }).start();
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
        firestoreDb.collection("sessions")
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

        if (arrived) {
            sendNotification("I have arrived at " + destination.getName() + ".");
        } else if (lastKnownLocation != null) {
            double lat = lastKnownLocation.getLatitude();
            double lng = lastKnownLocation.getLongitude();
            String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;
            sendNotification("I stopped the walk. My final location: " + mapsLink);
        }

        walkSessionActive = false;
        locationHelper.stopLocationUpdates();
        tvStatus.setText(getString(R.string.tvStatusText, "Walk session stopped"));
        btnToggleWalk.setText(getString(R.string.startWalkText));
        handler.removeCallbacks(sendLocationUpdateRunnable);

        // Write a final location ping before closing the session
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

                runOnUiThread(this::finish);   // finish after update
            }).start();
        } else {
            finish();
        }
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
        }

        if (!startLocationUpdated) {
            updateSessionStartLocation();
        }

        // Retrieve the user's current coordinates
        double lat = location.getLatitude();
        double lng = location.getLongitude();

        // Display current GPS location on screen
        tvCurrentLocation.setText(getString(R.string.tvCurrentLocationText, lat + ", " + lng));

        // Keep arrival detection based on direct radius
        float crowFlyDistance = LocationUtility.distanceInMeters(
                lat,
                lng,
                destination.getLatitude(),
                destination.getLongitude()
        );

        // Until the route API responds, show the direct distance temporarily
        tvDistance.setText(getString(R.string.tvDistanceText, crowFlyDistance));

        // Update map markers/camera immediately
        updateMapMarkers(location);

        // Fetch / refresh best walking route
        maybeFetchWalkingRoute(location);

        // Arrival detection
        if (!arrivalAlreadyHandled &&
                LocationUtility.hasArrived(location, destination, ARRIVAL_THRESHOLD_METERS)) {
            arrivalAlreadyHandled = true;
            onArrivalDetected();
        }
    }

    /*
     * Updates the visible map with:
     * - destination marker
     * - live user marker
     */
    private void updateMapMarkers(Location location) {
        if (googleMap == null || location == null || destination == null) {
            return;
        }

        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        LatLng destinationLatLng = new LatLng(destination.getLatitude(), destination.getLongitude());

        if (destinationMarker == null) {
            destinationMarker = googleMap.addMarker(
                    new MarkerOptions()
                            .position(destinationLatLng)
                            .title(destination.getName())
            );
        }

        if (currentLocationMarker == null) {
            currentLocationMarker = googleMap.addMarker(
                    new MarkerOptions()
                            .position(currentLatLng)
                            .title("You")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            );
        } else {
            currentLocationMarker.setPosition(currentLatLng);
        }

        if (!initialCameraFramed) {
            LatLngBounds bounds = new LatLngBounds.Builder()
                    .include(currentLatLng)
                    .include(destinationLatLng)
                    .build();

            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120));
            initialCameraFramed = true;
        } else {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f));
        }

        try {
            if (locationHelper.hasLocationPermission()) {
                googleMap.setMyLocationEnabled(true);
            }
        } catch (SecurityException ignored) {
        }
    }

    /*
     * Decide when to refresh the walking route.
     */
    private void maybeFetchWalkingRoute(Location location) {
        if (googleMap == null || location == null || destination == null || routeRequestInFlight) {
            return;
        }

        long now = System.currentTimeMillis();
        LatLng currentOrigin = new LatLng(location.getLatitude(), location.getLongitude());

        boolean enoughTimePassed = (now - lastRouteFetchTimeMs) >= ROUTE_REFRESH_INTERVAL_MS;
        boolean movedEnough = lastRouteOrigin == null ||
                distanceBetweenLatLng(lastRouteOrigin, currentOrigin) >= ROUTE_REFRESH_MIN_MOVE_METERS;

        if (!enoughTimePassed && !movedEnough) {
            return;
        }

        lastRouteFetchTimeMs = now;
        lastRouteOrigin = currentOrigin;
        fetchWalkingRouteAsync(currentOrigin,
                new LatLng(destination.getLatitude(), destination.getLongitude()));
    }

    /*
     * Fetch the best walking route from Google Routes API.
     */
    private void fetchWalkingRouteAsync(LatLng origin, LatLng destinationLatLng) {
        routeRequestInFlight = true;

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL("https://routes.googleapis.com/directions/v2:computeRoutes");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("X-Goog-Api-Key", BuildConfig.PLACES_API_KEY);
                connection.setRequestProperty(
                        "X-Goog-FieldMask",
                        "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline"
                );
                connection.setDoOutput(true);

                JSONObject requestBody = new JSONObject();

                JSONObject originObj = new JSONObject();
                JSONObject originLocation = new JSONObject();
                JSONObject originLatLng = new JSONObject();
                originLatLng.put("latitude", origin.latitude);
                originLatLng.put("longitude", origin.longitude);
                originLocation.put("latLng", originLatLng);
                originObj.put("location", originLocation);

                JSONObject destinationObj = new JSONObject();
                JSONObject destinationLocation = new JSONObject();
                JSONObject destinationLatLngObj = new JSONObject();
                destinationLatLngObj.put("latitude", destinationLatLng.latitude);
                destinationLatLngObj.put("longitude", destinationLatLng.longitude);
                destinationLocation.put("latLng", destinationLatLngObj);
                destinationObj.put("location", destinationLocation);

                requestBody.put("origin", originObj);
                requestBody.put("destination", destinationObj);
                requestBody.put("travelMode", "WALK");
                requestBody.put("languageCode", "en-US");
                requestBody.put("units", "IMPERIAL");
                requestBody.put("polylineQuality", "HIGH_QUALITY");

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(requestBody.toString().getBytes());
                }

                int responseCode = connection.getResponseCode();
                BufferedReader reader;

                if (responseCode >= 200 && responseCode < 300) {
                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                } else {
                    reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                }

                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                reader.close();

                if (responseCode < 200 || responseCode >= 300) {
                    runOnUiThread(() ->
                            Toast.makeText(
                                    WalkSessionActivity.this,
                                    "Route request failed",
                                    Toast.LENGTH_SHORT
                            ).show()
                    );
                    return;
                }

                JSONObject responseJson = new JSONObject(responseBuilder.toString());
                JSONArray routes = responseJson.optJSONArray("routes");
                if (routes == null || routes.length() == 0) {
                    return;
                }

                JSONObject firstRoute = routes.getJSONObject(0);
                int distanceMeters = firstRoute.optInt("distanceMeters", -1);

                JSONObject polylineObj = firstRoute.optJSONObject("polyline");
                if (polylineObj == null) {
                    return;
                }

                String encodedPolyline = polylineObj.optString("encodedPolyline", "");
                if (encodedPolyline.isEmpty()) {
                    return;
                }

                List<LatLng> routePoints = decodePolyline(encodedPolyline);

                runOnUiThread(() -> {
                    drawWalkingRoute(routePoints);

                    if (distanceMeters >= 0) {
                        tvDistance.setText(getString(R.string.tvDistanceText, (float) distanceMeters));
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(
                                WalkSessionActivity.this,
                                "Unable to fetch walking route",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                routeRequestInFlight = false;
            }
        }).start();
    }

    /*
     * Draw the walking route polyline on the map.
     */
    private void drawWalkingRoute(List<LatLng> routePoints) {
        if (googleMap == null || routePoints == null || routePoints.isEmpty()) {
            return;
        }

        if (routeLine != null) {
            routeLine.remove();
        }

        routeLine = googleMap.addPolyline(
                new PolylineOptions()
                        .addAll(routePoints)
                        .width(10f)
                        .geodesic(true)
        );
    }

    /*
     * Decode an encoded polyline into LatLng points.
     */
    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0;
        int len = encoded.length();
        int lat = 0;
        int lng = 0;

        while (index < len) {
            int b;
            int shift = 0;
            int result = 0;

            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);

            int dlat = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lat += dlat;

            shift = 0;
            result = 0;

            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);

            int dlng = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lng += dlng;

            LatLng point = new LatLng(lat / 1E5, lng / 1E5);
            poly.add(point);
        }

        return poly;
    }

    /*
     * Distance helper for deciding when to refresh route.
     */
    private float distanceBetweenLatLng(LatLng a, LatLng b) {
        float[] results = new float[1];
        Location.distanceBetween(
                a.latitude, a.longitude,
                b.latitude, b.longitude,
                results
        );
        return results[0];
    }

    private void updateSessionStartLocation(){
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
                        Toast.makeText(this, "Failed to update start location", Toast.LENGTH_SHORT).show());

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