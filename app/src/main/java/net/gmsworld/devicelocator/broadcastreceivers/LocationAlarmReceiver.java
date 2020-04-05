package net.gmsworld.devicelocator.broadcastreceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.services.SmsSenderService;
import net.gmsworld.devicelocator.utilities.LocationAlarmUtils;
import net.gmsworld.devicelocator.utilities.Permissions;

public class LocationAlarmReceiver extends BroadcastReceiver {

    private final static String TAG = "LocationAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received broadcast...");

        if (!Permissions.haveLocationPermission(context)) {
            Log.d(TAG, "Location permission is missing. No location update will be sent.");
        } else {
            Bundle extras = new Bundle();
            extras.putString("telegramId", context.getString(R.string.telegram_notification));
            SmsSenderService.initService(context, true, true, true, null, null, null, null, extras);
        }

        LocationAlarmUtils.initWhenDown(context, true);
    }
}
