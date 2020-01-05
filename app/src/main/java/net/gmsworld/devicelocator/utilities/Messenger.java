package net.gmsworld.devicelocator.utilities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.PinActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.fragments.NotificationActivationDialogFragment;
import net.gmsworld.devicelocator.fragments.TelegramSetupDialogFragment;
import net.gmsworld.devicelocator.services.RouteTrackingService;
import net.gmsworld.devicelocator.services.SmsSenderService;

import org.apache.commons.lang3.StringUtils;
import org.ocpsoft.prettytime.PrettyTime;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Created by jstakun on 5/6/17.
 */

public class Messenger {

    private static final String TAG = Messenger.class.getSimpleName();

    private static final DecimalFormat latAndLongFormat = new DecimalFormat("#.######");

    public static final String MAPS_URL_PREFIX = "https://maps.google.com/maps?q=";
    public static final String LAT_HEADER = "X-GMS-Lat";
    public static final String LNG_HEADER = "X-GMS-Lng";
    public static final String ACC_HEADER = "X-GMS-Acc";
    public static final String SPD_HEADER = "X-GMS-Speed";

    public static final String TELEGRAM_PACKAGE = "org.telegram.messenger";
    // --Commented out by Inspection (1/5/20 1:28 PM):private static final String FACEBOOK_MESSENGER_PACKAGE = "com.facebook.orca";

    public static final String CID_SEPARATOR = "+=+";

    private static void sendSMS(final Context context, final String phoneNumber, final String message) {
        String status = null;
        if (Permissions.haveSendSMSPermission(context)) {
            try {
                //on samsung intents can't be null. the messages are not sent if intents are null
                ArrayList<PendingIntent> samsungFix = new ArrayList<>();
                samsungFix.add(PendingIntent.getBroadcast(context, 0, new Intent("SMS_RECEIVED"), 0));

                SmsManager smsManager = SmsManager.getDefault();
                ArrayList<String> parts = smsManager.divideMessage(message);
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, samsungFix, samsungFix);
                //status = "SMS message sent";
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                status = context.getString(R.string.device_sms_error);
            }
        } else {
            status = context.getString(R.string.device_sms_permission_error);
        }
        if (StringUtils.isNotEmpty(status)) {
            Log.d(TAG, status);
            Toast.makeText(context, status, Toast.LENGTH_LONG).show();
        }
    }

    public static void sendCloudMessage(final Context context, final Location location, final String replyTo, final String message, final String replyToCommand, final int retryCount, final long delayMillis, final Map<String, String> headers) {
        if ((StringUtils.isNotEmpty(replyTo) && StringUtils.isNotEmpty(message)) || location != null) {
            if (Network.isNetworkAvailable(context)) {
                final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN, "");
                final String deviceId = getDeviceId(context, false);
                if (StringUtils.isNotEmpty(tokenStr)) {
                    headers.put("Authorization", "Bearer " + tokenStr);
                    headers.put("X-GMS-AppId", "2");
                    headers.put("X-GMS-AppVersionId", Integer.toString(AppUtils.getInstance().getVersionCode(context)));
                    if (StringUtils.isNotEmpty(deviceId)) {
                        headers.put("X-GMS-DeviceId", deviceId);
                    }
                    headers.put("X-GMS-DeviceName", getDeviceId(context, true));
                    if (location != null) {
                        headers.put(LAT_HEADER, latAndLongFormat.format(location.getLatitude()));
                        headers.put(LNG_HEADER, latAndLongFormat.format(location.getLongitude()));
                        if (location.hasAccuracy()) {
                            headers.put(ACC_HEADER, Float.toString(location.getAccuracy()));
                        }
                        if (location.hasSpeed()) {
                            headers.put(SPD_HEADER, Float.toString(location.getSpeed()));
                        }
                    }
                    headers.put("X-GMS-UseCount", Integer.toString(settings.getInt("useCount", 1)));
                    if (StringUtils.equalsAnyIgnoreCase(replyToCommand, Command.START_COMMAND, Command.STOP_COMMAND, Command.RESUME_COMMAND, Command.PERIMETER_COMMAND, Command.ROUTE_COMMAND)) {
                        headers.put("X-GMS-RouteId", RouteTrackingServiceUtils.getRouteId(context));
                    }
                    sendCloudMessage(context, replyTo, message, replyToCommand, retryCount, delayMillis, headers);
                } else {
                    String queryString = "scope=dl&user=" + deviceId;
                    Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            if (responseCode == 200) {
                                if (StringUtils.isNotEmpty(getToken(context, results))) {
                                    sendCloudMessage(context, location, replyTo, message, replyToCommand, retryCount, delayMillis, headers);
                                } else {
                                    Log.e(TAG, "Failed to parse token!");
                                }
                            } else if (responseCode == 500 && retryCount > 0) {
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        sendCloudMessage(context, location, replyTo, message, replyToCommand, retryCount - 1, delayMillis * 2, headers);
                                    }
                                }, delayMillis);
                            } else {
                                Log.d(TAG, "Failed to receive token: " + results);
                            }
                        }
                    });
                }
            } else {
                Log.w(TAG, context.getString(R.string.no_network_error));
                if (retryCount > 0) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            sendCloudMessage(context, location, replyTo, message, replyToCommand, retryCount - 1, delayMillis * 2, headers);
                        }
                    }, delayMillis);
                }
            }
        }
    }

    private static void sendCloudMessage(final Context context, final String replyTo, final String message, final String replyToCommand, final int retryCount, final long delayMillis, final Map<String, String> headers) {
        String[] tokens = StringUtils.split(replyTo, CID_SEPARATOR);
        if (tokens != null && tokens.length >= 1) {
            String content = "imei=" + tokens[0].trim();
            if (tokens.length >= 2) {
                content += "&command=" + Command.MESSAGE_COMMAND + "app";
                content += "&pin=" + tokens[1].trim();
            }
            if (StringUtils.isNotEmpty(replyToCommand)) {
                content += "&replyToCommand=" + StringUtils.remove(replyToCommand, "dl");
            }
            if (message != null) {
                try {
                    content += "&args=" + URLEncoder.encode(message, "UTF-8");
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
            Network.post(context, context.getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    if (responseCode == 200) {
                        //Toast.makeText(context, "Command has been sent!", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Message has been sent to the cloud!");
                    } else if (responseCode >= 400 && responseCode < 500) {
                        Toast.makeText(context, "Command has been rejected due to quota limit! Please try again after some time or contact " + context.getString(R.string.app_email), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Message has been rejected by server!");
                    } else if (responseCode == 500 && retryCount > 0) {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                sendCloudMessage(context, replyTo, message, replyToCommand, retryCount - 1, delayMillis * 2, headers);
                            }
                        }, delayMillis);
                    }
                }
            });
        } else {
            Log.e(TAG, "Invalid replyTo: " + replyTo);
        }
    }

    private static void sendEmail(final Context context, final Location location, final String email, final String message, final String title, final int retryCount, final Map<String, String> headers) {
        if (StringUtils.isNotEmpty(email) && (StringUtils.isNotEmpty(message))) {
            if (Network.isNetworkAvailable(context)) {
                final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN, "");
                if (StringUtils.isNotEmpty(tokenStr)) {
                    headers.put("Authorization", "Bearer " + tokenStr);
                    headers.put("X-GMS-AppId", "2");
                    String deviceId = getDeviceId(context, false);
                    if (StringUtils.isNotEmpty(deviceId)) {
                        headers.put("X-GMS-DeviceId", deviceId);
                    }
                    if (location != null) {
                        headers.put(LAT_HEADER, latAndLongFormat.format(location.getLatitude()));
                        headers.put(LNG_HEADER, latAndLongFormat.format(location.getLongitude()));
                        if (location.hasAccuracy()) {
                            headers.put(ACC_HEADER, Float.toString(location.getAccuracy()));
                        }
                        if (location.hasSpeed()) {
                            headers.put(SPD_HEADER, Float.toString(location.getSpeed()));
                        }
                    }
                    headers.put("X-GMS-UseCount", Integer.toString(settings.getInt("useCount", 1)));
                    sendEmail(context, email, message, title, 1, headers);
                } else {
                    String queryString = "scope=dl&user=" + getDeviceId(context, false);
                    Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            if (responseCode == 200) {
                                if (StringUtils.isNotEmpty(getToken(context, results))) {
                                    sendEmail(context, location, email, message, title, 1, headers);
                                } else {
                                    Log.e(TAG, "Failed to parse token!");
                                }
                            } else if (responseCode == 500 && retryCount > 0) {
                                sendEmail(context, location, email, message, title, retryCount - 1, headers);
                            } else {
                                Log.d(TAG, "Failed to receive token: " + results);
                            }
                        }
                    });
                }
            } else {
                Log.w(TAG, context.getString(R.string.no_network_error));
            }
        }
    }

    private static void sendEmail(final Context context, final String email, final String message, final String title, final int retryCount, final Map<String, String> headers) {
        try {
            final String queryString = "type=m_dl&emailTo=" + email + "&message=" + message + "&title=" + title + "&username=" + getDeviceId(context, false);
            Network.post(context, context.getString(R.string.notificationUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                    if (responseCode == 500 && retryCount > 0) {
                        sendEmail(context, email, message, title, retryCount - 1, headers);
                    } else if (responseCode == 200 && StringUtils.startsWith(results, "{")) {
                        JsonElement reply = new JsonParser().parse(results);
                        String status = null;
                        if (reply != null) {
                            JsonElement st = reply.getAsJsonObject().get("status");
                            if (st != null) {
                                status = st.getAsString();
                            }
                        }
                        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(MainActivity.EMAIL_REGISTRATION_STATUS, status).apply();
                        if (StringUtils.equalsIgnoreCase(status, "sent")) {
                            Log.d(TAG, "Email message sent successfully");
                        } else if (StringUtils.equalsIgnoreCase(status, "unverified")) {
                            Toast.makeText(context, R.string.email_unverified_error, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(context, R.string.email_internal_error, Toast.LENGTH_LONG).show();
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        }
    }

    private static void sendTelegram(final Context context, final Location location, final String telegramId, final String message, final int retryCount, final Map<String, String> headers) {
        if (isValidTelegramId(telegramId)) {
            if (Network.isNetworkAvailable(context)) {
                final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN, "");
                if (StringUtils.isNotEmpty(tokenStr)) {
                    headers.put("Authorization", "Bearer " + tokenStr);
                    String deviceId = getDeviceId(context, false);
                    if (StringUtils.isNotEmpty(deviceId)) {
                        headers.put("X-GMS-DeviceId", deviceId);
                    }
                    if (location != null) {
                        headers.put(LAT_HEADER, latAndLongFormat.format(location.getLatitude()));
                        headers.put(LNG_HEADER, latAndLongFormat.format(location.getLongitude()));
                        if (location.hasAccuracy()) {
                            headers.put(ACC_HEADER, Float.toString(location.getAccuracy()));
                        }
                        if (location.hasSpeed()) {
                            headers.put(SPD_HEADER, Float.toString(location.getSpeed()));
                        }
                    }
                    headers.put("X-GMS-UseCount", Integer.toString(settings.getInt("useCount", 1)));
                    sendTelegram(context, telegramId, message, 1, headers);
                } else {
                    String queryString = "scope=dl&user=" + getDeviceId(context, false);
                    Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            if (responseCode == 200) {
                                if (StringUtils.isNotEmpty(getToken(context, results))) {
                                    sendTelegram(context, location, telegramId, message, 1, headers);
                                } else {
                                    Log.e(TAG, "Failed to parse token!");
                                }
                            } else if (responseCode == 500 && retryCount > 0) {
                                sendTelegram(context, location, telegramId, message, retryCount - 1, headers);
                            } else {
                                Log.d(TAG, "Failed to receive token: " + results);
                            }
                        }
                    });
                }
            } else {
                Log.w(TAG, context.getString(R.string.no_network_error));
            }
        } else {
            Log.e(TAG, "Invalid Telegram chat or channel id: " + telegramId);
        }
    }

    private static void sendTelegram(final Context context, final String telegramId, final String message, final int retryCount, final Map<String, String> headers) {
        try {
            final String queryString = "type=t_dl&chatId=" + telegramId + "&message=" + message + "&username=" + getDeviceId(context, false);
            Network.post(context, context.getString(R.string.notificationUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                    if (responseCode == 500 && retryCount > 0) {
                        sendTelegram(context, telegramId, message, retryCount - 1, headers);
                    } else if (responseCode == 200 && StringUtils.startsWith(results, "{")) {
                        JsonElement reply = new JsonParser().parse(results);
                        String status = null;
                        if (reply != null) {
                            JsonElement st = reply.getAsJsonObject().get("status");
                            if (st != null) {
                                status = st.getAsString();
                            }
                        }
                        if (context instanceof Activity) {
                            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(MainActivity.SOCIAL_REGISTRATION_STATUS, status).apply();
                            if (StringUtils.equalsIgnoreCase(status, "sent")) {
                                Log.d(TAG, "Telegram notification sent successfully!");
                            } else if (StringUtils.equalsIgnoreCase(status, "unverified")) {
                                PreferenceManager.getDefaultSharedPreferences(context).edit().putString(MainActivity.NOTIFICATION_SOCIAL, "").apply();
                                final TextView telegramInput = ((Activity) context).findViewById(R.id.telegramId);
                                if (telegramInput != null) {
                                    telegramInput.setText("");
                                }
                                Toast.makeText(context, R.string.telegram_unverified_error, Toast.LENGTH_LONG).show();
                            } else if (StringUtils.equalsIgnoreCase(status, "failed")) {
                                PreferenceManager.getDefaultSharedPreferences(context).edit().putString(MainActivity.NOTIFICATION_SOCIAL, "").apply();
                                final TextView telegramInput = ((Activity) context).findViewById(R.id.telegramId);
                                if (telegramInput != null) {
                                    telegramInput.setText("");
                                }
                                Toast.makeText(context, R.string.telegram_invalid_id, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(context, R.string.telegram_internal_error, Toast.LENGTH_LONG).show();
                            }
                        }
                    } else if (responseCode >= 400) {
                        Toast.makeText(context, R.string.telegram_internal_error, Toast.LENGTH_LONG).show();
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        }

    }

    private static void sendRoutePoint(final Context context, final Location location, final int retryCount, final Map<String, String> headers) {
        if (Network.isNetworkAvailable(context)) {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN, "");
            if (StringUtils.isNotEmpty(tokenStr)) {
                headers.put("Authorization", "Bearer " + tokenStr);
                headers.put("X-GMS-AppId", "2");
                String deviceId = getDeviceId(context, false);
                if (StringUtils.isNotEmpty(deviceId)) {
                    headers.put("X-GMS-DeviceId", deviceId);
                }
                if (location != null) {
                    headers.put(LAT_HEADER, latAndLongFormat.format(location.getLatitude()));
                    headers.put(LNG_HEADER, latAndLongFormat.format(location.getLongitude()));
                    if (location.hasAccuracy()) {
                        headers.put(ACC_HEADER, Float.toString(location.getAccuracy()));
                    }
                    if (location.hasSpeed()) {
                        headers.put(SPD_HEADER, Float.toString(location.getSpeed()));
                    }
                }
                headers.put("X-GMS-UseCount", Integer.toString(settings.getInt("useCount", 1)));
                sendRoutePoint(context, 1, headers);
            } else {
                String queryString = "scope=dl&user=" + getDeviceId(context, false);
                Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            if (StringUtils.isNotEmpty(getToken(context, results))) {
                                sendRoutePoint(context, location, 1, headers);
                            } else {
                                Log.e(TAG, "Failed to parse token!");
                            }
                        } else if (responseCode == 500 && retryCount > 0) {
                            sendRoutePoint(context, location, retryCount - 1, headers);
                        } else {
                            Log.d(TAG, "Failed to receive token: " + results);
                        }
                    }
                });
            }
        } else {
            Log.w(TAG, context.getString(R.string.no_network_error));
        }
    }

    private static void sendRoutePoint(final Context context, final int retryCount, final Map<String, String> headers) {
        try {
            final String queryString = "type=routePoint&username=" + getDeviceId(context, false);
            Network.post(context, context.getString(R.string.notificationUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    if (responseCode == 500 && retryCount > 0) {
                        sendRoutePoint(context, retryCount - 1, headers);
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        }
    }

    public static void sendLocationMessage(Context context, Location location, boolean fused, String phoneNumber, String telegramId, String email, String app) {
        //Log.d(TAG, "sendLocationMessage()" + location.getAccuracy());
        final String deviceId = getDeviceId(context, true);
        String text = deviceId + " " + context.getString(fused ? R.string.approximate : R.string.accurate) + " location:\n";

        text += context.getString(R.string.latitude) + " " + latAndLongFormat.format(location.getLatitude()) + "\n";
        text += context.getString(R.string.longitude) + " " + latAndLongFormat.format(location.getLongitude()) + "\n";
        text += context.getString(R.string.accuracy) + " " + Math.round(location.getAccuracy()) + "m\n";

        PrettyTime p = new PrettyTime();
        text += "Taken " + p.format(new Date(location.getTime())) + "\n";

        text += "Battery level: " + getBatteryLevel(context);

        if (location.hasSpeed() && location.getSpeed() > 0f) {
            text += "\n" + context.getString(R.string.speed) + " " + getSpeed(context, location.getSpeed());
        }

        if (location.hasAltitude() && location.getAltitude() != 0) {
            text += "\n" + context.getString(R.string.altitude) + " " + ((int) location.getAltitude()) + "m";
        }

        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        }
        if (StringUtils.isNotEmpty(telegramId)) {
            sendTelegram(context, location, telegramId, text, 1, new HashMap<String, String>());
        }
        if (StringUtils.isNotEmpty(email)) {
            String title = context.getString(R.string.message, deviceId) + " - current location";
            text += "\n" + context.getString(R.string.deviceUrl) + "/" + getDeviceId(context, false);
            sendEmail(context, location, email, text, title, 1, new HashMap<String, String>());
        }
        if (StringUtils.isNotEmpty(app)) {
            sendCloudMessage(context, location, app, text, Command.SHARE_COMMAND, 1, 2000, new HashMap<String, String>());
        }
    }

    public static void sendGoogleMapsMessage(Context context, Location location, String phoneNumber, String telegramId, String email, String app) {
        final String deviceId = getDeviceId(context, true);
        String text = deviceId + " location" +
                "\n" + "Battery level: " + getBatteryLevel(context) +
                "\n" + MAPS_URL_PREFIX + latAndLongFormat.format(location.getLatitude()).replace(',', '.') + "," + latAndLongFormat.format(location.getLongitude()).replace(',', '.');
        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        }
        if (StringUtils.isNotEmpty(telegramId)) {
            sendTelegram(context, location, telegramId, text, 1, new HashMap<String, String>());
        }
        if (StringUtils.isNotEmpty(email)) {
            String title = context.getString(R.string.message, deviceId) + " - location map link";
            text += "\n" + context.getString(R.string.deviceUrl) + "/" + getDeviceId(context, false);
            sendEmail(context, location, email, text, title, 1, new HashMap<String, String>());
        }
        if (StringUtils.isNotEmpty(app)) {
            sendCloudMessage(context, location, app, text, Command.SHARE_COMMAND, 5, 2000, new HashMap<String, String>());
        }
    }

    public static void sendAcknowledgeMessage(Context context, String phoneNumber, String telegramId, String email, String app) {
        String text;
        final String deviceId = getDeviceId(context, true);
        if (GmsSmartLocationManager.isLocationEnabled(context)) {
            text = context.getString(R.string.acknowledgeMessage, deviceId) + "\n";
            text += context.getString(R.string.network) + " " + booleanToString(context, Network.isNetworkAvailable(context)) + "\n";
            text += context.getString(R.string.gps) + " " + locationToString(context) + "\n";
        } else {
            text = "Location service is disabled on device " + deviceId + "! Unable to send location. Please enable location service and send the command again.\n";
        }
        text += "Battery level: " + getBatteryLevel(context);

        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        }
        if (StringUtils.isNotEmpty(telegramId)) {
            sendTelegram(context, null, telegramId, text, 1, new HashMap<String, String>());
        }
        if (StringUtils.isNotEmpty(email)) {
            String title = context.getString(R.string.message, deviceId) + " - location request";
            text += "\n" + context.getString(R.string.deviceUrl) + "/" + getDeviceId(context, false);
            sendEmail(context, null, email, text, title, 1, new HashMap<String, String>());
        }
        if (StringUtils.isNotEmpty(app)) {
            sendCloudMessage(context, null, app, text, Command.SHARE_COMMAND, 1, 2000, new HashMap<String, String>());
        }
    }

    public static void sendRouteMessage(Context context, Location location, int distance, String phoneNumber, String telegramId, String email, String app) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        if (StringUtils.isNotEmpty(phoneNumber)) {
            if (settings.getBoolean(SmsSenderService.SEND_LOCATION_MESSAGE, false)) {
                sendLocationMessage(context, location, true, phoneNumber, null, null, null);
            }
            if (settings.getBoolean(SmsSenderService.SEND_MAP_LINK_MESSAGE, true)) {
                sendGoogleMapsMessage(context, location, phoneNumber, null, null, null);
            }
        }

        String deviceId = getDeviceId(context, true);

        int dist = distance;
        if (dist <= 0) {
            dist = 1;
        }

        String message = deviceId + " location: " + latAndLongFormat.format(location.getLatitude()) + ", " + latAndLongFormat.format(location.getLongitude()) +
                " in distance of " + DistanceFormatter.format(dist) + " from previous location with accuracy " + DistanceFormatter.format((int) location.getAccuracy());
        if (location.hasSpeed() && location.getSpeed() > 0f) {
            message += " and speed " + getSpeed(context, location.getSpeed());
        }
        message += "\n" + "Battery level: " + getBatteryLevel(context) +
                "\n" + MAPS_URL_PREFIX + latAndLongFormat.format(location.getLatitude()).replace(',', '.') + "," + latAndLongFormat.format(location.getLongitude()).replace(',', '.');

        final Map<String, String> headers = new HashMap<>();
        headers.put("X-GMS-RouteId", RouteTrackingServiceUtils.getRouteId(context));
        //First send notification to telegram and if not configured to email
        //REMEMBER this could send a lot of messages and your email account could be overloaded
        if (StringUtils.isNotEmpty(telegramId)) {
            sendTelegram(context, location, telegramId, message, 1, headers);
        } else if (StringUtils.isNotEmpty(email)) {
            final String title = context.getString(R.string.message, deviceId) + " - location change";
            final String mailMessage = message + "\n\n" + "We recommend to use Telegram Messenger for route tracking notifications!";
            sendEmail(context, location, email, mailMessage, title, 1, headers);
        } else {
            //send route point for online route tracking
            sendRoutePoint(context, location, 1, headers);
        }
        //send notification to cloud if tracking has been initiated with cloud message
        if (StringUtils.isNotEmpty(app)) {
            sendCloudMessage(context, location, app, message, null, 1, 2000, headers);
        }
    }

    public static void sendPerimeterMessage(Context context, Location location, String app) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final Map<String, String> headers = new HashMap<>();
        headers.put("X-GMS-RouteId", RouteTrackingServiceUtils.getRouteId(context));
        sendRoutePoint(context, location, 1, headers);
        final int perimeter = settings.getInt("perimeter", RouteTrackingService.DEFAULT_PERIMETER);
        if (StringUtils.isNotEmpty(app)) {
            final String deviceId = getDeviceId(context, true);
            final String message = deviceId + " is in perimeter " + DistanceFormatter.format(perimeter) +
                             "\n" + "Battery level: " + getBatteryLevel(context) +
                             "\n" + MAPS_URL_PREFIX + latAndLongFormat.format(location.getLatitude()).replace(',', '.') + "," + latAndLongFormat.format(location.getLongitude()).replace(',', '.') +
                             "\n" + perimeter;
            sendCloudMessage(context, location, app, message, null, 1, 2000, headers);
        }
    }

    public static void sendCommandMessage(final Context context, final Bundle extras) {
        String text = null, title = null, phoneNumber = null, telegramId = null, email = null, command = null, app = null;
        String deviceId = getDeviceId(context, true);
        List<String> notifications = new ArrayList<>();
        PreferencesUtils settings = new PreferencesUtils(context);

        if (extras != null) {
            phoneNumber = extras.getString("phoneNumber");
            telegramId = extras.getString("telegramId");
            email = extras.getString("email");
            command = extras.getString("command");
            app = extras.getString("app");
        }

        //Log.d(TAG, "sendCommandMessage params: " + phoneNumber + " " + telegramId + " " + email + " " + app + " " + command);

        if (command != null) {
            switch (command) {
                case Command.RESUME_COMMAND:
                    title = context.getString(R.string.app_name) + " resumed location tracking on device " + deviceId;
                    if (GmsSmartLocationManager.isLocationEnabled(context) && settings.getBoolean("motionDetectorRunning", false)) {
                        text = "Device location tracking has been resumed. ";
                        if (StringUtils.isNotEmpty(settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER))) {
                            notifications.add(settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER));
                        }
                        if (StringUtils.isNotEmpty(settings.getString(MainActivity.NOTIFICATION_EMAIL))) {
                            notifications.add(settings.getString(MainActivity.NOTIFICATION_EMAIL));
                        }
                        if (StringUtils.isNotEmpty(settings.getString(MainActivity.NOTIFICATION_SOCIAL))) {
                            notifications.add(printTelegram(settings.getString(MainActivity.NOTIFICATION_SOCIAL)));
                        }
                        if (notifications.isEmpty()) {
                            text += "No notifications will be sent!";
                        } else {
                            text += "Notifications will be sent to " + StringUtils.join(notifications, ',');
                        }
                    } else {
                        text = "Location service is not available. No notifications will be sent. Check permissions and device configuration!";
                    }
                    text += "\nBattery level: " + getBatteryLevel(context);
                    break;
                case Command.STOP_COMMAND:
                    title = context.getString(R.string.app_name) + " stopped location tracking on device " + deviceId;
                    text = "Device location tracking on device " + deviceId + " has been stopped.\nBattery level: " + getBatteryLevel(context);
                    break;
                case Command.START_COMMAND:
                    title = context.getString(R.string.app_name) + " started location tracking on device " + deviceId;
                    if (GmsSmartLocationManager.isLocationEnabled(context) && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("motionDetectorRunning", false)) {
                        text = "Device location tracking on device " + deviceId + " is running. " +
                                "Track route live " + RouteTrackingServiceUtils.getRouteUrl(context) + "/now";
                        if (StringUtils.isNotEmpty(settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER))) {
                            notifications.add(settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER));
                        }
                        if (StringUtils.isNotEmpty(settings.getString(MainActivity.NOTIFICATION_EMAIL))) {
                            notifications.add(settings.getString(MainActivity.NOTIFICATION_EMAIL));
                        }
                        if (StringUtils.isNotEmpty(settings.getString(MainActivity.NOTIFICATION_SOCIAL))) {
                            notifications.add(printTelegram(settings.getString(MainActivity.NOTIFICATION_SOCIAL)));
                        }
                        if (notifications.isEmpty()) {
                            text += "\nNo notifications will be sent!";
                        } else {
                            text += "\nNotifications will be sent to " + StringUtils.join(notifications, ',');
                        }
                    } else {
                        text = "Location service is not available. No notifications will be sent. Check permissions and device configuration!";
                    }
                    text += "\nBattery level: " + getBatteryLevel(context);
                    break;
                case Command.MUTE_COMMAND:
                    text = deviceId + " has been muted.";
                    break;
                case Command.UNMUTE_COMMAND:
                    text = deviceId + " has been unmuted.";
                    break;
                case Command.RADIUS_COMMAND:
                    int radius = settings.getInt("radius");
                    if (radius > 0) {
                        text = "Device location tracking radius on device " + deviceId + " has been changed to " + radius + " meters.";
                    } else {
                        text = "Device location tracking radius for device " + deviceId + " is incorrect. Please try again.";
                    }
                    break;
                case Command.ROUTE_COMMAND:
                    title = context.getString(R.string.message, deviceId) + " - route map link";
                    final int size = extras.getInt("size", 0);
                    if (size > 1) {
                        text = "Check your route from device " + deviceId + " at " + RouteTrackingServiceUtils.getRouteUrl(context);
                    } else if (size == 0) {
                        text = "No route points has been recorded on device " + deviceId + ". Try again later.";
                    } else if (size < 0) {
                        text = "No route points has been uploaded from device " + deviceId + ". Try again later.";
                    }
                    break;
                case Command.GPS_HIGH_COMMAND:
                    text = "GPS settings on " + deviceId + " has been changed to high accuracy.";
                    break;
                case Command.GPS_BALANCED_COMMAND:
                    text = "GPS settings on " + deviceId + " has been changed to balanced accuracy.";
                    break;
                case Command.NOTIFY_COMMAND:
                    text = "";
                    if (StringUtils.isNotEmpty(settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER))) {
                        notifications.add(settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER));
                    }
                    if (StringUtils.isNotEmpty(settings.getString(MainActivity.NOTIFICATION_EMAIL))) {
                        notifications.add(settings.getString(MainActivity.NOTIFICATION_EMAIL));
                    }
                    if (StringUtils.isNotEmpty(settings.getString(MainActivity.NOTIFICATION_SOCIAL))) {
                        notifications.add(printTelegram(settings.getString(MainActivity.NOTIFICATION_SOCIAL)));
                    }
                    if (notifications.isEmpty()) {
                        text += "No notifications will be sent from device " + deviceId + "! Please specify valid email, phone number or Telegram chat or channel.";
                    } else {
                        text += "Notifications from device " + deviceId + " will be sent to " + StringUtils.join(notifications, ", ");
                    }
                    break;
                case Command.AUDIO_COMMAND:
                    text = "Audio transmitter on device " + deviceId + " has been started.";
                    break;
                case Command.AUDIO_OFF_COMMAND:
                    text = "Audio transmitter on device " + deviceId + " has been stopped.";
                    break;
                case Command.TAKE_PHOTO_COMMAND:
                    title = context.getString(R.string.message, deviceId) + " - front camera photo";
                    boolean hiddenCamera = settings.getBoolean("hiddenCamera", false);
                    String imageUrl = extras.getString("imageUrl");
                    if (StringUtils.isEmpty(imageUrl) && hiddenCamera) {
                        text = "Front camera photo will be taken on device " + deviceId + ". You should receive link soon.";
                    } else if (StringUtils.isEmpty(imageUrl) && !hiddenCamera) {
                        text = "Front camera is disabled on device " + deviceId + "! No photo will be taken.";
                    } else {
                        text = "Front camera on device " + deviceId + " photo: " + imageUrl;
                    }
                    text += "\n" + "Battery level: " + getBatteryLevel(context);
                    break;
                case Command.PIN_COMMAND:
                    title = context.getString(R.string.message, deviceId) + " - Security PIN";
                    final String pin = settings.getEncryptedString(PinActivity.DEVICE_PIN);
                    if (StringUtils.isEmpty(pin)) {
                        text = "No Security PIN is set on device " + deviceId + "!";
                    } else {
                        text = "Security PIN on device " + deviceId + " is " + pin;
                        final String secret = extras.getString("secret");
                        if (StringUtils.isNotEmpty(secret)) {
                            text += "\n" + secret;
                        }
                    }
                    text += "\n" + "Battery level: " + getBatteryLevel(context);
                    break;
                case Command.PING_COMMAND:
                    text = "Pong from " + deviceId;
                    text += "\n" + "Battery level: " + getBatteryLevel(context);
                    break;
                case Command.HELLO_COMMAND:
                    title = "Greetings from " + context.getString(R.string.app_name)  + " installed on device " + deviceId;
                    text = "Hello from " + deviceId;
                    text += "\n" + "Battery level: " + getBatteryLevel(context);
                    break;
                case Command.RING_COMMAND:
                    text = "You should now hear ringtone from your device " + deviceId;
                    text += "\n" + "Battery level: " + getBatteryLevel(context);
                    break;
                case Command.RING_OFF_COMMAND:
                    text = "You should now stop hearing ringtone from your device " + deviceId;
                    text += "\n" + "Battery level: " + getBatteryLevel(context);
                    break;
                case Command.CALL_COMMAND:
                    text = "Failed to initiate phone call from device " + deviceId + "!";
                    break;
                case Command.SHARE_COMMAND:
                    text = "Unable to share location from device " + deviceId + ". Required permissions are not granted!";
                    break;
                case Command.ABOUT_COMMAND:
                    text = AppUtils.getInstance().getAboutMessage(context) +
                            "\n" + "Battery level: " + getBatteryLevel(context);
                    break;
                case Command.LOCK_SCREEN_COMMAND:
                    text = "Screen locked successfully on device " + deviceId + "!" +
                            "\n" + "Battery level: " + getBatteryLevel(context);
                    break;
                case Command.LOCK_SCREEN_FAILED:
                    text = "Screen lock failed on device " + deviceId + " due to insufficient privileges!";
                    command = Command.LOCK_SCREEN_COMMAND;
                    break;
                case Command.CONFIG_COMMAND:
                    text = "Configuration change on device " + deviceId + " has been applied.";
                    break;
                case Command.STOPPED_TRACKER:
                    text = "Device location tracking on device " + deviceId + " is stopped.\nBattery level: " + getBatteryLevel(context);
                    break;
                case Command.MUTE_FAILED:
                    text = "Mute failed on device " + deviceId + " due to insufficient privileges!";
                    command = Command.MUTE_COMMAND;
                    break;
                case Command.INVALID_PIN:
                    if (extras != null) {
                        String sender = extras.getString("sender", "unknown");
                        String source = extras.getString("source");
                        String invalidCommand = extras.getString("invalidCommand");
                        text = "Command " + invalidCommand + " with invalid pin has been sent to device " + deviceId + " from " + source + " " + sender + ".";
                    } else {
                        text = "Command with invalid pin has been sent to device " + deviceId + ".";
                    }
                    break;
                case Command.INVALID_COMMAND:
                    if (extras != null) {
                        String sender = extras.getString("sender", "unknown");
                        String source = extras.getString("source");
                        String invalidCommand = extras.getString("invalidCommand");
                        text = "Invalid command " + invalidCommand + " has been sent to device " + deviceId + " from " + source + " " + sender + ".";
                    } else {
                        text = "Invalid command has been sent to device " + deviceId + ".";
                    }
                    break;
                case Command.RESET_COMMAND:
                    text = "Reset to factory defaults on device " + deviceId + " has been started.";
                    break;
                case Command.RESET_FAILED:
                    text = "Reset to factory defaults on device " + deviceId + " has failed.";
                    command = Command.RESET_COMMAND;
                    break;
                case Command.ALARM_COMMAND:
                    int interval = settings.getInt(LocationAlarmUtils.ALARM_INTERVAL, 12);
                    text = context.getResources().getQuantityString(R.plurals.alarm_interval, interval, interval);
                    if (settings.getLong(LocationAlarmUtils.ALARM_KEY,0L) > 0) {
                        text += context.getString(R.string.alarm_settings_suffix, new PrettyTime().format(new Date(settings.getLong(LocationAlarmUtils.ALARM_KEY))));
                    }
                    break;
                default:
                    Log.e(TAG, "Messenger received wrong command: " + command);
                    break;
            }
        }
        if (StringUtils.isNotEmpty(text) && StringUtils.isNotEmpty(command)) {
            if (StringUtils.isNotEmpty(phoneNumber)) {
                sendSMS(context, phoneNumber, text);
            }
            try {
                text = URLEncoder.encode(text, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            if (StringUtils.isNotEmpty(telegramId)) {
                sendTelegram(context, null, telegramId, text, 1, new HashMap<String, String>());
            }
            if (StringUtils.isNotEmpty(email)) {
                if (StringUtils.isEmpty(title)) {
                    title = context.getString(R.string.message, deviceId);
                }
                text += "\n" + context.getString(R.string.deviceUrl) + "/" + getDeviceId(context, false);
                sendEmail(context, null, email, text, title, 1, new HashMap<String, String>());
            }
            if (StringUtils.isNotEmpty(app)) {
                sendCloudMessage(context, null, app, text, command, 1, 2000, new HashMap<String, String>());
            }
        }
    }

    public static void sendLocationErrorMessage(Context context, String phoneNumber, String telegramId, String email, String app) {
        String deviceId = getDeviceId(context, true);
        String message = context.getString(R.string.error_getting_location, deviceId) +
                "\n" + "Battery level: " + getBatteryLevel(context);
        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, message);
        }
        if (StringUtils.isNotEmpty(telegramId)) {
            sendTelegram(context, null, telegramId, message, 1, new HashMap<String, String>());
        }
        if (StringUtils.isNotEmpty(email)) {
            String title = context.getString(R.string.message, deviceId) + " - current location";
            message += "\n" + "https://www.gms-world.net/showDevice/" + getDeviceId(context, false);
            sendEmail(context, null, email, message, title, 1, new HashMap<String, String>());
        }
        if (StringUtils.isNotEmpty(app)) {
            sendCloudMessage(context, null, app, message, Command.SHARE_COMMAND, 1, 2000, new HashMap<String, String>());
        }
    }

    public static void sendLoginFailedMessage(Context context, String phoneNumber, String telegramId, String email, String app) {
        String deviceId = getDeviceId(context, true);
        String text = "Failed login attempt to your device " + deviceId + "."
                + " You should receive device location message soon."
                + "\n" + "Battery level: " + getBatteryLevel(context);
        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        }
        if (StringUtils.isNotEmpty(telegramId)) {
            sendTelegram(context, null, telegramId, text, 1, new HashMap<String, String>());
        }
        if (StringUtils.isNotEmpty(email)) {
            String title = context.getString(R.string.message, deviceId) + " - failed login";
            text += "\n" + context.getString(R.string.deviceUrl) + "/" + getDeviceId(context, false);
            sendEmail(context, null, email, text, title, 1, new HashMap<String, String>());
        }
        if (StringUtils.isNotEmpty(app)) {
            sendCloudMessage(context, null, app, text, null,1, 2000, new HashMap<String, String>());
        }
    }

    private static String booleanToString(Context context, Boolean enabled) {
        return (enabled) ? context.getString(R.string.enabled) : context.getString(R.string.disabled);
    }

    private static String printTelegram(String telegram) {
        if (StringUtils.startsWith(telegram, "@")) {
            return "Telegram: " + "https://t.me/" + telegram.substring(1);
        } else {
            return "Telegram: " + telegram;
        }
    }

    //public static double convertMPStoKMH(double speed) { return speed * 3.6; }

    //public static double convertMPStoMPH(double speed) { return speed * 2.23694; }

    public static String getSpeed(Context context, float speed) {
        Locale l;
        try {
            l = context.getResources().getConfiguration().locale;
        } catch (Exception e) { //might cause NPE on some devices
            l = java.util.Locale.getDefault();
        }

        if (l != null && StringUtils.equalsAny(l.getISO3Country(), "USA", "GBR")) {
            return (int) (speed * 2.23694) + "MPH";
        } else {
            return (int) (speed * 3.6) + "KM/H";
        }
    }

    private static int getBatteryLevel(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            float batteryPct = level / (float) scale;

            return (int) (batteryPct * 100);
        } else {
            return -1;
        }
    }

    public static void sendEmailRegistrationRequest(final Context context, final String email, final boolean validate, final int retryCount) {
        if (StringUtils.isNotEmpty(email)) {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN, "");
            if (StringUtils.isNotEmpty(tokenStr)) {
                sendEmailRegistrationRequest(context, email, validate, tokenStr, retryCount);
            } else {
                String queryString = "scope=dl&user=" + getDeviceId(context, false);
                Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            sendEmailRegistrationRequest(context, email, validate, getToken(context, results), retryCount);
                        } else if (responseCode == 500 && retryCount > 0) {
                            sendEmailRegistrationRequest(context, email, validate, retryCount - 1);
                        } else {
                            Log.d(TAG, "Failed to receive token: " + results);
                        }
                    }
                });
            }
        }
    }

    public static void sendTelegramRegistrationRequest(final Context context, final String telegramId, final int retryCount) {
        if (isValidTelegramId(telegramId)) {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN, "");
            if (StringUtils.isNotEmpty(tokenStr)) {
                sendTelegramRegistrationRequest(context, telegramId, tokenStr, 1);
            } else {
                String queryString = "scope=dl&user=" + getDeviceId(context, false);
                Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            sendTelegramRegistrationRequest(context, telegramId, getToken(context, results), 1);
                        } else if (responseCode == 500 && retryCount > 0) {
                            sendTelegramRegistrationRequest(context, telegramId, retryCount - 1);
                        } else {
                            Log.d(TAG, "Failed to receive token: " + results);
                        }
                    }
                });
            }
        }
    }

    private static void sendTelegramRegistrationRequest(final Context context, final String telegramId, final String tokenStr, final int retryCount) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + tokenStr);
        headers.put("X-GMS-AppVersionId", Integer.toString(AppUtils.getInstance().getVersionCode(context)));

        final String queryString = "type=register_t&chatId=" + telegramId + "&user=" + getDeviceId(context, false);
        Network.post(context, context.getString(R.string.notificationUrl), queryString, null, headers, new Network.OnGetFinishListener() {
            @Override
            public void onGetFinish(String results, int responseCode, String url) {
                if (responseCode == 200 && StringUtils.startsWith(results, "{")) {
                    JsonElement reply = new JsonParser().parse(results);
                    String status = null, secret = null;
                    if (reply != null) {
                        JsonElement st = reply.getAsJsonObject().get("status");
                        if (st != null) {
                            status = st.getAsString();
                            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(MainActivity.SOCIAL_REGISTRATION_STATUS, status).apply();
                        }
                        JsonElement se = reply.getAsJsonObject().get("secret");
                        if (se != null) {
                            secret = se.getAsString();
                            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(NotificationActivationDialogFragment.TELEGRAM_SECRET, secret).apply();
                        }
                    }
                    if (StringUtils.equalsIgnoreCase(status, "registered") || StringUtils.equalsIgnoreCase(status, "verified")) {
                        Toast.makeText(context, "Your Telegram chat or channel is already verified.", Toast.LENGTH_LONG).show();
                    } else if (StringUtils.equalsIgnoreCase(status, "unverified")) {
                        //show dialog to enter activation code sent to user
                        if (StringUtils.isNotEmpty(secret)) {
                            if (context instanceof Activity) {
                                Activity activity = (Activity)context;
                                if (!activity.isFinishing()) {
                                    try {
                                        NotificationActivationDialogFragment notificationActivationDialogFragment = NotificationActivationDialogFragment.newInstance(NotificationActivationDialogFragment.Mode.Telegram);
                                        notificationActivationDialogFragment.show(activity.getFragmentManager(), NotificationActivationDialogFragment.TAG);
                                    } catch (Exception e) {
                                        Log.e(TAG, e.getMessage(), e);
                                    }
                                }
                            }
                        } else {
                            onFailedTelegramRegistration(context, "Failed to send activation code to your Telegram chat or channel. Please register again your Telegram chat or channel!", true);
                        }
                    } else {
                        onFailedTelegramRegistration(context, "Oops! Something went wrong on our side. Please register again your Telegram chat or channel!", true);
                    }
                } else if (responseCode == 403) {
                    onFailedTelegramRegistration(context, "Please grant @device_locator_bot permission to write posts to you chat or channel!", true);
                } else if (responseCode == 400) {
                    if (context instanceof Activity) {
                        Activity activity = (Activity)context;
                        if (!activity.isFinishing()) {
                            try {
                                TelegramSetupDialogFragment telegramSetupDialogFragment = new TelegramSetupDialogFragment();
                                telegramSetupDialogFragment.show(activity.getFragmentManager(), TelegramSetupDialogFragment.TAG);
                            } catch (Exception e) {
                                Log.e(TAG, e.getMessage(), e);
                                onFailedTelegramRegistration(context, "Oops! Your Telegram channel id seems to be wrong. Please use button on the left to find your channel id!", false);
                            }
                        } else {
                            onFailedTelegramRegistration(context, "Oops! Your Telegram channel id seems to be wrong. Please use button on the left to find your channel id!", false);
                        }
                    }
                } else if (responseCode != 200 && retryCount > 0) {
                    sendTelegramRegistrationRequest(context, telegramId, tokenStr, retryCount - 1);
                } else {
                    onFailedTelegramRegistration(context, "Oops! Something went wrong on our side. Please register again your Telegram chat or channel!", true);
                }
            }
        });
    }

    public static void onFailedTelegramRegistration(Context context, String message, boolean clearTextInput) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(MainActivity.NOTIFICATION_SOCIAL, "").apply();
        if (context instanceof Activity) {
            final TextView telegramInput = ((Activity) context).findViewById(R.id.telegramId);
            if (clearTextInput) {
                telegramInput.setText("");
            }
            telegramInput.requestFocus();
            if (StringUtils.isNotEmpty(message)) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        }
    }

    private static void sendEmailRegistrationRequest(final Context context, final String email, final boolean validate, final String tokenStr, final int retryCount) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + tokenStr);
        headers.put("X-GMS-AppVersionId", Integer.toString(AppUtils.getInstance().getVersionCode(context)));

        try {
            final String queryString = "type=register_m&email=" + email + "&user=" + getDeviceId(context, false) + "&validate=" + validate;
            Network.post(context, context.getString(R.string.notificationUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    if (responseCode == 200 && StringUtils.startsWith(results, "{")) {
                        JsonElement reply = new JsonParser().parse(results);
                        String status = null, secret = null;
                        if (reply != null) {
                            JsonElement st = reply.getAsJsonObject().get("status");
                            if (st != null) {
                                status = st.getAsString();
                            }
                            JsonElement se = reply.getAsJsonObject().get("secret");
                            if (se != null) {
                                secret = se.getAsString();
                                PreferenceManager.getDefaultSharedPreferences(context).edit().putString(NotificationActivationDialogFragment.EMAIL_SECRET, secret).apply();
                            }
                        }
                        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(MainActivity.EMAIL_REGISTRATION_STATUS, status).apply();
                        if (StringUtils.equalsIgnoreCase(status, "registered") || StringUtils.equalsIgnoreCase(status, "verified")) {
                            Toast.makeText(context, "Your email address is already verified.", Toast.LENGTH_SHORT).show();
                        } else if (StringUtils.equalsIgnoreCase(status, "unverified")) {
                            //show dialog to enter activation code sent to user
                            if (StringUtils.isNotEmpty(secret)) {
                                if (context instanceof Activity) {
                                    Activity activity = (Activity) context;
                                    if (!activity.isFinishing()) {
                                        try {
                                            NotificationActivationDialogFragment notificationActivationDialogFragment = NotificationActivationDialogFragment.newInstance(NotificationActivationDialogFragment.Mode.Email);
                                            notificationActivationDialogFragment.show(activity.getFragmentManager(), NotificationActivationDialogFragment.TAG);
                                        } catch (Exception e) {
                                            Log.e(TAG, e.getMessage(), e);
                                        }
                                    }
                                }
                            } else {
                                onFailedEmailRegistration(context, "Failed to send activation email to your inbox. Please register your email address again!", true);
                            }
                        } else {
                            onFailedEmailRegistration(context, "Oops! Something went wrong. Please register your email address again!", true);
                        }
                    } else if (responseCode == 400) {
                        onFailedEmailRegistration(context, "Your email address seems to be incorrect. Please check it once again!", false);
                    } else if (responseCode != 200 && retryCount > 0) {
                        sendEmailRegistrationRequest(context, email, validate, tokenStr, retryCount - 1);
                    } else {
                        onFailedEmailRegistration(context, "Oops! Something went wrong. Please register your email address again!", true);
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        }
    }

    private static void onFailedEmailRegistration(Context context, String message, boolean clearTextInput) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(MainActivity.NOTIFICATION_EMAIL, "").apply();
        if (context instanceof Activity) {
            final TextView emailInput = ((Activity) context).findViewById(R.id.email);
            if (clearTextInput) {
                emailInput.setText("");
            }
            emailInput.requestFocus();
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("MissingPermission")
    public static String getDeviceId(Context context, boolean useUserDeviceName) {
        String androidDeviceId = null;

        if (useUserDeviceName) {
            if (PreferenceManager.getDefaultSharedPreferences(context).contains(MainActivity.DEVICE_NAME)) {
                androidDeviceId = PreferenceManager.getDefaultSharedPreferences(context).getString(MainActivity.DEVICE_NAME, null);
            } else {
                androidDeviceId = getDeviceName();
            }

        }

        if (androidDeviceId == null && context != null) {
            //get telephony imei Manifest.permission.READ_PHONE_STATE required
            if (Permissions.haveReadPhoneStatePermission(context) && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                try {
                    final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                    if (tm != null) {
                        androidDeviceId = tm.getDeviceId(); //imei
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }

            // get internal android device id
            if (StringUtils.isEmpty(androidDeviceId)) {
                try {
                    androidDeviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID); //Android Secure ID (SSAID)
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }

        if (androidDeviceId == null) {
            androidDeviceId = "unknown";
        }

        return androidDeviceId;
    }

    public static boolean composeEmail(Context context, String[] addresses, String subject, String message, boolean showToast) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL, addresses);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
            return true;
        } else {
            if (showToast) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
            return false;
        }
    }

    public static String getToken(Context context, String response) {
        String tokenStr = null;

        try {
            JsonElement reply = new JsonParser().parse(response);
            if (reply != null) {
                JsonElement t = reply.getAsJsonObject().get(DeviceLocatorApp.GMS_TOKEN);
                if (t != null) {
                    tokenStr = t.getAsString();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }

        if (StringUtils.isNotEmpty(tokenStr)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(DeviceLocatorApp.GMS_TOKEN, tokenStr).apply();
        }

        return tokenStr;
    }

    public static boolean isValidTelegramId(String telegramId) {
        //channel id could be negative number starting from -100 and length > 13 or string starting with @
        //chat id must be positive integer with length > 5
        if (StringUtils.startsWith(telegramId, "@") && telegramId.length() > 1 && !StringUtils.containsWhitespace(telegramId)) {
            return true;
        } else {
            if (StringUtils.isNotEmpty(telegramId)) {
                try {
                    long id = Long.parseLong(telegramId);
                    if (id < 0) {
                        return StringUtils.startsWith(telegramId, "-100") && telegramId.length() > 13;
                    } else {
                        return telegramId.length() > 5;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Invalid telegram chat or channel id " + telegramId);
                }
            }
        }
        return false;
    }

    public static String getDeviceName() {
        String manufacturer = StringUtils.capitalize(Build.MANUFACTURER);
        String model = StringUtils.capitalize(Build.MODEL);
        if (model.startsWith(manufacturer)) {
            return model;
        }
        return StringUtils.replaceAll(manufacturer + " " + model, " ", "-");
    }

    public static void getMyTelegramId(Context context) {
        onFailedTelegramRegistration(context, null, true);
        final boolean appInstalled = isAppInstalled(context, TELEGRAM_PACKAGE);
        String deviceSecret = "none";
        Intent intent;
        if (appInstalled) {
            final String deviceId = "device:"+getDeviceId(context, false);
            deviceSecret = Base64.encodeToString(deviceId.getBytes(), Base64.URL_SAFE).trim();
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.telegramStartUrl, deviceSecret)));
        } else {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.telegramWebUrl)));
        }
        try {
            if (appInstalled) {
                context.getPackageManager().getPackageInfo(TELEGRAM_PACKAGE, PackageManager.GET_ACTIVITIES);
                intent.setPackage(TELEGRAM_PACKAGE);
            }
            Log.d(TAG, "Setting Telegram Secret ");
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(NotificationActivationDialogFragment.TELEGRAM_SECRET, deviceSecret).apply();
            context.startActivity(intent);
            if (appInstalled) {
                Toast.makeText(context, "Please push Start button", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Enter command /id", Toast.LENGTH_LONG).show();
            }
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(context, "This function requires installed Telegram Messenger or Web Browser on your device.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            Toast.makeText(context, "Failed ot start Telegram Messenger.", Toast.LENGTH_LONG).show();
        }
    }

    public static void sendTelegramMessage(Context context, String message) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.setPackage(TELEGRAM_PACKAGE);
        context.startActivity(intent);
        Toast.makeText(context, "Find Device Locator bot", Toast.LENGTH_LONG).show();
    }

    /*public static void sendMessengerMessage(Context context, String message) {
        //Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("fb-messenger://user/252112178789066"));

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.setType("text/plain");
        intent.setPackage(FACEBOOK_MESSENGER_PACKAGE);
        try {
            context.startActivity(intent);
        }
        catch (Exception ex) {
            Log.e(TAG, ex.getMessage(), ex);
        }
    }*/

    public static boolean isAppInstalled(Context context, String packageName) {
        try {
            return context.getPackageManager().getApplicationInfo(packageName, 0).enabled;
        }
        catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static int getLocationMode(Context context) {
        try {
            return Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
        } catch (Settings.SettingNotFoundException e) {
            return -1;
        }
    }

    private static String locationToString(Context context) {
        int mode = getLocationMode(context);
        switch (mode) {
            case Settings.Secure.LOCATION_MODE_OFF:
                return context.getString(R.string.location_mode_off);
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                return context.getString(R.string.location_battery_saving);
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                return context.getString(R.string.location_sensors_only);
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                return context.getString(R.string.location_high_accuracy);
            default:
                //API version <= 17
                String status =  Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
                if (StringUtils.isEmpty(status)) {
                    status = "Unknown";
                }
                return status;
        }
    }
}
