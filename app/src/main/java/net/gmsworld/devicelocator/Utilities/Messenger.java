package net.gmsworld.devicelocator.Utilities;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Location;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.BroadcastReceivers.SmsReceiver;
import net.gmsworld.devicelocator.DeviceLocatorApp;
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

    protected static final DecimalFormat latAndLongFormat = new DecimalFormat("#.######");

    private Network.OnGetFinishListener telegramNotifier = new Network.OnGetFinishListener() {
        @Override
        public void onGetFinish(String results, int responseCode, String url) {
            Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
        }
    };


    public static void sendSMS(final Context context, final String phoneNumber, final String message) {
        //on samsung intents can't be null. the messages are not sent if intents are null
        ArrayList<PendingIntent> samsungFix = new ArrayList<>();
        samsungFix.add(PendingIntent.getBroadcast(context, 0, new Intent("SMS_RECEIVED"), 0));

        SmsManager smsManager = SmsManager.getDefault();
        ArrayList<String> parts = smsManager.divideMessage(message);
        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, samsungFix, samsungFix);
    }


    public static void sendEmail(final Context context, final String email, final String message, final String title, final int retryCount) {
        if (StringUtils.isNotEmpty(email) && (StringUtils.isNotEmpty(message) || StringUtils.isNotEmpty(title))) {
            if (Network.isNetworkAvailable(context)) {
                final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
                if (StringUtils.isNotEmpty(tokenStr)) {
                    sendEmail(context, email, message, title, tokenStr, 1);
                } else {
                    String queryString = "scope=dl&user=" + getDeviceId(context);
                    Network.get("https://www.gms-world.net/token?" + queryString, new Network.OnGetFinishListener() {
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
                                sendEmail(context, email, message, title, tokenStr, 1);
                            } else if (responseCode == 500 && retryCount > 0) {
                                sendEmail(context, email, message, title, retryCount - 1);
                            }
                        }
                    });
                }
            } else {
                Toast.makeText(context, R.string.no_network_error, Toast.LENGTH_LONG).show();
            }
        }
    }

    public static void sendTelegram(final Context context, final String telegramId, final String message, final int retryCount) {
        if (NumberUtils.isCreatable(telegramId)) {
            if (Network.isNetworkAvailable(context)) {
                final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
                if (StringUtils.isNotEmpty(tokenStr)) {
                    sendTelegram(context, telegramId, message, tokenStr, 1);
                } else {
                    String queryString = "scope=dl&user=" + getDeviceId(context);
                    Network.get("https://www.gms-world.net/token?" + queryString, new Network.OnGetFinishListener() {
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
                                sendTelegram(context, telegramId, message, tokenStr, 1);
                            } else if (responseCode == 500 && retryCount > 0) {
                                sendTelegram(context, telegramId, message, retryCount - 1);
                            }
                        }
                    });
                }
            } else {
                Toast.makeText(context, R.string.no_network_error, Toast.LENGTH_LONG).show();
            }
        }
    }

    private static void sendTelegram(final Context context, final String telegramId, final String message, final String tokenStr, final int retryCount) {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + tokenStr);

        try {
            String queryString = "type=t_dl&chatId=" + telegramId + "&message=" + message + "&user=" + getDeviceId(context);

            Network.post("https://www.gms-world.net/s/notifications", queryString, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                    if (responseCode == 500 && retryCount > 0) {
                        sendTelegram(context, telegramId, message, tokenStr, retryCount-1);
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        }

    }

    private static void sendEmail(final Context context, final String email, final String message, final String title, final String tokenStr, final int retryCount) {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + tokenStr);

        try {
            String queryString = "type=m_dl&emailTo=" + email + "&message=" + message + "&title=" + title;

            Network.post("https://www.gms-world.net/s/notifications", queryString, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                    if (responseCode == 500 && retryCount > 0) {
                        sendEmail(context, email, message, title, tokenStr, retryCount-1);
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, e.getMessage(), e);
        }
    }

    public static void sendLocationMessage(Context context, Location location, boolean fused, int speedType, String phoneNumber, String telegramId) {
        //Log.d(TAG, "sendLocationMessage()" + location.getAccuracy());
        Resources r = context.getResources();

        String text = r.getString(fused ? R.string.approximate : R.string.accurate) + " location:\n";

        text += r.getString(R.string.accuracy) + " " + Math.round(location.getAccuracy()) + "m\n";
        text += r.getString(R.string.latitude) + " " + latAndLongFormat.format(location.getLatitude()) + "\n";
        text += r.getString(R.string.longitude) + " " + latAndLongFormat.format(location.getLongitude()) + "\n";
        text += "Battery level: " + getBatteryLevel(context);

        if (location.hasSpeed()) {
            if (speedType == 0) {
                text += "\n" + r.getString(R.string.speed) + " " + ((int) convertMPStoKMH(location.getSpeed())) + "KM/H";
            } else {
                text += "\n" + r.getString(R.string.speed) + " " + ((int) convertMPStoMPH(location.getSpeed())) + "MPH";
            }
        }

        if (location.hasAltitude() && location.getAltitude() != 0) {
            text += "\n" + r.getString(R.string.altitude) + " " + ((int) location.getAltitude()) + "m";
        }

        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        } else if (StringUtils.isNotEmpty(telegramId)) {
            //text = text.replace("\n", ", ");
            sendTelegram(context, telegramId, text, 1);
        }
    }

    public static void sendGoogleMapsMessage(Context context, Location location, String phoneNumber, String telegramId) {
        String text = "https://maps.google.com/maps?q=" + latAndLongFormat.format(location.getLatitude()).replace(',', '.') + "," + latAndLongFormat.format(location.getLongitude()).replace(',', '.') + "\n" +
                "Battery level: " + getBatteryLevel(context);
        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        } else if (StringUtils.isNotEmpty(telegramId)) {
            sendTelegram(context, telegramId, text, 1);
        }
    }

    public static void sendCommandMessage(Context context, Intent intent, String command, String phoneNumber, String email, String telegramId, String notificationNumber, String param1) {
        String text = null;
        List<String> notifications = new ArrayList<String>();
        switch (command) {
            case SmsReceiver.START_COMMAND:
                text = "Device location tracking service has been started. Battery level: " + getBatteryLevel(context);
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
            case SmsReceiver.STOP_COMMAND:
                text = "Device location tracking service has been stopped. Battery level: " + getBatteryLevel(context);
                break;
            case SmsReceiver.RESET_COMMAND:
                text = "Device location tracking service has been reset. Battery level: " + getBatteryLevel(context);
                break;
            case SmsReceiver.MUTE_COMMAND:
                text = "Device has been muted.";
                break;
            case SmsReceiver.NORMAL_COMMAND:
                text = "Device has been set to normal audio settings.";
                break;
            case SmsReceiver.RADIUS_COMMAND:
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                int radius = settings.getInt("radius", -1);
                if (radius > 0) {
                    text = "Device location tracking service radius has been changed to " + radius + " meters.";
                } else {
                    text = "Device location tracking service radius is incorrect. Please try again.";
                }
                break;
            case SmsReceiver.ROUTE_COMMAND:
                String title = intent.getStringExtra("title");
                int size = intent.getIntExtra("size", 0);
                if (size > 1) {
                    String showRouteUrl = context.getResources().getString(R.string.showRouteUrl);
                    text = "Check your route at: " + showRouteUrl + "/" + title;
                } else if (size == 0) {
                    text = "No route points has been recorder yet. Try again later.";
                } else if (size < 0) {
                    text = "No route points has been uploaded yet. Try again later.";
                }
                break;
            case SmsReceiver.GPS_HIGH_COMMAND:
                text = "GPS settings as been changed to high accuracy.";
                break;
            case SmsReceiver.GPS_BALANCED_COMMAND:
                text = "GPS settings has been changed to balanced accuracy.";
                break;
            case SmsReceiver.NOTIFY_COMMAND:
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
            case SmsReceiver.AUDIO_COMMAND:
                text = "Audio transmitter has been started.";
                break;
            case SmsReceiver.NOAUDIO_COMMAND:
                text = "Audio transmitter has been stopped.";
                break;
            case SmsReceiver.TAKE_PHOTO_COMMAND:
                boolean hiddenCamera = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("hiddenCamera", false);
                if (StringUtils.isEmpty(param1) && hiddenCamera) {
                    text = "Photo will be taken. You should receive link soon.";
                } else if (StringUtils.isEmpty(param1) && !hiddenCamera) {
                    text = "Camera is disabled! No photo will be taken.";
                } else {
                    text = "Front camera photo: " + param1;
                }
                break;
            case SmsReceiver.PIN_COMMAND:
                String pin = PreferenceManager.getDefaultSharedPreferences(context).getString("token","");
                if (StringUtils.isEmpty(pin)) {
                    text = "No Security PIN is set!";
                } else {
                    text = "Your Security PIN is " + pin;
                }
            default:
                Log.e(TAG, "Messenger received wrong command: " + command);
                break;
        }
        if (StringUtils.isNotEmpty(text)) {
            if (StringUtils.isNotEmpty(phoneNumber)) {
                sendSMS(context, phoneNumber, text);
            } else if (StringUtils.isNotEmpty(telegramId)) {
                sendTelegram(context, telegramId, text, 1);
            }

        }
    }

    public static void sendAcknowledgeMessage(Context context, String phoneNumber, String telegramId) {
        Resources r = context.getResources();
        String text = r.getString(R.string.acknowledgeMessage);
        text += " " + r.getString(R.string.network) + " " + booleanToString(context, Network.isNetworkAvailable(context));
        text += ", " + r.getString(R.string.gps) + " " + SmsSenderService.locationToString(context);
        text += ", Battery level: " + getBatteryLevel(context);
        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        } else if (StringUtils.isNotEmpty(telegramId)) {
            sendTelegram(context, telegramId, text, 1);
        }
    }

    public static void sendLoginFailedMessage(Context context, String phoneNumber, String telegramId) {
        String text = "Failed login attempt to your device " + getDeviceId(context) + "."
                + " You should receive device location message soon."
                + " Battery level: " + getBatteryLevel(context);
        if (StringUtils.isNotEmpty(phoneNumber)) {
            sendSMS(context, phoneNumber, text);
        } else if (StringUtils.isNotEmpty(telegramId)) {
            sendTelegram(context, telegramId, text, 1);
        }
    }

    private static String booleanToString(Context context, Boolean enabled) {
        return (enabled) ? context.getResources().getString(R.string.enabled) : context.getResources().getString(R.string.disabled);
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

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level / (float)scale;

        return (int)(batteryPct * 100);
    }

    public static void sendEmailRegistrationRequest(final Context context, final String email, final int retryCount) {
        if (StringUtils.isNotEmpty(email)) {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
            if (StringUtils.isNotEmpty(tokenStr)) {
                sendEmailRegistrationRequest(context, email, tokenStr, 1);
            } else {
                String queryString = "scope=dl&user=" + getDeviceId(context);
                Network.get("https://www.gms-world.net/token?" + queryString, new Network.OnGetFinishListener() {
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
                Network.get("https://www.gms-world.net/token?" + queryString, new Network.OnGetFinishListener() {
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
            Network.post("https://www.gms-world.net/s/notifications", queryString, null, headers, new Network.OnGetFinishListener() {
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
                            Toast.makeText(context, "Oops! Something went wrong. Please add again Telegram chat id!", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(context, "Oops! Something went wrong. Please add again Telegram chat id!", Toast.LENGTH_LONG).show();
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
            Network.post("https://www.gms-world.net/s/notifications", queryString, null, headers, new Network.OnGetFinishListener() {
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
}
