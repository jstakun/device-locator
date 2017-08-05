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

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.Utilities.Messenger;

import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.config.LocationParams;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

public class SmsSenderService extends IntentService implements OnLocationUpdatedListener {
    private final static String TAG = SmsSenderService.class.getSimpleName();

    private final static int LOCATION_REQUEST_MAX_WAIT_TIME = 60;

    private Resources r = null;
    private Context context = null;
    private String phoneNumber = null;

    private boolean keywordReceivedSms = false;
    private boolean gpsSms = false;
    private boolean googleMapsSms = false;
    //private boolean networkSms = false;

    private int speedType = 0;
    private String command = null;

    private Location bestLocation = null;
    private long startTime = 0;

    public SmsSenderService() {
        super("SmsSenderService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        //Log.d(TAG, "onHandleIntent");
        this.phoneNumber = intent.getExtras().getString("phoneNumber");

        if (this.phoneNumber.length() == 0) {
            //Log.d(TAG, "Phonenumber empty, return.");
            return;
        }

        String email = intent.getExtras().getString("email");
        String telegramId = intent.getExtras().getString("telegramId");
        String notificationNumber = intent.getExtras().getString("notificationNumber");

        this.context = this;
        this.r = context.getResources();

        this.command = intent.getExtras().getString("command");

        if (command == null || command.length() == 0) {
            initSending();
        } else {
            Messenger.sendCommandMessage(this, intent, command, phoneNumber, email, telegramId, notificationNumber);
        }
    }


    private void initSending() {
        //Log.d(TAG, "initSending()");
        readSettings();

        if (keywordReceivedSms) {
            Messenger.sendAcknowledgeMessage(this, phoneNumber);
        }

        //set bestLocation to null and start time
        startTime = System.currentTimeMillis() / 1000;
        bestLocation = null;

        SmartLocation.with(context).location(new LocationGooglePlayServicesWithFallbackProvider(context))
                .config(LocationParams.NAVIGATION)
                .start(this);

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

        if (currentTime - startTime < this.LOCATION_REQUEST_MAX_WAIT_TIME) {
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


            if (this.isLocationFused(bestLocation)) {
                //Log.d(TAG, "Location still fused.");
                return;
            }

            if (bestLocation.getAccuracy() > 100) {
                //Log.d(TAG, "Accuracy more than 100, check again.");
                return;
            }
        }


        //stop the location
        //Log.d(TAG, "STOP LOCATION BECAUSE TIME ELAPSED OR ACCURACY IS GOOD");
        SmartLocation.with(context).location().stop();

        if (bestLocation == null) {
            Messenger.sendSMS(this, phoneNumber, r.getString(R.string.error_getting_location));
            return;
        }

        if (gpsSms) {
            Messenger.sendLocationMessage(this, bestLocation, isLocationFused(bestLocation), speedType, phoneNumber);
        }

        if (googleMapsSms) {
            Messenger.sendGoogleMapsMessage(this, bestLocation, phoneNumber);
        }

        /*if (networkSms) {
            Messenger.sendNetworkMessage(this, bestLocation, place, phoneNumber, new Messenger.OnNetworkMessageSentListener() {
                @Override
                public void onNetworkMessageSent() {
                    //Log.d(TAG, "Network Message Sent");
                }
            });
        }*/
    }

    private void readSettings() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

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
                return "Error";
        }
    }
}

