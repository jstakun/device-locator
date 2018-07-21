package net.gmsworld.devicelocator.Utilities;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.Services.SmsSenderService;

import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
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

    public static void sendSMS(final Context context, final String phoneNumber, final String message) {
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
                status = "Device Locator is unable to send SMS message due to device error!";
            }
        } else {
            status = "Device Locator is unable to send SMS message due to lack of SMS sending permission!";
        }
        if (StringUtils.isNotEmpty(status)) {
            Toast.makeText(context, status, Toast.LENGTH_LONG).show();
        }
    }

    public static void sendCloudMessage(final Context context, final Location location, final String imei, final String pin, final String message, final int retryCount, final Map<String, String> headers) {
        if (StringUtils.isNotEmpty(imei) && StringUtils.isNotEmpty(pin) && StringUtils.isNotEmpty(message)) {
            if (Network.isNetworkAvailable(context)) {
                final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
                if (StringUtils.isNotEmpty(tokenStr)) {
                    headers.put("Authorization", "Bearer " + tokenStr);
                    headers.put("X-GMS-AppId", "2");
                    String deviceId = getDeviceId(context, false);
                    if (StringUtils.isNotEmpty(deviceId)) {
                        headers.put("X-GMS-DeviceId", deviceId);
                    }
                    if (location != null) {
                        headers.put("X-GMS-Lat", latAndLongFormat.format(location.getLatitude()));
                        headers.put("X-GMS-Lng", latAndLongFormat.format(location.getLongitude()));
                    }
                    headers.put("X-GMS-UseCount", Integer.toString(settings.getInt("useCount", 1)));
                    sendCloudMessage(context, imei, pin, message, 1, headers);
                } else {
                    String queryString = "scope=dl&user=" + getDeviceId(context, false);
                    Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                            if (responseCode == 200) {
                                if (StringUtils.isNotEmpty(getToken(context, results))) {
                                    sendCloudMessage(context, location, imei, pin, message, 1, headers);
                                } else {
                                    Log.e(TAG, "Failed to parse token!");
                                }
                            } else if (responseCode == 500 && retryCount > 0) {
                                sendCloudMessage(context, location, imei, pin, message, retryCount - 1, headers);
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

    private static void sendCloudMessage(final Context context, final String imei, final String pin, final String message, final int retryCount, final Map<String, String> headers) {
        String content = "imei=" + imei;
        content += "&command=" + Command.MESSAGE_COMMAND + "app";
        content += "&pin=" + pin;
        content += "&args=" + message;
        String url = context.getString(R.string.deviceManagerUrl);

        Network.post(context, url, content, null, headers, new Network.OnGetFinishListener() {
            @Override
            public void onGetFinish(String results, int responseCode, String url) {
                if (responseCode == 200) {
                    //Toast.makeText(context, "Command has been sent!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Message has been sent to the cloud!");
                } else if (responseCode == 500 && retryCount > 0) {
                    sendCloudMessage(context, imei, pin, message, retryCount - 1, headers);
                } else {
                    Log.d(TAG, "Received following response " + responseCode + ": " + results + " from " + url);
                }
            }
        });
    }

    public static void sendEmail(final Context context, final Location location, final String email, final String message, final String title, final int retryCount, final Map<String, String> headers) {
        if (StringUtils.isNotEmpty(email) && (StringUtils.isNotEmpty(message))) {
            if (Network.isNetworkAvailable(context)) {
                final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
                if (StringUtils.isNotEmpty(tokenStr)) {
                    headers.put("Authorization", "Bearer " + tokenStr);
                    headers.put("X-GMS-AppId", "2");
                    String deviceId = getDeviceId(context, false);
                    if (StringUtils.isNotEmpty(deviceId)) {
                        headers.put("X-GMS-DeviceId", deviceId);
                    }
                    if (location != null) {
                        headers.put("X-GMS-Lat", latAndLongFormat.format(location.getLatitude()));
                        headers.put("X-GMS-Lng", latAndLongFormat.format(location.getLongitude()));
                    }
                    headers.put("X-GMS-UseCount", Integer.toString(settings.getInt("useCount", 1)));
                    sendEmail(context, email, message, title, 1, headers);
                } else {
                    String queryString = "scope=dl&user=" + getDeviceId(context, false);
                    Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
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
            String queryString = "type=m_dl&emailTo=" + email + "&message=" + message + "&title=" + title + "&username=" + getDeviceId(context, false);
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
                        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("emailStatus", status).commit();
                        if (StringUtils.equalsIgnoreCase(status, "sent")) {
                            Log.d(TAG, "Email message sent successfully");
                        } else if (StringUtils.equalsIgnoreCase(status, "unverified")) {
                            Toast.makeText(context, "Device Locator is unable to sent notification because your email address is unverified. Please check your inbox for registration message from device-locator@gms-world.net", Toast.LENGTH_LONG).show();
                        //} else if (StringUtils.equalsIgnoreCase(status, "failed")) {
                        //    PreferenceManager.getDefaultSharedPreferences(context).edit().putString("email", "").commit();
                        //    final TextView emailInput = (TextView) ((Activity)context).findViewById(R.id.email);
                        //    emailInput.setText("");
                        //    Toast.makeText(context, "Oops! Your email seems to be wrong. Please register again email address again", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(context, "Oops! Failed to sent email notification. Something went wrong on our side!", Toast.LENGTH_LONG).show();
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        }
    }

    public static void sendTelegram(final Context context, final Location location, final String telegramId, final String message, final int retryCount, final Map<String, String> headers) {
        if (isValidTelegramId(telegramId)) {
            if (Network.isNetworkAvailable(context)) {
                final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
                if (StringUtils.isNotEmpty(tokenStr)) {
                    headers.put("Authorization", "Bearer " + tokenStr);
                    String deviceId = getDeviceId(context, false);
                    if (StringUtils.isNotEmpty(deviceId)) {
                        headers.put("X-GMS-DeviceId", deviceId);
                    }
                    if (location != null) {
                        headers.put("X-GMS-Lat", latAndLongFormat.format(location.getLatitude()));
                        headers.put("X-GMS-Lng", latAndLongFormat.format(location.getLongitude()));
                    }
                    headers.put("X-GMS-UseCount", Integer.toString(settings.getInt("useCount", 1)));
                    sendTelegram(context, telegramId, message, 1, headers);
                } else {
                    String queryString = "scope=dl&user=" + getDeviceId(context, false);
                    Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
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
            String queryString = "type=t_dl&chatId=" + telegramId + "&message=" + message + "&username=" + getDeviceId(context, false);
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
                            PreferenceManager.getDefaultSharedPreferences(context).edit().putString("telegramStatus", status).commit();
                            if (StringUtils.equalsIgnoreCase(status, "sent")) {
                                Log.d(TAG, "Telegram notification sent successfully!");
                            } else if (StringUtils.equalsIgnoreCase(status, "unverified")) {
                                PreferenceManager.getDefaultSharedPreferences(context).edit().putString("telegramId", "").commit();
                                final TextView telegramInput = ((Activity) context).findViewById(R.id.telegramId);
                                if (telegramInput != null) {
                                    telegramInput.setText("");
                                }
                                Toast.makeText(context, "Device Locator is unable to sent notification because your chat or channel is unverified! Please register again your Telegram chat or channel.", Toast.LENGTH_LONG).show();
                            } else if (StringUtils.equalsIgnoreCase(status, "failed")) {
                                PreferenceManager.getDefaultSharedPreferences(context).edit().putString("telegramId", "").commit();
                                final TextView telegramInput = ((Activity) context).findViewById(R.id.telegramId);
                                if (telegramInput != null) {
                                    telegramInput.setText("");
                                }
                                Toast.makeText(context, "Oops! Your Telegram chat or channel id seems to be wrong. Please register again your Telegram chat or channel!", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(context, "Oops! Failed to sent Telegram notification. Something went wrong on our side!", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        }

    }

    public static void sendRoutePoint(final Context context, final Location location, final int retryCount, final Map<String, String> headers) {
        if (Network.isNetworkAvailable(context)) {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
            if (StringUtils.isNotEmpty(tokenStr)) {
                headers.put("Authorization", "Bearer " + tokenStr);
                headers.put("X-GMS-AppId", "2");
                String deviceId = getDeviceId(context, false);
                if (StringUtils.isNotEmpty(deviceId)) {
                    headers.put("X-GMS-DeviceId", deviceId);
                }
                if (location != null) {
                    headers.put("X-GMS-Lat", latAndLongFormat.format(location.getLatitude()));
                    headers.put("X-GMS-Lng", latAndLongFormat.format(location.getLongitude()));
                }
                headers.put("X-GMS-UseCount", Integer.toString(settings.getInt("useCount", 1)));
                sendRoutePoint(context, 1, headers);
            } else {
                String queryString = "scope=dl&user=" + getDeviceId(context, false);
                Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
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
            String queryString = "type=routePoint&username=" + getDeviceId(context, false);
            Network.post(context, context.getString(R.string.notificationUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
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
        String text = context.getString(fused ? R.string.approximate : R.string.accurate) + " location:\n";

        text += context.getString(R.string.latitude) + " " + latAndLongFormat.format(location.getLatitude()) + "\n";
        text += context.getString(R.string.longitude) + " " + latAndLongFormat.format(location.getLongitude()) + "\n";
        text += context.getString(R.string.accuracy) + " " + Math.round(location.getAccuracy()) + "m\n";

        long diff = System.currentTimeMillis() - location.getTime();
        if (diff < 1000) {
            text += "Taken " + diff + " milliseconds ago" + "\n";
        } else if (diff < 60000) {
            text += "Taken " + (diff / 1000) + " seconds ago" + "\n";
        } else {
            text += "Taken " + (diff / 60000) + " minutes ago" + "\n";
        }

        text += "Battery level: " + getBatteryLevel(context);

        if (location.hasSpeed() && location.getSpeed() > 0f) {
            text += "\n" + context.getString(R.string.speed) + " " + getSpeed(context, location.getSpeed());
        }

        if (location.hasAltitude() && location.getAltitude() != 0) {
            text += "\n" + context.getString(R.string.altitude) + " " + ((int) location.getAltitude()) + "m";
        }

        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        } else {
            if (StringUtils.isNotEmpty(telegramId)) {
                sendTelegram(context, location, telegramId, text, 1, new HashMap<String, String>());
            }
            if (StringUtils.isNotEmpty(email)) {
                String title = context.getString(R.string.message);
                String deviceId = getDeviceId(context, true);
                if (deviceId != null) {
                    title += " installed on device " + deviceId + " - current location";
                    text += "\n" + context.getString(R.string.deviceUrl) + "/" + getDeviceId(context, false);
                }
                sendEmail(context, location, email, text, title, 1, new HashMap<String, String>());
            }
            if (StringUtils.isNotEmpty(app)) {
                String[] tokens = StringUtils.split(app, "+=+");
                sendCloudMessage(context, null, tokens[0], tokens[1], text, 1, new HashMap<String, String>());
            }
        }
    }

    public static void sendGoogleMapsMessage(Context context, Location location, String phoneNumber, String telegramId, String email, String app) {
        String text = "https://maps.google.com/maps?q=" + latAndLongFormat.format(location.getLatitude()).replace(',', '.') + "," + latAndLongFormat.format(location.getLongitude()).replace(',', '.') +
                "\n" + "Battery level: " + getBatteryLevel(context);
        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        } else {
            if (StringUtils.isNotEmpty(telegramId)) {
                sendTelegram(context, location, telegramId, text, 1, new HashMap<String, String>());
            }
            if (StringUtils.isNotEmpty(email)) {
                String title = context.getString(R.string.message);
                String deviceId = getDeviceId(context, true);
                if (deviceId != null) {
                    title += " installed on device " + deviceId + " - location map link";
                    text += "\n" + context.getString(R.string.deviceUrl) + "/" + getDeviceId(context, false);
                }
                sendEmail(context, location, email, text, title, 1, new HashMap<String, String>());
            }
            if (StringUtils.isNotEmpty(app)) {
                String[] tokens = StringUtils.split(app, "+=+");
                Messenger.sendCloudMessage(context, null, tokens[0], tokens[1], text, 1, new HashMap<String, String>());
            }
        }
    }

    public static void sendAcknowledgeMessage(Context context, String phoneNumber, String telegramId, String email, String app) {
        String text;
        if (GmsSmartLocationManager.isLocationEnabled(context)) {
            text = context.getString(R.string.acknowledgeMessage) + "\n";
            text += context.getString(R.string.network) + " " + booleanToString(context, Network.isNetworkAvailable(context)) + "\n";
            text += context.getString(R.string.gps) + " " + SmsSenderService.locationToString(context) + "\n";
        } else {
            text = "Location service is disabled! Unable to send location. Please enable location service and send the command again.\n";
        }
        text += "Battery level: " + getBatteryLevel(context);

        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        } else {
            if (StringUtils.isNotEmpty(telegramId)) {
                sendTelegram(context, null, telegramId, text, 1, new HashMap<String, String>());
            }
            if (StringUtils.isNotEmpty(email)) {
                String title = context.getString(R.string.message);
                String deviceId = getDeviceId(context, true);
                if (deviceId != null) {
                    title += " installed on device " + deviceId + " - location request";
                    text += "\n" + context.getString(R.string.deviceUrl) + "/" + getDeviceId(context, false);
                }
                sendEmail(context, null, email, text, title, 1, new HashMap<String, String>());
            }
            if (StringUtils.isNotEmpty(app)) {
                String[] tokens = StringUtils.split(app, "+=+");
                Messenger.sendCloudMessage(context, null, tokens[0], tokens[1], text, 1, new HashMap<String, String>());
            }
        }
    }

    public static void sendCommandMessage(final Context context, final Bundle extras) {
        String text = null, title = null, phoneNumber = null, telegramId = null, email = null, notificationNumber = null, command = null, app = null;
        String deviceId = net.gmsworld.devicelocator.Utilities.Messenger.getDeviceId(context, true);
        List<String> notifications = new ArrayList<String>();

        if (extras != null) {
            phoneNumber = extras.getString("phoneNumber");
            telegramId = extras.getString("telegramId");
            email = extras.getString("email");
            notificationNumber = extras.getString("notificationNumber");
            command = extras.getString("command");
            app = extras.getString("app");
        }

        switch (command) {
            case Command.RESUME_COMMAND:
                title = "Device Locator resumed location tracking";
                if (deviceId != null) {
                    title += " on device " + deviceId;
                }
                if (GmsSmartLocationManager.isLocationEnabled(context) && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("motionDetectorRunning", false)) {
                    text = "Device location tracking has been resumed. ";
                    if (StringUtils.isNotEmpty(notificationNumber)) {
                        notifications.add(notificationNumber);
                    }
                    if (StringUtils.isNotEmpty(email)) {
                        notifications.add(email);
                    }
                    if (StringUtils.isNotEmpty(telegramId)) {
                        notifications.add(printTelegram(telegramId));
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
                text = "Device location tracking has been stopped.\nBattery level: " + getBatteryLevel(context);
                title = "Device Locator stopped location tracking";
                if (deviceId != null) {
                    title += " on device " + deviceId;
                }
                break;
            case Command.START_COMMAND:
                title = "Device Locator started location tracking";
                if (deviceId != null) {
                    title += " on device " + deviceId;
                }
                if (GmsSmartLocationManager.isLocationEnabled(context) && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("motionDetectorRunning", false)) {
                    text = "Device location tracking is running. " +
                            "Track route live: " + RouteTrackingServiceUtils.getRouteUrl(context) + "/now";
                    if (StringUtils.isNotEmpty(notificationNumber)) {
                        notifications.add(notificationNumber);
                    }
                    if (StringUtils.isNotEmpty(email)) {
                        notifications.add(email);
                    }
                    if (StringUtils.isNotEmpty(telegramId)) {
                        notifications.add(printTelegram(telegramId));
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
                text = "Device has been muted.";
                break;
            case Command.UNMUTE_COMMAND:
                text = "Device has been set to normal audio settings.";
                break;
            case Command.RADIUS_COMMAND:
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                int radius = settings.getInt("radius", -1);
                if (radius > 0) {
                    text = "Device location tracking radius has been changed to " + radius + " meters.";
                } else {
                    text = "Device location tracking radius is incorrect. Please try again.";
                }
                break;
            case Command.ROUTE_COMMAND:
                int size = extras.getInt("size", 0);
                if (size > 1) {
                    text = "Check your route at: " + RouteTrackingServiceUtils.getRouteUrl(context);
                } else if (size == 0) {
                    text = "No route points has been recorder yet. Try again later.";
                } else if (size < 0) {
                    text = "No route points has been uploaded yet. Try again later.";
                }
                break;
            case Command.GPS_HIGH_COMMAND:
                text = "GPS settings has been changed to high accuracy.";
                break;
            case Command.GPS_BALANCED_COMMAND:
                text = "GPS settings has been changed to balanced accuracy.";
                break;
            case Command.NOTIFY_COMMAND:
                text = "";
                if (StringUtils.isNotEmpty(notificationNumber)) {
                    notifications.add(notificationNumber);
                }
                if (StringUtils.isNotEmpty(email)) {
                    notifications.add(email);
                }
                if (StringUtils.isNotEmpty(telegramId)) {
                    notifications.add(printTelegram(telegramId));
                }
                if (notifications.isEmpty()) {
                    text += "No notifications will be sent! Please specify valid email, phone number or Telegram chat or channel.";
                } else {
                    text += "Notifications will be sent to " + StringUtils.join(notifications, ',');
                }
                break;
            case Command.AUDIO_COMMAND:
                text = "Audio transmitter has been started.";
                break;
            case Command.AUDIO_OFF_COMMAND:
                text = "Audio transmitter has been stopped.";
                break;
            case Command.TAKE_PHOTO_COMMAND:
                boolean hiddenCamera = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("hiddenCamera", false);
                String imageUrl = extras.getString("imageUrl");
                if (StringUtils.isEmpty(imageUrl) && hiddenCamera) {
                    text = "Photo will be taken. You should receive link soon.";
                } else if (StringUtils.isEmpty(imageUrl) && !hiddenCamera) {
                    text = "Camera is disabled! No photo will be taken.";
                } else {
                    text = "Front camera photo: " + imageUrl;
                }
                text += "\n" + "Battery level: " + getBatteryLevel(context);
                break;
            case Command.PIN_COMMAND:
                final String pin = PreferenceManager.getDefaultSharedPreferences(context).getString(MainActivity.DEVICE_PIN, "");
                if (StringUtils.isEmpty(pin)) {
                    text = "No Security PIN is set!";
                } else {
                    text = "Your Security PIN is " + pin;
                }
                text += "\n" + "Battery level: " + getBatteryLevel(context);
                break;
            case Command.PING_COMMAND:
                text = "Hello from " + getDeviceId(context, true);
                text += "\n" + "Battery level: " + getBatteryLevel(context);
                break;
            case Command.RING_COMMAND:
                text = "You should now hear ringtone from your device";
                text += "\n" + "Battery level: " + getBatteryLevel(context);
                break;
            case Command.RING_OFF_COMMAND:
                text = "You should now stop hearing ringtone from your device";
                text += "\n" + "Battery level: " + getBatteryLevel(context);
                break;
            case Command.CALL_COMMAND:
                text = "Failed to initiate phone call!";
                break;
            case Command.SHARE_COMMAND:
                text = "Unable to share location. Required permissions are not granted!";
                break;
            case Command.ABOUT_COMMAND:
                text = AppUtils.getInstance().getAboutMessage(context);
                text += "\n" + "Battery level: " + getBatteryLevel(context);
                break;
            case Command.LOCK_SCREEN_COMMAND:
                text = "Screen locked successfully!";
                text += "\n" + "Battery level: " + getBatteryLevel(context);
                break;
            case Command.LOCK_SCREEN_FAILED:
                text = "Screen lock failed due to insufficient privileges!";
                break;
            default:
                Log.e(TAG, "Messenger received wrong command: " + command);
                break;
        }
        if (StringUtils.isNotEmpty(text)) {
            if (StringUtils.isNotEmpty(phoneNumber)) {
                sendSMS(context, phoneNumber, text);
            } else {
                try {
                    text = URLEncoder.encode(text, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
                if (StringUtils.isNotEmpty(telegramId)) {
                    sendTelegram(context, null, telegramId, text, 1, new HashMap<String, String>());
                }

                if (StringUtils.isNotEmpty(email)) {
                    if (title == null) {
                        title = context.getString(R.string.message);
                        if (deviceId != null) {
                            title += " installed on device " + deviceId;
                        }
                    }
                    text += "\n" + context.getString(R.string.deviceUrl) + "/" + getDeviceId(context, false);
                    sendEmail(context, null, email, text, title, 1, new HashMap<String, String>());
                }

                if (StringUtils.isNotEmpty(app)) {
                    String[] tokens = StringUtils.split(app, "+=+");
                    sendCloudMessage(context, null, tokens[0], tokens[1], text, 1, new HashMap<String, String>());
                }
            }
        }
    }

    public static void sendLoginFailedMessage(Context context, String phoneNumber, String telegramId, String email, String app) {
        String deviceId = getDeviceId(context, true);
        String text = "Failed login attempt to your device " + deviceId + "."
                + " You should receive device location message soon."
                + "\n" + "Battery level: " + getBatteryLevel(context);
        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        } else {
            if (StringUtils.isNotEmpty(telegramId)) {
                sendTelegram(context, null, telegramId, text, 1, new HashMap<String, String>());
            }
            if (StringUtils.isNotEmpty(email)) {
                String title = context.getString(R.string.message);
                if (deviceId != null) {
                    title += " installed on device " + deviceId + " - failed login";
                    text += "\n" + context.getString(R.string.deviceUrl) + "/" + getDeviceId(context, false);
                }
                sendEmail(context, null, email, text, title, 1, new HashMap<String, String>());
            }
            if (StringUtils.isNotEmpty(app)) {
                String[] tokens = StringUtils.split(app, "+=+");
                Messenger.sendCloudMessage(context, null, tokens[0], tokens[1], text, 1, new HashMap<String, String>());
            }
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

    public static int getBatteryLevel(Context context) {
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

    public static void sendEmailRegistrationRequest(final Context context, final String email, final int retryCount) {
        if (StringUtils.isNotEmpty(email)) {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
            if (StringUtils.isNotEmpty(tokenStr)) {
                sendEmailRegistrationRequest(context, email, tokenStr, 1);
            } else {
                String queryString = "scope=dl&user=" + getDeviceId(context, false);
                Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                        if (responseCode == 200) {
                            sendEmailRegistrationRequest(context, email, getToken(context, results), 1);
                        } else if (responseCode == 500 && retryCount > 0) {
                            sendEmailRegistrationRequest(context, email, retryCount - 1);
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
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
            if (StringUtils.isNotEmpty(tokenStr)) {
                sendTelegramRegistrationRequest(context, telegramId, tokenStr, 1);
            } else {
                String queryString = "scope=dl&user=" + getDeviceId(context, false);
                Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
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
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + tokenStr);

        try {
            String queryString = "type=register_t&chatId=" + telegramId + "&user=" + getDeviceId(context, false);
            Network.post(context, context.getString(R.string.notificationUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                    if (responseCode != 200 && retryCount > 0) {
                        sendTelegramRegistrationRequest(context, telegramId, tokenStr, retryCount - 1);
                    } else if (responseCode == 200 && StringUtils.startsWith(results, "{")) {
                        JsonElement reply = new JsonParser().parse(results);
                        String status = null;
                        if (reply != null) {
                            JsonElement st = reply.getAsJsonObject().get("status");
                            if (st != null) {
                                status = st.getAsString();
                            }
                        }
                        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("telegramStatus", status).commit();
                        if (StringUtils.equalsIgnoreCase(status, "registered") || StringUtils.equalsIgnoreCase(status, "verified")) {
                            Toast.makeText(context, "Your chat or channel is already verified. You should start receiving notifications...", Toast.LENGTH_LONG).show();
                        } else if (StringUtils.equalsIgnoreCase(status, "unverified")) {
                            if (!((Activity)context).isFinishing()) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                builder.setPositiveButton(R.string.done, null);
                                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("telegramId", "").commit();
                                        final TextView telegramInput = ((Activity)context).findViewById(R.id.telegramId);
                                        telegramInput.setText(telegramId);
                                    }
                                });
                                builder.setMessage("Please check your Telegram chat or channel and confirm your registration.");
                                builder.setTitle("Telegram registration");
                                AlertDialog dialog = builder.create();
                                dialog.show();
                            } else {
                                Toast.makeText(context, "Please check your Telegram chat or channel and confirm your registration.", Toast.LENGTH_LONG).show();
                            }
                        } else if (StringUtils.equalsIgnoreCase(status, "failed")) {
                            PreferenceManager.getDefaultSharedPreferences(context).edit().putString("telegramId", "").commit();
                            final TextView telegramInput = ((Activity)context).findViewById(R.id.telegramId);
                            telegramInput.setText(telegramId);
                            Toast.makeText(context, "Oops! Your Telegram chat id seems to be wrong. Please register again your Telegram chat or channel!", Toast.LENGTH_LONG).show();
                        } else {
                            PreferenceManager.getDefaultSharedPreferences(context).edit().putString("telegramId", "").commit();
                            final TextView telegramInput = ((Activity)context).findViewById(R.id.telegramId);
                            telegramInput.setText(telegramId);
                            Toast.makeText(context, "Oops! Something went wrong on our side. Please register again your Telegram chat or channel!", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        if (telegramId.startsWith("-100") || telegramId.startsWith("@")) {
                            Toast.makeText(context, "Please add @device_locator_bot to your channel with message sending permission and send us email with your Telegram channel to finish registration!", Toast.LENGTH_LONG).show();
                            composeEmail(context, new String[]{"device-locator@gms-world.net"}, "Device Locator registration", "Please register my Telegram chat or channel " + telegramId + " to Device Locator notifications service.", false);
                        } else {
                            PreferenceManager.getDefaultSharedPreferences(context).edit().putString("telegramId", "").commit();
                            final TextView telegramInput = ((Activity)context).findViewById(R.id.telegramId);
                            telegramInput.setText(telegramId);
                            Toast.makeText(context, "Oops! Your Telegram channel id seems to be wrong. Please register again your Telegram chat or channel!", Toast.LENGTH_LONG).show();
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        }
    }

    private static void sendEmailRegistrationRequest(final Context context, final String email, final String tokenStr, final int retryCount) {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + tokenStr);

        try {
            String queryString = "type=register_m&email=" + email + "&user=" + getDeviceId(context, false);
            Network.post(context, context.getString(R.string.notificationUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url + " with content " + results);
                    if (responseCode != 200 && retryCount > 0) {
                        sendEmailRegistrationRequest(context, email, tokenStr, retryCount - 1);
                    } else if (responseCode == 200 && StringUtils.startsWith(results, "{")) {
                        JsonElement reply = new JsonParser().parse(results);
                        String status = null;
                        if (reply != null) {
                            JsonElement st = reply.getAsJsonObject().get("status");
                            if (st != null) {
                                status = st.getAsString();
                            }
                        }
                        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("emailStatus", status).commit();
                        if (StringUtils.equalsIgnoreCase(status, "registered") || StringUtils.equalsIgnoreCase(status, "verified")) {
                            Toast.makeText(context, "Your email address is already verified. You should start receiving notifications...", Toast.LENGTH_LONG).show();
                        } else if (StringUtils.equalsIgnoreCase(status, "unverified")) {
                            PreferenceManager.getDefaultSharedPreferences(context).edit().putLong("emailRegistrationMillis", System.currentTimeMillis()).commit();
                            if (!((Activity)context).isFinishing()) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                builder.setPositiveButton(R.string.done, null);
                                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("email", "").commit();
                                        final TextView emailInput = ((Activity)context).findViewById(R.id.email);
                                        emailInput.setText("");
                                    }
                                });
                                builder.setMessage("Please check your mail inbox for message from device-locator@gms-world.net and confirm your registration.");
                                builder.setTitle("Email registration");
                                AlertDialog dialog = builder.create();
                                dialog.show();
                            } else {
                                Toast.makeText(context, "Please check your mail inbox for message from device-locator@gms-world.net and confirm your registration.", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            PreferenceManager.getDefaultSharedPreferences(context).edit().putString("email", "").commit();
                            final TextView emailInput = ((Activity)context).findViewById(R.id.email);
                            emailInput.setText("");
                            Toast.makeText(context, "Oops! Something went wrong. Please add again your email address!", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("email", "").commit();
                        final TextView emailInput = ((Activity)context).findViewById(R.id.email);
                        emailInput.setText("");
                        Toast.makeText(context, "Oops! Something went wrong. Please add again your email address!", Toast.LENGTH_LONG).show();
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        }
    }

    public static String getDeviceId(Context context, boolean useUserDeviceName) {
        String androidDeviceId = null;

        if (useUserDeviceName) {
            androidDeviceId = PreferenceManager.getDefaultSharedPreferences(context).getString("deviceName", null);
        }

        if (androidDeviceId == null && context != null) {
            //android.Manifest.permission.READ_PHONE_STATE required
            // get telephony imei
            try {
                final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    androidDeviceId = tm.getDeviceId(); //imei
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }

            // get internal android device id
            if (StringUtils.isEmpty(androidDeviceId)) {
                try {
                    androidDeviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
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

    private static void composeEmail(Context context, String[] addresses, String subject, String message, boolean showToast) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL, addresses);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        } else if (showToast) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }

    public static String getToken(Context context, String response) {
        JsonElement reply = new JsonParser().parse(response);
        String tokenStr = null;
        if (reply != null) {
            JsonElement t = reply.getAsJsonObject().get(DeviceLocatorApp.GMS_TOKEN_KEY);
            if (t != null) {
                tokenStr = t.getAsString();
            }
        }
        //Log.d(TAG, "Received following token: " + tokenStr);
        if (StringUtils.isNotEmpty(tokenStr)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(DeviceLocatorApp.GMS_TOKEN_KEY, tokenStr).commit();
        }
        return tokenStr;
    }

    public static boolean isValidTelegramId(String telegramId) {
        //channel id could be negative number starting from -100 or string starting with @
        //chat id must be positive integer
        if (StringUtils.startsWith(telegramId, "@") && !StringUtils.containsWhitespace(telegramId)) {
            return true;
        } else {
            if (StringUtils.isNotEmpty(telegramId)) {
                try {
                    long id = Long.parseLong(telegramId);
                    if (id < 0) {
                        return StringUtils.startsWith(telegramId, "-100");
                    } else {
                        return true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Invalid telegram chat or channel id " + telegramId);
                }
            }
        }
        return false;
    }
}
