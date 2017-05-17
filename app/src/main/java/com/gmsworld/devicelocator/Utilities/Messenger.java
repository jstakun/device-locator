package com.gmsworld.devicelocator.Utilities;

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

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.gmsworld.devicelocator.BroadcastReceivers.SmsReceiver;
import net.gmsworld.locatedriver.R;
import com.gmsworld.devicelocator.Services.SmsSenderService;

import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * Created by jstakun on 5/6/17.
 */

public class Messenger {

    private static final String TAG = Messenger.class.getSimpleName();

    public static void sendSMS(final Context context, final String phoneNumber, final String message) {
        //Log.d(TAG, "Send SMS: " + phoneNumber + ", " + message);
        //on samsung intents can't be null. the messages are not sent if intents are null
        ArrayList<PendingIntent> samsungFix = new ArrayList<>();
        samsungFix.add(PendingIntent.getBroadcast(context, 0, new Intent("SMS_RECEIVED"), 0));

        SmsManager smsManager = SmsManager.getDefault();
        ArrayList<String> parts = smsManager.divideMessage(message);
        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, samsungFix, samsungFix);
    }

    public static void sendEmail(final Context context, final String email, final String message, final String title) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "Send email start to " + email);

                    CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                            context,
                            context.getResources().getString(R.string.identityPool), // Identity Pool ID
                            Regions.US_WEST_2 // Region
                    );
                    AmazonSimpleEmailServiceClient sesClient = new AmazonSimpleEmailServiceClient(credentialsProvider);

                    sesClient.sendEmail(new SendEmailRequest().
                            withDestination(new Destination().withToAddresses(email)).
                            withMessage(new Message().withBody(new Body().withText(new Content().withData(message))).withSubject(new Content().withData(title))).
                            withSource(context.getResources().getString(R.string.defaultMail)));
                    Log.i(TAG, "Send email done.");
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        };
        Network.execute(r);
    }

    public static void sendTelegram(final Context context, final String telegramId, final String message) {
        if (telegramId != null && telegramId.length() > 0) {
            Network.post(context.getResources().getString(R.string.telegramBot), "text=" + message + "&chat_id=" + telegramId, null, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String result) {
                    Log.d(TAG, "Received following response code: " + result);
                }
            });
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
                text = "Route tracking service has been started";
                break;
            case SmsReceiver.STOP_COMMAND:
                text = "Route tracking service has been stopped";
                break;
            case SmsReceiver.MUTE_COMMAND:
                text = "Your phone has been muted";
                break;
            case SmsReceiver.NORMAL_COMMAND:
                text = "Your phone has been set to normal audio mode";
                break;
            case SmsReceiver.RADIUS_COMMAND:
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                int radius = settings.getInt("radius", -1);
                if (radius > 0) {
                    text = "Route tracking service radius has been changed to " + radius;
                } else {
                    text = "Route tracking service radius is not set correctly. Please try again.";
                }
                break;
            case SmsReceiver.ROUTE_COMMAND:
                String title = intent.getStringExtra("title");
                int size = intent.getIntExtra("size", 0);
                if (size > 1) {
                    String showRouteUrl = context.getResources().getString(R.string.showRouteUrl);
                    text = "Check your route at: " + showRouteUrl + "/" + title;
                } else {
                    text = "No route has been tracked yet";
                }
                break;
            case SmsReceiver.GPS_HIGH_COMMAND:
                text = "GPS has been changed to high accuracy";
                break;
            case SmsReceiver.GPS_BALANCED_COMMAND:
                text = "GPS has been changed to balanced accuracy";
                break;
            default:
                Log.e(TAG, "Messenger received wrong command: " + command);
                break;
        }
        if (text != null && text.length() > 0) {
            sendSMS(context, phoneNumber, text);
        }
    }

    /*public static void sendNetworkMessage(final Context context, final Location location, final LDPlace place, final String phoneNumber, final OnNetworkMessageSentListener onNetworkMessageSentListener) {
        //Log.d(TAG, "sendNetworkMessage() " + location.getAccuracy());
        final Resources r = context.getResources();

        if (!Network.isNetworkAvailable(context)) {
            sendSMS(context, phoneNumber, r.getString(R.string.no_network));
            onNetworkMessageSentListener.onNetworkMessageSent();
            return;
        }


        //Log.d(TAG, "STARTED NETWORK REQUEST");
        Network.get("https://maps.googleapis.com/maps/api/geocode/json?latlng=" + location.getLatitude() + "," + location.getLongitude(), new Network.OnGetFinishListener() {
            @Override
            public void onGetFinish(String result) {
                //Log.d(TAG, "RESULT ARRIVED");
                try {
                    final String address = new JSONObject(result).getJSONArray("results").getJSONObject(0).getString("formatted_address");
                    final String firstText = r.getString(R.string.address) + " " + address + ". ";

                    if (place == null) {
                        sendSMS(context, phoneNumber, firstText + r.getString(R.string.no_destination));
                        onNetworkMessageSentListener.onNetworkMessageSent();
                        return;
                    }

                    Network.get("https://maps.googleapis.com/maps/api/directions/json?origin=" + location.getLatitude() + "," + location.getLongitude() + "&destination=" + place.getLatitude() + "," + place.getLongitude(), new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String result) {
                            try {
                                JSONObject j = new JSONObject(result).getJSONArray("routes").getJSONObject(0).getJSONArray("legs").getJSONObject(0);
                                String distance = j.getJSONObject("distance").getString("text");
                                String duration = j.getJSONObject("duration").getString("text");

                                sendSMS(context, phoneNumber, firstText + r.getString(R.string.remaining_distance_to) + " " + place.getName() + ": " + distance + ". " + r.getString(R.string.aprox_duration) + " " + duration + ".");
                                onNetworkMessageSentListener.onNetworkMessageSent();
                                return;
                            } catch (Exception e) {
                                //Log.d(TAG, "EXCEPTION E: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (JSONException e) {
                    e.printStackTrace();
                    //Log.d(TAG, "JSON EXCEPTION");
                }
            }
        });
    }

    public interface OnNetworkMessageSentListener {
        public void onNetworkMessageSent();
    }*/

    public static void sendAcknowledgeMessage(Context context, String phoneNumber) {
        Resources r = context.getResources();
        String text = r.getString(R.string.acknowledgeMessage);
        text += " " + r.getString(R.string.network) + " " + booleanToString(context, Network.isNetworkAvailable(context));
        text += ", " + r.getString(R.string.gps) + " " + SmsSenderService.locationToString(context, SmsSenderService.getLocationMode(context));
        text += ", Battery level: " + com.gmsworld.devicelocator.Utilities.Messenger.getBatteryLevel(context) + "%";
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
