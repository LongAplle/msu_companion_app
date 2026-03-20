package edu.msu.cse476.msucompanion;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class CurrentSessionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_current_session);
    }

    public void onCancelSession(View view) { //goes back to main activity FOR NOW
        Intent intent = new Intent(CurrentSessionActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

}
