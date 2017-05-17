package com.gmsworld.devicelocator.Utilities;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import com.gmsworld.devicelocator.MainActivity;
import net.gmsworld.locatedriver.R;

import java.util.ArrayList;

public class Permissions {

    public static void checkAndRequestPermissions(Activity activity) {
        ArrayList<String> permissions = new ArrayList();
        permissions.add(0, Manifest.permission.SEND_SMS);
        permissions.add(1, Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(2, Manifest.permission.READ_CONTACTS);
        permissions.add(3, Manifest.permission.READ_PHONE_STATE);
        permissions.add(4, Manifest.permission.MODIFY_AUDIO_SETTINGS);
        permissions.add(5, Manifest.permission.CALL_PHONE);

        ArrayList<String> neededPermissions = new ArrayList<>();
        for (int i = 0; i < permissions.size(); i++) {
            if (ContextCompat.checkSelfPermission(activity, permissions.get(i)) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(permissions.get(i));
            }
        }

        if(neededPermissions.size() == 0) {
            return;
        }

        String[] arr = new String[neededPermissions.size()];
        arr = neededPermissions.toArray(arr);

        if(arr == null) {
            return;
        }

        ActivityCompat.requestPermissions(activity, arr, 1);
    }

    public static void requestSendSMSAndLocationPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION}, 2);
    }

    public static void requestContactsPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE}, 2);
    }

    public static void requestAllRequiredPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.MODIFY_AUDIO_SETTINGS, Manifest.permission.CALL_PHONE}, 2);
    }

    public static boolean haveSendSMSAndLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveAllRequiredPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.MODIFY_AUDIO_SETTINGS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveReadContactsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    public static void setPermissionNotification(Context context) {
        Intent resultIntent = new Intent(context, MainActivity.class);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificaitonBuilder = new NotificationCompat.Builder(context)
                //.setSmallIcon(R.drawable.notification_icon)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(R.string.notification_content))
                .setContentIntent(resultPendingIntent)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL);

        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(0, notificaitonBuilder.build());
    }
}
