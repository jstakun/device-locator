package net.gmsworld.devicelocator.services;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.broadcastreceivers.DeviceAdminEventReceiver;
import net.gmsworld.devicelocator.utilities.AbstractLocationManager;
import net.gmsworld.devicelocator.utilities.GmsSmartLocationManager;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.NotificationUtils;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.HashMap;

import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.config.LocationParams;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

public class SmsSenderService extends IntentService implements OnLocationUpdatedListener {
    private final static String TAG = SmsSenderService.class.getSimpleName();

    private final static int LOCATION_REQUEST_MAX_WAIT_TIME = 120; //seconds
    private static final int NOTIFICATION_ID = 2222;

    public static final String SEND_ACKNOWLEDGE_MESSAGE = "settings_detected_sms";
    public static final String SEND_LOCATION_MESSAGE = "settings_gps_sms";
    public static final String SEND_MAP_LINK_MESSAGE = "settings_google_sms";

    private static boolean isRunning = false;

    private String phoneNumber = null;
    private String telegramId = null;
    private String email = null;
    private String app = null;

    private Location bestLocation = null;
    private long startTime = 0;

    private PreferencesUtils settings;

    private final Handler handler = new Handler();

    private final Runnable task = new Runnable() {
        @Override
        public void run () {
            Log.d(TAG, "Finalizing location task...");
            disableUpdates();
        }
    };

    public SmsSenderService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent()");

        Bundle extras = intent.getExtras();

        settings = new PreferencesUtils(this);

        if (extras != null) {
            this.phoneNumber = extras.getString("phoneNumber");
            this.telegramId = extras.getString("telegramId");
            this.email = extras.getString("email");
            this.app = extras.getString("app");

            //Log.d(TAG, extras.toString());

            if (StringUtils.isEmpty(phoneNumber) && StringUtils.isEmpty(telegramId) && StringUtils.isEmpty(email) && StringUtils.isEmpty(app)) {
                Log.e(TAG, "Notification settings are empty!");
                stop();
                return;
            }

            final String command = extras.getString("command");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String text;
                if (StringUtils.isEmpty(command)) {
                    text = "Refreshing device location in progress...";
                } else {
                    text = "Sending device command " + command + " in progress...";
                }
                startForeground(NOTIFICATION_ID, NotificationUtils.buildWorkerNotification(this,  NOTIFICATION_ID, null, text, true));
            }

            if (StringUtils.isEmpty(command)) {
                initSending(extras.getString("source"));
            } else {
                Messenger.sendCommandMessage(this, extras);
            }
        } else {
            Log.e(TAG, "Required parameters missing!");
            stop();
        }
    }

    private void initSending(String source) {
        Log.d(TAG, "initSending() " + source);

        if (StringUtils.equals(source, DeviceAdminEventReceiver.SOURCE)) {
            Messenger.sendLoginFailedMessage(this, phoneNumber, telegramId, email, app);
        } else if (!StringUtils.equals(telegramId, getString(R.string.telegram_notification)) && settings.getBoolean(SEND_ACKNOWLEDGE_MESSAGE, true)) {
            Messenger.sendAcknowledgeMessage(this, phoneNumber, telegramId, email, app);
        }

        if (isRunning) {
            Log.d(TAG, "GPS Location service is currently running!");
        } else {
            if (SmartLocation.with(this).location().state().isAnyProviderAvailable()) {
                isRunning = true;
                //set bestLocation to null and start time
                startTime = System.currentTimeMillis() / 1000;
                bestLocation = null;
                try {
                    SmartLocation.with(this).location(new LocationGooglePlayServicesWithFallbackProvider(this))
                            .config(LocationParams.NAVIGATION).start(this);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
                handler.postDelayed(task, LOCATION_REQUEST_MAX_WAIT_TIME * 1000);
            } else if (GmsSmartLocationManager.isLocationEnabled(this)) {
                Log.e(TAG, "No Location provider is available!");
                Messenger.sendLocationErrorMessage(this, phoneNumber, telegramId, email, app);
            } else {
                Log.e(TAG, getString(R.string.internal_error));
            }
        }
    }

    private static boolean isLocationFused(Location location) {
        return !location.hasAltitude() || !location.hasSpeed() || location.getAltitude() == 0;
    }

    @Override
    public void onLocationUpdated(Location location) {
        Log.d(TAG, "onLocationUpdated() " + new Date(location.getTime()));

        long currentTime = System.currentTimeMillis() / 1000;

        if (bestLocation == null) {
            Log.d(TAG, "Setting best location.");
            bestLocation = location;
        }

        if (currentTime - startTime < LOCATION_REQUEST_MAX_WAIT_TIME) {

            Log.d(TAG, "Checking for deadline: " + (currentTime - startTime) + " < " + LOCATION_REQUEST_MAX_WAIT_TIME);

            //check if location is older than 10 minutes
            if ((System.currentTimeMillis() - location.getTime()) > 10 * 60 * 1000) {
                Log.d(TAG, "Location is older than 10 minutes");
                return;
            }

            //if (!bestLocation.getProvider().equals(LocationManager.GPS_PROVIDER) || bestLocation.getProvider().equals(location.getProvider())) {
            if (location.hasAccuracy() && bestLocation.hasAccuracy() && location.getAccuracy() < bestLocation.getAccuracy()) {
                Log.d(TAG, "Updating best location.");
                bestLocation = location;
            }
            //}

            if (bestLocation.getAccuracy() > AbstractLocationManager.MAX_REASONABLE_ACCURACY) {
                Log.d(TAG, "Accuracy is " + bestLocation.getAccuracy() + " more than max " + AbstractLocationManager.MAX_REASONABLE_ACCURACY + ", will check again.");
                return;
            }
        }

        disableUpdates();
    }

    private synchronized void disableUpdates() {
        //stop the location updates
        SmartLocation.with(this).location().stop();

        if (isRunning) {
            Log.d(TAG, "Disabling location updates...");

            handler.removeCallbacks(task);

            if (bestLocation == null) {
                Messenger.sendLocationErrorMessage(this, phoneNumber, telegramId, email, app);
            } else {
                if (!StringUtils.equals(telegramId, getString(R.string.telegram_notification)) && settings.getBoolean(SEND_LOCATION_MESSAGE, false) && isRunning) {
                    Log.d(TAG, "Sending Location details message...");
                    Messenger.sendLocationMessage(this, bestLocation, isLocationFused(bestLocation), phoneNumber, telegramId, email, app);
                } else {
                    Log.d(TAG, "Location details message won't be send");
                }

                if ((StringUtils.equals(telegramId, getString(R.string.telegram_notification)) || settings.getBoolean(SEND_MAP_LINK_MESSAGE, true)) && isRunning) {
                    Log.d(TAG, "Sending Google Maps link message...");
                    Messenger.sendGoogleMapsMessage(this, bestLocation, phoneNumber, telegramId, email, app);
                } else {
                    Log.d(TAG, "Google Maps link message won't be send");
                }
                //send remote location message only for sms or local device
                final String thisDeviceId = Messenger.getDeviceId(this, false);
                if (telegramId == null && email == null && (app == null || StringUtils.startsWith(app, thisDeviceId))) {
                    Messenger.sendCloudMessage(this, bestLocation, thisDeviceId, null, null, 2, 2000, new HashMap<String, String>());
                }
            }
            isRunning = false;
        }

        stop();
    }

    public static boolean initService(final Context context, final boolean usePhoneNumber, final boolean useEmail, final boolean useTelegramId,
                                   final String app, final String command, final String sender, final String source, final Bundle extras) {
        PreferencesUtils settings = new PreferencesUtils(context);
        Intent smsSender = new Intent(context, SmsSenderService.class);

        String email = null;
        if (useEmail) {
            if (extras != null && extras.containsKey("email")) {
                email = extras.getString("email");
            } else if (Messenger.isEmailVerified(settings)) {
                email = settings.getString(MainActivity.NOTIFICATION_EMAIL);
                if (StringUtils.isNotEmpty(email)) {
                    smsSender.putExtra("email", email);
                }
            }
        }

        String telegramId = null;
        if (useTelegramId) {
            if (extras != null && extras.containsKey("telegramId")) {
                telegramId = extras.getString("telegramId");
            } else if (Messenger.isTelegramVerified(settings)) {
                telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL);
                if (StringUtils.isNotEmpty(telegramId)) {
                    smsSender.putExtra("telegramId", telegramId);
                }
            } else if (extras != null && extras.containsKey("adminTelegramId")) {
                telegramId = extras.getString("adminTelegramId");
                if (StringUtils.isNotEmpty(telegramId)) {
                    smsSender.putExtra("telegramId", telegramId);
                }
            }
        }

        String phoneNumber = null;
        if (usePhoneNumber) {
            if (extras != null && extras.containsKey("phoneNumber")) {
                phoneNumber = extras.getString("phoneNumber");
            } else {
                phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER);
                if (StringUtils.isNotEmpty(phoneNumber)) {
                    smsSender.putExtra("phoneNumber", phoneNumber);
                }
            }
        }

        if (StringUtils.isEmpty(phoneNumber) && StringUtils.isEmpty(telegramId) && StringUtils.isEmpty(email) && StringUtils.isEmpty(app)) {
            Log.d(TAG, context.getString(R.string.notifiers_error));
            return false;
        } else {
            if (StringUtils.isNotEmpty(app)) {
                smsSender.putExtra("app", app);
            }

            if (StringUtils.isNotEmpty(command)) {
                smsSender.putExtra("command", command);
            }

            if (StringUtils.isNotEmpty(sender)) {
                smsSender.putExtra("sender", sender);
            }

            if (StringUtils.isNotEmpty(source)) {
                smsSender.putExtra("source", source);
            }

            if (extras != null && !extras.isEmpty()) {
                smsSender.putExtras(extras);
            }

            ComponentName name;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                name = context.startForegroundService(smsSender);
            } else {
                name = context.startService(smsSender);
            }
            if (name != null) {
                Log.d(TAG, "Service " + name.getClassName() + " started...");
            }

            return true;
        }
    }

    private void stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        } else {
            stopSelf();
        }
    }
}

