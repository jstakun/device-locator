package net.gmsworld.devicelocator.services;

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

import net.gmsworld.devicelocator.morse.MorseSoundGenerator;
import net.gmsworld.devicelocator.utilities.AbstractLocationManager;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.Files;
import net.gmsworld.devicelocator.utilities.GmsSmartLocationManager;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.NotificationUtils;
import net.gmsworld.devicelocator.utilities.RouteTrackingServiceUtils;

import org.apache.commons.lang3.StringUtils;

import java.lang.ref.WeakReference;

public class RouteTrackingService extends Service {

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
    public static final int DEFAULT_RADIUS = 100; //meters
    public static final int DEFAULT_PERIMETER = 500; //meters

    private static final int NOTIFICATION_ID = 1001;
    //public static final int NOTIFICATION_ROUTE_ID = 1002;

    private static final String TAG = RouteTrackingService.class.getSimpleName();
    public static final String GPS_ACCURACY = "gpsAccuracy"; //1>= high 0<= balanced

    public enum Mode {Normal, Silent, Perimeter};

    private PowerManager.WakeLock mWakeLock;
    private final Handler incomingHandler = new IncomingHandler(this);
    private final Messenger mMessenger = new Messenger(incomingHandler);
    private Messenger mClient;
    private int radius = DEFAULT_RADIUS;
    private String phoneNumber, email, telegramId, app;
    private static Mode mode = Mode.Normal;

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
                        if (intent.hasExtra("mode")) {
                            mode = Mode.valueOf(intent.getStringExtra("mode"));
                        }
                        int gpsAccuracy = PreferenceManager.getDefaultSharedPreferences(this).getInt(GPS_ACCURACY, 1);
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

    private synchronized void startTracking(int gpsAccuracy, boolean resetRoute) {
        Log.d(TAG, "startTracking() in mode " + mode.name());

        PreferenceManager.getDefaultSharedPreferences(this).edit().remove(RouteTrackingServiceUtils.ROUTE_TITLE).apply();

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

        startForeground(NOTIFICATION_ID, NotificationUtils.buildTrackerNotification(this, NOTIFICATION_ID));

        //use smart location lib
        GmsSmartLocationManager.getInstance().enable(IncomingHandler.class.getName(), incomingHandler, this, radius, gpsAccuracy, resetRoute);
    }

    private synchronized void stopTracking() {
        Log.d(TAG, "stopTracking()");

        stopForeground(true);

        if (this.mWakeLock != null)
        {
            this.mWakeLock.release();
            this.mWakeLock = null;
        }

        //use smart location lib
        GmsSmartLocationManager.getInstance().disable(IncomingHandler.class.getName(), this);
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
            if (stopSelf) {
                RouteTrackingService.this.stopSelf();
            }
        }
    }

    private static class IncomingHandler extends Handler {

        final MorseSoundGenerator morseSoundGenerator = new MorseSoundGenerator(44100, 800.0, 50);

        private final WeakReference<RouteTrackingService> routeTrackingService;

        IncomingHandler(RouteTrackingService service) {
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
                                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(service);

                                long notificationSentMillis = settings.getLong("notificationSentMillis", 0);
                                //sent notification only if not in silent mode and if last notification was at least sent 10 seconds ago
                                if (mode == Mode.Normal && (System.currentTimeMillis() - notificationSentMillis) > 1000 * 10) {
                                    settings.edit().putLong("notificationSentMillis", System.currentTimeMillis()).apply();
                                    net.gmsworld.devicelocator.utilities.Messenger.sendRouteMessage(service, location, distance, service.phoneNumber, service.telegramId, service.email, service.app);
                                } else if (mode == Mode.Perimeter && (System.currentTimeMillis() - notificationSentMillis) > 1000 * 10) {
                                    settings.edit().putLong("notificationSentMillis", System.currentTimeMillis()).apply();
                                    net.gmsworld.devicelocator.utilities.Messenger.sendPerimeterMessage(service, location, service.app);
                                } else {
                                    Log.d(TAG, "No notification will be sent in mode " + mode.name());
                                }

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