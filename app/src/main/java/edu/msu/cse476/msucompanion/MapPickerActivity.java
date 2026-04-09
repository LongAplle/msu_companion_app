package edu.msu.cse476.msucompanion;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
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
import java.util.Locale;

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

        if (savedInstanceState != null) {
            hasSelection = savedInstanceState.getBoolean(Keys.STATE_HAS_SELECTION);
            if (hasSelection) {
                selectedPlaceName = savedInstanceState.getString(Keys.STATE_DEST_NAME);
                selectedLat = savedInstanceState.getDouble(Keys.STATE_DEST_LAT);
                selectedLng = savedInstanceState.getDouble(Keys.STATE_DEST_LNG);
            }
        }

        // Initialize Places SDK once before using autocomplete
        String apiKey = BuildConfig.PLACES_API_KEY;
        if (TextUtils.isEmpty(apiKey) || "DEFAULT_API_KEY".equals(apiKey)) {
            Toast.makeText(this, "Missing Places API key", Toast.LENGTH_SHORT).show();
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
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(Keys.STATE_HAS_SELECTION, hasSelection);
        if (hasSelection) {
            outState.putString(Keys.STATE_DEST_NAME, selectedPlaceName);
            outState.putDouble(Keys.STATE_DEST_LAT, selectedLat);
            outState.putDouble(Keys.STATE_DEST_LNG, selectedLng);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;

        if (hasSelection) {
            updateMapSelection(new LatLng(selectedLat, selectedLng), selectedPlaceName);
        } else {
            // Default MSU view
            LatLng msu = new LatLng(42.7284, -84.4834);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(msu, 14f));
        }

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

        String toText = String.format(Locale.US, "%.8f, %.8f", latLng.latitude, latLng.longitude);
        tvSelectedPlace.setText(getString(R.string.selectDestinationText, title, toText));
    }

    public void onOpenAutocomplete(View view) {
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

    public void onSelectLocation(View view) {
        if (!hasSelection) {
            Toast.makeText(this, "Please select a location first", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent resultIntent = new Intent();
        resultIntent.putExtra(Keys.EXTRA_DESTINATION_NAME, selectedPlaceName);
        resultIntent.putExtra(Keys.EXTRA_DESTINATION_LAT, selectedLat);
        resultIntent.putExtra(Keys.EXTRA_DESTINATION_LNG, selectedLng);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }
}
