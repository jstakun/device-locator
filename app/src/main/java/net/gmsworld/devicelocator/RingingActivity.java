package net.gmsworld.devicelocator;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class RingingActivity extends AppCompatActivity {

    private static final String TAG = RingingActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_ringing);

        final Button stopRingingButton = findViewById(R.id.stop_ringing_button);
        stopRingingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final PreferencesUtils prefs = new PreferencesUtils(RingingActivity.this);
                final String pin = prefs.getEncryptedString(PinActivity.DEVICE_PIN);
                Command.findCommandInMessage(RingingActivity.this, Command.RING_OFF_COMMAND + "app" + pin, null, null, null, null);
                finish();
             }
        });

        ViewCompat.setBackgroundTintList(stopRingingButton, ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
    }

    @Override
    public void onNewIntent(Intent intent) {
        //show tracker view
        Log.d(TAG, "onNewIntent()");
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                Log.d(TAG, "getIntent().getAction(): " + action);
                if (action.equals(Command.RING_OFF_COMMAND)) {
                    finish();
                } else if (action.equals(Command.RING_COMMAND)) {
                    //show this activity;
                    Log.e(TAG,"This should not happen!");
                }
            }
        }
    }
}
