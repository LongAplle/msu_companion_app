package edu.msu.cse476.msucompanion;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ContactListActivity extends AppCompatActivity {
    private ContactAdapter adapter;
    private AppDatabase db;
    private int currUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_list);

        // Get current user from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        currUserId = prefs.getInt("userId", 0);
        if (currUserId == 0) {
            // No user logged in → go to LoginActivity
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Initialize database
        db = AppDatabase.getInstance(this);

        setupRecyclerView();

        setupSearch();

        // Load all contacts initially
        loadContacts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadContacts();
    }

    private void loadContacts() {
        new Thread(() -> {
            // Convert int userId to String for DAO
            List<Contact> contacts = db.contactDao().getContactsForUser(currUserId);
            runOnUiThread(() -> adapter.updateList(contacts));
        }).start();
    }

    private void searchContacts(String query) {
        new Thread(() -> {
            List<Contact> contacts = db.contactDao().searchContacts(currUserId, query);
            runOnUiThread(() -> adapter.updateList(contacts));
        }).start();
    }

    public void onAddContact(View view) {
        Intent intent = new Intent(this, AddContactActivity.class);
        startActivity(intent);
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ContactAdapter(new ArrayList<>(), contact -> {
            Intent intent = new Intent(ContactListActivity.this, EditOrDeleteContactActivity.class);
            intent.putExtra("contact_id", contact.getId());
            intent.putExtra("contact_name", contact.getName());
            intent.putExtra("contact_phone", contact.getPhoneNumber());
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        EditText searchEditText = findViewById(R.id.search);
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    loadContacts();
                } else {
                    searchContacts(query);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
}