package net.gmsworld.devicelocator;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.Utilities.Network;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.acra.config.ACRAConfiguration;
import org.acra.config.ConfigurationBuilder;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by jstakun on 5/28/17.
 */

//@ReportsCrashes(
//        formUri = "https://www.gms-world.net/crashReport",
//        mode = ReportingInteractionMode.TOAST,
//        resToastText = R.string.Crash_error,
//        socketTimeout = 30000)
public class DeviceLocatorApp extends Application {

    public static final String GMS_TOKEN_KEY = "gmsToken";
    private static final String TAG = DeviceLocatorApp.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        final Map<String, String> headers = new HashMap<String, String>();
        //headers.put("Authorization", "Bearer:");
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String tokenStr = settings.getString(GMS_TOKEN_KEY, "");
        if (StringUtils.isNotEmpty(tokenStr)) {
            headers.put("Authorization", "Bearer " + tokenStr);
            headers.put("X-GMS-AppId", "2");
            headers.put("X-GMS-Scope", "dl");
            initAcra(headers);
        } else {
            String queryString = "scope=dl&user=" + Network.getDeviceId(this);
            Network.get("https://www.gms-world.net/token?" + queryString, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                    if (responseCode == 200) {
                        JsonObject token = new JsonParser().parse(results).getAsJsonObject();
                        SharedPreferences.Editor editor = settings.edit();
                        String tokenStr = token.get(GMS_TOKEN_KEY).getAsString();
                        Log.d(TAG, "Received following token: " + token);
                        editor.putString(GMS_TOKEN_KEY, tokenStr);
                        editor.commit();
                        headers.put("Authorization", "Bearer " + tokenStr);
                        headers.put("X-GMS-AppId", "2");
                        headers.put("X-GMS-Scope", "dl");
                        initAcra(headers);
                    }
                }
            });
        }
    }

    private void initAcra(Map<String, String> headers) {
        try {
            ACRAConfiguration config = new ConfigurationBuilder(this)
                    .setFormUri(getResources().getString(R.string.crashReportUrl))
                    .setMode(ReportingInteractionMode.TOAST)
                    .setHttpHeaders(headers)
                    .setResToastText(R.string.Crash_error)
                    .setSocketTimeout(30000)
                    .build();
            ACRA.init(this, config);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }
}

