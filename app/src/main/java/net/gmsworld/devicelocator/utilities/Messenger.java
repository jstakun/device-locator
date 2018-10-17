package net.gmsworld.devicelocator.utilities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
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
                status = "Device Locator is unable to send SMS message due to device error!";
            }
        } else {
            status = "Device Locator is unable to send SMS message due to lack of SMS sending permission!";
        }
        if (StringUtils.isNotEmpty(status)) {
            Toast.makeText(context, status, Toast.LENGTH_LONG).show();
        }
    }

    public static void sendCloudMessage(final Context context, final Location location, final String imei, final String pin, final String message, final String replyToCommand, final int retryCount, final Map<String, String> headers) {
        if (StringUtils.isNotEmpty(imei) && StringUtils.isNotEmpty(pin) && StringUtils.isNotEmpty(message)) {
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
                        headers.put("X-GMS-Lat", latAndLongFormat.format(location.getLatitude()));
                        headers.put("X-GMS-Lng", latAndLongFormat.format(location.getLongitude()));
                    }
                    headers.put("X-GMS-UseCount", Integer.toString(settings.getInt("useCount", 1)));
                    if (StringUtils.equalsAnyIgnoreCase(replyToCommand, Command.START_COMMAND, Command.STOP_COMMAND, Command.RESUME_COMMAND, Command.PERIMETER_COMMAND, Command.ROUTE_COMMAND)) {
                        headers.put("X-GMS-RouteId", RouteTrackingServiceUtils.getRouteId(context));
                    }
                    sendCloudMessage(context, imei, pin, message, replyToCommand, 1, headers);
                } else {
                    String queryString = "scope=dl&user=" + deviceId;
                    Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            if (responseCode == 200) {
                                if (StringUtils.isNotEmpty(getToken(context, results))) {
                                    sendCloudMessage(context, location, imei, pin, message, replyToCommand, 1, headers);
                                } else {
                                    Log.e(TAG, "Failed to parse token!");
                                }
                            } else if (responseCode == 500 && retryCount > 0) {
                                sendCloudMessage(context, location, imei, pin, message, replyToCommand, retryCount - 1, headers);
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

    private static void sendCloudMessage(final Context context, final String imei, final String pin, final String message, final String replyToCommand, final int retryCount, final Map<String, String> headers) {
        String content = "imei=" + imei;
        content += "&command=" + Command.MESSAGE_COMMAND + "app";
        content += "&pin=" + pin;
        if (StringUtils.isNotEmpty(replyToCommand)) {
            content += "&replyToCommand=" + StringUtils.remove(replyToCommand, "dl");
        }
        try {
            content += "&args=" + URLEncoder.encode(message, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        Network.post(context, context.getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
            @Override
            public void onGetFinish(String results, int responseCode, String url) {
                if (responseCode == 200) {
                    //Toast.makeText(context, "Command has been sent!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Message has been sent to the cloud!");
                } else if (responseCode == 500 && retryCount > 0) {
                    sendCloudMessage(context, imei, pin, message, replyToCommand, retryCount - 1, headers);
                }
            }
        });
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
                        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(MainActivity.EMAIL_REGISTRATION_STATUS, status).apply();
                        if (StringUtils.equalsIgnoreCase(status, "sent")) {
                            Log.d(TAG, "Email message sent successfully");
                        } else if (StringUtils.equalsIgnoreCase(status, "unverified")) {
                            Toast.makeText(context, "Device Locator is unable to sent notification because your email address is unverified. Please check your inbox for registration message from device-locator@gms-world.net", Toast.LENGTH_LONG).show();
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
                            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(MainActivity.SOCIAL_REGISTRATION_STATUS, status).apply();
                            if (StringUtils.equalsIgnoreCase(status, "sent")) {
                                Log.d(TAG, "Telegram notification sent successfully!");
                            } else if (StringUtils.equalsIgnoreCase(status, "unverified")) {
                                PreferenceManager.getDefaultSharedPreferences(context).edit().putString(MainActivity.NOTIFICATION_SOCIAL, "").apply();
                                final TextView telegramInput = ((Activity) context).findViewById(R.id.telegramId);
                                if (telegramInput != null) {
                                    telegramInput.setText("");
                                }
                                Toast.makeText(context, "Device Locator is unable to sent notification because your chat or channel is unverified! Please register again your Telegram chat or channel.", Toast.LENGTH_LONG).show();
                            } else if (StringUtils.equalsIgnoreCase(status, "failed")) {
                                PreferenceManager.getDefaultSharedPreferences(context).edit().putString(MainActivity.NOTIFICATION_SOCIAL, "").apply();
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
        } else {
            if (StringUtils.isNotEmpty(telegramId)) {
                sendTelegram(context, location, telegramId, text, 1, new HashMap<String, String>());
            }
            if (StringUtils.isNotEmpty(email)) {
                String title = context.getString(R.string.message);
                if (deviceId != null) {
                    title += " installed on device " + deviceId + " - current location";
                    text += "\n" + context.getString(R.string.deviceUrl) + "/" + getDeviceId(context, false);
                }
                sendEmail(context, location, email, text, title, 1, new HashMap<String, String>());
            }
            if (StringUtils.isNotEmpty(app)) {
                String[] tokens = StringUtils.split(app, "+=+");
                sendCloudMessage(context, location, tokens[0], tokens[1], text, Command.SHARE_COMMAND, 1, new HashMap<String, String>());
            }
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
            String title = context.getString(R.string.message);
            if (deviceId != null) {
                title += " installed on device " + deviceId + " - location map link";
                text += "\n" + context.getString(R.string.deviceUrl) + "/" + getDeviceId(context, false);
            }
            sendEmail(context, location, email, text, title, 1, new HashMap<String, String>());
        }
        if (StringUtils.isNotEmpty(app)) {
            String[] tokens = StringUtils.split(app, "+=+");
            sendCloudMessage(context, location, tokens[0], tokens[1], text, Command.SHARE_COMMAND, 1, new HashMap<String, String>());
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
            String title = context.getString(R.string.message);
            if (deviceId != null) {
                title += " installed on device " + deviceId + " - location request";
                text += "\n" + context.getString(R.string.deviceUrl) + "/" + getDeviceId(context, false);
            }
            sendEmail(context, null, email, text, title, 1, new HashMap<String, String>());
        }
        if (StringUtils.isNotEmpty(app)) {
            String[] tokens = StringUtils.split(app, "+=+");
            sendCloudMessage(context, null, tokens[0], tokens[1], text, Command.SHARE_COMMAND, 1, new HashMap<String, String>());
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

        String message = deviceId + " location: " + latAndLongFormat.format(location.getLatitude()) + ", " + latAndLongFormat.format(location.getLongitude()) +
                " in distance of " + DistanceFormatter.format(distance) + " from previous location with accuracy " + DistanceFormatter.format((int) location.getAccuracy());
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
            String title = context.getString(R.string.message);
            if (deviceId != null) {
                title += " installed on device " + deviceId + " - location change";
            }
            sendEmail(context, location, email, message, title, 1, headers);
        } else {
            //send route point for online route tracking
            sendRoutePoint(context, location, 1, headers);
        }
        //send notification to cloud if tracking has been initiated with cloud message
        if (StringUtils.isNotEmpty(app)) {
            String[] tokens = StringUtils.split(app, "+=+");
            sendCloudMessage(context, location, tokens[0], tokens[1], message, null,1, headers);
        }
    }

    public static void sendPerimeterMessage(Context context, Location location, String app) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final Map<String, String> headers = new HashMap<>();
        headers.put("X-GMS-RouteId", RouteTrackingServiceUtils.getRouteId(context));
        sendRoutePoint(context, location, 1, headers);
        final int perimeter = settings.getInt("perimeter", RouteTrackingService.DEFAULT_PERIMETER);
        if (StringUtils.isNotEmpty(app)) {
            String[] tokens = StringUtils.split(app, "+=+");
            final String deviceId = getDeviceId(context, true);
            final String message = deviceId + " is in perimeter " + DistanceFormatter.format(perimeter) +
                             "\n" + "Battery level: " + getBatteryLevel(context) +
                             "\n" + MAPS_URL_PREFIX + latAndLongFormat.format(location.getLatitude()).replace(',', '.') + "," + latAndLongFormat.format(location.getLongitude()).replace(',', '.') +
                             "\n" + perimeter;
            sendCloudMessage(context, location, tokens[0], tokens[1], message, null, 1, headers);
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
            //notificationNumber = extras.getString("notificationNumber");
            command = extras.getString("command");
            app = extras.getString("app");
        }

        switch (command) {
            case Command.RESUME_COMMAND:
                title = "Device Locator resumed location tracking";
                if (deviceId != null) {
                    title += " on device " + deviceId;
                }
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
                text = "Device location tracking on device " + deviceId + " has been stopped.\nBattery level: " + getBatteryLevel(context);
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
                title = context.getString(R.string.message);
                if (deviceId != null) {
                    title += " installed on device " + deviceId + " - route map link";
                }
                int size = 0;
                if (extras != null) {
                    size = extras.getInt("size", 0);
                }
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
                boolean hiddenCamera = settings.getBoolean("hiddenCamera", false);
                String imageUrl = null;
                if (extras != null) {
                    imageUrl = extras.getString("imageUrl");
                }
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
                final String pin = settings.getEncryptedString(PinActivity.DEVICE_PIN);
                if (StringUtils.isEmpty(pin)) {
                    text = "No Security PIN is set on device " + deviceId + "!";
                } else {
                    text = "Your Security PIN on device " + deviceId + " is " + pin;
                }
                text += "\n" + "Battery level: " + getBatteryLevel(context);
                break;
            case Command.PING_COMMAND:
                text = "Pong from " + deviceId;
                text += "\n" + "Battery level: " + getBatteryLevel(context);
                break;
            case Command.HELLO_COMMAND:
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
                break;
            case Command.CONFIG_COMMAND:
                text = "Configuration change on device " + deviceId + " has been applied.";
                break;
            case Command.STOPPED_TRACKER:
                text = "Device location tracking on device " + deviceId + " is stopped.\nBattery level: " + getBatteryLevel(context);
                break;
            case Command.MUTE_FAILED:
                text = "Mute failed on device " + deviceId + " due to insufficient privileges!";
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
                    sendCloudMessage(context, null, tokens[0], tokens[1], text, command, 1, new HashMap<String, String>());
                }
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
            String title = context.getString(R.string.message);
            if (deviceId != null) {
                title += " installed on device " + deviceId + " - current location";
            }
            message += "\n" + "https://www.gms-world.net/showDevice/" + deviceId;
            sendEmail(context, null, email, message, title, 1, new HashMap<String, String>());
        }
        if (StringUtils.isNotEmpty(app)) {
            String[] tokens = StringUtils.split(app, "+=+");
            sendCloudMessage(context, null, tokens[0], tokens[1], message, Command.SHARE_COMMAND, 1, new HashMap<String, String>());
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
                sendCloudMessage(context, null, tokens[0], tokens[1], text, null,1, new HashMap<String, String>());
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

    private static String getSpeed(Context context, float speed) {
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

    public static void sendEmailRegistrationRequest(final Context context, final String email, final int retryCount) {
        if (StringUtils.isNotEmpty(email)) {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN, "");
            if (StringUtils.isNotEmpty(tokenStr)) {
                sendEmailRegistrationRequest(context, email, tokenStr, 1);
            } else {
                String queryString = "scope=dl&user=" + getDeviceId(context, false);
                Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
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

        try {
            String queryString = "type=register_t&chatId=" + telegramId + "&user=" + getDeviceId(context, false);
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
                                PreferenceManager.getDefaultSharedPreferences(context).edit().putString(NotificationActivationDialogFragment.TELEGRAM_SECRET, secret).apply();
                            }
                        }
                        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(MainActivity.SOCIAL_REGISTRATION_STATUS, status).apply();
                        if (StringUtils.equalsIgnoreCase(status, "registered") || StringUtils.equalsIgnoreCase(status, "verified")) {
                            Toast.makeText(context, "Your Telegram chat or channel is already verified.", Toast.LENGTH_LONG).show();
                        } else if (StringUtils.equalsIgnoreCase(status, "unverified")) {
                            //show dialog to enter activation code sent to user
                            if (StringUtils.isNotEmpty(secret)) {
                                if (!((Activity) context).isFinishing()) {
                                    NotificationActivationDialogFragment notificationActivationDialogFragment = NotificationActivationDialogFragment.newInstance(NotificationActivationDialogFragment.Mode.Telegram);
                                    notificationActivationDialogFragment.show(((Activity) context).getFragmentManager(), NotificationActivationDialogFragment.TAG);
                                }
                            } else {
                                onFailedTelegramRegistration(context, "Failed to send activation code to your Telegram chat or channel. Please register again your Telegram chat or channel!", true);
                            }
                        } else if (StringUtils.equalsIgnoreCase(status, "failed")) {
                            onFailedTelegramRegistration(context, "Oops! Failed to send activation code to your Telegram channel or chat. Please register again your Telegram chat or channel!", true);
                        } else {
                            onFailedTelegramRegistration(context, "Oops! Something went wrong on our side. Please register again your Telegram chat or channel!", true);
                        }
                    } else if (responseCode == 403) {
                        onFailedTelegramRegistration(context, "Please grant @device_locator_bot permission to write posts to you chat or channel!", true);
                    } else if (responseCode != 200 && retryCount > 0) {
                        sendTelegramRegistrationRequest(context, telegramId, tokenStr, retryCount - 1);
                    } else {
                        onFailedTelegramRegistration(context, "Oops! Your Telegram channel id seems to be wrong. Please use button on the left to find your channel id!", false);
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        }
    }

    private static void onFailedTelegramRegistration(Context context, String message, boolean clearTextInput) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(MainActivity.NOTIFICATION_SOCIAL, "").apply();
        final TextView telegramInput = ((Activity) context).findViewById(R.id.telegramId);
        if (clearTextInput) {
            telegramInput.setText("");
        }
        telegramInput.requestFocus();
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();

    }

    private static void sendEmailRegistrationRequest(final Context context, final String email, final String tokenStr, final int retryCount) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + tokenStr);
        headers.put("X-GMS-AppVersionId", Integer.toString(AppUtils.getInstance().getVersionCode(context)));

        try {
            String queryString = "type=register_m&email=" + email + "&user=" + getDeviceId(context, false);
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
                                if (!((Activity) context).isFinishing()) {
                                    NotificationActivationDialogFragment notificationActivationDialogFragment = NotificationActivationDialogFragment.newInstance(NotificationActivationDialogFragment.Mode.Email);
                                    notificationActivationDialogFragment.show(((Activity) context).getFragmentManager(), NotificationActivationDialogFragment.TAG);
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
                        sendEmailRegistrationRequest(context, email, tokenStr, retryCount - 1);
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
        final TextView emailInput = ((Activity) context).findViewById(R.id.email);
        if (clearTextInput) {
            emailInput.setText("");
        }
        emailInput.requestFocus();
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
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
            if (Permissions.haveReadPhoneStatePermission(context)) {
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

    /*private static void composeEmail(Context context, String[] addresses, String subject, String message, boolean showToast) {
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
    }*/

    public static String getToken(Context context, String response) {
        JsonElement reply = new JsonParser().parse(response);
        String tokenStr = null;
        if (reply != null) {
            JsonElement t = reply.getAsJsonObject().get(DeviceLocatorApp.GMS_TOKEN);
            if (t != null) {
                tokenStr = t.getAsString();
            }
        }
        //Log.d(TAG, "Received following token: " + tokenStr);
        if (StringUtils.isNotEmpty(tokenStr)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(DeviceLocatorApp.GMS_TOKEN, tokenStr).apply();
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

    public static String getDeviceName() {
        String manufacturer = StringUtils.capitalize(Build.MANUFACTURER);
        String model = StringUtils.capitalize(Build.MODEL);
        if (model.startsWith(manufacturer)) {
            return model;
        }
        return StringUtils.replaceAll(manufacturer + " " + model, " ", "-");
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
