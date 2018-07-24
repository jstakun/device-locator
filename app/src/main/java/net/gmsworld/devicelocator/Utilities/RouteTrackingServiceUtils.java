package net.gmsworld.devicelocator.Utilities;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.Services.RouteTrackingService;

import org.apache.commons.lang3.StringUtils;

/**
 * Created by jstakun on 5/9/17.
 */

public class RouteTrackingServiceUtils {

    private static final String TAG = RouteTrackingServiceUtils.class.getSimpleName();

    public static boolean startRouteTrackingService(Context context, ServiceConnection mConnection, int radius, String phoneNumber, String email, String telegramId, boolean resetRoute, boolean silentMode) {
        Intent routeTracingService = new Intent(context, RouteTrackingService.class);
        routeTracingService.putExtra("radius", radius);
        routeTracingService.putExtra("phoneNumber", phoneNumber);
        routeTracingService.putExtra("email", email);
        routeTracingService.putExtra("telegramId", telegramId);
        routeTracingService.putExtra("resetRoute", resetRoute);
        routeTracingService.putExtra("silentMode", silentMode);
        routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_START);
        context.startService(routeTracingService);
        return mConnection != null && context.bindService(routeTracingService, mConnection, Context.BIND_AUTO_CREATE);
    }

    public static void stopRouteTrackingService(Context context, ServiceConnection mConnection, boolean isBound, boolean shareRoute, String title, String phoneNumber, String email, String telegramId) {
        Intent routeTracingService = new Intent(context, RouteTrackingService.class);
        if (!shareRoute) {
            routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_STOP);
        } else {
            routeTracingService.putExtra(RouteTrackingService.COMMAND, RouteTrackingService.COMMAND_STOP_SHARE);
            routeTracingService.putExtra("phoneNumber", phoneNumber);
            routeTracingService.putExtra("email", email);
            routeTracingService.putExtra("telegramId", telegramId);
            routeTracingService.putExtra("title", title);
        }
        unbindRouteTrackingService(context, mConnection, isBound);
        context.startService(routeTracingService);
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

    public static void setGpsAccuracy(Context context, int command) {
        if (GmsSmartLocationManager.getInstance().isEnabled()) {
            Intent routeTracingService = new Intent(context, RouteTrackingService.class);
            routeTracingService.putExtra(RouteTrackingService.COMMAND, command);
            context.startService(routeTracingService);
        }
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

    public static String getRouteId(Context context) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String title = settings.getString("routeTitle", "");

        if (StringUtils.isEmpty(title)) {
            title = "device_locator_route_" + Messenger.getDeviceId(context, false) + "_" + System.currentTimeMillis();
            settings.edit().putString("routeTitle", title).apply();
        }

        return title;
    }

    public static String getRouteUrl(Context context) {
        String routeId = getRouteId(context);
        String[] tokens = StringUtils.split(routeId, "_");
        if (tokens.length == 5) {
            return context.getString(R.string.showRouteUrl) + "/" + tokens[3] + "/" + tokens[4];
        } else {
            return context.getString(R.string.showRouteUrl) + "/" + routeId;
        }
    }
}
