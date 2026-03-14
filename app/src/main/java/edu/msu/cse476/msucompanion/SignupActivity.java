package edu.msu.cse476.msucompanion;

import android.os.Bundle;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class SignupActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
    }

    public void onSignUp(View view) {
        EditText usernameEditText = findViewById(R.id.username);
        EditText passwordEditText = findViewById(R.id.password);
        EditText passwordRetypeEditText = findViewById(R.id.passwordRetype);

        String username = usernameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        String password2 = passwordRetypeEditText.getText().toString();

        if (password.equals(password2) && !username.isEmpty()) {
            // TODO: Add User Exist and Password Requirement Check
            // TODO: Add User Data to Server Database

            // TODO: Get User data from server
            // Save credentials to SharedPreferences (local)
            SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("userId", 1);
            editor.putString("username", username);
            editor.putString("password", password);
            editor.apply();

            Toast.makeText(this, "Sign Up Successful!", Toast.LENGTH_SHORT).show();

            // Go to MainActivity
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish(); // Close SignupActivity so user can't go back

        } else {
            passwordEditText.setText("");
            passwordRetypeEditText.setText("");
            Toast.makeText(this, "Signup Failed!", Toast.LENGTH_SHORT).show();
        }
    }
}