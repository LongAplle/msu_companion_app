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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
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
    }

    public void onLogOut(View view) {
        // Clear stored credentials
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();

        Toast.makeText(MainActivity.this, "Logged out", Toast.LENGTH_SHORT).show();

        // Go back to LoginActivity
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    public void onStartSession(View view) {
        Intent intent = new Intent(MainActivity.this, SessionActivity.class);
        startActivity(intent);
    }
}