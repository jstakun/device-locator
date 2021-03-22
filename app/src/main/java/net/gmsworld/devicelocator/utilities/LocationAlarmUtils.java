package net.gmsworld.devicelocator.utilities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import net.gmsworld.devicelocator.broadcastreceivers.LocationAlarmReceiver;

import java.util.Date;

public class LocationAlarmUtils {

    private static final String TAG = LocationAlarmUtils.class.getName();
    public static final String ALARM_KEY = "LocationAlarmTriggerMillis";
    public static final String ALARM_INTERVAL = "LocationAlarmIntervalHours";
    public static final String ALARM_SETTINGS = "settings_alarm";
    public static final String ALARM_SILENT = "LocationAlarmUtilsSilent";
    public static final int ALARM_INTERVAL_VALUE = 1;

    public static void initWhenDown(Context context, boolean forceReset) {
        PreferencesUtils settings = new PreferencesUtils(context);
        long alarmInterval;

        if (settings.getBoolean(ALARM_SETTINGS, false)) {
            alarmInterval = settings.getInt(ALARM_INTERVAL, ALARM_INTERVAL_VALUE) * AlarmManager.INTERVAL_HOUR;
        } else {
            alarmInterval = AlarmManager.INTERVAL_DAY;
        }

        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent senderIntent = new Intent(context, LocationAlarmReceiver.class);

        if (alarmMgr != null && (forceReset || (PendingIntent.getBroadcast(context, 0, senderIntent, PendingIntent.FLAG_NO_CREATE) == null))) {
           final long triggerAtMillis = System.currentTimeMillis() + alarmInterval;
           Log.d(TAG, "Next Location Alarm will be triggered at " + new Date(triggerAtMillis));
           final PendingIntent operation = PendingIntent.getBroadcast(context, 0, senderIntent, PendingIntent.FLAG_UPDATE_CURRENT);
           if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
               alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
           } else {
               alarmMgr.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmInterval, operation);
           }
           settings.setLong(ALARM_KEY, triggerAtMillis);
        } else {
            Log.d(TAG, "Next location alarm will be triggered at " + new Date(settings.getLong(ALARM_KEY)));
        }
    }

    public static void cancel(Context context) {
        //AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        //Intent senderIntent = new Intent(context, LocationAlarmReceiver.class);
        //PendingIntent intent = PendingIntent.getBroadcast(context, 0, senderIntent, PendingIntent.FLAG_NO_CREATE);
        //if (intent != null && alarmMgr != null) {
        //    alarmMgr.cancel(intent);
        //}
        PreferencesUtils settings = new PreferencesUtils(context);
        settings.setBoolean(ALARM_SETTINGS, false);
        settings.remove(ALARM_KEY, ALARM_INTERVAL, ALARM_SILENT);
    }
}
