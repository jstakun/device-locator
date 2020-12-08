package net.gmsworld.devicelocator;

import android.app.Application;
import android.preference.PreferenceManager;
import android.util.Log;

import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.HttpSenderConfigurationBuilder;
import org.acra.config.ToastConfigurationBuilder;
import org.acra.data.StringFormat;
import org.acra.sender.HttpSender;
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
            headers.putAll(Network.getDefaultHeaders(this));
            initAcra(headers);
        } else if (Network.isNetworkAvailable(this)) {
            String queryString = "scope=dl&user=" + Messenger.getDeviceId(this, false);
            Network.get(this, getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    if (responseCode == 200) {
                        headers.put("Authorization", "Bearer " + Messenger.getToken(DeviceLocatorApp.this, results));
                        headers.putAll(Network.getDefaultHeaders(DeviceLocatorApp.this));
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
            CoreConfigurationBuilder builder = new CoreConfigurationBuilder(this)
                    .setBuildConfigClass(BuildConfig.class)
                    .setReportFormat(StringFormat.KEY_VALUE_LIST)
                    .setLogcatArguments( "-t", "500", "-v", "time", "net.gmsworld.devicelocator:D", "*.S")
                    .setEnabled(true);
            builder.getPluginConfigurationBuilder(HttpSenderConfigurationBuilder.class)
                    .setUri(getString(R.string.crashReportUrl))
                    .setHttpMethod(HttpSender.Method.POST)
                    .setHttpHeaders(headers)
                    .setSocketTimeout(30000)
                    .setEnabled(true);
            builder.getPluginConfigurationBuilder(ToastConfigurationBuilder.class)
                    .setResText(R.string.crash_error)
                    .setEnabled(true);
            ACRA.init(this, builder);
        } catch (Throwable e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }
}