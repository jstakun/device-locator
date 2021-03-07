package net.gmsworld.devicelocator;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import net.gmsworld.devicelocator.fragments.NotificationActivationDialogFragment;
import net.gmsworld.devicelocator.services.SmsSenderService;
import net.gmsworld.devicelocator.utilities.LocationAlarmUtils;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.NotificationUtils;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;

public class LauncherActivity extends Activity {

    private static final String TAG = LauncherActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Uri appLinkData = null;
        String action = null;

        if (intent != null) {
            action = intent.getAction();
            appLinkData = intent.getData();
        }

        PreferencesUtils settings = new PreferencesUtils(this);

        Intent showIntent = null;

        boolean isEmailVerified = Messenger.isEmailVerified(settings);

        if (!isEmailVerified && StringUtils.equals(action, Intent.ACTION_VIEW) && appLinkData != null){
            //email verification request
            final String secret = appLinkData.getLastPathSegment();
            if (StringUtils.equals(secret, settings.getString(NotificationActivationDialogFragment.EMAIL_SECRET))) {
                action = RegisterActivity.ACTION_VERIFY;
                Log.d(TAG, "Sending VERIFY action to Register Activity");
            } else {
                action = RegisterActivity.ACTION_INVALID;
                Log.d(TAG, "Sending INVALID action to Register Activity");
            }
            showIntent = new Intent(this, RegisterActivity.class);
        }

        if (showIntent == null) {
            if (!isEmailVerified) {
                showIntent = new Intent(this, RegisterActivity.class);
            } else if (PinActivity.isAuthRequired(settings)) {
                showIntent = new Intent(this, PinActivity.class);
            } else {
                showIntent = new Intent(this, MainActivity.class);
            }
        }

        if (action != null) {
            showIntent.setAction(action);
        }

        startActivity(showIntent);

        //Sending device location after 1 hour of inactivity
        if (Permissions.haveLocationPermission(this) && System.currentTimeMillis() - settings.getLong(Messenger.LOCATION_SENT_MILLIS) > (1000 * 60 * 60)) {
            Log.d(TAG, "Sending device location after long time inactivity");
            Bundle extras = new Bundle();
            extras.putString("telegramId", getString(R.string.telegram_notification));
            SmsSenderService.initService(this, false, false, true, null, null, null, null, extras);
        } else if (isEmailVerified && !Permissions.haveLocationPermission(this)) {
            NotificationUtils.showLocationPermissionNotification(this);
        } else if (isEmailVerified) {
            Log.d(TAG, "Device location has been sent less than 1 hour ago");
        } else {
            Log.d(TAG, "Waiting for email registration");
        }

        //enable location sharing by default
        if (!settings.contains(LocationAlarmUtils.ALARM_SETTINGS)) {
            settings.setBoolean(LocationAlarmUtils.ALARM_SETTINGS, true);
            settings.setBoolean(LocationAlarmUtils.ALARM_SILENT, true);
        }

        finish();
    }

    @Override
    public void onPause() {
        super.onPause();
        finish();
    }
}
