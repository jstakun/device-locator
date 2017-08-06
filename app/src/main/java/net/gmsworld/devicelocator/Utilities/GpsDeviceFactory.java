package net.gmsworld.devicelocator.Utilities;

/**
 * Created by jstakun on 8/5/17.
 */

import android.content.Context;
import android.os.Handler;
import android.util.Log;

/**
 *
 * @author jstakun
 */
public class GpsDeviceFactory {
    private static final String TAG = GpsDeviceFactory.class.getSimpleName();
    private static AndroidDevice device = null;

    public static AndroidDevice initGpsDevice(Context context) {
        if (device == null) {
            Log.d(TAG, "Starting GPS listener...");
            device = new AndroidDevice(context);
        }
        return device;
    }

    public static AndroidDevice initGpsDevice(Context context, Handler positionHandler) {
        initGpsDevice(context);
        device.setPositionHandler(positionHandler);
        return device;
    }

    public static void stopDevice(String handler) {
        if (device != null) {
            device.stopListening(handler);
        }
    }

    public static void startDevice(Context context, int radius, int priority, boolean resetRoute) {
        if (device != null) {
            device.startListening(context, radius, priority, resetRoute);
        }
    }

    public static void resetDevice() {
        device = null;
    }
}

