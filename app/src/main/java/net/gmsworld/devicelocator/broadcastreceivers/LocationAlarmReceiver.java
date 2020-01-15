package net.gmsworld.devicelocator.broadcastreceivers;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.services.SmsSenderService;
import net.gmsworld.devicelocator.utilities.LocationAlarmUtils;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;

public class LocationAlarmReceiver extends BroadcastReceiver {

    private final static String TAG = "LocationAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received broadcast...");
        PreferencesUtils settings = new PreferencesUtils(context);
        Intent senderIntent = new Intent(context, SmsSenderService.class);

        final String email = settings.getString(MainActivity.NOTIFICATION_EMAIL);
        final String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER);
        String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL);
        if (StringUtils.isEmpty(telegramId)) {
            telegramId = context.getString(R.string.telegram_notification);
        }
        senderIntent.putExtra("telegramId", telegramId);
        senderIntent.putExtra("email", email);
        senderIntent.putExtra("phoneNumber", phoneNumber);

        //for silent mode send empty phone just to send location update
        //senderIntent.putExtra("phoneNumber", "");

        ComponentName name = context.startService(senderIntent);
        Log.d(TAG, "Service " + name.getClassName() + " started after broadcast");

        LocationAlarmUtils.initWhenDown(context, true);
    }
}
