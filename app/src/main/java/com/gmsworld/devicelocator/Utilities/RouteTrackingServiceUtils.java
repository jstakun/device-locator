package com.gmsworld.devicelocator.Utilities;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;

import com.gmsworld.devicelocator.Services.RouteTrackingService;

/**
 * Created by jstakun on 5/9/17.
 */

public class RouteTrackingServiceUtils {

    private static final String TAG = RouteTrackingServiceUtils.class.getSimpleName();

    public static boolean startRouteTrackingService(Context context, ServiceConnection mConnection, int radius, String phoneNumber, String email, String telegramId, boolean resetRoute) {
        Intent routeTracingService = new Intent(context, RouteTrackingService.class);
        routeTracingService.putExtra("radius", radius);
        routeTracingService.putExtra("phoneNumber", phoneNumber);
        routeTracingService.putExtra("email", email);
        routeTracingService.putExtra("telegramId", telegramId);
        routeTracingService.putExtra("resetRoute", resetRoute);
        routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_START);
        context.startService(routeTracingService);
        if (mConnection != null) {
            return false; //this.bindService(routeTracingService, mConnection, Context.BIND_AUTO_CREATE);
        } else {
            return false;
        }
    }

    public static void stopRouteTrackingService(Context context, ServiceConnection mConnection, boolean isBound) {
        Intent routeTracingService = new Intent(context, RouteTrackingService.class);
        routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_STOP);
        unbindRouteTrackingService(context, mConnection, isBound);
        context.stopService(routeTracingService);
    }

    public static void resetRouteTrackingService(Context context, ServiceConnection mConnection, boolean isBound, int radius, String phoneNumber, String email, String telegramId) {
        Intent routeTracingService = new Intent(context, RouteTrackingService.class);
        routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_CONFIGURE);
        routeTracingService.putExtra("radius", radius);
        routeTracingService.putExtra("phoneNumber", phoneNumber);
        routeTracingService.putExtra("email", email);
        routeTracingService.putExtra("telegramId", telegramId);
        context.startService(routeTracingService);
    }

    public static void setHighGpsAccuracy(Context context) {
        Intent routeTracingService = new Intent(context, RouteTrackingService.class);
        routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_GPS_HIGH);
        context.startService(routeTracingService);
    }

    public static void setBalancedGpsAccuracy(Context context) {
        Intent routeTracingService = new Intent(context, RouteTrackingService.class);
        routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_GPS_BALANCED);
        context.startService(routeTracingService);
    }

    public static void unbindRouteTrackingService(Context context, ServiceConnection mConnection, boolean isBound) {
        if (isBound) {
            try {
                context.unbindService(mConnection);
            } catch (Exception e) {
                Log.d(TAG, e.getMessage(), e);
            }
        } else {
            Log.d(TAG, "RouteTrackingService is not bound to MainActivity");
        }
    }
}
