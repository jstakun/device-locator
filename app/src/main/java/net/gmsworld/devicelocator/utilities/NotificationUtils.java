package net.gmsworld.devicelocator.utilities;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;

import org.apache.commons.lang3.StringUtils;

import java.text.DecimalFormat;

import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

/**
 * Created by jstakun on 6/1/17.
 */

public class NotificationUtils {

    private static final DecimalFormat distanceFormat = new DecimalFormat("#.##");

    private static NotificationChannel channel = null;

    public static Notification buildTrackerNotification(Context context, int notificationId) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, notificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Bitmap icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_large);

        final String message = "Device Locator is tracking your device location. Click to open Device Locator";

        initChannels(context, Messenger.getDeviceName());

        return new NotificationCompat.Builder(context, Messenger.getDeviceName())
                .setContentTitle("Device Locator")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_location_on_white)
                .setLargeIcon(icon)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(true)
                //.setPublicVersion(publicNotification) //API 21
                .build();
    }

    public static Notification buildMessageNotification(Context context, int notificationId, String message, Location location) {
        PendingIntent contentIntent = null;
        String deviceName = null;

        if (location != null) {
            //message has location
            String device = "Your+Device";
            Bundle b = location.getExtras();
            if (b != null && b.containsKey(MainActivity.DEVICE_NAME)) {
                deviceName = b.getString(MainActivity.DEVICE_NAME);
                device = "Device+" + deviceName;
            }
            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + location.getLatitude() + "," + location.getLongitude() + "(" + device + ")");
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
                contentIntent = PendingIntent.getActivity(context, notificationId, mapIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }

            Location lastLocation = SmartLocation.with(context).location(new LocationGooglePlayServicesWithFallbackProvider(context)).getLastLocation();
            if (lastLocation != null && (System.currentTimeMillis() - lastLocation.getTime() < 10 * 60 * 1000)) { //10 min
                int distance = (int)location.distanceTo(lastLocation); //in meters
                if (distance > 0) {
                    message += "\n" + DistanceFormatter.format(distance) + " away";
                }
            }
        }

        if (contentIntent == null) {
            String[] tokens = message.split("\\s+");
            //message has no maps intent
            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i].startsWith("http://") || tokens[i].startsWith("https://")) {
                    Uri webpage = Uri.parse(tokens[i]);
                    Intent notificationIntent = new Intent(Intent.ACTION_VIEW, webpage);
                    contentIntent = PendingIntent.getActivity(context, notificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    break;
                }
            }
        } else {
            //message has maps intent
            String[] tokens = message.split("\n");
            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i].startsWith(Messenger.MAPS_URL_PREFIX)) {
                    tokens[i] = "Click to open Google Maps";
                    break;
                }
            }
            message = StringUtils.join(tokens, "\n");
        }

        Bitmap icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_large);

        Uri notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        initChannels(context, deviceName);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(context, deviceName)
                .setContentTitle("Device Locator Notification")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_devices_other_white_24dp)
                .setLargeIcon(icon)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setSound(notificationUri)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE);
                //.setOngoing(false)
                //.setPublicVersion(publicNotification) //API 21

        if (contentIntent != null) {
            nb.setContentIntent(contentIntent);
        }

        return nb.build();
    }

    public static void cancel(Context context, int notificationId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.cancel(notificationId);
        }
    }

    private static void initChannels(Context context, String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channel == null) {
            if (channelId == null) {
                channelId = "Device-Locator";
            }
            final String channelName = channelId.replace('-', ' ');
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            channel = notificationManager.getNotificationChannel(channelId);
            if (channel == null) {
                channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("Notifications from device " + channelName);
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
