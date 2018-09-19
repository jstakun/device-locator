package net.gmsworld.devicelocator.services;

import android.app.IntentService;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import net.gmsworld.devicelocator.CommandActivity;
import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.PinActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.AppUtils;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.NotificationUtils;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

public class CommandService extends IntentService implements OnLocationUpdatedListener {

    private final static String TAG = CommandService.class.getSimpleName();

    private static boolean commandInProgress = false;

    public CommandService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        SmartLocation.with(this).location(new LocationGooglePlayServicesWithFallbackProvider(this)).oneFix().start(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SmartLocation.with(this).location(new LocationGooglePlayServicesWithFallbackProvider(this)).stop();
    }

    @Override
    public void onLocationUpdated(Location location) {
        Log.d(TAG, "Location found with accuracy " + location.getAccuracy() + " m");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final PreferencesUtils prefs = new PreferencesUtils(this);
        Bundle extras = intent.getExtras();

        if (extras == null) {
            Log.e(TAG, "Missing command details!");
            return;
        }

        String cmd = extras.getString("command");
        if (StringUtils.endsWith(cmd, "dl")) {
            cmd = cmd.substring(0, cmd.length()-2);
        }

        final String command = cmd;
        final String imei = extras.getString("imei");
        final String args = extras.getString("args");
        final String name = extras.getString(MainActivity.DEVICE_NAME);
        final String cancelCommand = extras.getString("cancelCommand");

        if (command == null || imei == null) {
            Log.e(TAG, "Missing command or imei!");
            return;
        }

        Log.d(TAG, "onHandleIntent() with command: " + command);

        String pin = extras.getString("pin");
        if (pin == null) {
            pin = prefs.getEncryptedString(CommandActivity.PIN_PREFIX + imei);
        }

        if (pin == null) {
            Log.e(TAG, "Missing pin!");
            return;
        }

        prefs.setEncryptedString(CommandActivity.PIN_PREFIX + imei, pin);

        final String deviceId = Messenger.getDeviceId(this, false);

        String content = "imei=" + imei;
        content += "&command=" + command + "dlapp";
        content += "&pin=" + pin;
        content += "&correlationId=" + deviceId + "+=+" + prefs.getEncryptedString(PinActivity.DEVICE_PIN);
        if (StringUtils.isNotEmpty(args)) {
            try {
                content += "&args=" + URLEncoder.encode(args, "UTF-8");
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        if (!commandInProgress) {
            if (StringUtils.isNotEmpty(cancelCommand)) {
                String notificationId = imei + "_" + cancelCommand;
                Log.d(TAG, "Cancelling command " + cancelCommand);
                NotificationUtils.cancel(this, notificationId);
                NotificationUtils.cancel(this, notificationId.substring(0, notificationId.length()-2));
            }
            sendCommand(content, command, imei, name, prefs);
        } else {
            Toast.makeText(this, "Command in progress...", Toast.LENGTH_LONG).show();
        }
    }

    private void sendCommand(final String queryString, final String command, final String imei, final String name, final PreferencesUtils prefs) {
        if (Network.isNetworkAvailable(this)) {
            commandInProgress = true;
            String tokenStr = prefs.getString(DeviceLocatorApp.GMS_TOKEN);
            Map<String, String> headers = new HashMap<>();
            headers.put("X-GMS-AppId", "2");
            headers.put("X-GMS-AppVersionId", Integer.toString(AppUtils.getInstance().getVersionCode(this)));
            if (StringUtils.isNotEmpty(tokenStr)) {
                headers.put("Authorization", "Bearer " + tokenStr);
                Network.post(this, getString(R.string.deviceManagerUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        final String n = (StringUtils.isNotEmpty(name) ? name : imei);
                        if (responseCode == 200) {
                            Toast.makeText(CommandService.this, "Command " + StringUtils.capitalize(command) + " has been sent to the device " + n + "!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(CommandService.this, "Failed to send command " + StringUtils.capitalize(command) + " to the device " + n + "!", Toast.LENGTH_SHORT).show();
                        }
                        commandInProgress = false;
                    }
                });
            } else {
                String qs = "scope=dl&user=" + Messenger.getDeviceId(this, false);
                Network.get(this, getString(R.string.tokenUrl) + "?" + qs, null, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            Messenger.getToken(CommandService.this, results);
                            sendCommand(queryString, command, imei, name, prefs);
                        } else {
                            Log.d(TAG, "Failed to receive token: " + results);
                        }
                        commandInProgress = false;
                    }
                });
            }
        } else {
            Toast.makeText(this, R.string.no_network_error, Toast.LENGTH_LONG).show();
        }
    }
}
