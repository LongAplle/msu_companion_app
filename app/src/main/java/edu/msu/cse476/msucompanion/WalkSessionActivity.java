package edu.msu.cse476.msucompanion;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/*
 * Activity for the walk session, binding with the service and updating the UI.
 */
public class WalkSessionActivity extends AppCompatActivity implements OnMapReadyCallback, WalkSessionService.SessionListener {
    // UI components used to display information to the user
    private TextView tvCurrentLocation;
    private TextView tvDistance;
    private TextView tvStatus;
    private Button btnToggleWalk;

    // Destination selected by the user
    private Destination destination;

    // Contact selection
    private boolean useAllContacts = true;
    private long[] selectedContactIds = new long[0];

    private int notifyIntervalMinutes = WalkSessionService.NOTIFY_INTERVAL_MINUTES_DEFAULT;

    // Google Maps state
    private GoogleMap googleMap;
    private Marker currentLocationMarker;
    private Marker destinationMarker;
    private Polyline routeLine;
    private boolean initialCameraFramed = false;

    // Walking-route refresh control (activity‑side only)
    private boolean routeRequestInFlight = false;
    private long lastRouteFetchTimeMs = 0L;
    private LatLng lastRouteOrigin = null;
    private static final long ROUTE_REFRESH_INTERVAL_MS = 15000L;   // 15 sec
    private static final float ROUTE_REFRESH_MIN_MOVE_METERS = 25f; // refetch after meaningful movement

    // Service binding
    private WalkSessionService walkSessionService;
    private boolean isBound = false;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            onServiceBound(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            onServiceUnbound();
        }
    };

    // Flag to determine if this is to start or to resume an active session
    boolean startNewSessionFlag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walk_session);

        // Get current user ID from SharedPreferences (only for service, not used in activity)
        SharedPreferences prefs = getSharedPreferences(Keys.PREF_USER, Context.MODE_PRIVATE);
        String currUserId = prefs.getString(Keys.PREF_USER_ID, null);
        if (currUserId == null) {
            finish();
            return;
        }

        Intent sourceIntent = getIntent();

        startNewSessionFlag = sourceIntent.getBooleanExtra(Keys.EXTRA_START_NEW_SESSION, false);
        if (!startNewSessionFlag) {
            if (!ActiveSessionRepository.hasActiveSession()) {
                // The session has ended, so finish this activity
                finish();
                return;
            }
        }

        String destinationName = sourceIntent.getStringExtra(Keys.EXTRA_DESTINATION_NAME);
        double destinationLat = sourceIntent.getDoubleExtra(Keys.EXTRA_DESTINATION_LAT, 0.0);
        double destinationLng = sourceIntent.getDoubleExtra(Keys.EXTRA_DESTINATION_LNG, 0.0);

        if (destinationName == null) {
            destinationName = "Unknown";
        }

        destination = new Destination(destinationName, destinationLat, destinationLng);

        useAllContacts = sourceIntent.getBooleanExtra(Keys.EXTRA_USE_ALL_CONTACTS, true);
        selectedContactIds = sourceIntent.getLongArrayExtra(Keys.EXTRA_SELECTED_CONTACT_IDS);
        if (selectedContactIds == null) {
            selectedContactIds = new long[0];
        }

        notifyIntervalMinutes = getIntent().getIntExtra(Keys.EXTRA_NOTIFY_INTERVAL_MINUTES, WalkSessionService.NOTIFY_INTERVAL_MINUTES_DEFAULT);

        // Initialize UI components
        TextView tvDestination = findViewById(R.id.tvDestination);
        btnToggleWalk = findViewById(R.id.btnToggleWalk);
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        tvDistance = findViewById(R.id.tvDistance);
        tvStatus = findViewById(R.id.tvStatus);

        tvDestination.setText(getString(R.string.tvDestinationText, destination.getName()));
        tvCurrentLocation.setText(getString(R.string.tvCurrentLocationText, "None"));
        tvDistance.setText(getString(R.string.tvDistanceText, Float.NaN));
        tvStatus.setText(getString(R.string.tvStatusText, "Waiting"));

        TextView goBack = findViewById(R.id.goBackFromSession);
        goBack.setPaintFlags(goBack.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        // Create and attach the map fragment
        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.mapContainer, mapFragment)
                .commit();
        mapFragment.getMapAsync(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Bind to the service (creates it if needed)
        Intent intent = new Intent(this, WalkSessionService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Unbind when activity stops (service continues in background)
        if (isBound) {
            if (walkSessionService != null) {
                walkSessionService.unregisterListener(this);
            }
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    // Service binding
    private void onServiceBound(IBinder service) {
        WalkSessionService.LocalBinder binder = (WalkSessionService.LocalBinder) service;
        walkSessionService = binder.getService();
        isBound = true;

        if (!startNewSessionFlag && !walkSessionService.isWalkSessionActive()) {
            finish();
            return;
        }

        walkSessionService.registerListener(WalkSessionActivity.this);

        Location lastLoc = walkSessionService.getLastKnownLocation();
        if (lastLoc != null) {
            onLocationUpdated(lastLoc);
        }

        // Update UI if a session is active
        if (walkSessionService.isWalkSessionActive()) {
            btnToggleWalk.setText(R.string.endWalkText);
            tvStatus.setText(getString(R.string.tvStatusText, "Walk session active"));
        }
    }

    private void onServiceUnbound() {
        walkSessionService = null;
        isBound = false;
    }

    // SessionListener implementations
    @Override
    public void onSessionStarted() {
        startNewSessionFlag = false; // switch to observer mode
        btnToggleWalk.setEnabled(true);
        btnToggleWalk.setText(R.string.endWalkText);
        tvStatus.setText(getString(R.string.tvStatusText, "Walk session active"));
    }

    @Override
    public void onSessionEnded(boolean arrived) {
        // Service has ended; close activity
        if (isBound) {
            if (walkSessionService != null) {
                walkSessionService.unregisterListener(this);
            }
            unbindService(serviceConnection);
            isBound = false;
        }
        finish();
    }

    @Override
    public void onLocationUpdated(Location location) {
        if (location == null) return;

        // Update UI text
        String locText = String.format(Locale.US, "%.5f, %.5f", location.getLatitude(), location.getLongitude());
        tvCurrentLocation.setText(getString(R.string.tvCurrentLocationText, locText));

        // Update map markers
        updateMapMarkers(location);

        // Refresh walking route if needed
        maybeFetchWalkingRoute(location);
    }

    @Override
    public void onDistanceUpdated(float distanceMeters) {
        tvDistance.setText(getString(R.string.tvDistanceText, distanceMeters));
    }

    @Override
    public void onLocationError(String message) {
        Toast.makeText(this, "Location error: " + message, Toast.LENGTH_SHORT).show();
        tvStatus.setText(getString(R.string.tvStatusText, "Error - " + message));
    }

    // Permission handling
    private boolean checkLocationPermission() {
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                LocationHelper.LOCATION_PERMISSION_REQUEST_CODE);
    }

    private boolean checkSMSPermission() {
        return checkSelfPermission(android.Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSMSPermission() {
        requestPermissions(new String[]{android.Manifest.permission.SEND_SMS},
                WalkSessionService.SMS_PERMISSION_REQUEST_CODE);
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

        // Handle location permission request result
        if (requestCode == LocationHelper.LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onToggleStartStop(null);
            } else {
                Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show();
                tvStatus.setText(getString(R.string.tvStatusText, "Location permission denied"));
            }
        }

        // Handle SMS permission request result
        if (requestCode == WalkSessionService.SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS permission not granted.\nContacts will not be notified via SMS", Toast.LENGTH_SHORT).show();
            }
            onToggleStartStop(null);  // Proceed with the walk session
        }
    }

    // Session control, by UI button
    /*
     * Toggle the start/stop walk session.
     */
    public void onToggleStartStop(View view) {
        // Request location permission if not already granted
        if (!checkLocationPermission()) {
            requestLocationPermission();
            return;
        }

        // Stop the walk session if already active
        // place this before the SMS permission request so if SMS permission is denied before,
        // stopping a session will not ask for permission again
        if (walkSessionService != null && walkSessionService.isWalkSessionActive()) {
            onStopWalkSession();
            return;
        }

        // Request SMS permission if not already granted
        if (!checkSMSPermission()) {
            requestSMSPermission();
            return;
        }

        // Prevent double tap while service is starting
        btnToggleWalk.setEnabled(false);
        onStartWalkSession();
    }

    /*
     * Start the walk session by starting the service.
     */
    private void onStartWalkSession() {
        Intent intent = new Intent(this, WalkSessionService.class);
        intent.setAction(WalkSessionService.ACTION_START_SESSION);
        intent.putExtra(Keys.EXTRA_DESTINATION_NAME, destination.getName());
        intent.putExtra(Keys.EXTRA_DESTINATION_LAT, destination.getLatitude());
        intent.putExtra(Keys.EXTRA_DESTINATION_LNG, destination.getLongitude());

        intent.putExtra(Keys.EXTRA_USE_ALL_CONTACTS, useAllContacts);
        intent.putExtra(Keys.EXTRA_SELECTED_CONTACT_IDS, selectedContactIds);

        intent.putExtra(Keys.EXTRA_NOTIFY_INTERVAL_MINUTES, notifyIntervalMinutes);

        startService(intent);  // will update the button text when it actually starts
    }

    /*
     * Stop the walk session by stopping the service.
     */
    private void onStopWalkSession() {
        Intent intent = new Intent(this, WalkSessionService.class);
        intent.setAction(WalkSessionService.ACTION_STOP_SESSION);
        startService(intent); // The service will notify us via onSessionEnded, then we unbind and finish
    }

    // Google Map
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

        // If location already arrived before map was ready, draw immediately
        if (walkSessionService != null) {
            Location lastKnownLocation = walkSessionService.getLastKnownLocation();
            updateMapMarkers(lastKnownLocation);
            maybeFetchWalkingRoute(lastKnownLocation);
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
                            Toast.makeText(WalkSessionActivity.this, "Route request failed", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(WalkSessionActivity.this, "Unable to fetch walking route", Toast.LENGTH_SHORT).show()
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

    // Go back to main
    public void onGoBack(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}