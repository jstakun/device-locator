package net.gmsworld.devicelocator.services;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import net.gmsworld.devicelocator.broadcastreceivers.ScreenStatusBroadcastReceiver;
import net.gmsworld.devicelocator.utilities.NotificationUtils;

public class ScreenStatusService extends Service {

    private static final String TAG = ScreenStatusService.class.getSimpleName();
    private static final int NOTIFICATION_ID = 3333;

    public static final String RUNNING = "screenMonitorRunning";
    public static final String COMMAND = "ScreenStatusService.COMMAND";
    public static final int COMMAND_START = 1;
    public static final int COMMAND_STOP = 0;


    private static ScreenStatusBroadcastReceiver mScreenReceiver;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent != null) {
            if (intent.hasExtra(COMMAND)) {
                final int command = intent.getIntExtra(COMMAND, -1);
                Log.d(TAG, "ScreenStatusService onStartCommand(): " + command);
                switch (command) {
                    case COMMAND_START:
                        registerScreenStatusReceiver();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForeground(NOTIFICATION_ID, NotificationUtils.buildWorkerNotification(this, null, "Screen activity monitor is running...", false));
                        }
                        break;
                    case COMMAND_STOP:
                        stop();
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

        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        unregisterScreenStatusReceiver();
        Intent broadcastIntent = new Intent("net.gmsworld.devicelocator.Services.ServiceRestartReceiver");
        sendBroadcast(broadcastIntent);
    }

    private void registerScreenStatusReceiver() {
        if (mScreenReceiver == null) {
            mScreenReceiver = new ScreenStatusBroadcastReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(mScreenReceiver, filter);
        }
    }

    private void unregisterScreenStatusReceiver() {
        try {
            if (mScreenReceiver != null) {
                unregisterReceiver(mScreenReceiver);
            }
        } catch (IllegalArgumentException e) {
        }
    }

    private void stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        } else {
            stopSelf();
        }
    }

    public static void initService(final Context context) {
        ComponentName name;
        Intent intent = new Intent(context, ScreenStatusService.class);
        intent.putExtra(COMMAND, COMMAND_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            name = context.startForegroundService(intent);
        } else {
            name = context.startService(intent);
        }
        if (name != null) {
            Log.d(TAG, "Service " + name.getClassName() + " started...");
        }
    }

    public static void stopService(final Context context) {
        ComponentName name;
        Intent intent = new Intent(context, ScreenStatusService.class);
        intent.putExtra(COMMAND, COMMAND_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            name = context.startForegroundService(intent);
        } else {
            name = context.startService(intent);
        }
        if (name != null) {
            Log.d(TAG, "Service " + name.getClassName() + " stop...");
        }
    }
}

