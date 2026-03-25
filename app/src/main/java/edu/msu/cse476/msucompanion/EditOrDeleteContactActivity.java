package edu.msu.cse476.msucompanion;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EditOrDeleteContactActivity extends AppCompatActivity {
    private AppDatabase db;
    private FirebaseFirestore firestoreDb;

    int contactId;

    private String currUserIdFirestore;  // Firestore string ID
    private int currUserIdLocal;         // Local Room int ID

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_or_delete_contact);

        // Get both user IDs from SharedPreferences
        // Firestore uses a String ID, Room uses an int ID
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        currUserIdFirestore = prefs.getString("userId", null);
        currUserIdLocal = prefs.getInt("userIdLocal", 0);

        if (currUserIdFirestore == null) {
            finish();
            return;
        }

        // Get contact data passed from the previous activity
        Intent intent = getIntent();
        contactId = intent.getIntExtra("contact_id", -1);
        String name = intent.getStringExtra("contact_name");
        String phone = intent.getStringExtra("contact_phone");

        if (contactId == -1) {
            Toast.makeText(this, "Error loading contact", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Pre-fill the EditTexts with existing contact data
        EditText editName = findViewById(R.id.editName);
        EditText editPhone = findViewById(R.id.editPhone);
        editName.setText(name);
        editPhone.setText(phone);

        // Initialize local Room database and Firestore instance
        db = AppDatabase.getInstance(this);
        firestoreDb = FirebaseFirestore.getInstance();
    }

    public void onSave(View view) {
        EditText editName = findViewById(R.id.editName);
        EditText editPhone = findViewById(R.id.editPhone);
        String newName = editName.getText().toString().trim();
        String newPhone = editPhone.getText().toString().trim();

        if (newName.isEmpty() || newPhone.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            // Update contact in local Room database first
            Contact existing = db.contactDao().getContactById(contactId);
            if (existing != null) {
                existing.setName(newName);
                existing.setPhoneNumber(newPhone);
                db.contactDao().update(existing);

                // Mirror the update to Firestore using contactId as the document ID
                // Only updating name and phone, preserving userId and other fields
                Map<String, Object> updates = new HashMap<>();
                updates.put("name", newName);
                updates.put("phone", newPhone);

                firestoreDb.collection("contacts")
                        .document(String.valueOf(contactId))
                        .update(updates)
                        .addOnSuccessListener(unused -> {
                            runOnUiThread(() -> {
                                Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        })
                        .addOnFailureListener(e -> {
                            runOnUiThread(() -> {
                                Toast.makeText(this, "Failed to update contact remotely: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                        });
            } else {
                runOnUiThread(() -> Toast.makeText(this, "Contact not found", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public void onDelete(View view) {
        new Thread(() -> {
            // Delete contact from local Room database
            Contact contact = new Contact();
            contact.setId(contactId);
            db.contactDao().delete(contact);

            // Mirror the delete to Firestore using contactId as the document ID
            firestoreDb.collection("contacts")
                    .document(String.valueOf(contactId))
                    .delete()
                    .addOnSuccessListener(unused -> {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    })
                    .addOnFailureListener(e -> {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Failed to delete contact remotely: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    });
        }).start();
    }
}