package edu.msu.cse476.msucompanion;

import android.os.Bundle;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
    }

    public void onLogin(View view) {
        EditText usernameEditText = findViewById(R.id.username);
        EditText passwordEditText = findViewById(R.id.password);
        String username = usernameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();

        // TODO: User Authentication with Server Database
        if (true) {
            // TODO: Get User data from server (userId, fullName, username, password + Contacts + Session History)

            // Save credentials to SharedPreferences (local)
            SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("userId", 1);
            editor.putString("username", username);
            editor.putString("password", password);
            editor.apply();

            // TODO: Populate local Contact and Session History

            Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();

            // Go to MainActivity
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish(); // Close LoginActivity so user can't go back

        } else {
            Toast.makeText(this, "Login Failed!", Toast.LENGTH_SHORT).show();
        }
    }

    public void onSignUp(View view) {
        // Go to SignupActivity
        Intent intent = new Intent(this, SignupActivity.class);
        startActivity(intent);
    }
}