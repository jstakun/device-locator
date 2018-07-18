package net.gmsworld.devicelocator;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import net.gmsworld.devicelocator.Utilities.Messenger;
import net.gmsworld.devicelocator.Utilities.Network;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
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
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String tokenStr = settings.getString(GMS_TOKEN_KEY, "");
        if (StringUtils.isNotEmpty(tokenStr)) {
            headers.put("Authorization", "Bearer " + tokenStr);
            headers.put("X-GMS-AppId", "2");
            headers.put("X-GMS-Scope", "dl");
            initAcra(headers);
        } else if (Network.isNetworkAvailable(this)) {
            String queryString = "scope=dl&user=" + Messenger.getDeviceId(this, false);
            Network.get(this, getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                    if (responseCode == 200) {
                        headers.put("Authorization", "Bearer " + Messenger.getToken(DeviceLocatorApp.this, results));
                        headers.put("X-GMS-AppId", "2");
                        headers.put("X-GMS-Scope", "dl");
                        initAcra(headers);
                    } else {
                        Log.d(TAG, "Failed to receive token: " + results);
                    }
                }
            });
        }
    }

    private void initAcra(Map<String, String> headers) {
        try {
            ACRAConfiguration config = new ConfigurationBuilder(this)
                    .setFormUri(getString(R.string.crashReportUrl))
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

