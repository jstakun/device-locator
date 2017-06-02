package net.gmsworld.devicelocator.Utilities;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;

/**
 * Created by jstakun on 6/1/17.
 */

public class NotificationUtils {

    public static Notification buildNotification(Context context, int notificationId) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, notificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        /*Notification publicNotification = new Notification.Builder(context)
                .setContentTitle("Device Locator is on")
                .setContentText("Click to open")
                .setSmallIcon(R.drawable.ic_small)
                //.setLargeIcon()
                .setContentIntent(contentIntent)
                .build();*/

        Bitmap icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_large);

        return new Notification.Builder(context)
                .setContentTitle("Device Locator is tracking location of your device")
                .setContentText("Click to open Device Locator")
                .setSmallIcon(R.drawable.ic_small)
                .setLargeIcon(icon)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                //.setPublicVersion(publicNotification) //API 21
                .build();
    }

    public static void notify(Context context, int notificationId) {
        Notification notification = buildNotification(context, notificationId);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(notificationId, notification);
        }
    }

    public static void cancel(Context context, int notificationId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.cancel(notificationId);
        }
    }
}
