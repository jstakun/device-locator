package net.gmsworld.devicelocator.views;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import net.gmsworld.devicelocator.fragments.QuotaLimitNotificationDialogFragment;

import org.apache.commons.lang3.StringUtils;

import androidx.appcompat.app.AppCompatActivity;

public class QuotaResetDialogActivity extends AppCompatActivity {

    private static final String TAG = QuotaResetDialogActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null) {
            final String command = intent.getStringExtra("command");
            final String queryString = intent.getStringExtra("queryString");
            final String token = intent.getStringExtra("token");
            final String deviceName = intent.getStringExtra("deviceName");
            if (StringUtils.isNotEmpty(command) && StringUtils.isNotEmpty(queryString) && StringUtils.isNotEmpty(token) && StringUtils.isNotEmpty(deviceName)) {
                QuotaLimitNotificationDialogFragment frag = QuotaLimitNotificationDialogFragment.newInstance(StringUtils.capitalize(command), queryString, token, deviceName);
                frag.show(getSupportFragmentManager(), QuotaLimitNotificationDialogFragment.TAG);
            } else {
                Log.e(TAG, "Missing required parameters");
            }
        } else {
            Log.e(TAG, "Missing required parameters");
        }
    }
}
