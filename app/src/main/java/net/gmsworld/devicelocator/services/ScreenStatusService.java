package net.gmsworld.devicelocator.services;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;

import net.gmsworld.devicelocator.broadcastreceivers.ScreenStatusBroadcastReceiver;
import net.gmsworld.devicelocator.utilities.NotificationUtils;

public class ScreenStatusService extends Service {

    private static final String TAG = ScreenStatusService.class.getSimpleName();

    private ScreenStatusBroadcastReceiver mScreenReceiver;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        registerScreenStatusReceiver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NotificationUtils.WORKER_NOTIFICATION_ID, NotificationUtils.buildWorkerNotification(this, null, "Screen activity monitor is running...", false));
        }
    }

    @Override
    public void onDestroy() {
        unregisterScreenStatusReceiver();
        Intent broadcastIntent = new Intent("net.gmsworld.devicelocator.Services.ServiceRestartReceiver");
        sendBroadcast(broadcastIntent);
    }

    private void registerScreenStatusReceiver() {
        mScreenReceiver = new ScreenStatusBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mScreenReceiver, filter);
    }

    private void unregisterScreenStatusReceiver() {
        try {
            if (mScreenReceiver != null) {
                unregisterReceiver(mScreenReceiver);
            }
        } catch (IllegalArgumentException e) {
        }
    }
}

