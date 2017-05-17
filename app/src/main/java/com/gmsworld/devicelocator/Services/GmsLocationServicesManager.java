package com.gmsworld.devicelocator.Services;

/**
 * Created by jstakun on 04.05.17.
 */

import android.Manifest;
import android.content.Context;
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
import com.gmsworld.devicelocator.Model.Feature;
import com.gmsworld.devicelocator.Model.FeatureCollection;
import com.gmsworld.devicelocator.Model.Geometry;
import com.gmsworld.devicelocator.Model.Properties;
import net.gmsworld.locatedriver.R;
import com.gmsworld.devicelocator.Utilities.Network;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;

public class GmsLocationServicesManager implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = "GmsLocationServiceMngr";

    //public static final int GMS_CONNECTED = 300;
    public static final int UPDATE_LOCATION = 301;

    private static final int LOCATION_READ_INTERVAL_LOW = 10000; //ms
    private static final int LOCATION_READ_INTERVAL_HIGH = 5000; //ms

    private static final int MAX_REASONABLE_SPEED = 90; //324 kilometer per hour or 201 mile per hour
    private static final int MAX_REASONABLE_ALTITUDECHANGE = 200; //meters
    private static final int MAX_REASONABLE_ACCURACY = 50; //meters

    private static final Vector<Location> mWeakLocations = new Vector<Location>(3);
    private static final Queue<Double> mAltitudes = new LinkedList<Double>();;
    private boolean mSpeedSanityCheck = true;

    private boolean isEnabled = false;

    private int radius = -1;

    private GoogleApiClient mGoogleApiClient;
    private static Map<String, Handler> mLocationHandlers = new HashMap<String, Handler>();
    private LocationRequest mLocationRequest;
    private Location recentLocationSent, lastLocation;

    private static final List<Location> route = new CopyOnWriteArrayList<Location>();

    public static final GmsLocationServicesManager instance = new GmsLocationServicesManager();

    private GmsLocationServicesManager() {

    }

    public static GmsLocationServicesManager getInstance() {
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
                mLocationRequest.setInterval(LOCATION_READ_INTERVAL_LOW);
                mLocationRequest.setFastestInterval(LOCATION_READ_INTERVAL_LOW);
                mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);//
            } else {
                mLocationRequest.setInterval(LOCATION_READ_INTERVAL_HIGH);
                mLocationRequest.setFastestInterval(LOCATION_READ_INTERVAL_HIGH);
                mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);//
            }
            isEnabled = true;
        }
        this.radius = radius;
        if (resetRoute) {
            Log.d(TAG, "Route array has been cleared");
            this.route.clear();
        }
        mLocationHandlers.put(handlerName, locationHandler);
    }

    public void disable(String handlerName) {
        mLocationHandlers.remove(handlerName);
        if (mLocationHandlers.isEmpty() && mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            Log.d(TAG, "GmsLocationServicesManager removed location updates");
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            if (mGoogleApiClient != null) {
                mGoogleApiClient.disconnect();
                mGoogleApiClient = null;
            }
            isEnabled = false;
        } else {
            Log.d(TAG, "GmsLocationServicesManager has " + mLocationHandlers.size() + " handlers");
        }

        this.recentLocationSent = null;
        this.lastLocation = null;

        //send last known location
        if (lastLocation != null) {
            sendLocationMessage(lastLocation, (int) lastLocation.getAccuracy());
        }
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
        Log.d(TAG, "GmsLocationServicesManager received new location");
        if (location != null) {
            checkRadius(location);
            addLocationToRoute(location);
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "GmsLocationServicesManager connection failed with code " + connectionResult.getErrorCode());
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
            Log.d(TAG, "GmsLocationServicesManager requested location updates");
        } catch (SecurityException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    public void onConnectionSuspended(int reason) {
        //call logger
        Log.d(TAG, "GmsLocationServicesManager connection suspended with code " + reason);
    }

    private void checkRadius(Location location) {
        lastLocation = location;
        boolean update = false;
        float distWithAccuracy = 0;
        if (recentLocationSent != null) {
            float distance = lastLocation.distanceTo(recentLocationSent);
            distWithAccuracy = distance + lastLocation.getAccuracy();
            Log.d(TAG, "checkRadius compared " + distance + " + " + lastLocation.getAccuracy() + " with " + radius);
            if (distWithAccuracy > radius && radius > 0 && lastLocation.getAccuracy() < radius) {
                update = true;
            }
        } else if (recentLocationSent == null) {
            Log.d(TAG, "checkRadius compared " + lastLocation.getAccuracy() + " with " + radius);
            distWithAccuracy = lastLocation.getAccuracy();
            if (distWithAccuracy < radius) {
                update = true;
            }
        }
        if (update) {
            setRecentLocationSent(lastLocation);
            Log.d(TAG, "Saving route point: " + lastLocation.getLatitude() + "," + lastLocation.getLongitude());
            sendLocationMessage(lastLocation, (int)distWithAccuracy);
            route.add(lastLocation);
        }
    }

    private void sendLocationMessage(Location location, int distance) {
        for (Map.Entry<String, Handler> entry : mLocationHandlers.entrySet()) {
            Message msg = Message.obtain();
            msg.what = UPDATE_LOCATION;
            msg.arg1 = distance;
            msg.obj = location;
            entry.getValue().sendMessage(msg);
        }
    }

    public void setRecentLocationSent(Location location) {
        this.recentLocationSent = location;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public int getRouteSize() {
        return route != null ? route.size() : 0;
    }

    public void uploadRouteToServer(Context context, String title, long creationDate) {
        if (route.size() > 1) {
            String content = routeToGeoJson(route, title, "Device id: " + Network.getDeviceId(context), creationDate);
            String url = context.getResources().getString(R.string.routeProviderUrl);
            //Log.d(TAG, "Uploading route " + content);
            Network.post(url, "route=" + content, null, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String result) {
                    Log.d(TAG, "Received following response code: " + result);
                }
            });
        } else {
            Log.d(TAG, "Route must have at least 2 points. Currently it has only " + route.size() + " point");
        }
    }

    private static String routeToGeoJson(List<Location> routePoints, String filename, String description, long creationDate) {
        Gson gson = new Gson();

        FeatureCollection fc = new FeatureCollection();
        fc._id = filename;

        Feature[] f = {new Feature()};
        fc.features = f;

        Properties p = new Properties();
        p.name = filename;
        p.description = description;
        p.uploadDate = System.currentTimeMillis();
        p.username = "devicelocator";
        p.creationDate = creationDate;

        f[0].properties = p;

        Geometry g = new Geometry();

        double[][] coordinates = new double[routePoints.size()][2];
        int i = 0;
        for (Location point : routePoints) {
            //Log.d(TAG, "Adding new point to route geojson " + point.getLatitude() + "," + point.getLongitude());
            coordinates[i] = new double[]{point.getLatitude(), point.getLongitude()};
            i++;
        }

        g.coordinates = coordinates;
        f[0].geometry = g;

        return gson.toJson(fc);
    }

    private void addLocationToRoute(Location location) {
        Location l = filterLocation(location);
        if (l != null) {
            route.add(l);
        }
    }

    private Location filterLocation(Location proposedLocation) {
        // Do no include log wrong 0.0 lat 0.0 long, skip to next value in while-loop
        if (proposedLocation != null && (proposedLocation.getLatitude() == 0.0d || proposedLocation.getLongitude() == 0.0d)) {
            Log.d(TAG, "A wrong location was received, 0.0 latitude and 0.0 longitude... ");
            proposedLocation = null;
        }

        // Do not log a point which is more inaccurate then is configured to be acceptable
        if (proposedLocation != null && proposedLocation.getAccuracy() > MAX_REASONABLE_ACCURACY) {
            Log.d(TAG, String.format("A weak location was received, lots of inaccuracy... (%f is more then max %d)", proposedLocation.getAccuracy(), MAX_REASONABLE_ACCURACY));
            proposedLocation = addBadLocation(proposedLocation);
        }

        // Do not log a point which might be on any side of the previous point
        if (proposedLocation != null && lastLocation != null && proposedLocation.getAccuracy() > lastLocation.distanceTo(proposedLocation)) {
            Log.d(TAG, String.format("A weak location was received, not quite clear from the previous route point... (%f more then max %f)", proposedLocation.getAccuracy(), lastLocation.distanceTo(proposedLocation)));
            proposedLocation = addBadLocation(proposedLocation);
        }

        // Speed checks, check if the proposed location could be reached from the previous one in sane speed
        // Common to jump on network logging and sometimes jumps on Samsung Galaxy S type of devices
        if (mSpeedSanityCheck && proposedLocation != null && lastLocation != null) {
            // To avoid near instant teleportation on network location or glitches cause continent hopping
            float meters = proposedLocation.distanceTo(lastLocation);
            long seconds = (proposedLocation.getTime() - lastLocation.getTime()) / 1000L;
            float speed = meters / seconds;
            if (speed > MAX_REASONABLE_SPEED) {
                Log.d(TAG, "A strange location was received, a really high speed of " + speed + " m/s, prob wrong...");
                proposedLocation = addBadLocation(proposedLocation);
                //Might be a messed up Samsung Galaxy S GPS, reset the logging
                //stop and start gps listener
            }
        }

        // Remove speed if not sane
        if (mSpeedSanityCheck && proposedLocation != null && proposedLocation.getSpeed() > MAX_REASONABLE_SPEED) {
            Log.d(TAG, "A strange speed, a really high speed, prob wrong...");
            proposedLocation.removeSpeed();
        }

        // Remove altitude if not sane
        if (mSpeedSanityCheck && proposedLocation != null && proposedLocation.hasAltitude()) {
            if (!addSaneAltitude(proposedLocation.getAltitude())) {
                Log.d(TAG, "A strange altitude, a really big difference, prob wrong...");
                proposedLocation.removeAltitude();
            }
        }

        // Older bad locations will not be needed
        if (proposedLocation != null) {
            mWeakLocations.clear();
        }
        return proposedLocation;
    }

    private Location addBadLocation(Location location) {
        mWeakLocations.add(location);
        if (mWeakLocations.size() < 3) {
            location = null;
        } else {
            Location best = mWeakLocations.lastElement();
            for (Location whimp : mWeakLocations) {
                if (whimp.hasAccuracy() && best.hasAccuracy() && whimp.getAccuracy() < best.getAccuracy()) {
                    best = whimp;
                } else if (whimp.hasAccuracy() && !best.hasAccuracy()) {
                    best = whimp;
                }
            }
            synchronized (mWeakLocations) {
                mWeakLocations.clear();
            }
            location = best;
        }
        return location;
    }

    private boolean addSaneAltitude(double altitude) {
        boolean sane = true;
        double avg = 0;
        int elements = 0;
        // Even insane altitude shifts increases alter perception
        mAltitudes.add(altitude);
        if (mAltitudes.size() > 3) {
            mAltitudes.poll();
        }
        for (Double alt : mAltitudes) {
            avg += alt;
            elements++;
        }
        avg = avg / elements;
        sane = Math.abs(altitude - avg) < MAX_REASONABLE_ALTITUDECHANGE;

        return sane;
    }
}

