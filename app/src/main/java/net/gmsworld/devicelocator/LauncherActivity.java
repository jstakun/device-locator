package net.gmsworld.devicelocator;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import net.gmsworld.devicelocator.utilities.PreferencesUtils;

public class LauncherActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String action = null;
        if (getIntent() != null) {
            action = getIntent().getAction();
        }

        Intent showIntent;

        if (PinActivity.isAuthRequired(new PreferencesUtils(this))) {
            showIntent = new Intent(this, PinActivity.class);
        } else {
            showIntent = new Intent(this, MainActivity.class);
        }

        if (action != null) {
            showIntent.setAction(action);
        }

        startActivity(showIntent);
        finish();
    }

    @Override
    public void onPause() {
        super.onPause();
        finish();
    }
}
