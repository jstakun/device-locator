package net.gmsworld.devicelocator.broadcastreceivers;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.services.SmsSenderService;

import org.apache.commons.lang3.StringUtils;

/**
 * Created by jstakun on 8/24/17.
 */

public class DeviceAdminEventReceiver extends DeviceAdminReceiver {

    private static final String TAG = DeviceAdminEventReceiver.class.getSimpleName();

    public static final String SOURCE = "LoginFailed";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "Device admin enabled");
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("loginTracker", true).apply();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.d(TAG, "Device admin disabled");
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("loginTracker", false).apply();
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
        Log.d(TAG, "Wrong password has been entered to unlock this device. SENDING NOTIFICATION!");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
        String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
        String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");

        if (StringUtils.isNotEmpty(phoneNumber) || StringUtils.isNotEmpty(telegramId) || StringUtils.isNotEmpty(email)) {
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra("phoneNumber", phoneNumber);
            newIntent.putExtra("email", email);
            newIntent.putExtra("telegramId", telegramId);
            newIntent.putExtra("source", SOURCE);
            ContextCompat.startForegroundService(context, newIntent);
            //context.startService(newIntent);
        } else {
            Log.d(TAG, "Unable to send notification. No notifiers are set.");
        }

        if (settings.getBoolean("hiddenCamera", false)) {
            Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
            //context.startService(cameraIntent);
            ContextCompat.startForegroundService(context, cameraIntent);
        } else {
            Log.d(TAG, "Camera is disable. No photo will be taken");
        }
    }

    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        super.onPasswordSucceeded(context, intent);
        Log.d(TAG, "Correct password has been entered to unlock this device.");
    }

}
