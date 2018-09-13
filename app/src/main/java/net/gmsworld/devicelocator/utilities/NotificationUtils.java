package net.gmsworld.devicelocator.utilities;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import net.gmsworld.devicelocator.LauncherActivity;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;

import org.apache.commons.lang3.StringUtils;

import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

/**
 * Created by jstakun on 6/1/17.
 */

public class NotificationUtils {

    private static final String DEFAULT_CHANNEL_ID = "Device-Locator";
    private static final String DEFAULT_NOTIFICATION_TITLE = "Device Locator Notification";
    private static final String TAG = NotificationUtils.class.getSimpleName();
    public static final int WORKER_NOTIFICATION_ID = 1234;

    public static Notification buildTrackerNotification(Context context, int notificationId) {
        Intent trackerIntent = new Intent(context, LauncherActivity.class);
        trackerIntent.setAction(MainActivity.ACTION_DEVICE_TRACKER);
        PendingIntent contentIntent = PendingIntent.getActivity(context, notificationId, trackerIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        final String message = context.getString(R.string.notification_tracker);

        initChannels(context, Messenger.getDeviceName());

        return new NotificationCompat.Builder(context, Messenger.getDeviceName())
                .setContentTitle(DEFAULT_NOTIFICATION_TITLE)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_location_on_white)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_large))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(true)
                //.setPublicVersion(publicNotification) //API 21
                .build();
    }

    public static Notification buildMessageNotification(Context context, int notificationId, String message, Location location) {
        PendingIntent mapIntent = null, routeIntent = null, webIntent = null;
        String deviceName = null, routeId = null;

        if (location != null) {
            //message has location
            String device = "Your+Device";
            Bundle b = location.getExtras();
            if (b != null) {
                if (b.containsKey(MainActivity.DEVICE_NAME)) {
                    deviceName = b.getString(MainActivity.DEVICE_NAME);
                    device = "Device+" + deviceName;
                }
                if (b.containsKey("routeId")) {
                    routeId = b.getString("routeId");
                }
            }
            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + location.getLatitude() + "," + location.getLongitude() + "(" + device + ")");
            Intent gmsIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            gmsIntent.setPackage("com.google.android.apps.maps");
            if (gmsIntent.resolveActivity(context.getPackageManager()) != null) {
                mapIntent = PendingIntent.getActivity(context, notificationId, gmsIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }

            Location lastLocation = SmartLocation.with(context).location(new LocationGooglePlayServicesWithFallbackProvider(context)).getLastLocation();
            if (lastLocation != null && (System.currentTimeMillis() - lastLocation.getTime() < 10 * 60 * 1000)) { //10 min
                int distance = (int) location.distanceTo(lastLocation); //in meters
                if (distance > 0) {
                    message += "\n" + DistanceFormatter.format(distance) + " away";
                }
            }
        }

        String[] tokens = message.split("\\s+");
        for (String token : tokens) {
            if (webIntent == null && (token.startsWith("http://") || token.startsWith("https://"))) {
                Intent notificationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(token));
                webIntent = PendingIntent.getActivity(context, notificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                if (routeIntent == null && token.startsWith(context.getString(R.string.showRouteUrl))) {
                    notificationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(token));
                    routeIntent = PendingIntent.getActivity(context, notificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                }
            }
        }

        //change links to
        if (mapIntent != null || routeIntent != null) {
            tokens = message.split("\n");
            message = "";
            for (String token : tokens) {
                if (!token.startsWith(Messenger.MAPS_URL_PREFIX) && !token.startsWith(context.getString(R.string.showRouteUrl)) ){
                    message += token + "\n";
                }
            }
        }

        if (routeIntent == null && routeId != null) {
            tokens = StringUtils.split(routeId,'_');
            Log.d(TAG, "Route id: " + routeId);
            if (tokens.length == 5) {
                String routeUrl = context.getString(R.string.showRouteUrl) + "/" + tokens[3] + "/" + tokens[4] + "/now";
                Intent notificationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(routeUrl));
                routeIntent = PendingIntent.getActivity(context, notificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
        }

        Uri notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        String channelId = deviceName;
        if (channelId == null) {
            channelId = DEFAULT_CHANNEL_ID;
        }

        initChannels(context, channelId);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(DEFAULT_NOTIFICATION_TITLE)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_devices_other_white)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_large))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setSound(notificationUri)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE);
        //.setOngoing(false)
        //.setPublicVersion(publicNotification) //API 21

        if (mapIntent != null) {
            nb.setContentIntent(mapIntent);
            nb.addAction(R.drawable.ic_place_white, context.getString(R.string.map_button), mapIntent);
        } else if (webIntent != null) {
            nb.setContentIntent(webIntent);
            if (routeIntent == null) {
                nb.addAction(R.drawable.ic_open_in_browser, context.getString(R.string.browser_button), webIntent);
            }
        }

        if (routeIntent != null) {
            nb.addAction(R.drawable.ic_explore_white, context.getString(R.string.route_button), routeIntent);
        }

        return nb.build();
    }

    public static Notification buildWorkerNotification(Context context) {
        initChannels(context, DEFAULT_CHANNEL_ID);
        Notification notification = new NotificationCompat.Builder(context, DEFAULT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_devices_other_white)
                    .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_large))
                    .setContentTitle(DEFAULT_NOTIFICATION_TITLE)
                    .setContentText("Please wait...").build();
        return notification;
    }

    public static void cancel(Context context, int notificationId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.cancel(notificationId);
        }
    }

    private static void initChannels(Context context, String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final String channelName = channelId.replace('-', ' ');
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = notificationManager.getNotificationChannel(channelId);
            if (channel == null) {
                Log.d(TAG, "Creating new notification channel " + channelId);
                channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("Notifications from device " + channelName);
                notificationManager.createNotificationChannel(channel);
            } else {
                Log.d(TAG, "Notification channel " + channelId + " exists");
            }
        }
    }
}
