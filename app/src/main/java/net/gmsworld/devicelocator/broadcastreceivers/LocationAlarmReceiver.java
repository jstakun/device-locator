package net.gmsworld.devicelocator.broadcastreceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.services.SmsSenderService;
import net.gmsworld.devicelocator.utilities.LocationAlarmUtils;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.NotificationUtils;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

public class LocationAlarmReceiver extends BroadcastReceiver {

    private final static String TAG = "LocationAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received Location Alarm intent ...");
        PreferencesUtils settings = new PreferencesUtils(context);
        if (settings.getBoolean(LocationAlarmUtils.ALARM_SETTINGS, false)) {
            //periodical location sharing is enabled
            if (Permissions.haveLocationPermission(context)) {
                Log.d(TAG, "Sending location update.");
                Bundle extras = new Bundle();
                extras.putString("adminTelegramId", context.getString(R.string.telegram_notification));
                if (settings.getBoolean(LocationAlarmUtils.ALARM_SILENT, false)) {
                    extras.putString("email", null);
                }
                SmsSenderService.initService(context, true, true, true, null, null, null, null, extras);
            } else {
                if (Messenger.isEmailVerified(settings)) {
                    NotificationUtils.showLocationPermissionNotification(context);
                } else {
                    NotificationUtils.showRegistrationNotification(context);
                }
            }
        } else if (LocationAlarmUtils.initNow(context, settings)) {
            Log.d(TAG, "Location update notification shown now.");
        } else {
            Log.d(TAG, "No need to send location update.");
        }

        LocationAlarmUtils.initWhenDown(context, true);
    }
}
