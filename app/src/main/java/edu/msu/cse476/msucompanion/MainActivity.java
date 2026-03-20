package edu.msu.cse476.msucompanion;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import android.content.pm.PackageManager;
import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private SharedPreferences prefs;

    // GPS helper
    private LocationHelper locationHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String username = prefs.getString("username", null);
        String fullName = prefs.getString("full_name", null);

        if (username == null) {
            // No user logged in → go to LoginActivity
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish(); // Close MainActivity so back button doesn't return here
            return;
        }

        // User is logged in, proceed with normal UI
        setContentView(R.layout.activity_main);

        String displayName = (fullName != null && !fullName.isEmpty()) ? fullName : username;
        TextView greetingText = findViewById(R.id.greetingText);
        String greetingMsg = getString(R.string.greetingText, displayName);
        greetingText.setText(greetingMsg);

        // Initialize GPS helper
        locationHelper = new LocationHelper(this);
    }

    public void onViewContacts(View view) {
        Intent intent = new Intent(this, ContactListActivity.class);
        startActivity(intent);
    }

    public void onLogOut(View view) {
        // Clear Room data
        int currUserId = prefs.getInt("userId", 0);
        new Thread(() -> AppDatabase.getInstance(this).contactDao()
                .deleteContactsForUser(String.valueOf(currUserId))).start();

        // Clear stored credentials
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();

        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();

        // Go back to LoginActivity
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    public void onStartSession(View view) {
        Intent intent = new Intent(this, SessionActivity.class);
        startActivity(intent);
    }

    // Permission response handler (added for GPS)
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
                Toast.makeText(this, "Location permission granted. Tap GPS button again.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location permission required for GPS.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Stop GPS updates when activity closes
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationHelper != null) {
            locationHelper.stopLocationUpdates();
        }
    }

    public void onTestGps(View view) {
        if (!locationHelper.hasLocationPermission()) {
            locationHelper.requestLocationPermission(this);
            return;
        }

        locationHelper.startLocationUpdates(new LocationHelper.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(Location location) {
                double lat = location.getLatitude();
                double lng = location.getLongitude();
                TextView gpsText = findViewById(R.id.gpsText);
                gpsText.setText("Current Location:\n" +
                                "Lat: " + lat + "\n" +
                                "Lng: " + lng
                );
            }

            @Override
            public void onLocationError(String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onStartWalk(View view) {
        Intent intent = new Intent(this, DestinationPickerActivity.class);
        startActivity(intent);
    }
}