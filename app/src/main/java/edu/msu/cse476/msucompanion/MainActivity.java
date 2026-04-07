package edu.msu.cse476.msucompanion;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private SharedPreferences prefs;  // user preferences

    private Button startSessionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);

        String userId = prefs.getString("userId", null);
        String email = prefs.getString("email", null);
        String fullName = prefs.getString("full_name", null);
        String username = prefs.getString("username", null);

        if (userId == null) {
            // No user logged in → go to LoginActivity
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish(); // Close MainActivity so back button doesn't return here
            return;
        }

        // User is logged in, proceed with normal UI
        setContentView(R.layout.activity_main);

        String displayName;
        if (username != null && !username.isEmpty()) {
            displayName = username;
        } else if (fullName != null && !fullName.isEmpty()) {
            displayName = fullName;
        } else if (email != null && !email.isEmpty()) {
            displayName = email;
        } else {
            displayName = "User";
        }

        TextView greetingText = findViewById(R.id.greetingText);
        String greetingMsg = getString(R.string.greetingText, displayName);
        greetingText.setText(greetingMsg);

        startSessionButton = findViewById(R.id.startSessionButton);

        // Observe the active session live data
        ActiveSessionRepository.getActiveSession().observe(this, destination -> {
            if (destination != null) {
                // There is an active walk
                setResumeSession(destination);
            }
            else {
                // There is no active walk
                setCreateSession();
            }
        });
    }

    public void onViewContacts(View view) {
        Intent intent = new Intent(this, ContactListActivity.class);
        startActivity(intent);
    }

    public void onViewSessionHistory(View view) {
        Intent intent = new Intent(this, SessionHistoryActivity.class);
        startActivity(intent);
    }

    /**
     * Go to the destination picker activity.
     */
    private void onStartSession() {
        Intent intent = new Intent(this, SessionActivity.class);
        startActivity(intent);
    }

    /**
     * Go to the walk session activity.
     */
    private void onResumeSession(String destName, double destLat, double destLng) {
        Intent intent = new Intent(this, WalkSessionActivity.class);
        intent.putExtra("destination_name", destName);
        intent.putExtra("destination_lat", destLat);
        intent.putExtra("destination_lng", destLng);
        intent.putExtra("start_new_session", false);
        startActivity(intent);
    }

    public void onLogOut(View view) {
        // Prevent logout if a session is active
        if (ActiveSessionRepository.hasActiveSession()) {
            Toast.makeText(this, "Please stop your walk session before logging out.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Clear Room data for the current user
        String currUserId = prefs.getString("userId", null);
        if (currUserId != null) {
            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(this);
                db.clearAllTables();
                db.databaseDao().resetSequence();
            }).start();
        }

        // Clear stored credentials
        prefs.edit().clear().apply();

        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();

        // Go back to LoginActivity
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    private void setCreateSession() {
        startSessionButton.setText(R.string.createSessionText);
        startSessionButton.setOnClickListener(v -> onStartSession());
    }

    private void setResumeSession(Destination destination) {
        startSessionButton.setText(R.string.resumeSessionText);
        startSessionButton.setOnClickListener(v -> onResumeSession(
                destination.getName(),
                destination.getLatitude(),
                destination.getLongitude()));
    }
}