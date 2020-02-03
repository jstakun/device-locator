package net.gmsworld.devicelocator.services;

import android.app.IntentService;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
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
import net.gmsworld.devicelocator.views.DialogActivity;

import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

public class CommandService extends IntentService implements OnLocationUpdatedListener {

    private final static String TAG = CommandService.class.getSimpleName();

    public static final String AUTH_NEEDED = "authNeeded";

    public static final String LAST_COMMAND_SUFFIX = "_lastCommand";

    private static final List<String> commandsInProgress = new ArrayList<>();

    private Handler toastHandler;

    public CommandService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        toastHandler = new Handler(getMainLooper());
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
        Log.d(TAG, "onHandleIntent()");
        Bundle extras = intent.getExtras();

        //hide notification dialogs
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        if (extras == null) {
            Log.e(TAG, "Missing command details!");
            showToast(R.string.internal_error);
            return;
        }

        final PreferencesUtils prefs = new PreferencesUtils(this);

        showToast(R.string.please_wait);

        if (PinActivity.isAuthRequired(prefs)) {
            Log.d(TAG, "User should authenticate again!");
            showToast("Please authenticate before sending command");
            Intent authIntent = new Intent(this, PinActivity.class);
            authIntent.putExtras(extras);
            authIntent.setAction(AUTH_NEEDED);
            authIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(authIntent);
        } else {
            String cmd = extras.getString("command");
            if (StringUtils.endsWith(cmd, "dl")) {
                cmd = cmd.substring(0, cmd.length() - 2);
            }
            Log.d(TAG, "Command " + cmd + " will be executed...");

            final String command = cmd;
            final String imei = extras.getString("imei");
            final String args = extras.getString("args");
            final String name = extras.getString(MainActivity.DEVICE_NAME);
            final String cancelCommand = extras.getString("cancelCommand");
            final String routeId = extras.getString("routeId");

            if (command == null || imei == null) {
                Log.e(TAG, "Missing command or imei!");
                return;
            }

            prefs.setString(imei + LAST_COMMAND_SUFFIX, command);

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
            content += "&correlationId=" + deviceId + Messenger.CID_SEPARATOR + prefs.getEncryptedString(PinActivity.DEVICE_PIN);
            if (StringUtils.isNotEmpty(args)) {
                try {
                    content += "&args=" + URLEncoder.encode(args, "UTF-8");
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }

            if (!commandsInProgress.contains(imei + "_" + command)) {
                if (StringUtils.isNotEmpty(cancelCommand)) {
                    String notificationId = imei + "_" + cancelCommand;
                    Log.d(TAG, "Cancelling command " + cancelCommand);
                    NotificationUtils.cancel(this, notificationId);
                    NotificationUtils.cancel(this, notificationId.substring(0, notificationId.length() - 2));
                } else if (StringUtils.isNotEmpty("routeId")) {
                    NotificationUtils.cancel(this, routeId);
                }
                sendCommand(content, command, imei, name, prefs, deviceId);
            } else {
                showToast("Command " + command + " has been sent to the device " + (StringUtils.isNotEmpty(name) ? name : imei) + "!");
            }
        }
    }

    private void sendCommand(final String queryString, final String command, final String imei, final String name, final PreferencesUtils settings, final String deviceId) {
        if (Network.isNetworkAvailable(this)) {
            commandsInProgress.add(imei + "_" + command);
            final String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN);
            Map<String, String> headers = new HashMap<>();
            headers.put("X-GMS-AppId", "2");
            headers.put("X-GMS-AppVersionId", Integer.toString(AppUtils.getInstance().getVersionCode(this)));
            if (StringUtils.isNotEmpty(tokenStr)) {
                headers.put("Authorization", "Bearer " + tokenStr);
                Network.post(this, getString(R.string.deviceManagerUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        final String deviceName = (StringUtils.isNotEmpty(name) ? name : imei);
                        if (responseCode == 200) {
                            showToast("Command " + StringUtils.capitalize(command) + " has been sent to the device " + deviceName + "!");
                        } else if (responseCode == 404) {
                            showToast("Failed to send command " + StringUtils.capitalize(command) + " to the device " + deviceName + ". Is " + CommandService.this.getString(R.string.app_name) + " installed on this device?");
                        } else if (responseCode == 410) {
                            showToast("It seems device " + deviceName + " has been offline recently. Retry if no reply soon!");
                        } else if (responseCode == 403 && StringUtils.startsWith(results, "{")) {
                            //TODO show dialog with action=reset_quota appended to queryString
                            //JsonElement reply = new JsonParser().parse(results);
                            //final int count = reply.getAsJsonObject().get("count").getAsInt();
                            //showToast("Failed to send command " + StringUtils.capitalize(command) + " to the device " + deviceName + " after " + count + " attempts!");
                            Intent newIntent = new Intent(CommandService.this, DialogActivity.class);
                            newIntent.putExtra("command", command);
                            newIntent.putExtra("queryString", queryString);
                            newIntent.putExtra("token", tokenStr);
                            newIntent.putExtra("deviceName", deviceName);
                            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(newIntent);
                        } else {
                            showToast("Failed to send command " + StringUtils.capitalize(command) + " to the device " + deviceName + "!");
                        }
                        commandsInProgress.remove(imei + "_" + command);
                    }
                });
            } else {
                String qs = "scope=dl&user=" + deviceId;
                Network.get(this, getString(R.string.tokenUrl) + "?" + qs, null, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            Messenger.getToken(CommandService.this, results);
                            sendCommand(queryString, command, imei, name, settings, deviceId);
                        } else {
                            Log.d(TAG, "Failed to receive token: " + results);
                        }
                        commandsInProgress.remove(imei + "_" + command);
                    }
                });
            }
        } else {
            showToast(R.string.no_network_error);
        }
    }

    private void showToast(final String message) {
        toastHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showToast(final int messageId) {
        toastHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), messageId, Toast.LENGTH_LONG).show();
            }
        });
    }
}
