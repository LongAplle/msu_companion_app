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
        SharedPreferences prefs = getSharedPreferences(Keys.PREF_USER, Context.MODE_PRIVATE);
        currUserId = prefs.getString(Keys.PREF_USER_ID, null);
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
        contactData.put(Keys.FIELD_USER_ID, currUserId);
        contactData.put(Keys.FIELD_CONTACT_NAME, name);
        contactData.put(Keys.FIELD_CONTACT_PHONE, phone);

        // Create a new Firestore document reference first so we can get the ID
        // before saving so this way both Firestore and Room use the same remoteId
        String remoteId = firestoreDb.collection(Keys.COLLECTION_CONTACTS).document().getId();

        firestoreDb.collection(Keys.COLLECTION_CONTACTS).document(remoteId)
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
                                Toast.makeText(AddContactActivity.this, "Failed to save contact: " + e.getMessage(), Toast.LENGTH_LONG).show())
                );
    }
}