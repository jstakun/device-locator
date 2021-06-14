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
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import net.gmsworld.devicelocator.LauncherActivity;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.MapsActivity;
import net.gmsworld.devicelocator.PermissionsActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.RouteActivity;
import net.gmsworld.devicelocator.services.CommandService;

import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import androidx.core.app.NotificationCompat;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

/**
 * Created by jstakun on 6/1/17.
 */

public class NotificationUtils {

    private static final String DEFAULT_CHANNEL_ID = "Device-Locator";
    private static final String TAG = NotificationUtils.class.getSimpleName();
    private static final Map<String, Integer> notificationIds = new HashMap<>();

    public static final int LOCATION_PERMISSION_NOTIFICATION_ID = 4444;
    public static final int SAVED_LOCATION_NOTIFICATION_ID = 5555;

    public static Notification buildTrackerNotification(final Context context, final int notificationId, final String message) {
        Intent trackerIntent = new Intent(context, LauncherActivity.class);
        trackerIntent.setAction(MainActivity.ACTION_DEVICE_TRACKER);
        PendingIntent contentIntent = PendingIntent.getActivity(context, notificationId, trackerIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        initChannels(context, Messenger.getDefaultDeviceName());

        return new NotificationCompat.Builder(context, Messenger.getDefaultDeviceName())
                .setContentTitle(context.getString(R.string.app_name) + " Notification")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_place_white)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(true)
                .addAction(R.drawable.ic_open_in_browser, "Open " + context.getString(R.string.app_name), contentIntent)
                .build();
    }

    public static Notification buildMonitorNotification(final Context context, final int notificationId, final String message) {
        Intent trackerIntent = new Intent(context, LauncherActivity.class);
        trackerIntent.setAction(MainActivity.ACTION_DEVICE_MANAGER);
        PendingIntent contentIntent = PendingIntent.getActivity(context, notificationId, trackerIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        initChannels(context, Messenger.getDefaultDeviceName());

        return new NotificationCompat.Builder(context, Messenger.getDefaultDeviceName())
                .setContentTitle(context.getString(R.string.app_name) + " Notification")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_devices_other_white)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(true)
                .addAction(R.drawable.ic_open_in_browser, "Open " + context.getString(R.string.app_name), contentIntent)
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

            Notification notification = NotificationUtils.buildMessageNotification(context, notificationId, message, location, extras);
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                Log.d(TAG, "Creating notification " + id);
                notificationManager.notify(notificationId, notification);
            }
        }
    }

    public static void showLocationPermissionNotification(Context context) {
        Notification notification = NotificationUtils.buildLocationPermissionNotification(context);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            Log.d(TAG, "Creating notification " + LOCATION_PERMISSION_NOTIFICATION_ID);
            notificationManager.notify(LOCATION_PERMISSION_NOTIFICATION_ID, notification);
        }
    }

    public static void showSavedLocationNotification(Context context) {
        Notification notification = NotificationUtils.buildSavedLocationNotification(context);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            Log.d(TAG, "Creating notification " + SAVED_LOCATION_NOTIFICATION_ID);
            notificationManager.notify(SAVED_LOCATION_NOTIFICATION_ID, notification);
        }
    }

    public static void showRegistrationNotification(Context context) {
        Notification notification = NotificationUtils.buildRegistrationNotification(context, SAVED_LOCATION_NOTIFICATION_ID);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            Log.d(TAG, "Creating notification " + SAVED_LOCATION_NOTIFICATION_ID);
            notificationManager.notify(SAVED_LOCATION_NOTIFICATION_ID, notification);
        }
    }

    private static Notification buildMessageNotification(Context context, int notificationId, String message, Location deviceLocation, Bundle extras) {
        PendingIntent mapIntent = null, routeIntent = null, webIntent = null;
        final String deviceName = extras.getString(MainActivity.DEVICE_NAME);
        final String routeId = extras.getString("routeId");
        final String imei = extras.getString("imei", null);
        String title = context.getString(R.string.app_name) + " Notification";

        try {
            message = URLDecoder.decode(message, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }

        if (imei != null) {
            Intent gmsIntent = new Intent(context, MapsActivity.class);
            gmsIntent.putExtra("imei", imei);
            mapIntent = PendingIntent.getActivity(context, notificationId, gmsIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        } else if (deviceLocation != null) {
            String device = "Your+Device";
            if (StringUtils.isNotEmpty(deviceName)) {
                device = "Device+" + deviceName;
                title = deviceName.replace('-', ' ') + " Notification";
            }
            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + deviceLocation.getLatitude() + "," + deviceLocation.getLongitude() + "(" + device + ")");
            Intent gmsIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            gmsIntent.setPackage("com.google.android.apps.maps");
            if (gmsIntent.resolveActivity(context.getPackageManager()) != null) {
                mapIntent = PendingIntent.getActivity(context, notificationId, gmsIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
        }

        if (deviceLocation != null && GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
            Location lastLocation = SmartLocation.with(context).location(new LocationGooglePlayServicesWithFallbackProvider(context)).getLastLocation();
            if (lastLocation != null && (System.currentTimeMillis() - lastLocation.getTime() < 10 * 60 * 1000)) { //10 min
                int distance = (int) deviceLocation.distanceTo(lastLocation); //in meters
                if (distance <= 2) {
                    message += "\n" + "Next to you";
                } else {
                    message += "\n" + DistanceFormatter.format(distance) + " away from you";
                }
            }
            if (imei != null) {
                float lat = PreferenceManager.getDefaultSharedPreferences(context).getFloat(imei + "_previousLatitude", Float.NaN);
                float lng = PreferenceManager.getDefaultSharedPreferences(context).getFloat(imei + "_previousLongitude", Float.NaN);
                if (!Float.isNaN(lat) && !Float.isNaN(lng)) {
                    Location l = new Location("");
                    l.setLatitude((double) lat);
                    l.setLongitude((double) lng);
                    int distance = (int) deviceLocation.distanceTo(l);
                    if (distance <= 5) {
                        message += "\n" + "In the same location";
                    } else {
                        message += "\n" + DistanceFormatter.format(distance) + " away from previous location";
                    }
                }
                PreferenceManager.getDefaultSharedPreferences(context).edit().
                        putFloat(imei + "_previousLatitude", (float)deviceLocation.getLatitude()).
                        putFloat(imei + "_previousLongitude", (float)deviceLocation.getLongitude()).apply();
            }
            if (deviceLocation.hasSpeed() && deviceLocation.getSpeed() > 10f) {
                message += "\n" + "Speed: " + Messenger.getSpeed(context, deviceLocation.getSpeed());
            }
        }

        String[] tokens = message.split("\\s+");
        for (String token : tokens) {
            if (webIntent == null && (token.startsWith("http://") || token.startsWith("https://"))) {
                Intent notificationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(token));
                webIntent = PendingIntent.getActivity(context, notificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                if (routeIntent == null && token.startsWith(context.getString(R.string.showRouteUrl))) {
                    if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
                        String[] discs = StringUtils.split(token, "/");
                        Log.d(TAG, "Route tokens /: " + token);
                        Intent gmsIntent = new Intent(context, RouteActivity.class);
                        if (StringUtils.isNotEmpty(deviceName)) {
                            gmsIntent.putExtra("deviceName", deviceName);
                        }
                        if (token.endsWith("/now")) {
                            gmsIntent.putExtra("imei", discs[discs.length - 3]);
                            gmsIntent.putExtra("routeId", discs[discs.length - 2]);
                            gmsIntent.putExtra("now", "true");
                        }  else {
                            gmsIntent.putExtra("imei", discs[discs.length - 2]);
                            gmsIntent.putExtra("routeId", discs[discs.length - 1]);
                            gmsIntent.putExtra("now", "false");
                        }
                        routeIntent = PendingIntent.getActivity(context, notificationId, gmsIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    } else {
                        notificationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(token));
                        routeIntent = PendingIntent.getActivity(context, notificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    }
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
                if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
                    Log.d(TAG, "Route tokens _: " + routeId);
                    Intent gmsIntent = new Intent(context, RouteActivity.class);
                    if (StringUtils.isNotEmpty(deviceName)) {
                        gmsIntent.putExtra("deviceName", deviceName);
                    }
                    gmsIntent.putExtra("imei", tokens[3]);
                    gmsIntent.putExtra("routeId", tokens[4]);
                    gmsIntent.putExtra("now", "true");
                    routeIntent = PendingIntent.getActivity(context, notificationId, gmsIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                } else {
                    String routeUrl = context.getString(R.string.showRouteUrl) + "/" + tokens[3] + "/" + tokens[4] + "/now";
                    Intent notificationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(routeUrl));
                    routeIntent = PendingIntent.getActivity(context, notificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                }
            }
        }

        Uri notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        String channelId = deviceName;
        if (channelId == null) {
            channelId = DEFAULT_CHANNEL_ID;
        }

        initChannels(context, channelId);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_devices_other_white)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setSound(notificationUri)
                .setGroup("COMMAND_MESSAGES_" + (imei != null ? imei : ""))
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
            final String commandName = extras.getString("command");
            if (StringUtils.equals(commandName, Command.RINGER_MODE_FAILED)) {
                cancelCommand = Command.UNMUTE_COMMAND;
                extras.putString("command", Command.UNMUTE_COMMAND);
            }
        }

        if (imei != null && extras.containsKey("command")) {
            final String commandName = extras.getString("command");
            AbstractCommand command = Command.getCommandByName(commandName);
            if (command != null && command.canResend()) {
                Intent newIntent = new Intent(context, CommandService.class);
                final String args = command.getDefaultArgs();
                if (StringUtils.isNotEmpty(args)) {
                    newIntent.putExtra("args", args);
                }
                newIntent.putExtra("command", commandName);
                newIntent.putExtra("imei", imei);
                if (extras.containsKey("pin")) {
                    newIntent.putExtra("pin", extras.getString("pin"));
                }
                if (extras.containsKey(MainActivity.DEVICE_NAME)) {
                    newIntent.putExtra(MainActivity.DEVICE_NAME, extras.getString(MainActivity.DEVICE_NAME));
                }
                PendingIntent retryIntent = PendingIntent.getService(context, notificationId, newIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                nb.addAction(R.drawable.ic_open_in_browser, context.getString(R.string.resend_command), retryIntent);
            } else if (command != null && command.hasOppositeCommand()) {
                AbstractCommand c = Command.getCommandByName(command.getOppositeCommand());
                if (c != null) {
                    Intent newIntent = new Intent(context, CommandService.class);
                    newIntent.putExtra("command", c.getSmsCommand());
                    final String args = c.getDefaultArgs();
                    if (StringUtils.isNotEmpty(args)) {
                        newIntent.putExtra("args", args);
                    }
                    if (StringUtils.isNotEmpty(routeId)) {
                        newIntent.putExtra("routeId", routeId);
                    } else {
                        newIntent.putExtra("cancelCommand", StringUtils.isNotEmpty(cancelCommand) ? cancelCommand : commandName);
                    }
                    newIntent.putExtra("imei", imei);
                    if (extras.containsKey("pin")) {
                        newIntent.putExtra("pin", extras.getString("pin"));
                    }
                    if (extras.containsKey(MainActivity.DEVICE_NAME)) {
                        newIntent.putExtra(MainActivity.DEVICE_NAME, extras.getString(MainActivity.DEVICE_NAME));
                    }
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

    public static Notification buildWorkerNotification(Context context, int notificationId, String title, String text, boolean showProgress) {
        initChannels(context, DEFAULT_CHANNEL_ID);
        if (text == null) {
            text = context.getString(R.string.please_wait);
        }
        if (title == null) {
            title = context.getString(R.string.app_name) + " Notification";
        }

        Intent trackerIntent = new Intent(context, LauncherActivity.class);
        trackerIntent.setAction(MainActivity.ACTION_DEVICE_TRACKER);
        PendingIntent contentIntent = PendingIntent.getActivity(context, notificationId, trackerIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(context, DEFAULT_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_devices_other_white)
                        .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                        .setContentTitle(title)
                        .setContentText(text)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                        .addAction(R.drawable.ic_open_in_browser, "Open " + context.getString(R.string.app_name), contentIntent);

        if (showProgress) {
            nb.setProgress(0, 0 ,true);
        }

        return nb.build();
    }

    private static Notification buildLocationPermissionNotification(Context context) {
        initChannels(context, DEFAULT_CHANNEL_ID);

        final String text = "Please click the button below and grant Location permission to let us keep your device secure!";
        final String title = context.getString(R.string.app_name) + " Notification";

        Intent permissionIntent = new Intent(context, LauncherActivity.class);
        permissionIntent.setAction(PermissionsActivity.LOCATION_ACTION);
        PendingIntent contentIntent = PendingIntent.getActivity(context, LOCATION_PERMISSION_NOTIFICATION_ID, permissionIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(context, DEFAULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_devices_other_white)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .addAction(R.drawable.ic_open_in_browser, "Open " + context.getString(R.string.app_name), contentIntent);

        return nb.build();
    }

    private static Notification buildSavedLocationNotification(Context context) {
        initChannels(context, DEFAULT_CHANNEL_ID);

        final String text = "Refresh your devices location to keep them secure!";
        final String title = context.getString(R.string.app_name) + " Notification";

        Intent mapsIntent = new Intent(context, MapsActivity.class);
        mapsIntent.putExtra("locateAllDevices", "1");
        PendingIntent contentIntent = PendingIntent.getActivity(context, SAVED_LOCATION_NOTIFICATION_ID, mapsIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(context, DEFAULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_devices_other_white)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .addAction(R.drawable.ic_open_in_browser, "Open " + context.getString(R.string.app_name), contentIntent);

        return nb.build();
    }

    private static Notification buildRegistrationNotification(Context context, int notificationId) {
        initChannels(context, DEFAULT_CHANNEL_ID);

        final String text = "Complete your device registration to keep it secure!";
        final String title = context.getString(R.string.app_name) + " Notification";

        Intent permissionIntent = new Intent(context, LauncherActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, notificationId, permissionIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(context, DEFAULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_devices_other_white)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .addAction(R.drawable.ic_open_in_browser, "Open " + context.getString(R.string.app_name), contentIntent);

        return nb.build();
    }

    public static void cancel(Context context, String notificationId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null && notificationIds.containsKey(notificationId)) {
            notificationManager.cancel(notificationIds.remove(notificationId));
        }
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
