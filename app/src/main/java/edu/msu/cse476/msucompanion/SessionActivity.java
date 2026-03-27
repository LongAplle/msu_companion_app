package edu.msu.cse476.msucompanion;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class SessionActivity extends AppCompatActivity {

    private EditText destinationEditText;
    private String selectedDestinationName;
    private double selectedDestinationLat;
    private double selectedDestinationLng;
    private boolean hasSelectedDestination = false;

    private final ActivityResultLauncher<Intent> mapPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            selectedDestinationName = result.getData().getStringExtra("destination_name");
                            selectedDestinationLat = result.getData().getDoubleExtra("destination_lat", 0.0);
                            selectedDestinationLng = result.getData().getDoubleExtra("destination_lng", 0.0);
                            hasSelectedDestination = true;

                            if (selectedDestinationName != null) {
                                destinationEditText.setText(selectedDestinationName);
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);

        destinationEditText = findViewById(R.id.destinationInput);
        destinationEditText.setOnClickListener( v -> onOpenMapPicker());

        TextView goBack = findViewById(R.id.goBackFromSession);
        goBack.setPaintFlags(goBack.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
    }

    public void onOpenMapPicker() {
        Intent intent = new Intent(SessionActivity.this, MapPickerActivity.class);
        mapPickerLauncher.launch(intent);
    }

    public void onGoBackToMain(View view) {
        Intent intent = new Intent(SessionActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    public void onStartSession(View view) {

        if (!hasSelectedDestination) {
            Toast.makeText(this, "Please select a destination from the map", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(SessionActivity.this, WalkSessionActivity.class);
        intent.putExtra("destination_name", selectedDestinationName);
        intent.putExtra("destination_lat", selectedDestinationLat);
        intent.putExtra("destination_lng", selectedDestinationLng);
        startActivity(intent);
    }
}