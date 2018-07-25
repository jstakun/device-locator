package net.gmsworld.devicelocator.Services;

/**
 * Created by jstakun on 04.05.17.
 */

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import net.gmsworld.devicelocator.Audio.morse.MorseSoundGenerator;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.Utilities.AbstractLocationManager;
import net.gmsworld.devicelocator.Utilities.Command;
import net.gmsworld.devicelocator.Utilities.Files;
import net.gmsworld.devicelocator.Utilities.GmsSmartLocationManager;
import net.gmsworld.devicelocator.Utilities.Network;
import net.gmsworld.devicelocator.Utilities.NotificationUtils;
import net.gmsworld.devicelocator.Utilities.RouteTrackingServiceUtils;

import org.apache.commons.lang3.StringUtils;

import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class RouteTrackingService extends Service {

    private PowerManager.WakeLock mWakeLock;

    public static final String COMMAND = "RouteTracingService.COMMAND";
    public static final int COMMAND_START = 1;
    public static final int COMMAND_STOP = 0;
    public static final int COMMAND_STOP_SHARE = 7;
    public static final int COMMAND_REGISTER_CLIENT = 2;
    public static final int COMMAND_ROUTE = 3;
    public static final int COMMAND_CONFIGURE = 4;
    public static final int COMMAND_GPS_HIGH = 5;
    public static final int COMMAND_GPS_BALANCED = 6;
    public static final int COMMAND_SHOW_ROUTE = 50;
    public static final int DEFAULT_RADIUS = 100;

    private static final int NOTIFICATION_ID = 1;

    private static final String TAG = RouteTrackingService.class.getSimpleName();
    private int radius = DEFAULT_RADIUS;

    private final Handler incomingHandler = new IncomingHandler(this);
    private final Messenger mMessenger = new Messenger(incomingHandler);
    private Messenger mClient;
    private String phoneNumber, email, telegramId, app;
    private boolean silentMode = false;

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return mMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");
        return true;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.d(TAG, "RouteTrackingService onStartCommand()");
        super.onStartCommand(intent, flags, startId);

        if (intent != null) {
            if (intent.hasExtra("radius")) {
                radius = intent.getIntExtra("radius", DEFAULT_RADIUS);
            }
            if (intent.hasExtra(COMMAND)) {
                int command = intent.getIntExtra(COMMAND, -1);
                Log.d(TAG, "onStartCommand(): " + command);
                switch (command) {
                    case COMMAND_START:
                        this.phoneNumber = intent.getStringExtra("phoneNumber");
                        this.email = intent.getStringExtra("email");
                        this.telegramId = intent.getStringExtra("telegramId");
                        this.app = intent.getStringExtra("app");
                        boolean resetRoute = intent.getBooleanExtra("resetRoute", false);
                        silentMode = intent.getBooleanExtra("silentMode", false);
                        int gpsAccuracy = PreferenceManager.getDefaultSharedPreferences(this).getInt("gpsAccuracy", 1);
                        startTracking(gpsAccuracy, resetRoute);
                        break;
                    case COMMAND_STOP:
                        stopSelf();
                        break;
                    case COMMAND_ROUTE:
                        shareRoute(intent.getStringExtra("phoneNumber"), intent.getStringExtra("telegramId"), intent.getStringExtra("email"), intent.getStringExtra("app"), false);
                        break;
                    case COMMAND_STOP_SHARE:
                        shareRoute(intent.getStringExtra("phoneNumber"), intent.getStringExtra("telegramId"), intent.getStringExtra("email"), intent.getStringExtra("app"), true);
                        break;
                    case COMMAND_CONFIGURE:
                        this.phoneNumber = intent.getStringExtra("phoneNumber");
                        this.email = intent.getStringExtra("email");
                        this.telegramId = intent.getStringExtra("telegramId");
                        //use smart location lib
                        GmsSmartLocationManager.getInstance().setRadius(radius);
                        break;
                    case COMMAND_GPS_HIGH:
                        stopTracking();
                        startTracking(1, false);
                        break;
                    case COMMAND_GPS_BALANCED:
                        stopTracking();
                        startTracking(0, false);
                        break;
                    default:
                        Log.e(TAG, "Started with wrong command: " + command);
                        break;
                }
            } else {
                Log.d(TAG, "onStartCommand()");
            }
        } else {
            Log.e(TAG, "Started without intent. This is wrong!");
        }

        return START_REDELIVER_INTENT;   //START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        //startTracking();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy()");
        stopTracking();
        Intent broadcastIntent = new Intent("net.gmsworld.devicelocator.Services.RouteTrackingServiceRestartReceiver");
        sendBroadcast(broadcastIntent);
    }

    private synchronized void startTracking(int priority, boolean resetRoute) {
        Log.d(TAG, "startTracking() in silent mode " + silentMode);

        if (this.mWakeLock != null)
        {
            this.mWakeLock.release();
            this.mWakeLock = null;
        }

        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            this.mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            this.mWakeLock.acquire();
        }

        //NotificationUtils.notify(this, NOTIFICATION_ID);
        startForeground(NOTIFICATION_ID, NotificationUtils.buildNotification(this, NOTIFICATION_ID));

        //use smart location lib
        GmsSmartLocationManager.getInstance().enable(IncomingHandler.class.getName(), incomingHandler, this, radius, priority, resetRoute);
    }

    private synchronized void stopTracking() {
        Log.d(TAG, "stopTracking()");

        //NotificationUtils.cancel(this, NOTIFICATION_ID);
        stopForeground(true);

        if (this.mWakeLock != null)
        {
            this.mWakeLock.release();
            this.mWakeLock = null;
        }

        //use smart location lib
        GmsSmartLocationManager.getInstance().disable(IncomingHandler.class.getName(), this);

        PreferenceManager.getDefaultSharedPreferences(this).edit().remove("routeTitle").apply();
    }

    private void shareRoute(final String phoneNumber, final String telegramId, final String email, final String app, final boolean stopSelf) {
        Log.d(TAG, "shareRoute()");
        if (Files.hasRoutePoints(AbstractLocationManager.ROUTE_FILE, this, 2)) {
            GmsSmartLocationManager.getInstance().executeRouteUploadTask(this, true, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                    final Intent newIntent = new Intent(RouteTrackingService.this, SmsSenderService.class);
                    if (StringUtils.isNotEmpty(phoneNumber)) {
                        newIntent.putExtra("phoneNumber", phoneNumber);
                    } else {
                        if (StringUtils.isNotEmpty(telegramId)) {
                            newIntent.putExtra("telegramId", telegramId);
                        }
                        if (StringUtils.isNotEmpty(email)) {
                            newIntent.putExtra("email", email);
                        }
                    }
                    if (StringUtils.isNotEmpty(app)) {
                        newIntent.putExtra("app", app);
                    }
                    newIntent.putExtra("command", Command.ROUTE_COMMAND);
                    if (responseCode == 200) {
                        newIntent.putExtra("size", 2); //we know only size > 1
                    } else {
                        newIntent.putExtra("size", -1);
                    }
                    RouteTrackingService.this.startService(newIntent);
                    if (stopSelf) {
                        RouteTrackingService.this.stopSelf();
                    }
                }
            });
        } else {
            final Intent newIntent = new Intent(RouteTrackingService.this, SmsSenderService.class);
            if (StringUtils.isNotEmpty(phoneNumber)) {
                newIntent.putExtra("phoneNumber", phoneNumber);
            } else {
                if (StringUtils.isNotEmpty(telegramId)) {
                    newIntent.putExtra("telegramId", telegramId);
                }
                if (StringUtils.isNotEmpty(email)) {
                    newIntent.putExtra("email", email);
                }
            }
            newIntent.putExtra("command", Command.ROUTE_COMMAND);
            newIntent.putExtra("size", 0);
            startService(newIntent);
        }
    }

    private static class IncomingHandler extends Handler {

        final MorseSoundGenerator morseSoundGenerator = new MorseSoundGenerator(44100, 800.0, 50);

        private final WeakReference<RouteTrackingService> routeTrackingService;

        public IncomingHandler(RouteTrackingService service) {
            routeTrackingService = new WeakReference<RouteTrackingService>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            RouteTrackingService service = routeTrackingService.get();
            if (service != null) {
                switch (msg.what) {
                    case COMMAND_REGISTER_CLIENT:
                        service.mClient = msg.replyTo;
                        Log.d(TAG, "New client registered");
                        break;
                    case AbstractLocationManager.UPDATE_LOCATION:
                        Log.d(TAG, "Received new location");
                        if (service.mClient != null) {
                            try {
                                Message message = Message.obtain(null, COMMAND_SHOW_ROUTE);
                                service.mClient.send(message);
                            } catch (Exception e) {
                                Log.e(TAG, e.getMessage(), e);
                            }
                        }
                        try {
                            Location location = (Location) msg.obj;
                            int distance = msg.arg1;
                            if (location != null) {
                                //TODO move this to sms sender service
                                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(service);
                                long notificationSentMillis = settings.getLong("notificationSentMillis", 0);
                                //sent notification only if not in silent mode and if last notification was at least sent 10 seconds ago
                                if (!service.silentMode && (System.currentTimeMillis() - notificationSentMillis) > 1000 * 10) {
                                    settings.edit().putLong("notificationSentMillis", System.currentTimeMillis()).apply();
                                    if (StringUtils.isNotEmpty(service.phoneNumber)) {
                                        if (settings.getBoolean("settings_gps_sms", false)) {
                                            net.gmsworld.devicelocator.Utilities.Messenger.sendLocationMessage(service, location, true, service.phoneNumber, null, null, null);
                                        }
                                        if (settings.getBoolean("settings_google_sms", true)) {
                                            net.gmsworld.devicelocator.Utilities.Messenger.sendGoogleMapsMessage(service, location, service.phoneNumber, null, null, null);
                                        }
                                    }
                                    DecimalFormat latAndLongFormat = new DecimalFormat("#.######");
                                    String message = "New location: " + latAndLongFormat.format(location.getLatitude()) + ", " + latAndLongFormat.format(location.getLongitude()) +
                                            " in distance of " + distance + " meters from previous location with accuracy " + location.getAccuracy() + " m.";
                                    if (location.hasSpeed() && location.getSpeed() > 0f) {
                                        message += " and speed " + net.gmsworld.devicelocator.Utilities.Messenger.getSpeed(service, location.getSpeed());
                                    }
                                    message += "\n" + "Battery level: " + net.gmsworld.devicelocator.Utilities.Messenger.getBatteryLevel(service) +
                                            "\n" + "https://maps.google.com/maps?q=" + latAndLongFormat.format(location.getLatitude()).replace(',', '.') + "," + latAndLongFormat.format(location.getLongitude()).replace(',', '.');

                                    final Map<String, String> headers = new HashMap<String, String>();
                                    headers.put("X-GMS-RouteId", RouteTrackingServiceUtils.getRouteId(service));
                                    //First send notification to telegram and if not configured to email
                                    //REMEMBER this could send a lot of messages and your email account could be overloaded
                                    if (StringUtils.isNotEmpty(service.telegramId)) {
                                        net.gmsworld.devicelocator.Utilities.Messenger.sendTelegram(service, location, service.telegramId, message, 1, headers);
                                    } else if (StringUtils.isNotEmpty(service.email)) {
                                        String title = service.getString(R.string.message);
                                        String deviceId = net.gmsworld.devicelocator.Utilities.Messenger.getDeviceId(service, true);
                                        if (deviceId != null) {
                                            title += " installed on device " + deviceId + " - location change";
                                        }
                                        net.gmsworld.devicelocator.Utilities.Messenger.sendEmail(service, location, service.email, message, title, 1, headers);
                                    } else {
                                        //send route point for online route tracking
                                        net.gmsworld.devicelocator.Utilities.Messenger.sendRoutePoint(service, location, 1, headers);
                                    }
                                    //send notification to cloud if tracking has been initiated with cloud message
                                    if (StringUtils.isNotEmpty(service.app)) {
                                        String[] tokens = StringUtils.split(service.app, "+=+");
                                        net.gmsworld.devicelocator.Utilities.Messenger.sendCloudMessage(service, location, tokens[0], tokens[1], message, 1, headers);
                                    }
                                }
                                //

                                //EXPERIMENTAL FEATURE audio transmitter
                                //you should plug antenna to your device audio transmitter
                                boolean useAudio = PreferenceManager.getDefaultSharedPreferences(service).getBoolean("useAudio", false);
                                if (useAudio) {
                                    synchronized (morseSoundGenerator) {
                                        final String signal = ((int) (location.getLatitude() * 1e6)) + "," + ((int) (location.getLongitude() * 1e6));
                                        Log.d(TAG, "Sending audio signal: " + signal);
                                        morseSoundGenerator.morseOnce(signal);
                                    }
                                }
                                //------------------
                            }
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage(), e);
                        }
                        break;
                    default:
                        super.handleMessage(msg);
                }
            }
        }
    }
}