package net.gmsworld.devicelocator.Services;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import net.gmsworld.devicelocator.Utilities.Command;
import net.gmsworld.devicelocator.Utilities.Messenger;

import java.util.HashMap;
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
                if (message.containsKey("correlationId")) {
                    String correlationId = message.get("correlationId");
                    Log.d(TAG, "Sending notification for " + correlationId);
                    Messenger.sendTelegram(this, null, "@dlcorrelationId", correlationId, 1, new HashMap<String, String>());
                }
                Command.findCommandInMessage(this, command);
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


