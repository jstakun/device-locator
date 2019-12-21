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
    public static final String ALARM_KEY = "locationAlarm";

    public static void initWhenDown(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent senderIntent = new Intent(context, SmsSenderService.class);
        PreferencesUtils settings = new PreferencesUtils(context);

        //TODO remove after testing
        final String email = settings.getString(MainActivity.NOTIFICATION_EMAIL);
        final String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL);
        senderIntent.putExtra("telegramId", telegramId);
        senderIntent.putExtra("email", email);
        //
        //send empty phone just to send location update
        senderIntent.putExtra("phoneNumber", "");

        boolean alarmDown = (PendingIntent.getService(context, 0, senderIntent, PendingIntent.FLAG_NO_CREATE) == null);
        if (alarmDown) {
            final long triggerAtMillis = System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR;
            Log.d(TAG, "Creating Location Alarm to be triggered at " + new Date(triggerAtMillis));
            final PendingIntent operation = PendingIntent.getService(context, 0, senderIntent, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            } else {
                alarmMgr.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerAtMillis, AlarmManager.INTERVAL_HOUR, operation);
            }
        } else {
            Log.d(TAG, "Location Alarm has been set on " + new Date(settings.getLong(ALARM_KEY)));
            settings.setLong(ALARM_KEY, System.currentTimeMillis());
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
