package net.gmsworld.devicelocator.services;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import net.gmsworld.devicelocator.CommandActivity;
import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.PinActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class CommandService extends IntentService {

    private final static String TAG = CommandService.class.getSimpleName();

    public CommandService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final PreferencesUtils prefs = new PreferencesUtils(this);
        Bundle extras = intent.getExtras();

        if (extras == null) {
            Log.e(TAG, "Missing command or imei!");
            return;
        }

        final String command = extras.getString("command");
        final String imei = extras.getString("imei");
        final String args = extras.getString("args");
        final String name = extras.getString("name");

        if (command == null || imei == null) {
            Log.e(TAG, "Missing command or imei!");
            return;
        }

        final String pin = prefs.getEncryptedString(CommandActivity.PIN_PREFIX + imei);

        if (pin == null) {
            Log.e(TAG, "Missing pin!");
            return;
        }

        String tokenStr = prefs.getString(DeviceLocatorApp.GMS_TOKEN);
        String content = "imei=" + imei;
        content += "&command=" + command + "dlapp";
        content += "&pin=" + pin;
        content += "&correlationId=" + Messenger.getDeviceId(this, false) + "+=+" + prefs.getEncryptedString(PinActivity.DEVICE_PIN);
        if (StringUtils.isNotEmpty(args)) {
            try {
                content += "&args=" + URLEncoder.encode(args, "UTF-8");
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + tokenStr);

        Network.post(this, getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
            @Override
            public void onGetFinish(String results, int responseCode, String url) {
                final String n = (StringUtils.isNotEmpty(name) ? name : imei);
                if (responseCode == 200) {
                    Toast.makeText(CommandService.this, "Command " + command + " has been sent. You'll receive notification when this message will be delivered to the device " + n + "!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(CommandService.this, "Failed to send command " + command + " to the device " + n + "!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        prefs.setEncryptedString(CommandActivity.PIN_PREFIX + imei, pin);
    }
}
