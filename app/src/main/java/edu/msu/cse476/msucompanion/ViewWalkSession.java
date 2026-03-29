package edu.msu.cse476.msucompanion;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Date;
import java.util.Locale;

public class ViewWalkSession extends AppCompatActivity {
    private AppDatabase db;

    private WalkSession session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_walk_session);

        db = AppDatabase.getInstance(this);

        Intent intent = getIntent();
        long sessionId = intent.getLongExtra("session_id", -1);

        if (sessionId == -1) {
            Toast.makeText(this, "Id error", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        new Thread(() -> {
            session = db.walkSessionDao().getSessionById(sessionId);

            if (session == null) {
                Toast.makeText(this, "Session not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            populateUI();
        }).start();
    }

    private void populateUI() {
        TextView tvDestination = findViewById(R.id.tvDestination);
        TextView tvStatus = findViewById(R.id.tvStatus);
        TextView tvDay = findViewById(R.id.tvDay);
        TextView tvTimeRange = findViewById(R.id.tvTimeRange);
        TextView tvFrom = findViewById(R.id.tvFrom);
        TextView tvTo = findViewById(R.id.tvTo);

        // Destination
        tvDestination.setText(getString(R.string.tvDestinationText, session.getDestinationName()));

        // Format status
        String status = session.getStatus();
        String statusCap = status.substring(0, 1).toUpperCase() + status.substring(1);
        tvStatus.setText(getString(R.string.tvStatusText, statusCap));

        Date startTime = session.getStartTime();
        Date endTime = session.getEndTime();

        // Format date (e.g., "Tue, Mar 28, 2025")
        if (startTime != null) {
            String dateString = DateFormat.getLongDateFormat(this).format(startTime);
            tvDay.setText(dateString);
        }

        // Format time range (e.g., "10:30 AM - 11:15 AM")
        String startTimeStr = startTime != null ? DateFormat.getTimeFormat(this).format(startTime) : "Unknown";
        String endTimeStr = endTime != null ? DateFormat.getTimeFormat(this).format(endTime) : "In progress";
        tvTimeRange.setText(getString(R.string.timeRangeText, startTimeStr, endTimeStr));

        // Start location (lat/lng)
        Double startLat = session.getStartLat();
        Double startLng = session.getStartLng();
        if (startLat != null && startLng != null) {
            String fromText = String.format(Locale.US, "%.5f, %.5f", startLat, startLng);
            tvFrom.setText(getString(R.string.fromText, fromText));

            // Add the link to open Map
            tvFrom.setOnClickListener(v -> openMap(startLat, startLng));
            tvFrom.setEnabled(true);
        } else {
            tvFrom.setText(getString(R.string.fromText, "Unknown"));
            tvFrom.setEnabled(false);
            tvFrom.setOnClickListener(null);
        }

        // Destination location (lat/lng)
        Double destLat = session.getDestinationLat();
        Double destLng = session.getDestinationLng();
        if (destLat != null && destLng != null) {
            String toText = String.format(Locale.US, "%.5f, %.5f", destLat, destLng);
            tvTo.setText(getString(R.string.toText, toText));

            // Add the link to open Map
            tvTo.setOnClickListener(v -> openMap(destLat, destLng));
            tvTo.setEnabled(true);
        } else {
            tvTo.setText(getString(R.string.toText, "Unknown"));
            tvTo.setEnabled(false);
            tvTo.setOnClickListener(null);
        }
    }

    private void openMap(Double lat, Double lng) {
        String uri = "geo:" + lat + "," + lng + "?q=" + lat + "," + lng;
        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri));

        // Verify there is an app that can handle it
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, "No map app found", Toast.LENGTH_SHORT).show();
        }
    }
}