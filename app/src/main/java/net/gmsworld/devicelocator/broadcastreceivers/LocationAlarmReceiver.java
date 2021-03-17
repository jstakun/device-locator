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
        Log.d(TAG, "Received Location Alarm broadcast...");
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
                NotificationUtils.showLocationPermissionNotification(context);
            }
        } else if (System.currentTimeMillis() - settings.getLong(Messenger.LOCATION_SENT_MILLIS) > (1000 * 60 * 60 * 24)) {
            //periodical location sharing is disabled
            if (Permissions.haveLocationPermission(context)) {
                NotificationUtils.showSavedLocationNotification(context);
            } else {
                NotificationUtils.showLocationPermissionNotification(context);
            }
        } else {
            Log.d(TAG, "No need to send location update.");
        }

        LocationAlarmUtils.initWhenDown(context, true);
    }
}
