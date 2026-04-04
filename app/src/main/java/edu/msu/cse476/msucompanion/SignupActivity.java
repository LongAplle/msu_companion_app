package edu.msu.cse476.msucompanion;

import android.graphics.Paint;
import android.os.Bundle;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class SignupActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        TextView goBack = findViewById(R.id.goBackFromSignup);
        goBack.setPaintFlags(goBack.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
    }

    public void onGoBackToLogin(View view) {
        Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    public void onSignUp(View view) {
        EditText fullNameEditText = findViewById(R.id.fullName);
        EditText usernameEditText = findViewById(R.id.username);
        EditText emailEditText = findViewById(R.id.email);
        EditText passwordEditText = findViewById(R.id.password);
        EditText passwordRetypeEditText = findViewById(R.id.passwordRetype);

        String fullName = fullNameEditText.getText().toString().trim();
        String username = usernameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        String password2 = passwordRetypeEditText.getText().toString();

        // Local validation first before network calls
        if (!password.equals(password2)) {
            passwordEditText.setText("");
            passwordRetypeEditText.setText("");
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();

        } else if (username.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();

        } else if (username.contains("@")) {
            usernameEditText.setText("");
            Toast.makeText(this, "Username cannot contain '@'", Toast.LENGTH_SHORT).show();

        } else {
            // Check if username is already taken in Firestore
            // Firebase Auth enforces unique emails not usernames
            // so manually check Firestore before creating the account
            FirebaseFirestore.getInstance().collection("users")
                    .whereEqualTo("username", username)
                    .get()
                    .addOnSuccessListener(query -> {
                        if (!query.isEmpty()) {
                            // Username already exists — stop and tell the user
                            usernameEditText.setText("");
                            Toast.makeText(this, "Username already taken, please choose another", Toast.LENGTH_SHORT).show();
                        } else {
                            // Username is free — proceed with account creation
                            createAccount(email, password, fullName, username);
                        }
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error checking username: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        }
    }

    /*
     * Only called after username uniqueness is confirmed.
     * Firebase Auth handles password hashing securely.
     */
    private void createAccount(String email, String password, String fullName, String username) {
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = Objects.requireNonNull(authResult.getUser()).getUid();

                    // Store extra user info in Firestore using Auth uid as document ID
                    // This links the Auth account to the Firestore user document
                    Map<String, Object> user = new HashMap<>();
                    user.put("fullName", fullName);
                    user.put("username", username);
                    user.put("email", email);

                    FirebaseFirestore.getInstance().collection("users")
                            .document(uid)
                            .set(user)
                            .addOnSuccessListener(unused -> {
                                // Save to SharedPreferences after both Auth and Firestore
                                SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("userId", uid);        // Firestore string ID
                                editor.putInt("userIdLocal", 1);        // Local Room int ID
                                editor.putString("full_name", fullName);
                                editor.putString("username", username);
                                editor.putString("email", email);
                                editor.apply();

                                Toast.makeText(this, "Sign Up Successful!", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(this, MainActivity.class);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to save user data: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                            );
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Signup Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
}