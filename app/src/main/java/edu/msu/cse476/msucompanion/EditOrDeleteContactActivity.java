package edu.msu.cse476.msucompanion;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


public class EditOrDeleteContactActivity extends AppCompatActivity {
    private AppDatabase db;

    int contactId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_or_delete_contact);

        // Get data from intent
        Intent intent = getIntent();
        contactId = intent.getIntExtra("contact_id", -1);
        String name = intent.getStringExtra("contact_name");
        String phone = intent.getStringExtra("contact_phone");

        if (contactId == -1) {
            Toast.makeText(this, "Error loading contact", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Pre‑fill the EditTexts
        EditText editName = findViewById(R.id.editName);
        EditText editPhone = findViewById(R.id.editPhone);
        editName.setText(name);
        editPhone.setText(phone);

        db = AppDatabase.getInstance(this);
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
            // Load the existing contact to preserve userId and other fields
            Contact existing = db.contactDao().getContactById(contactId);
            if (existing != null) {
                existing.setName(newName);
                existing.setPhoneNumber(newPhone);
                db.contactDao().update(existing);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } else {
                runOnUiThread(() -> Toast.makeText(this, "Contact not found", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public void onDelete(View view) {
        new Thread(() -> {
            // Create a contact with just the ID since Room uses the primary key for deletion
            Contact contact = new Contact();
            contact.setId(contactId);
            db.contactDao().delete(contact);
            runOnUiThread(() -> {
                Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }
}