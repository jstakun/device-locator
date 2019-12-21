package net.gmsworld.devicelocator.utilities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.services.SmsSenderService;

import java.util.Date;

public class LocationAlarmUtils {

    private static final String TAG = LocationAlarmUtils.class.getName();
    private static final String ALARM_KEY = "locationAlarmSetTime";

    public static void initWhenDown(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent senderIntent = new Intent(context, SmsSenderService.class);
        PreferencesUtils settings = new PreferencesUtils(context);

        //TODO remove after testing
        final String email = settings.getString(MainActivity.NOTIFICATION_EMAIL);
        final String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL);
        senderIntent.putExtra("telegramId", telegramId);
        senderIntent.putExtra("email", email);

        //send empty phone just to send location update
        //senderIntent.putExtra("phoneNumber", "");
        boolean alarmDown = (PendingIntent.getService(context, 0, senderIntent, PendingIntent.FLAG_NO_CREATE) == null);
        if (alarmDown) {
            Log.d(TAG, "Creating Location Alarm");
            alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(),
                    AlarmManager.INTERVAL_HOUR, PendingIntent.getService(context, 0, senderIntent, 0));
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
