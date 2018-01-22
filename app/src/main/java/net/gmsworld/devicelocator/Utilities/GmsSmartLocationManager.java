package net.gmsworld.devicelocator.Utilities;

import android.content.Context;
import android.location.Location;
import android.os.Handler;

import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.config.LocationParams;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

/**
 * Created by jstakun on 8/7/17.
 */

public class GmsSmartLocationManager extends AbstractLocationManager implements OnLocationUpdatedListener {

    //singleton
    private static final GmsSmartLocationManager instance = new GmsSmartLocationManager();

    private GmsSmartLocationManager() {

    }

    public static GmsSmartLocationManager getInstance() {
        return instance;
    }

    //

    public void enable(String handlerName, Handler locationHandler, Context context, int radius, int priority, boolean resetRoute) {

        LocationParams config = (priority <= 0) ? LocationParams.BEST_EFFORT : LocationParams.NAVIGATION;

        SmartLocation.with(context).location(new LocationGooglePlayServicesWithFallbackProvider(context))
                .config(config)
                .start(this);

        isEnabled = true;

        init(handlerName, locationHandler, context, radius, priority, resetRoute);
    }

    public void disable(String handlerName, Context context) {

        SmartLocation.with(context).location().stop();

        isEnabled = false;

        finish(handlerName);
    }

    @Override
    public void onLocationUpdated(Location location) {

        onLocationReceived(location);
    }

    public static boolean isLocationEnabled(Context context) {
        return SmartLocation.with(context).location().state().locationServicesEnabled();
    }
}
