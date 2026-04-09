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

public class StartSessionActivity extends AppCompatActivity {
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
                            selectedDestinationName = result.getData().getStringExtra(Keys.EXTRA_DESTINATION_NAME);
                            selectedDestinationLat = result.getData().getDoubleExtra(Keys.EXTRA_DESTINATION_LAT, 0.0);
                            selectedDestinationLng = result.getData().getDoubleExtra(Keys.EXTRA_DESTINATION_LNG, 0.0);
                            hasSelectedDestination = true;

                            if (selectedDestinationName != null) {
                                destinationEditText.setText(selectedDestinationName);
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_session);

        destinationEditText = findViewById(R.id.destinationInput);
        destinationEditText.setOnClickListener( v -> onOpenMapPicker());

        TextView goBack = findViewById(R.id.goBackFromSession);
        if (goBack != null) {
            goBack.setPaintFlags(goBack.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        }
    }

    public void onOpenMapPicker() {
        Intent intent = new Intent(this, MapPickerActivity.class);
        mapPickerLauncher.launch(intent);
    }

    // Go back to main
    public void onGoBack(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    public void onStartSession(View view) {

        if (!hasSelectedDestination) {
            Toast.makeText(this, "Please select a destination from the map", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, WalkSessionActivity.class);
        intent.putExtra(Keys.EXTRA_DESTINATION_NAME, selectedDestinationName);
        intent.putExtra(Keys.EXTRA_DESTINATION_LAT, selectedDestinationLat);
        intent.putExtra(Keys.EXTRA_DESTINATION_LNG, selectedDestinationLng);
        intent.putExtra(Keys.EXTRA_START_NEW_SESSION, true);
        startActivity(intent);
        finish();
    }
}