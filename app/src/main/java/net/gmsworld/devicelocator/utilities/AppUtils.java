package net.gmsworld.devicelocator.utilities;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import net.gmsworld.devicelocator.BuildConfig;
import net.gmsworld.devicelocator.R;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Created by jstakun on 3/5/18.
 */

public class AppUtils {


    private static final String TAG = AppUtils.class.getSimpleName();

    private String aboutMessage = null, userAgentMessage = null;
    private PackageInfo pi;

    private static final AppUtils instance = new AppUtils();

    private AppUtils() {
    }

    public static AppUtils getInstance() {
        return instance;
    }

    public synchronized String getAboutMessage(Context c) {
        if (aboutMessage == null) {
            String versionName = "latest";
            int versionCode = 1;
            String androidVersion =  "Android " + Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT;

            if (getPackageInfo(c) != null) {
                versionName = getPackageInfo(c).versionName;
                versionCode = getPackageInfo(c).versionCode;
            }

            aboutMessage = c.getString(R.string.info_about, c.getString(R.string.app_name), versionName, versionCode, BuildConfig.APP_TYPE, getBuildDate(c), Calendar.getInstance().get(Calendar.YEAR), c.getString(R.string.serverUrl), Messenger.getDeviceId(c, true), androidVersion);
        }
        return aboutMessage;
    }

    public synchronized String getUserAgent(Context c) {
        if (userAgentMessage == null) {
            String versionName = "latest";
            int versionCode = 1;

            if (getPackageInfo(c) != null) {
                versionName = getPackageInfo(c).versionName;
                versionCode = getPackageInfo(c).versionCode;
            }

            userAgentMessage = c.getString(R.string.app_name) + "/" + versionName + "-" + versionCode + "." + BuildConfig.APP_TYPE + " (+" + c.getString(R.string.serverUrl) + ")";
        }
        return userAgentMessage;
    }

    private String getBuildDate(Context c) {
        String s = "recently";
        try{
            Locale l = getCurrentLocale(c);
            DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, l);
            s = formatter.format(BuildConfig.TIMESTAMP);
        } catch(Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return s;
    }

    private PackageInfo getPackageInfo(Context c) {
        if (pi == null) {
            try {
                pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
        return pi;
    }

    public int getVersionCode(Context c) {
        return getPackageInfo(c).versionCode;
    }

    public boolean isFullVersion() {
        return BuildConfig.APP_TYPE.equalsIgnoreCase("FULL");
    }

    public boolean hasTelephonyFeature(Context c) {
        return c.getPackageManager().hasSystemFeature("android.hardware.telephony");
    }

    public Locale getCurrentLocale(Context c) {
        try {
            return c.getResources().getConfiguration().locale;
        } catch (Exception e) { //might cause NPE on some devices
            return Locale.getDefault();
        }
    }
    //private ApplicationInfo getApplicationInfo(Context c) throws PackageManager.NameNotFoundException {
    //    PackageManager pm = c.getPackageManager();
    //    ApplicationInfo ai = pm.getApplicationInfo(c.getPackageName(), 0);
    //    return ai;
    //}
}
