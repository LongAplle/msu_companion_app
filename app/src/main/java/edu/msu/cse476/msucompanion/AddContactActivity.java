package edu.msu.cse476.msucompanion;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AddContactActivity extends AppCompatActivity {
    private AppDatabase db;
    private int currUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_contact);

        // Get current user
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        currUserId = prefs.getInt("userId", 0);
        if (currUserId == 0) {
            finish();
            return;
        }

        db = AppDatabase.getInstance(this);
    }

    public void onSave(View view) {
        EditText editName = findViewById(R.id.editName);
        EditText editPhone = findViewById(R.id.editPhone);
        String name = editName.getText().toString().trim();
        String phone = editPhone.getText().toString().trim();
        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(AddContactActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create new contact
        Contact contact = new Contact(currUserId, name, phone);
        new Thread(() -> {
            db.contactDao().insert(contact);
            runOnUiThread(() -> {
                Toast.makeText(AddContactActivity.this, "Contact saved", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();

        // TODO: Add Contact to Server Database
    }
}