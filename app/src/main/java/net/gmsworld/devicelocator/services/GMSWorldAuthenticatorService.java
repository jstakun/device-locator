package net.gmsworld.devicelocator.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import net.gmsworld.devicelocator.auth.GMSWorldAuthenticator;

public class GMSWorldAuthenticatorService extends Service {

    private GMSWorldAuthenticator authenticator;

    private static final String TAG = "GMSWorldAuthnService";

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        authenticator = new GMSWorldAuthenticator(this);
    }
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return authenticator.getIBinder();
    }
}
