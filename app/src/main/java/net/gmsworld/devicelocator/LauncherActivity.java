package net.gmsworld.devicelocator;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import org.apache.commons.lang3.StringUtils;

public class LauncherActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        final String pin = settings.getString(PinActivity.DEVICE_PIN, "");
        final long pinVerificationMillis = settings.getLong("pinVerificationMillis", 0);
        final boolean settingsVerifyPin = settings.getBoolean("settings_verify_pin", false);

        Intent showIntent;

        if (StringUtils.isNotEmpty(pin) && settingsVerifyPin && System.currentTimeMillis() - pinVerificationMillis > PinActivity.PIN_VALIDATION_MILLIS) {
            showIntent = new Intent(this, PinActivity.class);
        } else {
            showIntent = new Intent(this, MainActivity.class);
        }

        startActivity(showIntent);
    }

    @Override
    public void onPause() {
        super.onPause();
        finish();
    }
}
