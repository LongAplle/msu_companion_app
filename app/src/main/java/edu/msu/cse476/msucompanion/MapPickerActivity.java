package edu.msu.cse476.msucompanion;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;

import java.util.Arrays;
import java.util.List;

public class MapPickerActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap googleMap;
    private TextView tvSelectedPlace;
    private String selectedPlaceName;
    private double selectedLat;
    private double selectedLng;
    private boolean hasSelection = false;

    private final ActivityResultLauncher<Intent> autocompleteLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Place place = Autocomplete.getPlaceFromIntent(result.getData());

                            String displayName = place.getDisplayName();
                            if (displayName == null) {
                                displayName = place.getFormattedAddress();
                            }
                            if (displayName == null) {
                                displayName = "Selected destination";
                            }

                            if (place.getLocation() != null) {
                                selectedPlaceName = displayName;
                                selectedLat = place.getLocation().latitude;
                                selectedLng = place.getLocation().longitude;
                                hasSelection = true;

                                updateMapSelection(new LatLng(selectedLat, selectedLng), selectedPlaceName);
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_picker);

        tvSelectedPlace = findViewById(R.id.tvSelectedPlace);
        Button btnSearchPlace = findViewById(R.id.btnSearchPlace);
        Button btnUseSelectedLocation = findViewById(R.id.btnUseSelectedLocation);

        // Initialize Places SDK once before using autocomplete
        String apiKey = BuildConfig.PLACES_API_KEY;
        if (TextUtils.isEmpty(apiKey) || "DEFAULT_API_KEY".equals(apiKey)) {
            Toast.makeText(this, "Missing Places API key", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(getApplicationContext(), apiKey);
        }

        FragmentManager fm = getSupportFragmentManager();
        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        fm.beginTransaction()
                .replace(R.id.mapContainer, mapFragment)
                .commit();
        mapFragment.getMapAsync(this);

        btnSearchPlace.setOnClickListener(v -> openAutocomplete());

        btnUseSelectedLocation.setOnClickListener(v -> {
            if (!hasSelection) {
                Toast.makeText(this, "Please select a location first", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent resultIntent = new Intent();
            resultIntent.putExtra("destination_name", selectedPlaceName);
            resultIntent.putExtra("destination_lat", selectedLat);
            resultIntent.putExtra("destination_lng", selectedLng);
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        });
    }

    private void openAutocomplete() {
        List<Place.Field> fields = Arrays.asList(
                Place.Field.DISPLAY_NAME,
                Place.Field.FORMATTED_ADDRESS,
                Place.Field.LOCATION
        );

        Intent intent = new Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY,
                fields
        ).build(this);

        autocompleteLauncher.launch(intent);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;

        LatLng msu = new LatLng(42.7284, -84.4834);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(msu, 14f));

        googleMap.setOnMapClickListener(latLng -> {
            selectedLat = latLng.latitude;
            selectedLng = latLng.longitude;
            selectedPlaceName = "Pinned location";
            hasSelection = true;

            updateMapSelection(latLng, selectedPlaceName);
        });
    }

    private void updateMapSelection(LatLng latLng, String title) {
        if (googleMap == null) return;

        googleMap.clear();
        googleMap.addMarker(new MarkerOptions().position(latLng).title(title));
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f));

        String selectText = "Selected: " + title + "\nLat: " + latLng.latitude + "\nLng: " + latLng.longitude;
        tvSelectedPlace.setText(selectText);
    }
}
