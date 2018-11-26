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
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import net.gmsworld.devicelocator.LauncherActivity;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.services.CommandService;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

/**
 * Created by jstakun on 6/1/17.
 */

public class NotificationUtils {

    private static final String DEFAULT_CHANNEL_ID = "Device-Locator";
    private static final String TAG = NotificationUtils.class.getSimpleName();
    public static final int WORKER_NOTIFICATION_ID = 1234;
    private static final Map<String, Integer> notificationIds = new HashMap<>();

    public static Notification buildTrackerNotification(Context context, int notificationId) {
        Intent trackerIntent = new Intent(context, LauncherActivity.class);
        trackerIntent.setAction(MainActivity.ACTION_DEVICE_TRACKER);
        PendingIntent contentIntent = PendingIntent.getActivity(context, notificationId, trackerIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        final String message = context.getString(R.string.notification_tracker);

        initChannels(context, Messenger.getDeviceName());

        return new NotificationCompat.Builder(context, Messenger.getDeviceName())
                .setContentTitle(context.getString(R.string.app_name) + " Notification")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_location_on_white)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_large))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(true)
                .addAction(R.drawable.ic_open_in_browser, "Open " + context.getString(R.string.app_name), contentIntent)
                //.setPublicVersion(publicNotification) //API 21
                .build();
    }

    static void showMessageNotification(Context context, String message, Location location, Bundle extras) {
        int notificationId = (int) System.currentTimeMillis();
        String id = null;

        if (extras != null) {
            if (extras.containsKey("routeId")) {
                id = extras.getString("routeId");
            } else if (extras.containsKey("imei") && extras.containsKey("command")) {
                id = extras.getString("imei") + "_" + extras.getString("command");
            }
            if (id != null) {
                if (notificationIds.containsKey(id)) {
                    notificationId = notificationIds.get(id);
                } else {
                    notificationId = notificationIds.size();
                    notificationIds.put(id, notificationId);
                }
            }
        }

        Notification notification = NotificationUtils.buildMessageNotification(context, notificationId, message, location, extras);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            Log.d(TAG, "Creating notification " + id);
            notificationManager.notify(notificationId, notification);
        }
    }

    private static Notification buildMessageNotification(Context context, int notificationId, String message, Location deviceLocation, Bundle extras) {
        PendingIntent mapIntent = null, routeIntent = null, webIntent = null;
        String deviceName = extras.getString(MainActivity.DEVICE_NAME);
        String routeId = extras.getString("routeId");

        if (deviceLocation != null) {
            //message has location
            String device = "Your+Device";
            if (StringUtils.isNotEmpty(deviceName)) {
                device = "Device+" + deviceName;
            }
            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + deviceLocation.getLatitude() + "," + deviceLocation.getLongitude() + "(" + device + ")");
            Intent gmsIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            gmsIntent.setPackage("com.google.android.apps.maps");
            if (gmsIntent.resolveActivity(context.getPackageManager()) != null) {
                mapIntent = PendingIntent.getActivity(context, notificationId, gmsIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }

            Location lastLocation = SmartLocation.with(context).location(new LocationGooglePlayServicesWithFallbackProvider(context)).getLastLocation();
            if (lastLocation != null && (System.currentTimeMillis() - lastLocation.getTime() < 10 * 60 * 1000)) { //10 min
                int distance = (int) deviceLocation.distanceTo(lastLocation); //in meters
                if (distance <= 0) {
                    distance = 1;
                }
                message += "\n" + DistanceFormatter.format(distance) + " away from you";
            }
            if (extras.containsKey("imei")) {
                float lat = PreferenceManager.getDefaultSharedPreferences(context).getFloat(extras.getString("imei") + "_previousLatitude", Float.NaN);
                float lng = PreferenceManager.getDefaultSharedPreferences(context).getFloat(extras.getString("imei") + "_previousLongitude", Float.NaN);
                if (lat != Float.NaN && lng != Float.NaN) {
                    Location l = new Location("");
                    l.setLatitude((double) lat);
                    l.setLongitude((double) lng);
                    int distance = (int) deviceLocation.distanceTo(l);
                    if (distance <= 0) {
                        distance = 1;
                    }
                    message += "\n" + DistanceFormatter.format(distance) + " away from previous location";
                }
                PreferenceManager.getDefaultSharedPreferences(context).edit().
                        putFloat(extras.getString("imei") + "_previousLatitude", (float)deviceLocation.getLatitude()).
                        putFloat(extras.getString("imei") + "_previousLongitude", (float)deviceLocation.getLongitude()).apply();
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
            StringBuilder messageBuilder = new StringBuilder();
            for (String token : tokens) {
                if (!token.startsWith(Messenger.MAPS_URL_PREFIX) && !token.startsWith(context.getString(R.string.showRouteUrl)) ){
                    messageBuilder.append(token).append("\n");
                }
            }
            message = messageBuilder.toString();
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
                .setContentTitle(context.getString(R.string.app_name) + " Notification")
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

        String cancelCommand = null;
        //modify some command names to create better actions
        if (routeIntent != null && !extras.containsKey("command")) {
            extras.putString("command", Command.START_COMMAND);
        } else if (extras.containsKey("command")) {
            String commandName = extras.getString("command");
            if (StringUtils.equals(commandName + "dl", Command.RING_OFF_COMMAND)) {
                cancelCommand = Command.RING_OFF_COMMAND;
                extras.putString("command", Command.RING_COMMAND);
            } else if (StringUtils.equals(commandName, Command.MUTE_FAILED)) {
                cancelCommand = Command.MUTE_FAILED;
                extras.putString("command", Command.UNMUTE_COMMAND);
            }
        }

        //remove
        //Log.d(TAG, " ---------------------- ----------------------- Extras: " +  extras);

        if (extras.containsKey("imei") && extras.containsKey("command")) {
            final String commandName = extras.getString("command");
            AbstractCommand command = Command.getCommandByName(commandName);
            if (command != null && command.canResend()) {
                Intent newIntent = new Intent(context, CommandService.class);
                if (extras.containsKey("args")) {
                    newIntent.putExtra("args", extras.getString("args"));
                }
                newIntent.putExtra("command", commandName);
                newIntent.putExtra("imei", extras.getString("imei"));
                if (extras.containsKey("pin")) {
                    newIntent.putExtra("pin", extras.getString("pin"));
                }
                if (extras.containsKey(MainActivity.DEVICE_NAME)) {
                    newIntent.putExtra(MainActivity.DEVICE_NAME, extras.getString(MainActivity.DEVICE_NAME));
                }
                PendingIntent retryIntent = PendingIntent.getService(context, notificationId, newIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                nb.addAction(R.drawable.ic_open_in_browser, context.getString(R.string.resend_command), retryIntent);
            } else if (command != null && command.hasOppositeCommand()) {
                Intent newIntent = new Intent(context, CommandService.class);
                if (extras.containsKey("args")) {
                    newIntent.putExtra("args", extras.getString("args"));
                }
                newIntent.putExtra("command", command.getOppositeCommand());
                if (StringUtils.isNotEmpty(routeId)) {
                    newIntent.putExtra("routeId", routeId);
                } else {
                    newIntent.putExtra("cancelCommand", StringUtils.isNotEmpty(cancelCommand) ? cancelCommand : commandName);
                }
                newIntent.putExtra("imei", extras.getString("imei"));
                if (extras.containsKey("pin")) {
                    newIntent.putExtra("pin", extras.getString("pin"));
                }
                if (extras.containsKey(MainActivity.DEVICE_NAME)) {
                    newIntent.putExtra(MainActivity.DEVICE_NAME, extras.getString(MainActivity.DEVICE_NAME));
                }
                AbstractCommand c = Command.getCommandByName(command.getOppositeCommand());
                if (c != null) {
                    PendingIntent oppositeIntent = PendingIntent.getService(context, notificationId, newIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    nb.addAction(R.drawable.ic_open_in_browser, c.getLabel(), oppositeIntent);
                }
            } else if (command == null) {
                Log.d(TAG, "Command " + commandName + " not found!");
            } else {
                Log.d(TAG, "Command " + commandName + " doesn't allow resending!");
            }
        }

        return nb.build();
    }

    public static Notification buildWorkerNotification(Context context) {
        initChannels(context, DEFAULT_CHANNEL_ID);
        return new NotificationCompat.Builder(context, DEFAULT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_devices_other_white)
                    .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_large))
                    .setContentTitle(context.getString(R.string.app_name) + " Notification")
                    .setContentText(context.getString(R.string.please_wait)).build();
    }

    public static void cancel(Context context, String notificationId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null && notificationIds.containsKey(notificationId)) {
            notificationManager.cancel(notificationIds.remove(notificationId));
        }
    }

    private static void initChannels(Context context, String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final String channelName = channelId.replace('-', ' ');
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
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
}
