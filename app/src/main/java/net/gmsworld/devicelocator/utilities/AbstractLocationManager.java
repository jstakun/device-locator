package net.gmsworld.devicelocator.utilities;

import android.content.Context;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.model.Feature;
import net.gmsworld.devicelocator.model.FeatureCollection;
import net.gmsworld.devicelocator.model.Geometry;
import net.gmsworld.devicelocator.model.Properties;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.lang.ref.WeakReference;
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

    private static final String TAG = AbstractLocationManager.class.getSimpleName();

    public static final String ROUTE_FILE = "routeFile.bin";

    public static final int UPDATE_LOCATION = 301;

    private static final int MAX_REASONABLE_SPEED = 90; //324 kilometer per hour or 201 mile per hour
    private static final int MAX_REASONABLE_ALTITUDECHANGE = 200; //meters
    public static final int MAX_REASONABLE_ACCURACY = 50; //meters

    private static final Vector<Location> mWeakLocations = new Vector<>(3);
    private static final Queue<Double> mAltitudes = new LinkedList<>();
    private final boolean mSpeedSanityCheck = true;

    private int radius = -1;
    protected int gpsAccuracy = 1;

    private static final Map<String, Handler> mLocationHandlers = new HashMap<>();
    private Location recentLocationSent;
    private Location lastLocation;

    private File routeFile;

    boolean isEnabled = false;

    private void checkRadius(Location location) {
        lastLocation = location;
        boolean update = false;
        float distWithAccuracy;
        if (recentLocationSent != null) {
            float distance = lastLocation.distanceTo(recentLocationSent);
            distWithAccuracy = distance + lastLocation.getAccuracy();
            Log.d(TAG, "checkRadius compared " + distance + " + " + lastLocation.getAccuracy() + " with " + radius);
            if (distWithAccuracy > radius && radius > 0 && lastLocation.getAccuracy() < MAX_REASONABLE_ACCURACY) { //radius * 0.9f) {
                update = true;
                //TODO if device has changed temporary gps accuracy to balanced change it back to high
            } else if (lastLocation.getTime() - recentLocationSent.getTime() >= 5 * 60 * 1000 && gpsAccuracy >= 1) {
                //TODO if device not moving change temporary gps accuracy to balanced
                Log.d(TAG, "No location change since 5 mins. Consider to change temporary gps accuracy");
            }
        } else { //if (recentLocationSent == null) {
            Log.d(TAG, "checkRadius compared " + lastLocation.getAccuracy() + " with " + radius);
            distWithAccuracy = lastLocation.getAccuracy();
            if (distWithAccuracy < MAX_REASONABLE_ACCURACY) { //radius * 0.9f) {
                update = true;
            }
        }
        if (update) {
            this.recentLocationSent = location;
            //Log.d(TAG, "Saving route point: " + lastLocation.getLatitude() + "," + lastLocation.getLongitude());
            sendLocationMessage(lastLocation, (int) distWithAccuracy);
            //route.add(lastLocation);
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

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    void onLocationReceived(Location location) {
        Log.d(TAG, "Received new location");
        //location must be newer than 1 minute
        if (location != null && (System.currentTimeMillis() - location.getTime()) < 60 * 1000) {
            checkRadius(location);
            addLocationToRoute(location);
        } else if (location == null) {
            Log.d(TAG, "Received null location!");
        } else {
            Log.d(TAG, "Received old location: " + (System.currentTimeMillis() - location.getTime()) + "!");
        }
    }

    void init(String handlerName, Handler handler, Context context, int radius, boolean resetRoute) {
        routeFile = Files.getFilesDir(context, ROUTE_FILE, false);
        this.radius = radius;
        if (resetRoute) {
            Log.d(TAG, "Route has been cleared");
            Files.deleteFileFromContextDir(ROUTE_FILE, context, false);
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

    private void addLocationToRoute(Location location) {
        Location l = filterLocation(location);
        if (l != null) {
            Log.d(TAG, "Route point will be added to data store");
            if (routeFile != null) {
                String line = location.getLatitude() + "," + location.getLongitude() + "," + System.currentTimeMillis();
                Files.appendLineToFileFromContextDir(routeFile, line, 10000, 1000);
            } else {
                Log.e(TAG, "Route file object is null. I'm unable to persist route point!");
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
            } else if (meters < 1f) {
                Log.d(TAG, "Location is in distance less that 1 meter from previous: "+ meters);
                proposedLocation = addBadLocation(proposedLocation);
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

        return Math.abs(altitude - avg) < MAX_REASONABLE_ALTITUDECHANGE;
    }

    public void executeRouteUploadTask(Context activity, boolean isBackground, Network.OnGetFinishListener onGetFinishListener) {
        if (Network.isNetworkAvailable(activity)) {
            new RouteUploadTask(activity, isBackground, onGetFinishListener).execute();
        } else {
            Toast.makeText(activity, R.string.no_network_error, Toast.LENGTH_LONG).show();
        }
    }

    static class RouteUploadTask extends AsyncTask<Void, Integer, Integer> {
        private final WeakReference<Context> callerActivity;
        private final Network.OnGetFinishListener onGetFinishListener;
        private final boolean isBackground;

        RouteUploadTask(Context activity, boolean isBackground, Network.OnGetFinishListener onGetFinishListener) {
            this.callerActivity = new WeakReference<>(activity);
            this.isBackground = isBackground;
            this.onGetFinishListener = onGetFinishListener;
        }

        @Override
        public void onPreExecute () {
            if (!isBackground) {
                Context activity = callerActivity.get();
                if (activity == null) {
                    return;
                }
                Toast.makeText(activity, "Route sharing task has been started...", Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public Integer doInBackground(Void... params) {
            Context activity = callerActivity.get();
            if (activity == null) {
                return -1;
            } else {
                return uploadRouteToServer(activity, onGetFinishListener);
            }
        }

        @Override
        public void onPostExecute(Integer routeSize) {
            Context activity = callerActivity.get();
            if (activity == null) {
                return;
            }
            if (routeSize <= 1 && !isBackground) {
                Toast.makeText(activity, "No route is saved yet. Please make sure device location tracking is started and try again after some time.", Toast.LENGTH_LONG).show();
            }
        }

        private Integer uploadRouteToServer(final Context context, Network.OnGetFinishListener onFinishListener) {
            List<String> route = Files.readFileByLinesFromContextDir(ROUTE_FILE, context);
            final int size = route.size();
            Log.d(TAG, "Route has " + size + " points");
            if (size > 1) {
                try {
                    String deviceId = Messenger.getDeviceId(context, true);
                    String desc = "Route recorded by " + context.getString(R.string.app_name) + " on device: " + deviceId;
                    String content = routeToGeoJson(route, desc, deviceId);
                    String url = context.getString(R.string.routeProviderUrl);
                    final Map<String, String> headers = new HashMap<>();
                    String tokenStr = PreferenceManager.getDefaultSharedPreferences(context).getString(DeviceLocatorApp.GMS_TOKEN, "");
                    if (StringUtils.isNotEmpty(tokenStr)) {
                        url = context.getString(R.string.secureRouteProviderUrl);
                        headers.put("Authorization", "Bearer " + tokenStr);
                    }
                    //Log.d(TAG, "Route:\n" + route);
                    headers.put("X-GMS-RouteId", RouteTrackingServiceUtils.getRouteId(context));
                    headers.put("X-GMS-AppVersionId", Integer.toString(AppUtils.getInstance().getVersionCode(context)));
                    Network.post(context, url, "route=" + content, null, headers, onFinishListener);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            } else {
                Log.d(TAG, "Route must have at least 2 points. Currently it has " + size);
            }
            return size;
        }

        private String routeToGeoJson(List<String> routePoints, String description, String deviceId) {
            Gson gson = new Gson();

            String filename = RouteTrackingServiceUtils.getRouteId(callerActivity.get());

            FeatureCollection fc = new FeatureCollection();
            fc.name = filename;
            fc.deviceId = deviceId;

            Feature[] f = {new Feature()};
            fc.features = f;

            Properties p = new Properties();
            p.name = filename;
            p.username = "device-locator";
            p.deviceId = deviceId;

            f[0].properties = p;

            Geometry g = new Geometry();

            int routeSize = routePoints.size();
            double[][] coordinates = new double[routeSize][2];
            Log.d(TAG, "Creating route " + filename + " geojson containing " + routeSize + " points");
            long routeTime = -1L;
            long creationTime = 0L;
            float routeDistance = 0L;
            for (int i = 0; i < routeSize; i++) {
                String coordsStr = routePoints.get(i);
                //Log.d(TAG, "Parsing: " + coordsStr);
                String[] coords = StringUtils.split(coordsStr, ",");
                if (coords.length >= 2) {
                    //LNG, LAT
                    coordinates[i] = new double[]{Double.parseDouble(coords[1]), Double.parseDouble(coords[0])};
                }

                if (i == 0 && coords.length >= 3) {
                    routeTime = creationTime = Long.parseLong(coords[2]);
                } else if (i == (routeSize - 1) && coords.length >= 3) {
                    routeTime = Long.parseLong(coords[2]) - routeTime;
                }

                if (i > 0) {
                    float dist[] = new float[1];
                    Location.distanceBetween(coordinates[i][0], coordinates[i][1], coordinates[i - 1][0], coordinates[i - 1][1], dist);
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

            p.creationDate = creationTime;

            p.description = description;
            p.uploadDate = System.currentTimeMillis();

            g.coordinates = coordinates;
            f[0].geometry = g;

            return gson.toJson(fc);
        }
    }
}
