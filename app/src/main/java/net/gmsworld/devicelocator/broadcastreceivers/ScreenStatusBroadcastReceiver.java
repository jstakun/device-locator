package net.gmsworld.devicelocator.broadcastreceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.gmsworld.devicelocator.utilities.Files;

import java.io.File;

public class ScreenStatusBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = ScreenStatusBroadcastReceiver.class.getSimpleName();

    public static final String SCREEN_FILE = "screenFile.bin";

    @Override
    public void onReceive(Context context, Intent intent) {
        File screenFile = Files.getFilesDir(context, SCREEN_FILE, false);
        String line = "" + System.currentTimeMillis();
        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            line = "0," + line;
            Log.d(TAG, "Screen Off Broadcast Received");
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            line = "1," + line;
            Log.d(TAG, "Screen On Broadcast Received");
        }
        if (screenFile != null) {
            Files.appendLineToFileFromContextDir(screenFile, line, 10000, 1000);
        }
    }
}
