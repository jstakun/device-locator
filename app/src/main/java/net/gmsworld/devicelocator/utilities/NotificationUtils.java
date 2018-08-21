package net.gmsworld.devicelocator.utilities;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.media.RingtoneManager;
import android.net.Uri;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;

/**
 * Created by jstakun on 6/1/17.
 */

public class NotificationUtils {

    public static Notification buildTrackerNotification(Context context, int notificationId) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, notificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Bitmap icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_large);

        final String message = "Device Locator is tracking location of your device. Click to open Device Locator";

        return new Notification.Builder(context)
                .setContentTitle("Device Locator")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_location_on_white)
                .setLargeIcon(icon)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                //.setPublicVersion(publicNotification) //API 21
                .build();
    }

    public static Notification buildMessageNotification(Context context, int notificationId, String message, Location location) {
        PendingIntent contentIntent = null;
        //TODO add distance from current location to message
        if (location != null) {
            Uri gmmIntentUri = Uri.parse("geo:" + location.getLatitude() + "," + location.getLongitude());
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            contentIntent = PendingIntent.getActivity(context, notificationId, mapIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            String[] tokens = message.split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i].startsWith("http://") || tokens[i].startsWith("https://")) {
                    Uri webpage = Uri.parse(tokens[i]);
                    Intent notificationIntent = new Intent(Intent.ACTION_VIEW, webpage);
                    contentIntent = PendingIntent.getActivity(context, notificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    break;
                }
            }
        }

        Bitmap icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_large);

        Uri notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        Notification.Builder nb = new Notification.Builder(context)
                .setContentTitle("Device Locator Notification")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_devices_other_white_24dp)
                .setLargeIcon(icon)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setSound(notificationUri);
                //.setPriority(1)
                //.setCategory(Notification.CATEGORY_MESSAGE) //API 21
                //.setOngoing(false)
                //.setPublicVersion(publicNotification) //API 21

        if (contentIntent != null) {
            nb.setContentIntent(contentIntent);
        }

        return nb.build();
    }

    public static void notify(Context context, int notificationId) {
        Notification notification = buildTrackerNotification(context, notificationId);

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
