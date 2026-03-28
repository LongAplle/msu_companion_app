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

        if (password.equals(password2) && !username.isEmpty() && !email.isEmpty()) {

            // Firebase Auth handles password hashing securely
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        String uid = authResult.getUser().getUid();

                        // Store extra user info in Firestore using Auth uid as document ID
                        Map<String, Object> user = new HashMap<>();
                        user.put("fullName", fullName);
                        user.put("username", username);
                        user.put("email", email);

                        FirebaseFirestore.getInstance().collection("users")
                                .document(uid)
                                .set(user)
                                .addOnSuccessListener(unused -> {
                                    // Save to SharedPreferences after both Auth and Firestore succeed
                                    SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = prefs.edit();
                                    editor.putString("userId", uid); // firestore string ID
                                    editor.putString("full_name", fullName);
                                    editor.putString("username", username);
                                    editor.putString("email", email);
                                    editor.apply();

                                    Toast.makeText(this, "Sign Up Successful!", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(this, MainActivity.class);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Failed to save user data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Signup Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });

        } else {
            passwordEditText.setText("");
            passwordRetypeEditText.setText("");
            Toast.makeText(this, "Passwords do not match or fields are empty", Toast.LENGTH_SHORT).show();
        }
    }
}