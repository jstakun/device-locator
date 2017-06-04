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
import android.telephony.SmsManager;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.BroadcastReceivers.SmsReceiver;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.Services.SmsSenderService;

import org.apache.commons.lang3.StringUtils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jstakun on 5/6/17.
 */

public class Messenger {

    private static final String TAG = Messenger.class.getSimpleName();
    private static final String GMS_TOKEN_KEY = "gmsToken";

    private Network.OnGetFinishListener telegramNotifier = new Network.OnGetFinishListener() {
        @Override
        public void onGetFinish(String results, int responseCode, String url) {
            Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
        }
    };


    public static void sendSMS(final Context context, final String phoneNumber, final String message) {
        //Log.d(TAG, "Send SMS: " + phoneNumber + ", " + message);
        //on samsung intents can't be null. the messages are not sent if intents are null
        ArrayList<PendingIntent> samsungFix = new ArrayList<>();
        samsungFix.add(PendingIntent.getBroadcast(context, 0, new Intent("SMS_RECEIVED"), 0));

        SmsManager smsManager = SmsManager.getDefault();
        ArrayList<String> parts = smsManager.divideMessage(message);
        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, samsungFix, samsungFix);
    }


    public static void sendEmail(final Context context, final String email, final String message, final String title, final int retryCount) {
        if (StringUtils.isNotEmpty(email) && (StringUtils.isNotEmpty(message) || StringUtils.isNotEmpty(title))) {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String tokenStr = settings.getString(GMS_TOKEN_KEY, "");
            if (StringUtils.isNotEmpty(tokenStr)) {
                sendEmail(context, email, message, title, tokenStr, 1);
            } else {
                String queryString = "scope=dl&user=" + Network.getDeviceId(context);
                Network.get("https://www.gms-world.net/token?" + queryString, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                        if (responseCode == 200) {
                            JsonObject token = new JsonParser().parse(results).getAsJsonObject();
                            SharedPreferences.Editor editor = settings.edit();
                            String tokenStr = token.get(GMS_TOKEN_KEY).getAsString();
                            Log.d(TAG, "Received following token: " + token);
                            editor.putString(GMS_TOKEN_KEY, tokenStr);
                            editor.commit();
                            sendEmail(context, email, message, title, tokenStr, 1);
                        } else if (responseCode == 500 && retryCount > 0) {
                            sendEmail(context, email, message, title, retryCount-1);
                        }
                    }
                });
            }
        }
    }

    public static void sendTelegram(final Context context, final String telegramId, final String message, final int retryCount) {
        if (StringUtils.isNumeric(telegramId)) {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String tokenStr = settings.getString(GMS_TOKEN_KEY, "");
            if (StringUtils.isNotEmpty(tokenStr)) {
                sendTelegram(context, telegramId, message, tokenStr, 1);
            } else {
                String queryString = "scope=dl&user=" + Network.getDeviceId(context);
                Network.get("https://www.gms-world.net/token?" + queryString, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                        if (responseCode == 200) {
                            JsonObject token = new JsonParser().parse(results).getAsJsonObject();
                            SharedPreferences.Editor editor = settings.edit();
                            String tokenStr = token.get(GMS_TOKEN_KEY).getAsString();
                            Log.d(TAG, "Received following token: " + token);
                            editor.putString(GMS_TOKEN_KEY, tokenStr);
                            editor.commit();
                            sendTelegram(context, telegramId, message, tokenStr, 1);
                        } else if (responseCode == 500 && retryCount > 0) {
                            sendTelegram(context, telegramId, message, retryCount-1);
                        }
                    }
                });
            }
        }
    }

    private static void sendTelegram(final Context context, final String telegramId, final String message, final String tokenStr, final int retryCount) {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + tokenStr);
        headers.put("X-GMS-AppId", "2");
        headers.put("X-GMS-Scope", "dl");

        try {
            String queryString = "type=t_dl&chatId=" + telegramId + "&message=" + message + "&user=" + Network.getDeviceId(context);

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
        headers.put("X-GMS-AppId", "2");
        headers.put("X-GMS-Scope", "dl");

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

    public static void sendLocationMessage(Context context, Location location, boolean fused, int speedType, String phoneNumber) {
        //Log.d(TAG, "sendLocationMessage()" + location.getAccuracy());
        Resources r = context.getResources();

        DecimalFormat latAndLongFormat = new DecimalFormat("#.######");

        String text = r.getString(fused ? R.string.approximate : R.string.accurate) + " location:\n";

        text += r.getString(R.string.accuracy) + " " + Math.round(location.getAccuracy()) + "m\n";
        text += r.getString(R.string.latitude) + " " + latAndLongFormat.format(location.getLatitude()) + "\n";
        text += r.getString(R.string.longitude) + " " + latAndLongFormat.format(location.getLongitude()) + "\n";
        text += "Battery level: " + getBatteryLevel(context) + "%";

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

        sendSMS(context, phoneNumber, text);
    }

    public static void sendGoogleMapsMessage(Context context, Location location, String phoneNumber) {
        //Log.d(TAG, "sendGoogleMapsMessage() " + location.getAccuracy());
        String text = "https://maps.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
        sendSMS(context, phoneNumber, text);
    }

    public static void sendCommandMessage(Context context, Intent intent, String command, String phoneNumber) {
        String text = null;
        switch (command) {
            case SmsReceiver.START_COMMAND:
                text = "Device location tracking service has been started";
                break;
            case SmsReceiver.STOP_COMMAND:
                text = "Device Location tracking service has been stopped";
                break;
            case SmsReceiver.RESET_COMMAND:
                text = "Device location tracking service has been reset and started";
                break;
            case SmsReceiver.MUTE_COMMAND:
                text = "Device has been muted";
                break;
            case SmsReceiver.NORMAL_COMMAND:
                text = "Device has been set to normal audio settings";
                break;
            case SmsReceiver.RADIUS_COMMAND:
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                int radius = settings.getInt("radius", -1);
                if (radius > 0) {
                    text = "Device location tracking service radius has been changed to " + radius;
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
                text = "GPS settings as been changed to high accuracy";
                break;
            case SmsReceiver.GPS_BALANCED_COMMAND:
                text = "GPS settings has been changed to balanced accuracy";
                break;
            default:
                Log.e(TAG, "Messenger received wrong command: " + command);
                break;
        }
        if (text != null && text.length() > 0) {
            sendSMS(context, phoneNumber, text);
        }
    }

    public static void sendAcknowledgeMessage(Context context, String phoneNumber) {
        Resources r = context.getResources();
        String text = r.getString(R.string.acknowledgeMessage);
        text += " " + r.getString(R.string.network) + " " + booleanToString(context, Network.isNetworkAvailable(context));
        text += ", " + r.getString(R.string.gps) + " " + SmsSenderService.locationToString(context, SmsSenderService.getLocationMode(context));
        text += ", Battery level: " + Messenger.getBatteryLevel(context) + "%";
        sendSMS(context, phoneNumber, text);
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
}