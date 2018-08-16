package net.gmsworld.devicelocator.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.PinActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class DlFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "DlFirebaseMsgService";

    public static final String FIREBASE_TOKEN = "firebaseToken";

    private FirebaseAnalytics firebaseAnalytics;
    //private static final int NOTIFICATION_ID = 2;

    @Override
    public void onCreate() {
        super.onCreate();
        firebaseAnalytics = FirebaseAnalytics.getInstance(this);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "Received message from: " + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> message = remoteMessage.getData();
            if (message.containsKey("command") && message.containsKey("pin")) {
                final String pinRead = message.get("pin");
                final String pin = new PreferencesUtils(this).getEncryptedString(PinActivity.DEVICE_PIN);
                String command = message.get("command");
                final String commandName = command.split("dl")[0];
                final boolean pinValid = StringUtils.equals(pin, pinRead);
                final String[] correlationId = StringUtils.split(message.get("correlationId"), "+=+");
                if (pinValid && correlationId != null && correlationId.length == 2 && !StringUtils.startsWithIgnoreCase(command, "message")) {
                    //send confirmation to correlationId
                    Messenger.sendCloudMessage(this, null, correlationId[0], correlationId[1], "Command " + commandName + " has been received by device " + Messenger.getDeviceId(this, true), 1, new HashMap<String, String>());
                }
                if (pinValid) {
                    //search for command
                    command += pinRead;
                    if (message.containsKey("args")) {
                        command += " " + message.get("args");
                    }
                    String sender = null;
                    if (correlationId != null) {
                        sender = message.get("correlationId");
                    }
                    String foundCommand = Command.findCommandInMessage(this, command, sender);
                    if (foundCommand == null) {
                        Log.d(TAG, "Invalid command " + commandName + " found!");
                    }
                } else if (!pinValid && correlationId != null && correlationId.length == 2 && !StringUtils.startsWithIgnoreCase(command, "message")) {
                    Messenger.sendCloudMessage(this, null, correlationId[0], correlationId[1], "Command " + commandName + " has been rejected by device " + Messenger.getDeviceId(this, true), 1, new HashMap<String, String>());
                    Log.e(TAG, "Invalid pin found in cloud message!");
                } else {
                    Log.e(TAG, "Invalid pin found in cloud message!");
                }
                Bundle bundle = new Bundle();
                //bundle.putString("command", commandName);
                firebaseAnalytics.logEvent("cloud_command_received_" + commandName.toLowerCase(), bundle);
            } else {
                Log.e(TAG, "Invalid data payload!");
            }
        }

        //we are not interested in notification messages
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message notification body: " + remoteMessage.getNotification().getBody());
        }
    }

    @Override
    public void onNewToken(String token) {
        // Get updated InstanceID token.
        Log.d(TAG, "New Firebase token: " + token);
        PreferenceManager.getDefaultSharedPreferences(this).edit().remove(FIREBASE_TOKEN).apply();
        final String pin = new PreferencesUtils(this).getEncryptedString(PinActivity.DEVICE_PIN);
        if (StringUtils.isNotEmpty(pin)) {
            sendRegistrationToServer(this, token, null, null);
        }
    }

    private static boolean sendRegistrationToServer(final Context context, final String firebaseToken, final String username, final String deviceName) {
        if (StringUtils.isNotEmpty(firebaseToken) && !StringUtils.equalsIgnoreCase(firebaseToken, "BLACKLISTED")) {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN, "");
            if (StringUtils.isNotEmpty(tokenStr)) {
                sendRegistrationToServer(context, firebaseToken, username, deviceName, tokenStr);
            } else {
                String queryString = "scope=dl&user=" + Messenger.getDeviceId(context, false);
                Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            sendRegistrationToServer(context, firebaseToken, username, deviceName, Messenger.getToken(context, results));
                        } else {
                            Log.d(TAG, "Failed to receive token: " + results);
                        }
                    }
                });
            }
            return true;
        } else {
            Log.e(TAG, "This device can't be registered with token: " + firebaseToken);
            return false;
        }
    }

    private static void sendRegistrationToServer(final Context context, final String firebaseToken, final String username, final String deviceName, final String gmsTokenStr) {
        try {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            if (!StringUtils.equalsIgnoreCase(firebaseToken, "BLACKLISTED")) {
                String imei = Messenger.getDeviceId(context, false);
                String content = "imei=" + imei;
                if (StringUtils.equalsIgnoreCase(imei, "unknown")) {
                    Log.e(TAG, "Invalid imei");
                    return;
                }
                if (StringUtils.isNotBlank(firebaseToken)) {
                    content += "&token=" + firebaseToken;
                }
                if (StringUtils.isNotBlank(username)) {
                    content += "&username=" + username;
                }
                if (StringUtils.isNotBlank(deviceName)) {
                    content += "&name=" + deviceName;
                }

                Map<String, String> headers = new HashMap<String, String>();
                headers.put("Authorization", "Bearer " + gmsTokenStr);

                Network.post(context, context.getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            //save firebase token only if it was successfully registered by the server
                            if (StringUtils.isNotBlank(firebaseToken)) {
                                settings.edit().putString(FIREBASE_TOKEN, firebaseToken).apply();
                                Log.d(TAG, "Firebase token is set");
                            }
                            if (username != null) {
                                settings.edit().putString(MainActivity.USER_LOGIN, username).apply();
                                Log.d(TAG, "User login is set");
                                if (context instanceof MainActivity) {
                                    ((MainActivity)context).initDeviceList();
                                }
                            }
                            if (deviceName != null) {
                                settings.edit().putString(MainActivity.DEVICE_NAME, deviceName).apply();
                                Log.d(TAG, "Device name is set");
                                if (context instanceof MainActivity) {
                                    ((MainActivity)context).initDeviceList();
                                }
                            }
                        } else {
                            Toast.makeText(context, "Device registration failed! Please restart Device Manager and try again.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            } else {
                Log.e(TAG, "Invalid token: " + firebaseToken);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static boolean sendRegistrationToServer(final Context context, final String username, final String deviceName) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String firebaseToken = settings.getString(FIREBASE_TOKEN, "");
        if (StringUtils.isEmpty(firebaseToken)) {
            Task<InstanceIdResult> result = FirebaseInstanceId.getInstance().getInstanceId();

            result.addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                @Override
                public void onComplete(@NonNull Task<InstanceIdResult> task) {
                    if (task.isSuccessful()) {
                        // Task completed successfully
                        InstanceIdResult result = task.getResult();
                        sendRegistrationToServer(context, result.getToken(), username, deviceName);
                    } else {
                        // Task failed with an exception
                        Exception exception = task.getException();
                        Log.e(TAG, exception.getMessage(), exception);
                    }
                }
            });

            return true;
        } else {
            return sendRegistrationToServer(context, firebaseToken, username, deviceName);
        }
    }
}


