package net.gmsworld.devicelocator.broadcastreceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.services.RouteTrackingService;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.RouteTrackingServiceUtils;

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
            if (Permissions.haveLocationPermission(context)) {
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, phoneNumber, email, telegramId, null, false, RouteTrackingService.Mode.Normal);
            } else {
                Log.e(TAG, "Unable to start route tracking service due to lack of Location permission");
            }
        } else {
            Log.d(TAG, "No need to restart RouteTrackingService.");
        }
    }

    private void restoreSavedData(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        this.motionDetectorRunning = settings.getBoolean("motionDetectorRunning", false);
        this.radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
        this.phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
        this.email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
        this.telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
    }
}
