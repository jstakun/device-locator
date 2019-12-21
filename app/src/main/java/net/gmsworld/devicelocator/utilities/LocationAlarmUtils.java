package net.gmsworld.devicelocator.utilities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.services.SmsSenderService;

import java.util.Date;

public class LocationAlarmUtils {

    private static final String TAG = LocationAlarmUtils.class.getName();
    private static final String ALARM_KEY = "locationAlarm";

    public static void initWhenDown(Context context, boolean forceReset) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent senderIntent = new Intent(context, SmsSenderService.class);
        PreferencesUtils settings = new PreferencesUtils(context);

        final String email = settings.getString(MainActivity.NOTIFICATION_EMAIL);
        final String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL);
        final String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER);
        senderIntent.putExtra("telegramId", telegramId);
        senderIntent.putExtra("email", email);
        senderIntent.putExtra("phoneNumber", phoneNumber);

        //for silent mode send empty phone just to send location update
        //senderIntent.putExtra("phoneNumber", "");

        if (forceReset || (PendingIntent.getService(context, 0, senderIntent, PendingIntent.FLAG_NO_CREATE) == null)) {
            final long triggerAtMillis = System.currentTimeMillis() + AlarmManager.INTERVAL_DAY;
            Log.d(TAG, "Creating Location Alarm to be triggered at " + new Date(triggerAtMillis));
            final PendingIntent operation = PendingIntent.getService(context, 0, senderIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            } else {
                alarmMgr.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerAtMillis, AlarmManager.INTERVAL_DAY, operation);
            }
            settings.setLong(ALARM_KEY, System.currentTimeMillis());
        } else {
            Log.d(TAG, "Location Alarm has been set on " + new Date(settings.getLong(ALARM_KEY)) + " with interval " + AlarmManager.INTERVAL_DAY + "ms");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (alarmMgr.getNextAlarmClock() != null) {
                long nextAlarm = alarmMgr.getNextAlarmClock().getTriggerTime();
                if (nextAlarm > 0) {
                    Log.d(TAG, "Next alarm will be triggered on " + new Date(nextAlarm));
                }
            }
        }
    }

    public static void cancel(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent senderIntent = new Intent(context, SmsSenderService.class);
        PendingIntent intent = PendingIntent.getService(context, 0, senderIntent, PendingIntent.FLAG_NO_CREATE);
        if (intent != null) {
            alarmMgr.cancel(intent);
            PreferencesUtils settings = new PreferencesUtils(context);
            settings.remove(ALARM_KEY);
        }
    }
}
