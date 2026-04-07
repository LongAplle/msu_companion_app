package edu.msu.cse476.msucompanion;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SessionHistoryActivity extends AppCompatActivity {
    private SessionHistoryAdapter adapter;
    private AppDatabase db;
    private String currUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_history);

        // Get current user from SharedPreferences
        SharedPreferences prefs = getSharedPreferences(Keys.PREF_USER, Context.MODE_PRIVATE);
        currUserId = prefs.getString(Keys.PREF_USER_ID, null);
        if (currUserId == null) {
            finish();
            return;
        }

        // Initialize database
        db = AppDatabase.getInstance(this);

        setupRecyclerView();

        // Load all sessions initially
        loadSessions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSessions();
    }

    private void loadSessions() {
        new Thread(() -> {
            List<WalkSession> sessions = db.walkSessionDao().getSessionsForUser(currUserId);
            runOnUiThread(() -> adapter.updateList(sessions));
        }).start();
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.sessionRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SessionHistoryAdapter(new ArrayList<>(), session -> {
            Intent intent = new Intent(SessionHistoryActivity.this, ViewWalkSession.class);
            intent.putExtra(Keys.EXTRA_SESSION_ID, session.getId());
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);
    }
}