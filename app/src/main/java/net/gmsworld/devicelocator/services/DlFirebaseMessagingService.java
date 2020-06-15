package net.gmsworld.devicelocator.services;

import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.PinActivity;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.DevicesUtils;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

import static net.gmsworld.devicelocator.utilities.Messenger.CID_SEPARATOR;

public class DlFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "DlFirebaseMsgService";

    public static final String FIREBASE_TOKEN = "firebaseToken";
    public static final String NEW_FIREBASE_TOKEN = "newFirebaseToken";

    private FirebaseAnalytics firebaseAnalytics;

    @Override
    public void onCreate() {
        super.onCreate();
        firebaseAnalytics = FirebaseAnalytics.getInstance(this);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "Received message from: " + remoteMessage.getFrom());
        //Log.d(TAG, "Received message: " + remoteMessage.getData().toString());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> message = remoteMessage.getData();
            if (message.containsKey("command") && message.containsKey("pin")) {
                Bundle extras = new Bundle();

                final String pinRead = message.get("pin");
                String command = message.get("command");
                command += pinRead;
                if (message.containsKey("args")) {
                    String args = message.get("args");
                    command += " " + args;
                    extras.putString("args", args);
                    Log.d(TAG, "Found args string: " + args);
                }

                //flex
                Location location =  null;
                if (message.containsKey("flex")) {
                    final String flex = message.get("flex");
                    Log.d(TAG, "Found flex string: " + flex);
                    try {
                        String[] tokens = StringUtils.split(flex, ",");
                        for (String token : tokens) {
                            if (token.startsWith("geo:")) {
                                String[] coords = StringUtils.split(token.substring(4), " ");
                                if (coords.length >= 2) {
                                    location = new Location("");
                                    location.setLatitude(Location.convert(coords[0]));
                                    location.setLongitude(Location.convert(coords[1]));
                                    if (coords.length >= 3) {
                                        location.setAccuracy(Float.valueOf(coords[2]));
                                    }
                                    if (coords.length >= 4) {
                                        location.setSpeed(Float.valueOf(coords[3]));
                                    }
                                    DevicesUtils.loadDeviceList(this, new PreferencesUtils(this), null);
                                }
                            } else if (token.startsWith("routeId:")) {
                                extras.putString("routeId", token.split(":")[1]);
                            } else if (token.startsWith("deviceName:")) {
                                extras.putString(MainActivity.DEVICE_NAME, token.split(":")[1]);
                            } else if (token.startsWith("deviceId:")) {
                                extras.putString("imei", token.split(":")[1]);
                            } else if (token.startsWith("command:")) {
                                extras.putString("command", token.split(":")[1]);
                            } else if (token.startsWith("telegram:")) {
                                extras.putString("telegram", token.split(":")[1]);
                            } else if (token.startsWith("messenger:")) {
                                extras.putString("messenger", token.split(":")[1]);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
                //correlationId
                String replyTo = null;
                if (message.containsKey("correlationId")) {
                    replyTo = message.get("correlationId");
                    if (StringUtils.split(replyTo, CID_SEPARATOR).length < 2) {
                        Log.e(TAG, "Invalid replyTo: " + replyTo);
                        replyTo = null;
                    }
                }

                String foundCommand = Command.findCommandInMessage(this, command, replyTo, location, extras, pinRead);
                if (StringUtils.isNotEmpty(foundCommand)) {
                    firebaseAnalytics.logEvent("cloud_command_received_" + foundCommand.toLowerCase(), new Bundle());
                }
            } else {
                Log.e(TAG, "Invalid data payload " + message.toString());
            }
        }

        //we are not interested in notification messages
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message notification body: " + remoteMessage.getNotification().getBody());
        }
    }

    @Override
    public void onDeletedMessages() {
        super.onDeletedMessages();
        Log.d(TAG, "Received onDeletedMessages callback from Firebase");
    }

    @Override
    public void onNewToken(String token) {
        // Get updated InstanceID token.
        Log.d(TAG, "New Firebase token: " + token);
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString(NEW_FIREBASE_TOKEN, token).remove(FIREBASE_TOKEN).apply();
        final String pin = new PreferencesUtils(this).getEncryptedString(PinActivity.DEVICE_PIN);
        if (StringUtils.isNotEmpty(pin)) {
            Messenger.sendRegistrationToServer(this, token, null, null);
        }
    }
}