package net.gmsworld.devicelocator.broadcastreceivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.services.SmsSenderService;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

public class BootReceiver extends BroadcastReceiver {

    private final static String TAG = BootReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            Log.d(TAG, "Received Boot Broadcast");

            AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

            Intent senderIntent = new Intent(context, SmsSenderService.class);

            PreferencesUtils settings = new PreferencesUtils(context);
            final String email = settings.getString(MainActivity.NOTIFICATION_EMAIL);
            final String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL);
            senderIntent.putExtra("telegramId", telegramId);
            senderIntent.putExtra("email", email);
            //send empty phone just to send location update
            //senderIntent.putExtra("phoneNumber", "");

            PendingIntent alarmIntent = PendingIntent.getService(context, 0, intent, 0);

            alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_DAY,
                    AlarmManager.INTERVAL_HALF_DAY, alarmIntent);
        }
    }
}
