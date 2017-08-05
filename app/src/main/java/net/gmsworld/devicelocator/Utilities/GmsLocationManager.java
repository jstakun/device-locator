package net.gmsworld.devicelocator.Utilities;

/**
 * Created by jstakun on 04.05.17.
 */

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;

import net.gmsworld.devicelocator.BroadcastReceivers.SmsReceiver;
import net.gmsworld.devicelocator.Model.Feature;
import net.gmsworld.devicelocator.Model.FeatureCollection;
import net.gmsworld.devicelocator.Model.Geometry;
import net.gmsworld.devicelocator.Model.Properties;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.Services.SmsSenderService;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Vector;

public class GmsLocationManager extends AbstractLocationManager implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = GmsLocationManager.class.getSimpleName();

    private static final int LOCATION_READ_INTERVAL_LOW = 10000; //ms
    private static final int LOCATION_READ_INTERVAL_HIGH = 5000; //ms

    private boolean isEnabled = false;

    private GoogleApiClient mGoogleApiClient;
    private static Map<String, Handler> mLocationHandlers = new HashMap<String, Handler>();
    private LocationRequest mLocationRequest;
    private Location recentLocationSent, lastLocation;

    private static final GmsLocationManager instance = new GmsLocationManager();

    private GmsLocationManager() {

    }

    public static GmsLocationManager getInstance() {
        return instance;
    }

    public void enable(String handlerName, Handler locationHandler, Context context, int radius, int priority, boolean resetRoute) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        buildGoogleApiClient(context);
        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected() && !mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.connect();
        }
        if (!isEnabled) {
            mLocationRequest = new LocationRequest();
            if (priority <= 0) {
                Log.d(TAG, "Balanced gps accuracy selected");
                mLocationRequest.setInterval(LOCATION_READ_INTERVAL_LOW);
                mLocationRequest.setFastestInterval(LOCATION_READ_INTERVAL_LOW);
                mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);//
            } else {
                Log.d(TAG, "High gps accuracy selected");
                mLocationRequest.setInterval(LOCATION_READ_INTERVAL_HIGH);
                mLocationRequest.setFastestInterval(LOCATION_READ_INTERVAL_HIGH);
                mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);//
            }
            isEnabled = true;
        }
        this.radius = radius;
        if (resetRoute) {
            Log.d(TAG, "Route has been cleared");
            Files.deleteFileFromContextDir(ROUTE_FILE, context);
        }
        callerContext = context;
        mLocationHandlers.put(handlerName, locationHandler);
    }

    public void disable(String handlerName) {
        mLocationHandlers.remove(handlerName);
        if (mLocationHandlers.isEmpty() && mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            Log.d(TAG, "Removed location updates");
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            if (mGoogleApiClient != null) {
                mGoogleApiClient.disconnect();
                mGoogleApiClient = null;
            }
            isEnabled = false;
        } else {
            Log.d(TAG, mLocationHandlers.size() + " handlers registered");
        }

        //send last known location
        if (lastLocation != null) {
            sendLocationMessage(lastLocation, (int) lastLocation.getAccuracy());
        }

        this.recentLocationSent = null;
        this.lastLocation = null;

    }

    public boolean isEnabled() {
        return isEnabled;
    }

    private synchronized void buildGoogleApiClient(Context context) {
        if (mGoogleApiClient == null && context != null) {
            mGoogleApiClient = new GoogleApiClient.Builder(context)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        //if (AndroidDevice.isBetterLocation(location, ConfigurationManager.getInstance().getLocation())) {
        Log.d(TAG, "Received new location");
        if (location != null) {
            checkRadius(location);
            addLocationToRoute(location);
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "Connection failed with code " + connectionResult.getErrorCode());
    }

    @Override
    public void onConnected(Bundle bundle) {
        Location location = null;
        try {
            location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        } catch (SecurityException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        if (location != null) {
            checkRadius(location);
            addLocationToRoute(location);
        }
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);

        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            Log.d(TAG, "Requested location updates");
        } catch (SecurityException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    public void onConnectionSuspended(int reason) {
        //call logger
        Log.d(TAG, "Connection suspended with code " + reason);
    }
}

