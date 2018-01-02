package net.gmsworld.devicelocator.Utilities;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.Services.SmsSenderService;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by jstakun on 5/6/17.
 */

public class Messenger {

    private static final String TAG = Messenger.class.getSimpleName();

    private static final DecimalFormat latAndLongFormat = new DecimalFormat("#.######");

    public static void sendSMS(final Context context, final String phoneNumber, final String message) {
        //on samsung intents can't be null. the messages are not sent if intents are null
        ArrayList<PendingIntent> samsungFix = new ArrayList<>();
        samsungFix.add(PendingIntent.getBroadcast(context, 0, new Intent("SMS_RECEIVED"), 0));

        SmsManager smsManager = SmsManager.getDefault();
        ArrayList<String> parts = smsManager.divideMessage(message);
        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, samsungFix, samsungFix);
    }


    public static void sendEmail(final Context context, final Location location, final String email, final String message, final String title, final int retryCount) {
        if (StringUtils.isNotEmpty(email) && (StringUtils.isNotEmpty(message))) {
            if (Network.isNetworkAvailable(context)) {
                final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
                if (StringUtils.isNotEmpty(tokenStr)) {
                    sendEmail(context, location, email, message, title, tokenStr, 1);
                } else {
                    String queryString = "scope=dl&user=" + getDeviceId(context);
                    Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                            if (responseCode == 200) {
                                JsonObject token = new JsonParser().parse(results).getAsJsonObject();
                                SharedPreferences.Editor editor = settings.edit();
                                String tokenStr = token.get(DeviceLocatorApp.GMS_TOKEN_KEY).getAsString();
                                Log.d(TAG, "Received following token: " + token);
                                editor.putString(DeviceLocatorApp.GMS_TOKEN_KEY, tokenStr);
                                editor.commit();
                                sendEmail(context, location, email, message, title, tokenStr, 1);
                            } else if (responseCode == 500 && retryCount > 0) {
                                sendEmail(context, location, email, message, title, retryCount - 1);
                            } else {
                                Log.d(TAG, "Failed to receive token: " + results);
                            }
                        }
                    });
                }
            } else {
                Toast.makeText(context, R.string.no_network_error, Toast.LENGTH_LONG).show();
            }
        }
    }

    public static void sendTelegram(final Context context, final Location location, final String telegramId, final String message, final int retryCount) {
        if (NumberUtils.isCreatable(telegramId)) {
            if (Network.isNetworkAvailable(context)) {
                final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
                if (StringUtils.isNotEmpty(tokenStr)) {
                    sendTelegram(context, location, telegramId, message, tokenStr, 1);
                } else {
                    String queryString = "scope=dl&user=" + getDeviceId(context);
                    Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                            if (responseCode == 200) {
                                JsonObject token = new JsonParser().parse(results).getAsJsonObject();
                                SharedPreferences.Editor editor = settings.edit();
                                String tokenStr = token.get(DeviceLocatorApp.GMS_TOKEN_KEY).getAsString();
                                Log.d(TAG, "Received following token: " + token);
                                editor.putString(DeviceLocatorApp.GMS_TOKEN_KEY, tokenStr);
                                editor.commit();
                                sendTelegram(context, location, telegramId, message, tokenStr, 1);
                            } else if (responseCode == 500 && retryCount > 0) {
                                sendTelegram(context, location, telegramId, message, retryCount - 1);
                            } else {
                                Log.d(TAG, "Failed to receive token: " + results);
                            }
                        }
                    });
                }
            } else {
                Toast.makeText(context, R.string.no_network_error, Toast.LENGTH_LONG).show();
            }
        }
    }

    private static void sendTelegram(final Context context, final Location location, final String telegramId, final String message, final String tokenStr, final int retryCount) {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + tokenStr);
        headers.put("X-GMS-AppId", "2");
        String deviceId = getDeviceId(context);
        if (StringUtils.isNotEmpty(deviceId)) {
            headers.put("X-GMS-DeviceId", deviceId);
        }
        if (location != null) {
            headers.put("X-GMS-Lat", latAndLongFormat.format(location.getLatitude()));
            headers.put("X-GMS-Lng", latAndLongFormat.format(location.getLongitude()));
        }

        try {
            String queryString = "type=t_dl&chatId=" + telegramId + "&message=" + message + "&username=" + getDeviceId(context);
            Network.post(context, context.getString(R.string.notificationUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                    if (responseCode == 500 && retryCount > 0) {
                        sendTelegram(context, location, telegramId, message, tokenStr, retryCount-1);
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        }

    }

    private static void sendEmail(final Context context, final Location location, final String email, final String message, final String title, final String tokenStr, final int retryCount) {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + tokenStr);
        headers.put("X-GMS-AppId", "2");
        String deviceId = getDeviceId(context);
        if (StringUtils.isNotEmpty(deviceId)) {
            headers.put("X-GMS-DeviceId", deviceId);
        }
        if (location != null) {
            headers.put("X-GMS-Lat", latAndLongFormat.format(location.getLatitude()));
            headers.put("X-GMS-Lng", latAndLongFormat.format(location.getLongitude()));
        }

        try {
            String queryString = "type=m_dl&emailTo=" + email + "&message=" + message + "&title=" + title + "&username=" + getDeviceId(context);
            Network.post(context, context.getString(R.string.notificationUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                    if (responseCode == 500 && retryCount > 0) {
                        sendEmail(context, location, email, message, title, tokenStr, retryCount-1);
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        }
    }

    public static void sendLocationMessage(Context context, Location location, boolean fused, int speedType, String phoneNumber, String telegramId, String email) {
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

        if (location.hasSpeed()) {
            if (speedType == 0) {
                text += "\n" + context.getString(R.string.speed) + " " + ((int) convertMPStoKMH(location.getSpeed())) + "KM/H";
            } else {
                text += "\n" + context.getString(R.string.speed) + " " + ((int) convertMPStoMPH(location.getSpeed())) + "MPH";
            }
        }

        if (location.hasAltitude() && location.getAltitude() != 0) {
            text += "\n" + context.getString(R.string.altitude) + " " + ((int) location.getAltitude()) + "m";
        }

        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        } else {
            if (StringUtils.isNotEmpty(telegramId)) {
                sendTelegram(context, location, telegramId, text, 1);
            }
            if (StringUtils.isNotEmpty(email)) {
                String title = context.getString(R.string.message);
                String deviceId = net.gmsworld.devicelocator.Utilities.Messenger.getDeviceId(context);
                if (deviceId != null) {
                    title += " installed on device " + deviceId + " - current location";
                }
                text += "\n" + context.getString(R.string.deviceUrl) + "/" + deviceId;
                sendEmail(context, location, email, text, title, 1);
            }
        }
    }

    public static void sendGoogleMapsMessage(Context context, Location location, String phoneNumber, String telegramId, String email) {
        String text = "https://maps.google.com/maps?q=" + latAndLongFormat.format(location.getLatitude()).replace(',', '.') + "," + latAndLongFormat.format(location.getLongitude()).replace(',', '.') +
                "\n" + "Battery level: " + getBatteryLevel(context);
        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        } else {
            if (StringUtils.isNotEmpty(telegramId)) {
                sendTelegram(context, location, telegramId, text, 1);
            }
            if (StringUtils.isNotEmpty(email)) {
                String title = context.getString(R.string.message);
                String deviceId = net.gmsworld.devicelocator.Utilities.Messenger.getDeviceId(context);
                if (deviceId != null) {
                    title += " installed on device " + deviceId + " - location map link";
                }
                text += "\n" + context.getString(R.string.deviceUrl) + "/" + deviceId;
                sendEmail(context, location, email, text, title, 1);
            }
        }
    }

    public static void sendCommandMessage(final Context context, final Intent intent) {
        String text = null;
        String phoneNumber = intent.getExtras().getString("phoneNumber");
        String telegramId = intent.getExtras().getString("telegramId");
        String email = intent.getExtras().getString("email");
        String notificationNumber = intent.getExtras().getString("notificationNumber");
        String command = intent.getExtras().getString("command");

        List<String> notifications = new ArrayList<String>();

        switch (command) {
            case Command.RESUME_COMMAND:
                text = "Device location tracking service has been resumed. Battery level: " + getBatteryLevel(context);
                if (StringUtils.isNotEmpty(notificationNumber)) {
                    notifications.add(notificationNumber);
                }
                if (StringUtils.isNotEmpty(email)) {
                    notifications.add(email);
                }
                if (StringUtils.isNotEmpty(telegramId)) {
                    notifications.add("Telegram chat id: " + telegramId);
                }
                if (notifications.isEmpty()) {
                    text += " No notifications will be sent!";
                } else {
                    text += " Notifications will be sent to " + StringUtils.joinWith(", ", notifications);
                }
                break;
            case Command.STOP_COMMAND:
                text = "Device location tracking service has been stopped. Battery level: " + getBatteryLevel(context);
                break;
            case Command.START_COMMAND:
                text = "Device location tracking service has been started. Battery level: " + getBatteryLevel(context);
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
                    text = "Device location tracking service radius has been changed to " + radius + " meters.";
                } else {
                    text = "Device location tracking service radius is incorrect. Please try again.";
                }
                break;
            case Command.ROUTE_COMMAND:
                String title = intent.getStringExtra("title");
                int size = intent.getIntExtra("size", 0);
                if (size > 1) {
                    String showRouteUrl = context.getString(R.string.showRouteUrl);
                    text = "Check your route at: " + showRouteUrl + "/" + title;
                } else if (size == 0) {
                    text = "No route points has been recorder yet. Try again later.";
                } else if (size < 0) {
                    text = "No route points has been uploaded yet. Try again later.";
                }
                break;
            case Command.GPS_HIGH_COMMAND:
                text = "GPS settings as been changed to high accuracy.";
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
                    notifications.add("Telegram chat id: " + telegramId);
                }
                if (notifications.isEmpty()) {
                    text += "No notifications will be sent! Please specify valid email, phone number or Telegram chat id.";
                } else {
                    text += "Notifications will be sent to " + StringUtils.joinWith(", ", notifications);
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
                String imageUrl = intent.getExtras().getString("imageUrl");
                if (StringUtils.isEmpty(imageUrl) && hiddenCamera) {
                    text = "Photo will be taken. You should receive link soon.";
                } else if (StringUtils.isEmpty(imageUrl) && !hiddenCamera) {
                    text = "Camera is disabled! No photo will be taken.";
                } else {
                    text = "Front camera photo: " + imageUrl;
                }
                text +=  "\n" + "Battery level: " + getBatteryLevel(context);
                break;
            case Command.PIN_COMMAND:
                String pin = PreferenceManager.getDefaultSharedPreferences(context).getString(MainActivity.DEVICE_PIN,"");
                if (StringUtils.isEmpty(pin)) {
                    text = "No Security PIN is set!";
                } else {
                    text = "Your Security PIN is " + pin;
                }
                text += "\n" + "Battery level: " + getBatteryLevel(context);
                break;
            case Command.PING_COMMAND:
                text = "Pong from " + getDeviceId(context);
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
            default:
                Log.e(TAG, "Messenger received wrong command: " + command);
                break;
        }
        if (StringUtils.isNotEmpty(text)) {
            if (StringUtils.isNotEmpty(phoneNumber)) {
                sendSMS(context, phoneNumber, text);
            } else {
                if (StringUtils.isNotEmpty(telegramId)) {
                    sendTelegram(context, null, telegramId, text, 1);
                }

                if (StringUtils.isNotEmpty(email)) {
                    String title = context.getString(R.string.message);
                    String deviceId = net.gmsworld.devicelocator.Utilities.Messenger.getDeviceId(context);
                    if (deviceId != null) {
                        title += " installed on device " + deviceId;
                    }
                    text += "\n" + context.getString(R.string.deviceUrl) + "/" + deviceId;
                    sendEmail(context, null, email, text, title, 1);
                }
            }
        }
    }

    public static void sendAcknowledgeMessage(Context context, String phoneNumber, String telegramId, String email) {
        String text = context.getString(R.string.acknowledgeMessage) + "\n";
        text +=  context.getString(R.string.network) + " " + booleanToString(context, Network.isNetworkAvailable(context)) + "\n";
        text += context.getString(R.string.gps) + " " + SmsSenderService.locationToString(context) + "\n";
        text += "Battery level: " + getBatteryLevel(context);
        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        } else {
            if (StringUtils.isNotEmpty(telegramId)) {
                sendTelegram(context, null, telegramId, text, 1);
            }
            if (StringUtils.isNotEmpty(email)) {
                String title = context.getString(R.string.message);
                String deviceId = net.gmsworld.devicelocator.Utilities.Messenger.getDeviceId(context);
                if (deviceId != null) {
                    title += " installed on device " + deviceId + " - location request";
                }
                text += "\n" + context.getString(R.string.deviceUrl) + "/" + deviceId;
                sendEmail(context, null, email, text, title, 1);
            }
        }
    }

    public static void sendLoginFailedMessage(Context context, String phoneNumber, String telegramId, String email) {
        String deviceId = getDeviceId(context);
        String text = "Failed login attempt to your device " + deviceId + "."
                + " You should receive device location message soon."
                + "\n" + "Battery level: " + getBatteryLevel(context);
        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        } else {
            if (StringUtils.isNotEmpty(telegramId)) {
                sendTelegram(context, null, telegramId, text, 1);
            }
            if (StringUtils.isNotEmpty(email)) {
                String title = context.getString(R.string.message);
                if (deviceId != null) {
                    title += " installed on device " + deviceId + " - failed login";
                }
                text += "\n" + context.getString(R.string.deviceUrl)+ "/" + deviceId;
                sendEmail(context, null, email, text, title, 1);
            }
        }
    }

    private static String booleanToString(Context context, Boolean enabled) {
        return (enabled) ? context.getString(R.string.enabled) : context.getString(R.string.disabled);
    }

    private static double convertMPStoKMH(double speed) {
        return speed * 3.6;
    }

    private static double convertMPStoMPH(double speed) {
        return speed * 2.23694;
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
                String queryString = "scope=dl&user=" + getDeviceId(context);
                Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                        if (responseCode == 200) {
                            JsonObject token = new JsonParser().parse(results).getAsJsonObject();
                            SharedPreferences.Editor editor = settings.edit();
                            String tokenStr = token.get(DeviceLocatorApp.GMS_TOKEN_KEY).getAsString();
                            Log.d(TAG, "Received following token: " + token);
                            editor.putString(DeviceLocatorApp.GMS_TOKEN_KEY, tokenStr);
                            editor.commit();
                            sendEmailRegistrationRequest(context, email, tokenStr, 1);
                        } else if (responseCode == 500 && retryCount > 0) {
                            sendEmailRegistrationRequest(context, email, retryCount-1);
                        } else {
                            Log.d(TAG, "Failed to receive token: " + results);
                        }
                    }
                });
            }
        }
    }

    public static void sendTelegramRegistrationRequest(final Context context, final String telegramId, final int retryCount) {
        if (NumberUtils.isCreatable(telegramId)) {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
            if (StringUtils.isNotEmpty(tokenStr)) {
                sendTelegramRegistrationRequest(context, telegramId, tokenStr, 1);
            } else {
                String queryString = "scope=dl&user=" + getDeviceId(context);
                Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                        if (responseCode == 200) {
                            JsonObject token = new JsonParser().parse(results).getAsJsonObject();
                            SharedPreferences.Editor editor = settings.edit();
                            String tokenStr = token.get(DeviceLocatorApp.GMS_TOKEN_KEY).getAsString();
                            Log.d(TAG, "Received following token: " + token);
                            editor.putString(DeviceLocatorApp.GMS_TOKEN_KEY, tokenStr);
                            editor.commit();
                            sendTelegramRegistrationRequest(context, telegramId, tokenStr, 1);
                        } else if (responseCode == 500 && retryCount > 0) {
                            sendTelegramRegistrationRequest(context, telegramId, retryCount-1);
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
            String queryString = "type=register_t&chatId=" + telegramId + "&user=" + getDeviceId(context);
            Network.post(context, context.getString(R.string.notificationUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                    if (responseCode != 200 && retryCount > 0) {
                        sendTelegramRegistrationRequest(context, telegramId, tokenStr, retryCount-1);
                    } else if (responseCode == 200  && StringUtils.startsWith(results, "{")) {
                        JsonObject reply = new JsonParser().parse(results).getAsJsonObject();
                        String status = reply.get("status").getAsString();
                        if (StringUtils.equals(status, "registered")) {
                            Toast.makeText(context, "Your chat or channel is already verified. You should start receiving notifications...", Toast.LENGTH_LONG).show();
                        } else if (StringUtils.equals(status, "unverified")) {
                            Toast.makeText(context, "You'll receive verification instruction to your chat or channel", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(context, "Oops! Something went wrong on our side. Please remove and add again Telegram chat id!", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        //TODO testing
                        if (telegramId.startsWith("-")) {
                            Toast.makeText(context, "Please add @device_locator_bot to your channel with sending message permission and send us email with your Telegram channel id to finish registration!", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(context, "Oops! Something went wrong on our side. Please send us email with your Telegram chat id to finish registration!", Toast.LENGTH_LONG).show();
                        }
                        composeEmail(context, new String[] {"device-locator@gms-world.net"}, "Device Locator registration", "Please register my Telegram channel " + telegramId + " to Device Locator notifications service.", false);
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
            String queryString = "type=register_m&email=" + email + "&user=" + getDeviceId(context);
            Network.post(context, context.getString(R.string.notificationUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url + " with content " + results);
                    if (responseCode != 200 && retryCount > 0) {
                        sendEmailRegistrationRequest(context, email, tokenStr, retryCount-1);
                    } else if (responseCode == 200  && StringUtils.startsWith(results, "{")) {
                        JsonObject reply = new JsonParser().parse(results).getAsJsonObject();
                        String status = reply.get("status").getAsString();
                        if (StringUtils.equals(status, "registered")) {
                            Toast.makeText(context, "Your email address is already verified. You should start receiving notifications...", Toast.LENGTH_LONG).show();
                        } else if (StringUtils.equals(status, "unverified")) {
                            Toast.makeText(context, "You'll receive verification instruction to your email address", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(context, "Oops! Something went wrong. Please add again email address!", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(context, "Oops! Something went wrong. Please add again email address!", Toast.LENGTH_LONG).show();
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        }
    }

    public static String getDeviceId(Context context) {
        String androidDeviceId = null;

        if (context != null) {
            //android.Manifest.permission.READ_PHONE_STATE required
            // get telephony imei
            try {
                final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                androidDeviceId = tm.getDeviceId(); //imei
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }

            // get internal android device id
            if (androidDeviceId == null || androidDeviceId.length() == 0) {
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
}
