package net.gmsworld.devicelocator.Services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import net.gmsworld.devicelocator.BroadcastReceivers.DeviceAdminEventReceiver;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.Utilities.Messenger;

import org.apache.commons.lang3.StringUtils;

import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.config.LocationParams;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

public class SmsSenderService extends IntentService implements OnLocationUpdatedListener {
    private final static String TAG = SmsSenderService.class.getSimpleName();

    private final static int LOCATION_REQUEST_MAX_WAIT_TIME = 60;

    private static boolean isRunning = false;

    private Resources r = null;
    private String phoneNumber = null;
    private String telegramId = null;

    private boolean keywordReceivedSms = false;
    private boolean gpsSms = false;
    private boolean googleMapsSms = false;

    private int speedType = 0;

    private Location bestLocation = null;
    private long startTime = 0;

    public SmsSenderService() {
        super("SmsSenderService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        //Log.d(TAG, "onHandleIntent");
        this.phoneNumber = intent.getExtras().getString("phoneNumber");

        this.telegramId = intent.getExtras().getString("telegramId");

        if (StringUtils.isEmpty(this.phoneNumber) && StringUtils.isEmpty(this.telegramId)) {
            //Log.d(TAG, "Phonenumber empty, return.");
            return;
        }

        this.r = getResources();

        String email = intent.getExtras().getString("email");

        String notificationNumber = intent.getExtras().getString("notificationNumber");

        String command = intent.getExtras().getString("command");

        String param1 = intent.getExtras().getString("param1");

        if (StringUtils.isEmpty(command)) {
            if (!isRunning) {
                initSending(intent.getExtras().getString("source"));
            } else {
                Log.d(TAG, "GPS provider is already running!");
            }
        } else {
            Messenger.sendCommandMessage(this, intent, command, phoneNumber, email, telegramId, notificationNumber, param1);
        }
    }


    private void initSending(String source) {
        //Log.d(TAG, "initSending()");
        readSettings();

        if (StringUtils.equals(source, DeviceAdminEventReceiver.SOURCE)) {
            Messenger.sendLoginFailedMessage(this, phoneNumber, telegramId);
        } else if (keywordReceivedSms) {
            Messenger.sendAcknowledgeMessage(this, phoneNumber, telegramId);
        }

        //set bestLocation to null and start time
        startTime = System.currentTimeMillis() / 1000;
        bestLocation = null;


        if (SmartLocation.with(this).location().state().isAnyProviderAvailable()) {

            isRunning = true;

            SmartLocation.with(this).location(new LocationGooglePlayServicesWithFallbackProvider(this))
                    .config(LocationParams.NAVIGATION)
                    .start(this);
        } else {
            Log.e(TAG, "No GPS providers are available!");
        }
    }

    public static boolean isLocationFused(Location location) {
        return !location.hasAltitude() || !location.hasSpeed() || location.getAltitude() == 0;
    }

    @Override
    public void onLocationUpdated(Location location) {
        //Log.d(TAG, "LOCATION UPDATE");

        long currentTime = System.currentTimeMillis() / 1000;

        //Log.d(TAG, "Start time: " + startTime + ", Current time: " + currentTime);
        //Log.d(TAG, "Difference: " + (currentTime - startTime));

        if (currentTime - startTime < LOCATION_REQUEST_MAX_WAIT_TIME) {
            //Log.d(TAG, "NOT EXPIRED YET. CHECK");

            if (bestLocation == null) {
                bestLocation = location;
            }

            //still null? check again
            if (bestLocation == null) {
                //Log.d(TAG, "BEST LOCATION STILL NULL, CHECK MORE");
                return;
            }

            //Log.d(TAG, bestLocation.toString());
            //Log.d(TAG, location.toString());

            //Log.d(TAG, "HAS ALTITUDE:" + location.hasAltitude());
            //Log.d(TAG, "HAS SPEED: " + location.hasSpeed());
            //Log.d(TAG, "LOCATION PROVIDER: " + location.getProvider());


            if (!bestLocation.getProvider().equals(LocationManager.GPS_PROVIDER) || bestLocation.getProvider().equals(location.getProvider())) {
                //Log.d(TAG, "NOT GPS OR BOTH GPS!");
                if (location.getAccuracy() < bestLocation.getAccuracy()) {
                    //Log.d(TAG, "Update best location.");
                    bestLocation = location;
                }
            }


            if (isLocationFused(bestLocation)) {
                //Log.d(TAG, "Location still fused.");
                return;
            }

            if (bestLocation.getAccuracy() > 100) {
                //Log.d(TAG, "Accuracy more than 100, check again.");
                return;
            }

            if ((System.currentTimeMillis() - bestLocation.getTime()) > 10 * 60 * 1000) {
                //Log.d(TAG, "Location is older than 10 minutes");
                return;
            }
        }

        //stop the location
        //Log.d(TAG, "STOP LOCATION BECAUSE TIME ELAPSED OR ACCURACY IS GOOD");

        SmartLocation.with(this).location().stop();

        if (bestLocation == null) {
            Messenger.sendSMS(this, phoneNumber, r.getString(R.string.error_getting_location));
            return;
        }

        if (gpsSms) {
            Messenger.sendLocationMessage(this, bestLocation, isLocationFused(bestLocation), speedType, phoneNumber, telegramId);
        }

        if (googleMapsSms) {
            Messenger.sendGoogleMapsMessage(this, bestLocation, phoneNumber, telegramId);
        }

        isRunning = false;
    }

    private void readSettings() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        keywordReceivedSms = settings.getBoolean("settings_detected_sms", true);
        gpsSms = settings.getBoolean("settings_gps_sms", true);
        googleMapsSms = settings.getBoolean("settings_google_sms", true);
        //networkSms = settings.getBoolean("settings_network_sms", false);
        speedType = Integer.parseInt(settings.getString("settings_kmh_or_mph", "0"));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //Log.d(TAG, "onCreate()");
    }

    @Override
    public void onDestroy() {
        //Log.d(TAG, "onDestroy()");
        super.onDestroy();
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
                return context.getResources().getString(R.string.location_mode_off);
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                return context.getResources().getString(R.string.location_battery_saving);
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                return context.getResources().getString(R.string.locateion_sensors_only);
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                return context.getResources().getString(R.string.location_high_accuracy);
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

