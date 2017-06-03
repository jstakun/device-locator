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

import net.gmsworld.devicelocator.Utilities.GmsLocationManager;
import net.gmsworld.devicelocator.Utilities.Network;
import net.gmsworld.devicelocator.Utilities.NotificationUtils;

public class RouteTrackingService extends Service {

    private PowerManager.WakeLock mWakeLock;

    public static final String COMMAND = "RouteTracingService.COMMAND";
    public static final int COMMAND_START = 1;
    public static final int COMMAND_STOP = 0;
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

    private final Handler incomingHandler = new IncomingHandler();
    private final Messenger mMessenger = new Messenger(incomingHandler);
    private Messenger mClient;
    private String phoneNumber, email, telegramId;
    private long startTime;

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
                        this.phoneNumber = intent.getExtras().getString("phoneNumber");
                        this.email = intent.getExtras().getString("email");
                        this.telegramId = intent.getExtras().getString("telegramId");
                        boolean resetRoute = intent.getBooleanExtra("resetRoute", false);
                        int gpsAccuracy = PreferenceManager.getDefaultSharedPreferences(this).getInt("gpsAccuracy", 0);
                        startTracking(gpsAccuracy, resetRoute);
                        break;
                    case COMMAND_STOP:
                        stopTracking();
                        break;
                    case COMMAND_ROUTE:
                        String title = intent.getStringExtra("title");
                        //don't set phoneNumber here
                        //this.phoneNumber = intent.getExtras().getString("phoneNumber");
                        shareRoute(title, intent.getExtras().getString("phoneNumber"));
                        break;
                    case COMMAND_CONFIGURE:
                        this.phoneNumber = intent.getExtras().getString("phoneNumber");
                        this.email = intent.getExtras().getString("email");
                        this.telegramId = intent.getExtras().getString("telegramId");
                        GmsLocationManager.getInstance().setRadius(radius);
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
            Log.e(TAG, "Started without intent. This is wrong! --------------------------------------");
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
        Log.d(TAG, "startTracking()");

        if (this.mWakeLock != null)
        {
            this.mWakeLock.release();
            this.mWakeLock = null;
        }

        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        this.mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        this.mWakeLock.acquire();

        startTime = System.currentTimeMillis();

        //NotificationUtils.notify(this, NOTIFICATION_ID);
        startForeground(NOTIFICATION_ID, NotificationUtils.buildNotification(this, NOTIFICATION_ID));

        //TODO add support for non google api compliant devices
        //if (!IntentsHelper.getInstance().isGoogleApiAvailable(this)) {
        //    GpsDeviceFactory.initGpsDevice(this, incomingHandler);
        //    GpsDeviceFactory.startDevice();
        //} else {
            GmsLocationManager.getInstance().enable(IncomingHandler.class.getName(), incomingHandler, this, radius, priority, resetRoute);
        //}
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

        //TODO add support for non google api compliant devices
        //if (!IntentsHelper.getInstance().isGoogleApiAvailable(this)) {
        //    GpsDeviceFactory.stopDevice();
        //} else {
            GmsLocationManager.getInstance().disable(IncomingHandler.class.getName());
        //}
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.remove("routeTitle");
        editor.commit();
    }

    private void shareRoute(String title, String phoneNumber) {
        Log.d(TAG, "shareRoute()");
        GmsLocationManager.getInstance().uploadRouteToServer(this, title, phoneNumber, startTime, true);
    }

    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case COMMAND_REGISTER_CLIENT:
                    mClient = msg.replyTo;
                    Log.d(TAG, "new client registered");
                    break;
                case GmsLocationManager.UPDATE_LOCATION:
                    Log.d(TAG, "received new location");
                    if (mClient != null) {
                        try {
                            Message message = Message.obtain(null, COMMAND_SHOW_ROUTE);
                            mClient.send(message);
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage(), e);
                        }
                    }
                    try {
                        Location location = (Location) msg.obj;
                        int distance = msg.arg1;
                        if (location != null) {
                            if (phoneNumber != null && phoneNumber.length() > 0) {
                                net.gmsworld.devicelocator.Utilities.Messenger.sendLocationMessage(RouteTrackingService.this, location, true, 0, phoneNumber);
                                net.gmsworld.devicelocator.Utilities.Messenger.sendGoogleMapsMessage(RouteTrackingService.this, location, phoneNumber);
                            }
                            String message = "New location: " + location.getLatitude() + "," + location.getLongitude() +
                                             " in distance of " + distance + " meters with accuracy " + location.getAccuracy() + " m." +
                                             "\nBattery level: " + net.gmsworld.devicelocator.Utilities.Messenger.getBatteryLevel(RouteTrackingService.this) + " p." +
                                             "\nhttps://maps.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();

                            //Log.d(TAG, message);
                            if (email != null && email.length() > 0) {
                                String title = "Message from Device Locator";
                                String deviceId = Network.getDeviceId(RouteTrackingService.this);
                                if (deviceId != null) {
                                    title += " installed on device " + deviceId;
                                }
                                net.gmsworld.devicelocator.Utilities.Messenger.sendEmail(RouteTrackingService.this, email, message, title, 1);
                            }

                            if (telegramId != null && telegramId.length() > 0) {
                                message = message.replace("\n", ", ");
                                net.gmsworld.devicelocator.Utilities.Messenger.sendTelegram(RouteTrackingService.this, telegramId, message, 1);
                            }
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