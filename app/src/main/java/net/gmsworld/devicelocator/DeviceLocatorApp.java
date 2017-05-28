package net.gmsworld.devicelocator;

import android.app.Application;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

/**
 * Created by jstakun on 5/28/17.
 */

@ReportsCrashes(
        formUri = "https://www.gms-world.net/crashReport",
        mode = ReportingInteractionMode.TOAST,
        resToastText = R.string.Crash_error,
        socketTimeout = 30000)
public class DeviceLocatorApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ACRA.init(this);
    }
}

