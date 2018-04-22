package net.gmsworld.devicelocator.Utilities;

import android.content.Context;
import android.content.pm.PackageInfo;
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

            if (getPackageInfo(c) != null) {
                versionName = getPackageInfo(c).versionName;
                versionCode = getPackageInfo(c).versionCode;
            }

            aboutMessage = c.getString(R.string.Info_about, c.getString(R.string.app_name), versionName, versionCode, getBuildDate(c), Calendar.getInstance().get(Calendar.YEAR), c.getString(R.string.serverUrl));
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

            userAgentMessage = c.getString(R.string.app_name) + "/" + versionName + "-" + versionCode + " (+" + c.getString(R.string.serverUrl) + ")";
        }
        return userAgentMessage;
    }

    private String getBuildDate(Context c) {
        String s = "recently";
        try{
            Locale l;
            try {
                l = c.getResources().getConfiguration().locale;
            } catch (Exception e) { //might cause NPE on some devices
                l = java.util.Locale.getDefault();
            }
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

    //private ApplicationInfo getApplicationInfo(Context c) throws PackageManager.NameNotFoundException {
    //    PackageManager pm = c.getPackageManager();
    //    ApplicationInfo ai = pm.getApplicationInfo(c.getPackageName(), 0);
    //    return ai;
    //}
}
