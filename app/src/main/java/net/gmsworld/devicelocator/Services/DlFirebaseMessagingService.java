package net.gmsworld.devicelocator.Services;

import android.preference.PreferenceManager;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.Utilities.Command;
import net.gmsworld.devicelocator.Utilities.Messenger;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class DlFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "DlFirebaseMsgService";

    //private static final int NOTIFICATION_ID = 2;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> message = remoteMessage.getData();
            if (message.containsKey("command") && message.containsKey("pin")) {
                String pinRead = message.get("pin");
                String pin = PreferenceManager.getDefaultSharedPreferences(this).getString(MainActivity.DEVICE_PIN, null);
                String command = message.get("command");
                boolean pinValid = StringUtils.equals(pin, pinRead);
                String[] correlationId = StringUtils.split(message.get("correlationId"), "+=+");
                if (pinValid && correlationId != null && correlationId.length == 2 && !StringUtils.startsWithIgnoreCase(command, "message")) {
                    //old code for Telegram
                    //String correlationId = message.get("correlationId");
                    //Log.d(TAG, "Sending notification for " + correlationId);
                    //Map<String, String> headers = new HashMap<String, String>();
                    //headers.put("X-GMS-AuthStatus", pinValid ? "ok" : "failed");
                    //Messenger.sendTelegram(this, null, "@dlcorrelationId", correlationId, 1, headers);

                    //send notification to correlationId
                    Messenger.sendCloudMessage(this, null, correlationId[0], correlationId[1], "Command " + command.split("dl")[0] + " has been received by device " + Messenger.getDeviceId(this, true), 1, new HashMap<String, String>());
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
                    Messenger.sendCloudMessage(this, null, correlationId[0], correlationId[1], "Command " + command.split("dl")[0] + " has been rejected by device " + Messenger.getDeviceId(this, true), 1, new HashMap<String, String>());
                    Log.e(TAG, "Invalid pin received in cloud message!");
                } else {
                    Log.e(TAG, "Invalid pin received in cloud message!");
                }
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


