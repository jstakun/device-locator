package net.gmsworld.devicelocator.broadcastreceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import net.gmsworld.devicelocator.services.RouteTrackingService;
import net.gmsworld.devicelocator.services.ScreenStatusService;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.RouteTrackingServiceUtils;

/**
 * Created by jstakun on 5/7/17.
 */

public class ServiceRestartReceiver extends BroadcastReceiver {

    private static final String TAG = ServiceRestartReceiver.class.getSimpleName();

    private int radius = RouteTrackingService.DEFAULT_RADIUS;
    private boolean motionDetectorRunning = false;
    private RouteTrackingService.Mode mode;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "RouteTrackingService will be restarted if needed...");
        restoreSavedData(context);
        if (motionDetectorRunning) {
            if (Permissions.haveLocationPermission(context)) {
                RouteTrackingServiceUtils.startRouteTrackingService(context, null, radius, null, false, mode);
            } else {
                Log.e(TAG, "Unable to start route tracking service due to lack of Location permission");
            }
        } else {
            Log.d(TAG, "No need to restart RouteTrackingService.");
        }
        //TODO restart screen status service if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(new Intent(context, ScreenStatusService.class));
        } else {
            context.startService(new Intent(context, ScreenStatusService.class));
        }
    }

    private void restoreSavedData(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        this.motionDetectorRunning = settings.getBoolean("motionDetectorRunning", false);
        this.radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
        String modeName = settings.getString("motionDetectorRunning", "Normal");
        mode = RouteTrackingService.Mode.valueOf(modeName);
    }
}
