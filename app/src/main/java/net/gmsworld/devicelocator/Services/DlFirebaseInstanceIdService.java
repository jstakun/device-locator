package net.gmsworld.devicelocator.Services;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.MainActivity;
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
        sendRegistrationToServer(this, refreshedToken, PreferenceManager.getDefaultSharedPreferences(this).getString(MainActivity.DEVICE_PIN, ""));
    }

    public static void sendRegistrationToServer(final Context context, final String token, final String pin) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
        if (StringUtils.isNotEmpty(tokenStr)) {
            sendRegistrationToServer(context, token, pin, tokenStr);
        } else {
            String queryString = "scope=dl&user=" + Messenger.getDeviceId(context);
            Network.get(context, context.getString(R.string.tokenUrl) + "?" + queryString, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                    if (responseCode == 200) {
                        JsonObject tokenJson = new JsonParser().parse(results).getAsJsonObject();
                        String tokenStr = tokenJson.get(DeviceLocatorApp.GMS_TOKEN_KEY).getAsString();
                        Log.d(TAG, "Received gms token");
                        settings.edit().putString(DeviceLocatorApp.GMS_TOKEN_KEY, tokenStr).apply();
                        sendRegistrationToServer(context, token, pin, tokenStr);
                    } else {
                        Log.d(TAG, "Failed to receive token: " + results);
                    }
                }
            });
        }
    }

    private static void sendRegistrationToServer(Context context, final String token, final String pin, final String tokenStr) {
        try {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            if (StringUtils.isNotEmpty(pin)) {
                String content = "imei=" + Messenger.getDeviceId(context) + "&pin=" + pin;
                if (StringUtils.isNotEmpty(token)) {
                    content += "&token=" + token;
                }
                String url = context.getString(R.string.deviceManagerUrl);

                Map<String, String> headers = new HashMap<String, String>();
                headers.put("Authorization", "Bearer " + tokenStr);
                headers.put("X-GMS-AppId", "2");
                headers.put("X-GMS-Scope", "dl");

                Network.post(context, url, content, null, headers, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            //save firebase token only if it was successfully registered by the server
                            if (StringUtils.isNotEmpty(token)) {
                                settings.edit().putString(FIREBASE_TOKEN, token).apply();
                            }
                            Log.d(TAG, "Firebase token registered successfully");
                        } else {
                            Log.d(TAG, "Received following response " + responseCode + ": " + results + " from " + url);
                        }
                    }
                });
            } else {
                Log.e(TAG, "PIN can't be empty to register for cloud commands");
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }
}
