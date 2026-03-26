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
    private String currUserIdFirestore;  // Firestore string ID
    private int currUserIdLocal;         // Local Room int ID

    private FirebaseFirestore firestoreDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_contact);

        // Get both user IDs from SharedPreferences
        // Firestore uses a String ID, Room uses an int ID
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        currUserIdFirestore = prefs.getString("userId", null);
        currUserIdLocal = prefs.getInt("userIdLocal", 0);

        if (currUserIdFirestore == null) {
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

        // Use int ID for Room local database
        Contact contact = new Contact(currUserIdLocal, name, phone);
        new Thread(() -> {
            // Save to local Room database first
            long rowId = db.contactDao().insert(contact);
            String contactId = String.valueOf(rowId);

            // Build contact data map for Firestore
            // Use Firestore string ID for remote database
            Map<String, Object> contactData = new HashMap<>();
            contactData.put("contactId", contactId);
            contactData.put("userId", currUserIdFirestore);
            contactData.put("name", name);
            contactData.put("phone", phone);

            // Mirror contact to Firestore using Room row ID as document ID
            // so both databases stay in sync with the same identifier
            firestoreDb.collection("contacts")
                    .document(contactId)
                    .set(contactData)
                    .addOnSuccessListener(unused -> {
                        runOnUiThread(() -> {
                            Toast.makeText(AddContactActivity.this, "Contact saved", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    })
                    .addOnFailureListener(e -> {
                        runOnUiThread(() -> {
                            Toast.makeText(AddContactActivity.this, "Failed to save contact remotely: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    });
        }).start();
    }
}