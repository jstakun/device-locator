package net.gmsworld.devicelocator.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
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

import static net.gmsworld.devicelocator.utilities.Messenger.CID_SEPARATOR;

public class DlFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "DlFirebaseMsgService";

    public static final String FIREBASE_TOKEN = "firebaseToken";

    public static final String NEW_FIREBASE_TOKEN = "newFirebaseToken";

    private FirebaseAnalytics firebaseAnalytics;

    @Override
    public void onCreate() {
        super.onCreate();
        firebaseAnalytics = FirebaseAnalytics.getInstance(this);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "Received message from: " + remoteMessage.getFrom());
        //Log.d(TAG, "Received message: " + remoteMessage.getData().toString());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> message = remoteMessage.getData();
            if (message.containsKey("command") && message.containsKey("pin")) {
                Bundle extras = new Bundle();

                final String pinRead = message.get("pin");
                String command = message.get("command");
                command += pinRead;
                if (message.containsKey("args")) {
                    String args = message.get("args");
                    command += " " + args;
                    extras.putString("args", args);
                }

                //flex
                Location location =  null;
                if (message.containsKey("flex")) {
                    String flex = message.get("flex");
                    Log.d(TAG, "Found flex string: " + flex);
                    try {
                        String[] tokens = StringUtils.split(flex, ",");
                        for (String token : tokens) {
                            if (token.startsWith("geo:")) {
                                String[] coords = StringUtils.split(token.substring(4), " ");
                                if (coords.length >= 2) {
                                    location = new Location("");
                                    location.setLatitude(Location.convert(coords[0]));
                                    location.setLongitude(Location.convert(coords[1]));
                                    if (coords.length >= 3) {
                                        location.setAccuracy(Float.valueOf(coords[2]));
                                    }
                                }
                            } else if (token.startsWith("routeId:")) {
                                extras.putString("routeId", token.split(":")[1]);
                            } else if (token.startsWith("deviceName:")) {
                                extras.putString(MainActivity.DEVICE_NAME, token.split(":")[1]);
                            } else if (token.startsWith("deviceId:")) {
                                extras.putString("imei", token.split(":")[1]);
                            } else if (token.startsWith("command:")) {
                                extras.putString("command", token.split(":")[1]);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
                //correlationId
                String replyTo = null;
                if (message.containsKey("correlationId")) {
                    replyTo = message.get("correlationId");
                    if (StringUtils.split(replyTo, CID_SEPARATOR).length < 2) {
                        Log.e(TAG, "Invalid replyTo: " + replyTo);
                        replyTo = null;
                    }
                }

                String foundCommand = Command.findCommandInMessage(this, command, replyTo, location, extras, pinRead);
                if (StringUtils.isNotEmpty(foundCommand)) {
                    firebaseAnalytics.logEvent("cloud_command_received_" + foundCommand.toLowerCase(), new Bundle());
                }
            } else {
                Log.e(TAG, "Invalid data payload " + message.toString());
            }
        }

        //we are not interested in notification messages
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message notification body: " + remoteMessage.getNotification().getBody());
        }
    }

    @Override
    public void onDeletedMessages() {
        super.onDeletedMessages();
        Log.d(TAG, "Received onDeletedMessages callback from Firebase");
    }

    @Override
    public void onNewToken(String token) {
        // Get updated InstanceID token.
        Log.d(TAG, "New Firebase token: " + token);
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString(NEW_FIREBASE_TOKEN, token).remove(FIREBASE_TOKEN).apply();
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

                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + gmsTokenStr);

                Network.post(context, context.getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            //save firebase token only if it was successfully registered by the server
                            if (StringUtils.isNotBlank(firebaseToken)) {
                                settings.edit().putString(FIREBASE_TOKEN, firebaseToken).remove(NEW_FIREBASE_TOKEN).apply();
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
                            //TODO user handler like in CommandService
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

    public static boolean sendRegistrationToServer(final Context context, final String username, final String deviceName, final boolean silent) {
        if (Network.isNetworkAvailable(context)) {
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
                        if (result != null) {
                            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(NEW_FIREBASE_TOKEN, result.getToken()).apply();
                            sendRegistrationToServer(context, result.getToken(), username, deviceName);
                        }
                    } else {
                        // Task failed with an exception
                        Exception exception = task.getException();
                        Log.e(TAG, "Failed to receive Firebase token!", exception);
                        if (!silent) {
                            //TODO user handler like in CommandService
                            Toast.makeText(context, "Failed to synchronize device. Please restart " + context.getString(R.string.app_name) + " and try again!", Toast.LENGTH_LONG).show();
                        }
                    }
                    }
                });

                return true;
            } else {
                return sendRegistrationToServer(context, firebaseToken, username, deviceName);
            }
        } else {
            return false;
        }
    }
}