package edu.msu.cse476.msucompanion;

import android.os.Bundle;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SignupActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        EditText username = findViewById(R.id.username);
        EditText password = findViewById(R.id.password);
        EditText passwordRetype = findViewById(R.id.passwordRetype);
        Button signupButton = findViewById(R.id.signupButton);

        signupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String user = username.getText().toString().trim();
                String pwd = password.getText().toString();
                String pwd2 = passwordRetype.getText().toString();

                if (pwd.equals(pwd2) && !user.isEmpty()) {
                    //TODO: Add password requirements
                    //TODO: Add User Info to Server Database (including checking if user already exists)

                    // Save credentials to SharedPreferences (local)
                    SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("username", user);
                    editor.putString("password", pwd);
                    editor.apply();

                    Toast.makeText(SignupActivity.this, "Sign Up Successful!", Toast.LENGTH_SHORT).show();

                    // Go to MainActivity
                    Intent intent = new Intent(SignupActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish(); // Close SignupActivity so user can't go back

                } else {
                    password.setText("");
                    passwordRetype.setText("");
                    Toast.makeText(SignupActivity.this, "Signup Failed!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}