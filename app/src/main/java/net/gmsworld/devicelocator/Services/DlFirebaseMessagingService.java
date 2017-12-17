package net.gmsworld.devicelocator.Services;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import net.gmsworld.devicelocator.Utilities.Command;

import java.util.Map;

public class DlFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "DlFirebaseMsgService";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> message = remoteMessage.getData();
            if (message.containsKey("command") && message.containsKey("pin")) {
                String command = message.get("command") + message.get("pin");
                if (message.containsKey("args")) {
                    command += " " + message.get("args");
                }
                Command.findCommandInMessage(this, command);
            } else {
                Log.e(TAG, "Invalid data payload!");
            }
        }

        //we are not interested in notification messages
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
        }
    }
}


