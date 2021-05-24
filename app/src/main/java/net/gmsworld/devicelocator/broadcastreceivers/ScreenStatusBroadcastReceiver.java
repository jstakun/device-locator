package net.gmsworld.devicelocator.broadcastreceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.gmsworld.devicelocator.utilities.Files;

import org.apache.commons.lang3.StringUtils;

import java.io.File;

public class ScreenStatusBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = ScreenStatusBroadcastReceiver.class.getSimpleName();

    public static final String SCREEN_FILE = "screenFile.bin";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            persistScreenStatus(context, intent.getAction());
        }
    }

    public static void persistScreenStatus(Context context, String action) {
        File screenFile = Files.getFilesDir(context, SCREEN_FILE, false);
        String line = "" + System.currentTimeMillis();
        if (StringUtils.equals(action, Intent.ACTION_SCREEN_OFF)) {
            line = "0," + line;
            Log.d(TAG, "Screen Off Broadcast Received");
        } else if (StringUtils.equals(action, Intent.ACTION_SCREEN_ON)) {
            line = "1," + line;
            Log.d(TAG, "Screen On Broadcast Received");
        }
        if (screenFile != null) {
            Files.appendLineToFileFromContextDir(screenFile, line, 10000, 1000);
        }
    }
}
