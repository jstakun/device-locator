package net.gmsworld.devicelocator.broadcastreceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import net.gmsworld.devicelocator.services.ScreenStatusService;
import net.gmsworld.devicelocator.utilities.LocationAlarmUtils;

import org.apache.commons.lang3.StringUtils;

public class BootReceiver extends BroadcastReceiver {

    private final static String TAG = BootReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (StringUtils.equals(intent.getAction(), "android.intent.action.BOOT_COMPLETED")) {
            Log.d(TAG, "Received Boot Broadcast");
            LocationAlarmUtils.initWhenDown(context, false);

            //TODO testing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(new Intent(context, ScreenStatusService.class));
            } else {
                context.startService(new Intent(context, ScreenStatusService.class));
            }
        } else if (StringUtils.equals(intent.getAction(), "android.intent.action.ACTION_SHUTDOWN")) {
            Log.d(TAG, "Received Shutdown Broadcast");
        }
    }
}
