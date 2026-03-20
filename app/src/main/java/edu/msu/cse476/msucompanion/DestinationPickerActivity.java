package edu.msu.cse476.msucompanion;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

/*
 * DestinationPickerActivity
 *
 * This screen allows the user to select where they are walking to.
 * Once a destination is chosen, the app launches WalkSessionActivity
 * and passes the destination name and coordinates to it.
 *
 * The coordinates are later used by the GPS tracking system to calculate
 * distance and detect when the user has safely arrived.
 */
public class DestinationPickerActivity extends AppCompatActivity {

    // Buttons for selecting preset destinations
    private Button btnLibrary;
    private Button btnDorm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the layout for the destination picker screen
        setContentView(R.layout.activity_destination_picker);

        // Connect UI buttons to their XML components
        btnLibrary = findViewById(R.id.btnLibrary);
        btnDorm = findViewById(R.id.btnDorm);

        /*
         * When the "MSU Library" button is clicked,
         * open the walk session and pass the library coordinates.
         */
        btnLibrary.setOnClickListener(v -> openWalkSession(
                "MSU Library",      // Destination name
                42.7284,            // Latitude of MSU Library
                -84.4814            // Longitude of MSU Library
        ));

        /*
         * When the "Dorm / Apartment" button is clicked,
         * open the walk session and pass the dorm coordinates.
         */
        btnDorm.setOnClickListener(v -> openWalkSession(
                "Dorm / Apartment", // Destination name
                42.7210,            // Example dorm/apartment latitude
                -84.4700            // Example dorm/apartment longitude
        ));
    }

    /*
     * openWalkSession
     *
     * This method launches WalkSessionActivity and sends the
     * selected destination information through an Intent.
     *
     * The destination name and coordinates are passed so that
     * WalkSessionActivity can:
     * 1. Track the user's movement
     * 2. Calculate distance to the destination
     * 3. Detect when the user arrives safely
     */
    private void openWalkSession(String name, double lat, double lng) {

        // Create intent to move to the WalkSessionActivity
        Intent intent = new Intent(this, WalkSessionActivity.class);

        // Pass destination information to the next screen
        intent.putExtra("destination_name", name);
        intent.putExtra("destination_lat", lat);
        intent.putExtra("destination_lng", lng);

        // Start the walk session screen
        startActivity(intent);
    }
}