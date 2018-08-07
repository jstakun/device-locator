package net.gmsworld.devicelocator.Services;

import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import net.gmsworld.devicelocator.PinActivity;
import net.gmsworld.devicelocator.Utilities.Command;
import net.gmsworld.devicelocator.Utilities.Messenger;
import net.gmsworld.devicelocator.Utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class DlFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "DlFirebaseMsgService";

    private FirebaseAnalytics firebaseAnalytics;
    //private static final int NOTIFICATION_ID = 2;

    @Override
    public void onCreate() {
        super.onCreate();

        firebaseAnalytics = FirebaseAnalytics.getInstance(this);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

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
                    //send notification to correlationId
                    Messenger.sendCloudMessage(this, null, correlationId[0], correlationId[1], "Command " + commandName + " has been received by device " + Messenger.getDeviceId(this, true), 1, new HashMap<String, String>());
                }
                if (pinValid) {
                    command += pinRead;
                    if (message.containsKey("args")) {
                        command += " " + message.get("args");
                    }
                    String sender = null;
                    if (correlationId != null) {
                        sender = message.get("correlationId");
                    }
                    Command.findCommandInMessage(this, command, sender);
                } else if (correlationId != null && correlationId.length == 2 && !StringUtils.startsWithIgnoreCase(command, "message")) {
                    Messenger.sendCloudMessage(this, null, correlationId[0], correlationId[1], "Command " + commandName + " has been rejected by device " + Messenger.getDeviceId(this, true), 1, new HashMap<String, String>());
                    Log.e(TAG, "Invalid pin received in cloud message!");
                } else {
                    Log.e(TAG, "Invalid pin received in cloud message!");
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
}


