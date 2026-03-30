package edu.msu.cse476.msucompanion;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AddContactActivity extends AppCompatActivity {
    private AppDatabase db;
    private String currUserId;  // Firestore string ID

    private FirebaseFirestore firestoreDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_contact);

        // Get current user from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        currUserId = prefs.getString("userId", null);
        if (currUserId == null) {
            finish();
            return;
        }

        // Initialize local Room database and Firestore instance
        db = AppDatabase.getInstance(this);
        firestoreDb = FirebaseFirestore.getInstance();
    }

    public void onSave(View view) {
        EditText editName = findViewById(R.id.editName);
        EditText editPhone = findViewById(R.id.editPhone);
        String name = editName.getText().toString().trim();
        String phone = editPhone.getText().toString().trim();

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> contactData = new HashMap<>();
        contactData.put("userId", currUserId);
        contactData.put("name", name);
        contactData.put("phone", phone);

        // Create a new Firestore document reference first so we can get the ID
        // before saving so this way both Firestore and Room use the same remoteId
        String remoteId = firestoreDb.collection("contacts").document().getId();

        firestoreDb.collection("contacts").document(remoteId)
                .set(contactData)
                .addOnSuccessListener(unused -> {
                    // Save to local Room database using the same remoteId from Firestore
                    new Thread(() -> {
                        Contact contact = new Contact(remoteId, currUserId, name, phone);
                        db.contactDao().insert(contact);
                        runOnUiThread(() -> {
                            Toast.makeText(AddContactActivity.this, "Contact saved", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }).start();
                })
                .addOnFailureListener(e ->
                        runOnUiThread(() ->
                                Toast.makeText(AddContactActivity.this, "Failed to save contact: " + e.getMessage(), Toast.LENGTH_SHORT).show())
                );
    }
}