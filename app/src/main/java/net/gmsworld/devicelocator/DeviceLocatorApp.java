package net.gmsworld.devicelocator;

import android.app.Application;
import android.preference.PreferenceManager;
import android.util.Log;

import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.config.ACRAConfiguration;
import org.acra.config.ConfigurationBuilder;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class DeviceLocatorApp extends Application {

    public static final String GMS_TOKEN = "gmsToken";
    private static final String TAG = DeviceLocatorApp.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        final Map<String, String> headers = new HashMap<>();
        final String tokenStr = PreferenceManager.getDefaultSharedPreferences(this).getString(GMS_TOKEN, "");
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
                    .setReportingInteractionMode(ReportingInteractionMode.TOAST)
                    .setHttpHeaders(headers)
                    .setResToastText(R.string.crash_error)
                    .setSocketTimeout(30000)
                    .build();
            ACRA.init(this, config);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }
}

