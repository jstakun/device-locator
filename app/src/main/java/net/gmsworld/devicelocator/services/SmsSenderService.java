package net.gmsworld.devicelocator.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.broadcastreceivers.DeviceAdminEventReceiver;
import net.gmsworld.devicelocator.utilities.AbstractLocationManager;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.NotificationUtils;

import org.apache.commons.lang3.StringUtils;

import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.config.LocationParams;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

public class SmsSenderService extends IntentService implements OnLocationUpdatedListener {
    private final static String TAG = SmsSenderService.class.getSimpleName();

    private final static int LOCATION_REQUEST_MAX_WAIT_TIME = 120; //seconds

    public static final String SEND_ACKNOWLEDGE_MESSAGE = "settings_detected_sms";
    public static final String SEND_LOCATION_MESSAGE = "settings_gps_sms";
    public static final String SEND_MAP_LINK_MESSAGE = "settings_google_sms";

    private static boolean isRunning = false;

    private String phoneNumber = null;
    private String telegramId = null;
    private String email = null;
    private String app = null;

    private boolean keywordReceivedSms = false;
    private boolean gpsSms = false;
    private boolean googleMapsSms = false;

    private Location bestLocation = null;
    private long startTime = 0;

    private final Handler handler = new Handler();

    private final Runnable task = new Runnable() {
        @Override
        public void run () {
            disableUpdates();
        }
    };

    public SmsSenderService() {
        super("SmsSenderService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent()");

        Bundle extras = intent.getExtras();

        if (extras != null) {
            this.phoneNumber = extras.getString("phoneNumber");

            this.telegramId = extras.getString("telegramId");

            this.email = extras.getString("email");

            this.app = extras.getString("app");

            if (StringUtils.isEmpty(this.phoneNumber) && StringUtils.isEmpty(this.telegramId) && StringUtils.isEmpty(email) && StringUtils.isEmpty(app)) {
                //Log.d(TAG, "Phonenumber empty, return.");
                return;
            }

            String command = extras.getString("command");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(NotificationUtils.WORKER_NOTIFICATION_ID, NotificationUtils.buildWorkerNotification(this));
            }

            if (StringUtils.isEmpty(command)) {
                initSending(extras.getString("source"));
            } else {
                Messenger.sendCommandMessage(this, extras);
            }
        } //else {
            //Log.e(TAG, "This intent requires extra parameters");
        //}
    }

    private void initSending(String source) {
        Log.d(TAG, "initSending()");

        readSettings();

        if (StringUtils.equals(source, DeviceAdminEventReceiver.SOURCE)) {
            Messenger.sendLoginFailedMessage(this, phoneNumber, telegramId, email, app);
        } else if (keywordReceivedSms) {
            Messenger.sendAcknowledgeMessage(this, phoneNumber, telegramId, email, app);
        }

        if (!isRunning && SmartLocation.with(this).location().state().isAnyProviderAvailable()) {

            isRunning = true;

            //set bestLocation to null and start time
            startTime = System.currentTimeMillis() / 1000;

            bestLocation = null;

            try {
                SmartLocation.with(this).location(new LocationGooglePlayServicesWithFallbackProvider(this))
                        .config(LocationParams.NAVIGATION)
                        .start(this);

                //SmartLocation smartLocation = new SmartLocation.Builder(this).logging(true).build();
                //smartLocation.location(new LocationGooglePlayServicesWithFallbackProvider(this))
                //        .config(LocationParams.NAVIGATION)
                //        .start(this);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }

            handler.postDelayed(task, LOCATION_REQUEST_MAX_WAIT_TIME * 1000);

        } else {

            Log.e(TAG, "No GPS providers are available!");

            Messenger.sendLocationErrorMessage(this, phoneNumber, telegramId, email, app);
        }
    }

    private static boolean isLocationFused(Location location) {
        return !location.hasAltitude() || !location.hasSpeed() || location.getAltitude() == 0;
    }

    @Override
    public void onLocationUpdated(Location location) {
        Log.d(TAG, "onLocationUpdated()");

        long currentTime = System.currentTimeMillis() / 1000;

        if (currentTime - startTime < LOCATION_REQUEST_MAX_WAIT_TIME) {

            //check if location is older than 10 minutes
            if ((System.currentTimeMillis() - location.getTime()) > 10 * 60 * 1000) {
                //Log.d(TAG, "Location is older than 10 minutes");
                return;
            }

            if (bestLocation == null) {
                bestLocation = location;
            }

            //still null? check again
            if (bestLocation == null) {
                return;
            }

            if (!bestLocation.getProvider().equals(LocationManager.GPS_PROVIDER) || bestLocation.getProvider().equals(location.getProvider())) {
                //Log.d(TAG, "NOT GPS OR BOTH GPS!");
                if (location.hasAccuracy() && bestLocation.hasAccuracy() && location.getAccuracy() < bestLocation.getAccuracy()) {
                    //Log.d(TAG, "Update best location.");
                    bestLocation = location;
                }
            }


            //if (isLocationFused(bestLocation)) {
                //Log.d(TAG, "Location still fused.");
            //    return;
            //}

            if (bestLocation.getAccuracy() > AbstractLocationManager.MAX_REASONABLE_ACCURACY) {
                //Log.d(TAG, "Accuracy more than 50, check again.");
                return;
            }
        }

        handler.removeCallbacks(task);

        disableUpdates();
    }

    private synchronized void disableUpdates() {
        //stop the location updates
        if (isRunning) {
            Log.d(TAG, "Disabling location updates...");

            SmartLocation.with(this).location().stop();

            if (bestLocation == null) {
                Messenger.sendLocationErrorMessage(this, phoneNumber, telegramId, email, app);
            } else {
                if (gpsSms && isRunning) {
                    Messenger.sendLocationMessage(this, bestLocation, isLocationFused(bestLocation), phoneNumber, telegramId, email, app);
                } else {
                    Log.d(TAG, "Location message won't be send");
                }

                if (googleMapsSms && isRunning) {
                    Messenger.sendGoogleMapsMessage(this, bestLocation, phoneNumber, telegramId, email, app);
                } else {
                    Log.d(TAG, "Google Maps link message won't be send");
                }
            }
            isRunning = false;
        }
    }

    private void readSettings() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        keywordReceivedSms = settings.getBoolean(SEND_ACKNOWLEDGE_MESSAGE, true);
        gpsSms = settings.getBoolean(SEND_LOCATION_MESSAGE, false);
        googleMapsSms = settings.getBoolean(SEND_MAP_LINK_MESSAGE, true);
    }

    private static int getLocationMode(Context context) {
        try {
            return Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
        } catch (Settings.SettingNotFoundException e) {
            return -1;
        }
    }

    public static String locationToString(Context context) {
        int mode = getLocationMode(context);
        switch (mode) {
            case Settings.Secure.LOCATION_MODE_OFF:
                return context.getString(R.string.location_mode_off);
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                return context.getString(R.string.location_battery_saving);
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                return context.getString(R.string.location_sensors_only);
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                return context.getString(R.string.location_high_accuracy);
            default:
                //API version <= 17
                String status =  Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
                if (StringUtils.isEmpty(status)) {
                    status = "Unknown";
                }
                return status;
        }
    }
}

