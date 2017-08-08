package net.gmsworld.devicelocator.Utilities;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

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

/**
 * Created by jstakun on 8/5/17.
 */

public abstract class AbstractLocationManager {

    private static final String TAG =  AbstractLocationManager.class.getSimpleName();

    public static final String ROUTE_FILE = "routeFile.txt";

    public static final int UPDATE_LOCATION = 301;

    private static final int MAX_REASONABLE_SPEED = 90; //324 kilometer per hour or 201 mile per hour
    private static final int MAX_REASONABLE_ALTITUDECHANGE = 200; //meters
    private static final int MAX_REASONABLE_ACCURACY = 50; //meters

    private static final Vector<Location> mWeakLocations = new Vector<Location>(3);
    private static final Queue<Double> mAltitudes = new LinkedList<Double>();
    private boolean mSpeedSanityCheck = true;

    protected int radius = -1;

    static Map<String, Handler> mLocationHandlers = new HashMap<String, Handler>();
    Location recentLocationSent;
    Location lastLocation;

    protected Context callerContext;

    boolean isEnabled = false;

    void checkRadius(Location location) {
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
        } else { //if (recentLocationSent == null) {
            Log.d(TAG, "checkRadius compared " + lastLocation.getAccuracy() + " with " + radius);
            distWithAccuracy = lastLocation.getAccuracy();
            if (distWithAccuracy < radius) {
                update = true;
            }
        }
        if (update) {
            this.recentLocationSent = location;
            //Log.d(TAG, "Saving route point: " + lastLocation.getLatitude() + "," + lastLocation.getLongitude());
            sendLocationMessage(lastLocation, (int)distWithAccuracy);
            //route.add(lastLocation);
        }
    }

    protected void sendLocationMessage(Location location, int distance) {
        for (Map.Entry<String, Handler> entry : mLocationHandlers.entrySet()) {
            Message msg = Message.obtain();
            msg.what = UPDATE_LOCATION;
            msg.arg1 = distance;
            msg.obj = location;
            entry.getValue().sendMessage(msg);
        }
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    void onLocationReceived(Location location) {
        Log.d(TAG, "Received new location");
        if (location != null) {
            checkRadius(location);
            addLocationToRoute(location);
        }
    }

    void init(String handlerName, Handler handler, Context context, int radius, int priority, boolean resetRoute) {
        callerContext = context;
        this.radius = radius;
        if (resetRoute) {
            Log.d(TAG, "Route has been cleared");
            Files.deleteFileFromContextDir(ROUTE_FILE, callerContext);
        }
        mLocationHandlers.put(handlerName, handler);
    }

    void finish(String handlerName) {
        mLocationHandlers.remove(handlerName);
        //send last known location
        if (lastLocation != null) {
            sendLocationMessage(lastLocation, (int) lastLocation.getAccuracy());
        }

        this.recentLocationSent = null;
        this.lastLocation = null;
    }

    public int uploadRouteToServer(final Context context, final String title, final String phoneNumber, final long creationDate, final boolean smsNotify, Network.OnGetFinishListener onFinishListener) {
        List<String> route = Files.readFileByLinesFromContextDir(ROUTE_FILE, context);
        final int size = route.size();
        final Intent newIntent = new Intent(context, SmsSenderService.class);
        if (smsNotify) {
            newIntent.putExtra("phoneNumber", phoneNumber);
            newIntent.putExtra("command", SmsReceiver.ROUTE_COMMAND);
            newIntent.putExtra("title", title);
        }
        if (size > 1) {
            try {
                String desc = "Device id: " + Network.getDeviceId(context);
                String content = routeToGeoJson(route, title, desc, creationDate);
                String url = context.getResources().getString(R.string.routeProviderUrl);
                //Log.d(TAG, "Uploading route " + content);
                Network.post(url, "route=" + content, null, onFinishListener);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                if (smsNotify) {
                    newIntent.putExtra("size", -1);
                    context.startService(newIntent);
                }
            }
        } else {
            Log.d(TAG, "Route must have at least 2 points. Currently it has " + size);
            if (smsNotify) {
                newIntent.putExtra("size", 0);
                context.startService(newIntent);
            }
        }
        return size;
    }

    private static String routeToGeoJson(List<String> routePoints, String filename, String description, long creationDate) throws Exception {
        Gson gson = new Gson();

        FeatureCollection fc = new FeatureCollection();
        fc.name = filename;

        Feature[] f = {new Feature()};
        fc.features = f;

        Properties p = new Properties();
        p.name = filename;
        p.username = "device-locator";
        p.creationDate = creationDate;

        f[0].properties = p;

        Geometry g = new Geometry();

        int routeSize = routePoints.size();
        double[][] coordinates = new double[routeSize][2];
        Log.d(TAG, "Creating route geojson containing " + routeSize + " points");
        long routeTime = -1L;
        float routeDistance = 0L;
        for (int i=0;i<routeSize;i++) {
            String coordsStr = routePoints.get(i);
            //Log.d(TAG, "Parsing: " + coordsStr);
            String[] coords = StringUtils.split(coordsStr, ",");
            if (coords.length >= 2) {
                //Log.d(TAG, "Adding point " + coords[0] + "," + coords[1]);
                coordinates[i] = new double[]{Double.parseDouble(coords[0]), Double.parseDouble(coords[1])};
            }

            if (i == 0 && coords.length >= 3) {
                routeTime = Long.parseLong(coords[2]);
            } else if (i == (routeSize-1) && coords.length >= 3) {
                routeTime = Long.parseLong(coords[2]) - routeTime;
            }

            if (i > 0) {
                float dist[] = new float[1];
                Location.distanceBetween(coordinates[i][0], coordinates[i][1], coordinates[i-1][0], coordinates[i-1][1], dist);
                if (dist[0] > 0) {
                    routeDistance += dist[0];
                }
            }

        }

        if (routeTime > 0) {
            description += ", time: " + routeTime + " ms.";
            p.time = routeTime;
        }

        if (routeDistance > 0) {
            description += ", distance: " + routeDistance + " meters";
            p.distance = routeDistance;
        }

        p.description = description;
        p.uploadDate = System.currentTimeMillis();

        g.coordinates = coordinates;
        f[0].geometry = g;

        return gson.toJson(fc);
    }

    void addLocationToRoute(Location location) {
        Location l = filterLocation(location);
        if (l != null) {
            Log.d(TAG, "Route point will be added to data store");
            if (callerContext != null) {
                String line = location.getLatitude() + "," + location.getLongitude() + "," + System.currentTimeMillis();
                Files.appendLineToFileFromContextDir(ROUTE_FILE, callerContext, line);
            } else {
                Log.e(TAG, "Caller context is null. I'm unable to persist route point!");
            }
        } else {
            Log.d(TAG, "Weak location won't be added to route");
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