package net.gmsworld.devicelocator.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;

import net.gmsworld.devicelocator.CommandActivity;
import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.PinActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.AppUtils;
import net.gmsworld.devicelocator.utilities.Files;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.NotificationUtils;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.utilities.Toaster;
import net.gmsworld.devicelocator.views.QuotaResetDialogActivity;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

import static net.gmsworld.devicelocator.utilities.AbstractCommand.AUDIT_FILE;

public class CommandService extends IntentService implements OnLocationUpdatedListener {

    public static final String AUTH_NEEDED = "CommandServiceAuthNeeded";
    public static final String LAST_COMMAND_SUFFIX = "_lastCommand";

    private final static String TAG = CommandService.class.getSimpleName();
    private static final List<String> commandSendingInProgress = new ArrayList<>();
    private static final List<String> commandsSent = new ArrayList<>();

    private final Handler commandHandler = new Handler();

    private final Toaster toaster;

    private FirebaseAnalytics firebaseAnalytics;

    public CommandService() {
        super(TAG);
        toaster = new Toaster(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        firebaseAnalytics = FirebaseAnalytics.getInstance(this);
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
        Bundle extras = null;
        if (intent != null) {
            extras = intent.getExtras();
        }

        //hide notification dialogs
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        if (extras == null) {
            Log.e(TAG, "Missing command details!");
            toaster.showServiceToast(R.string.internal_error);
            return;
        }

        final PreferencesUtils prefs = new PreferencesUtils(this);

        toaster.showServiceToast(R.string.please_wait);

        final String thisDeviceId = Messenger.getDeviceId(this, false);
        final String imei = extras.getString("imei");

        if (!StringUtils.equals(imei, thisDeviceId) && PinActivity.isAuthRequired(prefs)) {
            Log.d(TAG, "User should login again!");
            toaster.showServiceToast(R.string.please_auth);
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

            final String command = cmd;
            final String args = extras.getString("args");
            final String deviceName = extras.getString(MainActivity.DEVICE_NAME);

            if (command == null || imei == null) {
                Log.e(TAG, "Missing command or imei!");
                return;
            }

            Log.d(TAG, "Command " + cmd + " will be send to the device " + imei + "...");

            prefs.setString(imei + LAST_COMMAND_SUFFIX, command);

            String pin = extras.getString("pin");
            if (StringUtils.isEmpty(pin)) {
                pin = prefs.getEncryptedString(CommandActivity.PIN_PREFIX + imei);
            }

            if (StringUtils.isEmpty(pin)) {
                Log.e(TAG, "Missing pin!");
                return;
            }

            prefs.setEncryptedString(CommandActivity.PIN_PREFIX + imei, pin);

            final String correlationId = thisDeviceId + Messenger.CID_SEPARATOR + prefs.getEncryptedString(PinActivity.DEVICE_PIN);

            String content = "imei=" + imei;
            content += "&command=" + command + "dlapp";
            content += "&pin=" + pin;
            content += "&correlationId=" + correlationId;
            if (StringUtils.isNotEmpty(args)) {
                try {
                    content += "&args=" + URLEncoder.encode(args, "UTF-8");
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
            //Log.d(TAG, "Command content: " + content);

            if (!commandSendingInProgress.contains(imei + "_" + command)) {
                //Log.d(TAG, "Comparing " + imei + " with " + thisDeviceId);
                if (StringUtils.equals(imei, thisDeviceId)) {
                    //if imei is this device send command locally
                    auditCommand(this, command, imei);
                    Map<String, String> messageData = new HashMap<>();
                    messageData.put("pin", pin);
                    messageData.put("command", command + "dlapp");
                    if (StringUtils.isNotEmpty(args)) {
                        messageData.put("args", args);
                    }
                    messageData.put("correlationId", correlationId);
                    //create flex
                    List<String> tokens = new ArrayList<>();
                    if (StringUtils.isNotEmpty(thisDeviceId)) {
                        tokens.add("deviceId:" + thisDeviceId);
                    }
                    if (StringUtils.isNotEmpty(deviceName)) {
                        tokens.add("deviceName:" + deviceName);
                    }
                    tokens.add("language:" + AppUtils.getInstance().getCurrentLocale(this).getLanguage());
                    messageData.put("flex", StringUtils.join(tokens, ","));
                    final String foundCommand = DlFirebaseMessagingService.processMessage(this, messageData);
                    if (StringUtils.isNotEmpty(foundCommand)) {
                        toaster.showServiceToast(R.string.command_sent_to_device, command, (StringUtils.isNotEmpty(deviceName) ? deviceName : imei));
                        firebaseAnalytics.logEvent("cloud_command_received_" + foundCommand.toLowerCase(), new Bundle());
                    }
                } else {
                    //register firebase token if is not yet set or has been changed and not send to server
                    final String firebaseToken = prefs.getString(DlFirebaseMessagingService.FIREBASE_TOKEN);
                    final String newFirebaseToken = prefs.getString(DlFirebaseMessagingService.NEW_FIREBASE_TOKEN);
                    if (StringUtils.isEmpty(firebaseToken) || StringUtils.isNotEmpty(newFirebaseToken)) {
                        Messenger.sendRegistrationToServer(this, prefs.getString(MainActivity.USER_LOGIN), deviceName, true);
                    }
                    //else send command to remote device
                    sendCommand(content, command, imei, deviceName, prefs, thisDeviceId);
                }
            } else {
                toaster.showServiceToast(R.string.command_sent_to_device, command, (StringUtils.isNotEmpty(deviceName) ? deviceName : imei));
            }
        }
    }

    private void sendCommand(final String queryString, final String command, final String imei, final String name, final PreferencesUtils settings, final String deviceId) {
        if (Network.isNetworkAvailable(this)) {
            commandSendingInProgress.add(imei + "_" + command);
            final String deviceName = (StringUtils.isNotEmpty(name) ? name : imei);
            auditCommand(this, command, imei);
            Log.d(TAG, "Sending command " + command + " to " + imei);
            final String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN);
            Map<String, String> headers = new HashMap<>();
            if (StringUtils.isNotEmpty(tokenStr)) {
                headers.put("Authorization", "Bearer " + tokenStr);
                Network.post(this, getString(R.string.deviceManagerUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            toaster.showServiceToast(R.string.command_sent_to_device, StringUtils.capitalize(command), deviceName);
                            final String commandSent = imei + "_" +  command.toLowerCase();
                            commandsSent.add(commandSent);
                            commandHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    Log.d(TAG, "Checking if reply to command " + command + " received from the device " + imei);
                                    if (commandsSent.contains(commandSent)) {
                                        commandsSent.remove(commandSent);
                                        Bundle extras = new Bundle();
                                        extras.putString(MainActivity.DEVICE_NAME, deviceName);
                                        extras.putString("imei", imei);
                                        extras.putString("command", command);
                                        extras.putBoolean("showResend", true);
                                        NotificationUtils.showMessageNotification(CommandService.this, "No reply to command " + StringUtils.capitalize(command) + " received from device " + deviceName + ".\nPlease send this command again.", null ,extras);
                                    }
                                }
                            }, 180000); //3 mins
                        } else if (responseCode == 404) {
                            toaster.showServiceToast(R.string.command_failed_device_gone, StringUtils.capitalize(command), deviceName, CommandService.this.getString(R.string.app_name));
                        } else if (responseCode == 410) {
                            toaster.showServiceToast(R.string.command_failed_device_offline, deviceName);
                        } else if (responseCode == 403 && StringUtils.startsWith(results, "{")) {
                            //show dialog with action=reset_quota appended to queryString
                            Intent newIntent = new Intent(CommandService.this, QuotaResetDialogActivity.class);
                            newIntent.putExtra("command", command);
                            newIntent.putExtra("queryString", queryString);
                            newIntent.putExtra("token", tokenStr);
                            newIntent.putExtra("deviceName", deviceName);
                            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(newIntent);
                        } else {
                            toaster.showServiceToast(R.string.command_failed_to_device, StringUtils.capitalize(command), deviceName);
                        }
                        commandSendingInProgress.remove(imei + "_" + command);
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
                            toaster.showServiceToast(R.string.command_failed_to_device, StringUtils.capitalize(command), deviceName);
                            Log.d(TAG, "Failed to receive token: " + results);
                            commandSendingInProgress.remove(imei + "_" + command);
                        }
                    }
                });
            }
        } else {
            toaster.showServiceToast(R.string.no_network_error);
            commandSendingInProgress.remove(imei + "_" + command);
        }
    }

    private void auditCommand(Context context, final String command, final String to) {
        String auditLog = System.currentTimeMillis() + " ";
        if (StringUtils.isNotEmpty(to)) {
            auditLog += Messenger.CID_SEPARATOR + to + " ";
        }
        auditLog += command + " 1";
        File auditFile = Files.getFilesDir(context, AUDIT_FILE, false);
        Files.appendLineToFileFromContextDir(auditFile, auditLog, 100, 10);
    }

    public static void receivedReplyToCommand(final String imei, final String command) {
        Log.d(TAG, "Received reply to command " + command + " from device " + imei);
        commandsSent.remove(imei + "_" + command.toLowerCase());
    }
}
