package com.gmsworld.devicelocator.BroadcastReceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.gmsworld.devicelocator.Services.RouteTrackingService;
import com.gmsworld.devicelocator.Utilities.RouteTrackingServiceUtils;

/**
 * Created by jstakun on 5/7/17.
 */

public class RouteTrackingServiceRestartReceiver extends BroadcastReceiver {

    private static final String TAG = RouteTrackingServiceRestartReceiver.class.getSimpleName();

    private String phoneNumber, email, telegramId;
    private int radius = RouteTrackingService.DEFAULT_RADIUS;
    private boolean motionDetectorRunning = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "RouteTrackingService will be restarted if needed...");
        restoreSavedData(context);
        if (motionDetectorRunning) {
            RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, false);
        } else {
            Log.d(TAG, "No need to restart RouteTrackingService.");
        }
    }

    private void restoreSavedData(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        this.motionDetectorRunning = settings.getBoolean("motionDetectorRunning", false);
        this.radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
        this.phoneNumber = settings.getString("phoneNumber", "");
        this.email = settings.getString("email", "");
        this.telegramId = settings.getString("telegramId", "");
    }
}
