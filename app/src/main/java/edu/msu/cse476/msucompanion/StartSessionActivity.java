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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AlertDialog;
import java.util.ArrayList;
import java.util.List;

public class StartSessionActivity extends AppCompatActivity {
    private AppDatabase db;
    private String currUserId;

    private EditText destinationEditText;
    private String selectedDestinationName;
    private double selectedDestinationLat;
    private double selectedDestinationLng;
    private boolean hasSelectedDestination = false;

    private TextView selectedContactsSummary;
    private final ArrayList<Long> selectedContactIds = new ArrayList<>();
    private boolean useAllContacts = true;

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

        db = AppDatabase.getInstance(this);

        SharedPreferences prefs = getSharedPreferences(Keys.PREF_USER, Context.MODE_PRIVATE);
        currUserId = prefs.getString(Keys.PREF_USER_ID, null);
        if (currUserId == null) {
            finish();
            return;
        }

        selectedContactsSummary = findViewById(R.id.selectedContactsSummary);
        destinationEditText = findViewById(R.id.destinationInput);
        destinationEditText.setOnClickListener( v -> onOpenMapPicker());

        // Restore data if we are recovering from a rotation
        if (savedInstanceState != null) {
            useAllContacts = savedInstanceState.getBoolean(Keys.STATE_USE_ALL_CONTACTS, true);

            ArrayList<Long> restoredIds = (ArrayList<Long>) savedInstanceState.getSerializable(Keys.STATE_SELECTED_CONTACT_IDS);
            if (restoredIds != null) {
                selectedContactIds.clear();
                selectedContactIds.addAll(restoredIds);
            }

            updateSelectedContactsSummary();

            hasSelectedDestination = savedInstanceState.getBoolean(Keys.STATE_HAS_SELECTION);
            if (hasSelectedDestination) {
                selectedDestinationName = savedInstanceState.getString(Keys.STATE_DEST_NAME);
                selectedDestinationLat = savedInstanceState.getDouble(Keys.STATE_DEST_LAT);
                selectedDestinationLng = savedInstanceState.getDouble(Keys.STATE_DEST_LNG);

                if (selectedDestinationName != null) {
                    destinationEditText.setText(selectedDestinationName);
                }
            }
        }

        TextView goBack = findViewById(R.id.goBackFromSession);
        goBack.setPaintFlags(goBack.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(Keys.STATE_HAS_SELECTION, hasSelectedDestination);
        if (hasSelectedDestination) {
            outState.putString(Keys.STATE_DEST_NAME, selectedDestinationName);
            outState.putDouble(Keys.STATE_DEST_LAT, selectedDestinationLat);
            outState.putDouble(Keys.STATE_DEST_LNG, selectedDestinationLng);
        }

        outState.putBoolean(Keys.STATE_USE_ALL_CONTACTS, useAllContacts);
        outState.putSerializable(Keys.STATE_SELECTED_CONTACT_IDS, selectedContactIds);
    }

    public void onOpenMapPicker() {
        Intent intent = new Intent(this, MapPickerActivity.class);
        mapPickerLauncher.launch(intent);
    }

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

        if (!useAllContacts && selectedContactIds.isEmpty()) {
            Toast.makeText(this, "Please select at least one trusted contact or choose Select all contacts", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, WalkSessionActivity.class);
        intent.putExtra(Keys.EXTRA_DESTINATION_NAME, selectedDestinationName);
        intent.putExtra(Keys.EXTRA_DESTINATION_LAT, selectedDestinationLat);
        intent.putExtra(Keys.EXTRA_DESTINATION_LNG, selectedDestinationLng);
        intent.putExtra(Keys.EXTRA_START_NEW_SESSION, true);

        intent.putExtra(Keys.EXTRA_USE_ALL_CONTACTS, useAllContacts);

        long[] selectedIdsArray = new long[selectedContactIds.size()];
        for (int i = 0; i < selectedContactIds.size(); i++) {
            selectedIdsArray[i] = selectedContactIds.get(i);
        }
        intent.putExtra(Keys.EXTRA_SELECTED_CONTACT_IDS, selectedIdsArray);

        startActivity(intent);
        finish();
    }

    public void onSelectAllContacts(View view) {
        useAllContacts = true;
        selectedContactIds.clear();
        updateSelectedContactsSummary();
    }

    public void onSelectTrustedContacts(View view) {
        new Thread(() -> {
            List<Contact> contacts = db.contactDao().getContactsForUser(currUserId);

            runOnUiThread(() -> {
                if (contacts == null || contacts.isEmpty()) {
                    Toast.makeText(this, "No saved contacts found", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] names = new String[contacts.size()];
                boolean[] checkedItems = new boolean[contacts.size()];

                for (int i = 0; i < contacts.size(); i++) {
                    Contact contact = contacts.get(i);
                    names[i] = contact.getName() + " (" + contact.getPhoneNumber() + ")";
                    checkedItems[i] = selectedContactIds.contains(contact.getId());
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Select trusted contacts");

                // snapshot before showing dialog
                ArrayList<Long> tempSelection = new ArrayList<>(selectedContactIds);
                builder.setMultiChoiceItems(names, checkedItems, (dialog, which, isChecked) -> {
                    long contactId = contacts.get(which).getId();
                    if (isChecked) {
                        if (!tempSelection.contains(contactId)) tempSelection.add(contactId);
                    } else {
                        tempSelection.remove(contactId);
                    }
                });

                builder.setPositiveButton("Done", (dialog, which) -> {
                    selectedContactIds.clear();
                    selectedContactIds.addAll(tempSelection);
                    useAllContacts = false;
                    updateSelectedContactsSummary();
                });

                builder.setNeutralButton("Select all", (dialog, which) -> onSelectAllContacts(null));

                builder.setNegativeButton("Cancel", null);
                builder.show();
            });
        }).start();
    }

    private void updateSelectedContactsSummary() {
        if (selectedContactsSummary == null) return;

        if (useAllContacts) {
            selectedContactsSummary.setText(getString(R.string.summaryAllContacts));
        } else if (selectedContactIds.isEmpty()) {
            selectedContactsSummary.setText(getString(R.string.summaryNoContacts));
        } else {
            int selectedCount = selectedContactIds.size();
            String text = selectedCount != 1 ?
                    getString(R.string.summarySelectedContacts, selectedCount) :
                    getString(R.string.summary1SelectedContact);
            selectedContactsSummary.setText(text);
        }
    }
}


