package net.gmsworld.devicelocator.broadcastreceivers;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.services.SmsSenderService;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import androidx.core.content.ContextCompat;

/**
 * Created by jstakun on 8/24/17.
 */

public class DeviceAdminEventReceiver extends DeviceAdminReceiver {

    private static final String TAG = DeviceAdminEventReceiver.class.getSimpleName();

    public static final String SOURCE = "LoginFailed";

    public static final String DEVICE_ADMIN_ENABLED = "loginTracker";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "Device admin enabled");
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(DEVICE_ADMIN_ENABLED, true).putBoolean("allowReset", true).apply();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.d(TAG, "Device admin disabled");
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(DEVICE_ADMIN_ENABLED, false).putBoolean("allowReset", false).apply();
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
        Log.d(TAG, "Wrong password has been entered to unlock this device. SENDING NOTIFICATION!");
        PreferencesUtils settings = new PreferencesUtils(context);
        if (!SmsSenderService.initService(context, true, true, true, null, null, null, SOURCE, null)) {
            Log.d(TAG, context.getString(R.string.notifiers_error));
        }

        if (settings.getBoolean(HiddenCaptureImageService.STATUS, false) && HiddenCaptureImageService.isNotBusy()) {
            Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
            //context.startForegroundService(cameraIntent);
            ContextCompat.startForegroundService(context, cameraIntent);
        } else {
            Log.d(TAG, "Camera is currently unavailable. No photo will be taken");
        }
    }

    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        super.onPasswordSucceeded(context, intent);
        Log.d(TAG, "Correct password has been entered to unlock this device.");
    }

}
