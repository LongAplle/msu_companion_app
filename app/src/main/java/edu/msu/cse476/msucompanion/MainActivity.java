package edu.msu.cse476.msucompanion;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private SharedPreferences prefs;

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
    }

    public void onViewContacts(View view) {
        Intent intent = new Intent(this, ContactListActivity.class);
        startActivity(intent);
    }

    public void onViewSessionHistory(View view) {
        // TODO: Add view session history functionality
    }

    public void onStartSession(View view) {
        Intent intent = new Intent(this, SessionActivity.class);
        startActivity(intent);
    }

    public void onLogOut(View view) {
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
}