package net.gmsworld.devicelocator.Utilities;

/**
 * Created by jstakun on 8/5/17.
 */

import android.content.Context;
import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.GpsStatus.Listener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

/**
 *
 * @author jstakun
 */
public class AndroidDevice extends AbstractLocationManager implements LocationListener {

    private static final int MILLIS = 5000;
    private static final int METERS = 5;
    //private static final int TWO_MINUTES = 1000 * 60 * 2;
    private static final int THREE_MINUTES = 1000 * 60 * 3;
    private static final String TAG = AndroidDevice.class.getSimpleName();

    private LocationManager locationManager;
    private boolean isListening = false;

    private Listener gpsStatusListener = new GpsStatus.Listener() {
        public synchronized void onGpsStatusChanged(int event) {
            switch (event) {
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    if (locationManager != null) {
                        GpsStatus status = locationManager.getGpsStatus(null);
                        int satellites = 0;
                        Iterable<GpsSatellite> list = status.getSatellites();
                        for (GpsSatellite satellite : list) {
                            if (satellite.usedInFix()) {
                                satellites++;
                            }
                        }
                        Log.d(TAG, "Number of available satellites: " + satellites);
                    }
                    break;
                case GpsStatus.GPS_EVENT_STOPPED:
                    Log.d(TAG, "GPS device stopped!");
                    break;
                case GpsStatus.GPS_EVENT_STARTED:
                    Log.d(TAG, "GPS device started!");
                    break;
                default:
                    break;
            }
        }
    };

    public AndroidDevice(Context context) {

        Log.d(TAG, "GPS Provider created...");

        if (locationManager == null && context != null) {
            locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        }

        Location lastKnownLocation = null;

        if (locationManager != null) {
            lastKnownLocation = getLastKnownLocation();
        }

        if (lastKnownLocation != null) {
            setMyLocation(lastKnownLocation);
        }

        callerContext = context;
    }

    public void startListening(String handlerName, Handler handler, Context context, int radius, int priority, boolean resetRoute) {
        Log.d(TAG, "startListening(): " + isListening);
        setMyLocation(getLastKnownLocation(context, 10)); //set last know location in last 10 minutes
        if (!isListening) {
            Criteria crit = new Criteria();
            crit.setAccuracy(Criteria.ACCURACY_FINE);
            String provider = locationManager.getBestProvider(crit, false);
            try {
                locationManager.removeUpdates(this);
                if (priority <= 0) {
                    locationManager.requestLocationUpdates(provider, MILLIS * 2, METERS * 2, this);
                } else {
                    locationManager.requestLocationUpdates(provider, MILLIS, METERS, this);
                }
                isListening = true;
            } catch (Exception e) {
                stopListening(null);
            }

            locationManager.addGpsStatusListener(gpsStatusListener);


            init(handlerName, handler, context, radius, priority, resetRoute);
        }
    }

    public void onLocationChanged(Location location) {
        onLocationReceived(location);
    }

    public void stopListening(String handler) {
        Log.d(TAG, "stopListening(): " + isListening);
        finish(handler);
        if (isListening) {
            locationManager.removeUpdates(this);
            locationManager.removeGpsStatusListener(gpsStatusListener);
        }
        isListening = false;
    }


    public void onProviderDisabled(String provider) {
        Log.d(TAG, "GPS Provider Disabled: " + provider);
    }

    public void onProviderEnabled(String provider) {
        Log.d(TAG, "GPS Provider Enabled: " + provider);
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "GPS Provider Status Changed: " + provider + ", Status=[" + status + "], extras=" + extras);
    }

    private Location getLastKnownLocation() {
        Location lastKnownLocation = null;

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        } catch (Exception e) {
            Log.e(TAG, "getLastKnownLocation() exception:", e);
        }

        if (lastKnownLocation == null) {
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
            } catch (Exception e) {
                Log.e(TAG, "getLastKnownLocation() exception:", e);
            }
        }

        return lastKnownLocation;
    }

    private void setMyLocation(Location lastKnownLocation) {
        if (lastKnownLocation != null) {
            Log.d(TAG, "Setting last known location.");
            onLocationChanged(lastKnownLocation);
        }
    }

    public static int getBearingIndex(Location location) {

        final double sector = 22.5; // = 360 degrees / 16 sectors
        final int[] compass = {0 /* N */, 1 /* NNE */, 2 /* NE */, 3 /* ENE */,
                4 /* E */, 5 /* ESE */, 6 /* SE */, 7 /* SSE */,
                8 /* S */, 9 /* SSW */, 10 /* SW */, 11 /* WSW */,
                12 /* W */, 13 /* WNW */, 14 /* NW */, 15 /* NNW */, 0 /* N */};
        final int directionIndex = (int) (Math.floor((location.getBearing() - 11.25) / sector) + 1);// we add one because north would otherwise be a -1 index, and we add a reference to N as the zero index
        int heading = compass[directionIndex];
        return heading;
    }

    public static boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }
        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > THREE_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -THREE_MINUTES;
        boolean isNewer = timeDelta > 0; //millis
        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
        } else if (isSignificantlyOlder) {
            // If the new location is more than two minutes older, it must be worse
            return false;
        }
        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 100; //meters
        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(), currentBestLocation.getProvider());
        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether two providers are the same *
     */
    private static boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    public static Location getLastKnownLocation(Context context, long validityMinutes) {
        Location location = null;

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            try {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } catch (Exception e) {
                Log.e(TAG, "getLastKnownLocation() exception:", e);
            }
            if (location == null) {
                try {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                } catch (Exception e) {
                    Log.e(TAG, "getLastKnownLocation() exception:", e);
                }
            }
            if (location == null) {
                Log.d(TAG, "getLastKnownLocation() no location from location manager available");
            }
        } else {
            Log.d(TAG, "getLastKnownLocation() no location manager available");
        }

        if (location != null && (System.currentTimeMillis() - location.getTime()) < (validityMinutes * 60 * 1000)) {
            return location;
        } else {
            return null;
        }
    }
}
