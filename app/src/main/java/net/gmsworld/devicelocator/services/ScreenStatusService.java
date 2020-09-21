package net.gmsworld.devicelocator.services;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.broadcastreceivers.ScreenStatusBroadcastReceiver;
import net.gmsworld.devicelocator.utilities.Files;
import net.gmsworld.devicelocator.utilities.NotificationUtils;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.util.List;

public class ScreenStatusService extends Service {

    private static final String TAG = ScreenStatusService.class.getSimpleName();
    private static final int NOTIFICATION_ID = 3333;

    public static final String RUNNING = "screenMonitorRunning";
    public static final String COMMAND = "ScreenStatusService.COMMAND";
    public static final int COMMAND_START = 1;
    public static final int COMMAND_STOP = 0;


    private PreferencesUtils settings;
    private static ScreenStatusBroadcastReceiver mScreenReceiver;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        settings = new PreferencesUtils(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent != null) {
            if (intent.hasExtra(COMMAND)) {
                final int command = intent.getIntExtra(COMMAND, -1);
                Log.d(TAG, "onStartCommand(): " + command);
                switch (command) {
                    case COMMAND_START:
                        if (mScreenReceiver == null) {
                            registerScreenStatusReceiver();
                            settings.setBoolean(RUNNING, true);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForeground(NOTIFICATION_ID, NotificationUtils.buildMonitorNotification(this, NOTIFICATION_ID, getString(R.string.notification_monitor)));
                            }
                            if (isScreenOn()) {
                                ScreenStatusBroadcastReceiver.persistScreenStatus(this, Intent.ACTION_SCREEN_ON);
                            } else {
                                ScreenStatusBroadcastReceiver.persistScreenStatus(this, Intent.ACTION_SCREEN_OFF);
                            }
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
            Log.d(TAG, "Registered broadcast receiver");
            Files.deleteFileFromContextDir(ScreenStatusBroadcastReceiver.SCREEN_FILE, this, false);
        }
    }

    private void unregisterScreenStatusReceiver() {
        try {
            if (mScreenReceiver != null) {
                unregisterReceiver(mScreenReceiver);
                Log.d(TAG, "Unregistered broadcast receiver");
                mScreenReceiver = null;
            }
        } catch (IllegalArgumentException e) {
        }
    }

    private void stop() {
        unregisterScreenStatusReceiver();
        if (isScreenOn()) {
            ScreenStatusBroadcastReceiver.persistScreenStatus(this, Intent.ACTION_SCREEN_ON);
        } else {
            ScreenStatusBroadcastReceiver.persistScreenStatus(this, Intent.ACTION_SCREEN_OFF);
        }
        settings.setBoolean(RUNNING, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        } else {
            stopSelf();
        }
    }

    private boolean isScreenOn() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        return powerManager.isScreenOn();
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
            Log.d(TAG, "Service " + name.getClassName() + " stopped...");
        }
    }

    public static String readScreenActivityLog(Context context) {
        final List<String> logs = Files.readFileByLinesFromContextDir(ScreenStatusBroadcastReceiver.SCREEN_FILE, context);
        long total = 0, endTime = 0, startTime = 0, oldestTime = 0;
        for (String log : logs) {
            //0 or 1,milliseconds
            final String[] tokens = StringUtils.split(log, ",");
            if (tokens.length >= 2) {
                //Log.d(TAG, tokens[0] + " - " + tokens[1]);
                if (StringUtils.equals(tokens[0], "0")) {
                    endTime = Long.parseLong(tokens[1]);
                } else if (StringUtils.equals(tokens[0], "1")) {
                    startTime = Long.parseLong(tokens[1]);
                }
                if (oldestTime == 0 && startTime > 0) {
                    oldestTime = startTime;
                } else if (oldestTime == 0 && endTime > 0) {
                    oldestTime = endTime;
                }
                if (endTime > startTime && endTime > 0 && startTime > 0) {
                    total += endTime - startTime;
                }
            }
        }
        if (total > 0 && oldestTime > 0) {
            String duration;
            final long timespan = System.currentTimeMillis() - oldestTime;
            if (total > 60000) { //1 min
                duration = DurationFormatUtils.formatDuration(total, "HH 'hrs' mm 'mins'", true) + " during last " + DurationFormatUtils.formatDuration(timespan, "HH 'hrs' mm 'mins'", true);
            } else if (timespan > 60000) { //1 min
                duration = DurationFormatUtils.formatDuration(total, "ss 'sec'", true) + " during last " + DurationFormatUtils.formatDuration(timespan, "HH 'hrs' mm 'mins'", true);
            } else {
                duration = DurationFormatUtils.formatDuration(total, "ss 'sec'", true) + " during last " + DurationFormatUtils.formatDuration(timespan, "ss 'sec'", true);
            }
            return duration;
        } else {
            return null;
        }
    }
}

