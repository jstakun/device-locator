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

        final String command = extras.getString("command");
        final String imei = extras.getString("imei");
        final String args = extras.getString("args");
        final String name = extras.getString(MainActivity.DEVICE_NAME);

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

        final String deviceId = Messenger.getDeviceId(this, false);

        String tokenStr = prefs.getString(DeviceLocatorApp.GMS_TOKEN);
        String content = "imei=" + imei;
        if (command.endsWith("dl")) {
            content += "&command=" + command + "app";
        } else {
            content += "&command=" + command + "dlapp";
        }
        content += "&pin=" + pin;
        content += "&correlationId=" + deviceId + "+=+" + prefs.getEncryptedString(PinActivity.DEVICE_PIN);
        if (StringUtils.isNotEmpty(args)) {
            try {
                content += "&args=" + URLEncoder.encode(args, "UTF-8");
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + tokenStr);
        headers.put("X-GMS-AppId", "2");
        headers.put("X-GMS-AppVersionId", Integer.toString(AppUtils.getInstance().getVersionCode(this)));
        //headers.put("X-GMS-DeviceId", deviceId);
        //headers.put("X-GMS-DeviceName", Messenger.getDeviceId(this, true));

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
