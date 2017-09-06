package net.gmsworld.devicelocator.BroadcastReceivers;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import net.gmsworld.devicelocator.Services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.Services.SmsSenderService;

/**
 * Created by jstakun on 8/24/17.
 */

public class DeviceAdminEventReceiver extends DeviceAdminReceiver {

    private static final String TAG = DeviceAdminEventReceiver.class.getSimpleName();

    public static final String SOURCE = "LoginFailed";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "Failed login tracker enabled");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.d(TAG, "Failed login tracker disabled");
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
        Log.d(TAG, "Wrong password has been entered to unlock this device. SENT NOTIFICATION!");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String email = settings.getString("email", "");;
        String phoneNumber = settings.getString("phoneNumber", "");
        String telegramId = settings.getString("telegramId", "");

        Intent newIntent = new Intent(context, SmsSenderService.class);
        newIntent.putExtra("notificationNumber", phoneNumber);
        newIntent.putExtra("email", email);
        newIntent.putExtra("telegramId", telegramId);
        newIntent.putExtra("source", SOURCE);
        context.startService(newIntent);

        Intent cameraIntent = new Intent(context, HiddenCaptureImageService.class);
        context.startService(cameraIntent);
    }

    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
        Log.d(TAG, "Correct password has been entered to unlock this device.");
    }

}
