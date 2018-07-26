package net.gmsworld.devicelocator.Services;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.PinActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.Utilities.Messenger;
import net.gmsworld.devicelocator.Utilities.Network;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class DlFirebaseInstanceIdService extends FirebaseInstanceIdService {

    private static final String TAG = "DlFirebaseIdService";
    public static final String FIREBASE_TOKEN = "firebaseToken";

    @Override
    public void onTokenRefresh() {
        // Get updated InstanceID token.
        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        Log.d(TAG, "Refreshed Firebase token: " + refreshedToken);
        PreferenceManager.getDefaultSharedPreferences(this).edit().remove(DlFirebaseInstanceIdService.FIREBASE_TOKEN).apply();
        final String pin = PreferenceManager.getDefaultSharedPreferences(this).getString(PinActivity.DEVICE_PIN, "");
        if (StringUtils.isNotEmpty(pin)) {
            sendRegistrationToServer(this, refreshedToken, null, null, null);
        } 
    }

    public static void sendRegistrationToServer(final Context context, final String token, final String username, final String deviceName) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN, "");
        if (StringUtils.isNotEmpty(tokenStr)) {
            sendRegistrationToServer(context, token, username, deviceName, tokenStr);
        } else {
            String queryString = "scope=dl&user=" + Messenger.getDeviceId(context, false);
            Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                    if (responseCode == 200) {
                        sendRegistrationToServer(context, token, username, deviceName, Messenger.getToken(context, results));
                    } else {
                        Log.d(TAG, "Failed to receive token: " + results);
                    }
                }
            });
        }
    }

    private static void sendRegistrationToServer(final Context context, final String token, final String username, final String deviceName, final String tokenStr) {
        try {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            if (!StringUtils.equalsIgnoreCase(token, "BLACKLISTED")) {
                String imei = Messenger.getDeviceId(context, false);
                String content = "imei=" + imei;
                if (StringUtils.equalsIgnoreCase(imei, "unknown")) {
                    Log.e(TAG, "Invalid imei");
                    return;
                }
                if (StringUtils.isNotBlank(token)) {
                    content += "&token=" + token;
                }
                if (StringUtils.isNotBlank(username)) {
                    content += "&username=" + username;
                }
                if (StringUtils.isNotBlank(deviceName)) {
                    content += "&name=" + deviceName;
                }
                String url = context.getString(R.string.deviceManagerUrl);

                Map<String, String> headers = new HashMap<String, String>();
                headers.put("Authorization", "Bearer " + tokenStr);

                Network.post(context, url, content, null, headers, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            //save firebase token only if it was successfully registered by the server
                            if (StringUtils.isNotBlank(token)) {
                                settings.edit().putString(FIREBASE_TOKEN, token).apply();
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
                            Log.d(TAG, "Received following response " + responseCode + ": " + results + " from " + url);
                            Toast.makeText(context, "Device registration failed! Please restart Device Manager and try again.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            } else {
                Log.e(TAG, "Invalid token: " + token);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }
}
