package edu.msu.cse476.msucompanion;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SessionActivity extends AppCompatActivity{
    private EditText destinationEditText;
    private EditText buddyNameEditText;
    private EditText buddyPhoneEditText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);

        destinationEditText = findViewById(R.id.destinationInput);
        buddyNameEditText = findViewById(R.id.buddyNameInput);
        buddyPhoneEditText = findViewById(R.id.buddyPhoneInput);

        TextView goBack = findViewById(R.id.goBackFromSession);
        goBack.setPaintFlags(goBack.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
    }

    public void onGoBackToMain(View view) {
        Intent intent = new Intent(SessionActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    public void onStartSess(View view) {
        String destination = destinationEditText.getText().toString().trim();
        String buddyName = buddyNameEditText.getText().toString().trim();
        String buddyPhone = buddyPhoneEditText.getText().toString().trim();

        if (destination.isEmpty() || buddyName.isEmpty() || buddyPhone.isEmpty()) {
            Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        //actual tracking
        String msg = "Session to " + destination + " started. Notifying " + buddyName;
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(SessionActivity.this, CurrentSessionActivity.class);
        intent.putExtra("destination", destination);
        intent.putExtra("buddyName", buddyName);
        intent.putExtra("buddyPhone", buddyPhone);
        startActivity(intent);

    }


}
