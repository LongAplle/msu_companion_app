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

    long contactId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_or_delete_contact);

        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String currUserId = prefs.getString("userId", null);  // Firestore string ID

        if (currUserId == null) {
            finish();
            return;
        }

        // Initialize local Room database and Firestore instance
        db = AppDatabase.getInstance(this);
        firestoreDb = FirebaseFirestore.getInstance();

        // Get contact data passed from the previous activity
        Intent intent = getIntent();
        contactId = intent.getLongExtra("contact_id", -1);
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
            Contact currentContact = db.contactDao().getContactById(contactId);

            if (currentContact != null) {
                // Update contact in Firestore
                String remoteId = currentContact.getRemoteId();
                Map<String, Object> updates = new HashMap<>();
                updates.put("name", newName);
                updates.put("phone", newPhone);

                firestoreDb.collection("contacts")
                        .document(remoteId)
                        .update(updates)
                        .addOnSuccessListener(unused -> {
                            // Update contact in local Room database
                            new Thread(() -> {
                                currentContact.setName(newName);
                                currentContact.setPhoneNumber(newPhone);
                                db.contactDao().update(currentContact);

                                runOnUiThread(() -> {
                                    Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                            }).start();
                        })
                        .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(this, "Failed to update contact remotely: " + e.getMessage(), Toast.LENGTH_LONG).show()));
            } else {
                runOnUiThread(() -> Toast.makeText(this, "Contact not found", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public void onDelete(View view) {
        new Thread(() -> {
            Contact currentContact = db.contactDao().getContactById(contactId);

            if (currentContact != null) {
                // Delete contact from Firestore
                String remoteId = currentContact.getRemoteId();
                firestoreDb.collection("contacts")
                        .document(remoteId)
                        .delete()
                        .addOnSuccessListener(unused -> {
                            // Delete contact from local Room database
                            new Thread(() -> {
                                db.contactDao().delete(currentContact);

                                runOnUiThread(() -> {
                                    Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                            }).start();
                        })
                        .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(this, "Failed to delete contact remotely: " + e.getMessage(), Toast.LENGTH_LONG).show()));
            }
        }).start();
    }
}