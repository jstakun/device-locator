package net.gmsworld.devicelocator.services;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.DevicesUtils;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.util.Map;

import androidx.annotation.NonNull;

import static net.gmsworld.devicelocator.utilities.Messenger.CID_SEPARATOR;

public class DlFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "DlFirebaseMsgService";

    public static final String FIREBASE_TOKEN = "firebaseToken";
    public static final String FIREBASE_ID = "firebaseId";
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

        // Check if message contains a data payload.
        Map<String, String> messageData = remoteMessage.getData();
        if (messageData != null && !messageData.isEmpty()) {
            final String foundCommand = processMessage(this, messageData);
            if (StringUtils.isNotEmpty(foundCommand)) {
                firebaseAnalytics.logEvent("cloud_command_received_" + foundCommand.toLowerCase(), new Bundle());
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
    public void onNewToken(@NonNull String token) {
        // Get updated InstanceID token.
        Log.d(TAG, "New Firebase token received: " + StringUtils.abbreviate(token, 14));
        PreferencesUtils settings = new PreferencesUtils(this);
        settings.setString(NEW_FIREBASE_TOKEN, token);
        settings.remove(FIREBASE_TOKEN);
        Messenger.sendRegistrationToServer(this, token, null, null);
    }

    @Override
    public void onMessageSent(String msgId) {
        super.onMessageSent(msgId);
        Log.d(TAG, "Firebase message " + msgId +" sent successfully");
    }

    @Override
    public void onSendError(String msgId, Exception exception) {
        super.onSendError(msgId, exception);
        Log.e(TAG,"Failed to send Firebase message " + msgId, exception);
    }

    public static String processMessage(Context context, Map<String, String> message) {
        if (message.containsKey("command") && message.containsKey("pin")) {
            Bundle extras = new Bundle();

            final String pinRead = message.get("pin");
            String command = message.get("command");
            command += pinRead;
            if (message.containsKey("args")) {
                try {
                    final String args = message.get("args");
                    command += " " + URLDecoder.decode(args, "UTF-8");
                    extras.putString("args", args);
                    Log.d(TAG, "Found args string: " + args);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }

            //flex
            Location location = null;
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
                                    location.setAccuracy(Float.parseFloat(coords[2]));
                                }
                                if (coords.length >= 4) {
                                    location.setSpeed(Float.parseFloat(coords[3]));
                                }
                                DevicesUtils.loadDeviceList(context, new PreferencesUtils(context), null);
                            }
                        } else if (token.startsWith("deviceName:")) {
                            extras.putString(MainActivity.DEVICE_NAME, token.split(":")[1]);
                        } else if (token.startsWith("deviceId:")) {
                            extras.putString("imei", token.split(":")[1]);
                        } else if (token.startsWith("routeId:")) {
                            extras.putString("routeId", token.split(":")[1]);
                        } else if (token.startsWith("command:")) {
                            extras.putString("command", token.split(":")[1]);
                        } else if (token.startsWith("telegram:")) {
                            extras.putString("telegram", token.split(":")[1]);
                        } else if (token.startsWith("messenger:")) {
                            extras.putString("messenger", token.split(":")[1]);
                        } else if (token.startsWith("language:")) {
                            extras.putString("language", token.split(":")[1]);
                        }
                        if (extras.containsKey("command") && extras.containsKey("imei")) {
                            CommandService.receivedReplyToCommand(extras.getString("imei"), extras.getString("command"));
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
            return Command.findCommandInMessage(context, command, replyTo, location, extras, pinRead);
        } else {
            Log.e(TAG, "Invalid data payload " + message.toString());
            return null;
        }
    }
}