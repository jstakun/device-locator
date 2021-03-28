package net.gmsworld.devicelocator.utilities;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.broadcastreceivers.DeviceAdminEventReceiver;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class Permissions {

    public static final int PERMISSIONS_REQUEST_GET_ACCOUNTS = 1001;
    public static final int PERMISSIONS_REQUEST_SMS_CONTROL = 1002;
    public static final int PERMISSIONS_REQUEST_TRACKER_CONTROL = 1003;
    public static final int PERMISSIONS_REQUEST_CONTACTS = 1004;
    public static final int PERMISSIONS_REQUEST_CALL = 1005;
    public static final int PERMISSIONS_WRITE_STORAGE = 1006;
    public static final int PERMISSIONS_REQUEST_ALARM_CONTROL = 1007;
    public static final int PERMISSIONS_REQUEST_GET_EMAIL = 1008;
    public static final int PERMISSIONS_LOCATION = 1009;

    public static final String BIOMETRIC_AUTH = "biometricAuth";

    private static final String TAG = Permissions.class.getSimpleName();

    public static void requestSendSMSAndLocationPermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION}, requestCode);
        } catch (Throwable e) {
            Toaster.showToast(activity, R.string.internal_error);
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestSendSMSPermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS}, requestCode);
        } catch (Throwable e) {
            Toaster.showToast(activity, R.string.internal_error);
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestLocationPermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //startSettingsIntent(activity, "Location");
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting Access Background Location");
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, requestCode);
            } else {
                Log.d(TAG, "Requesting Access Fine Location");
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, requestCode);
            }
        } else {
            try {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION}, requestCode);
            } catch (Throwable e) {
                Toaster.showToast(activity, R.string.internal_error);
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    public static void requestContactsPermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_CONTACTS}, requestCode);
        } catch (Throwable e) {
            Toaster.showToast(activity, R.string.internal_error);
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestCallPhonePermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CALL_PHONE}, requestCode);
        } catch (Throwable e) {
            Toaster.showToast(activity, R.string.internal_error);
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestCameraPermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, requestCode);
        } catch (Throwable e) {
            Toaster.showToast(activity, R.string.internal_error);
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestGetAccountsPermission(Activity activity, int requestCode) {
        try {
            //READ_CONTACTS permission is needed to GET_ACCOUNT
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_CONTACTS}, requestCode);
        } catch (Throwable e) {
            Toaster.showToast(activity, R.string.internal_error);
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestReadPhoneStatePermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_PHONE_STATE}, requestCode);
        } catch (Throwable e) {
            Toaster.showToast(activity, R.string.internal_error);
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestWriteStoragePermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
        } catch (Throwable e) {
            Toaster.showToast(activity, R.string.internal_error);
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void startSettingsIntent(Activity activity, String permission) {
        try {
            Toaster.showToast(activity, activity.getString(R.string.permission_settings, permission));
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            Toaster.showToast(activity, "Unable to open Application Settings on your device!");
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void startAddDeviceAdminIntent(Activity context, int requestCode) {
        final ComponentName deviceAdmin = new ComponentName(context, DeviceAdminEventReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.admin_grant_explanation));
        context.startActivityForResult(intent, requestCode);
    }

    public static void startManageOverlayIntent(Activity context, int requestCode) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.getPackageName()));
        context.startActivityForResult(intent, requestCode);
    }

    public static void startNotificationPolicyAccessIntent(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toaster.showToast(activity, "Please grant \"Do Not Disturb\" access to " + activity.getString(R.string.app_name));
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            try {
                activity.startActivity(intent);
            } catch (Exception e){
                Toaster.showToast(activity, R.string.internal_error);
                Log.e(TAG, e.getMessage(), e);
            }
        } else {
            Toaster.showToast(activity, "This permission is granted by default on your device!");
        }
    }

    public static void startDeviceAdminIntent(Activity activity) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings"));
            activity.startActivity(intent);
        } catch (Exception e) {
            Toaster.showToast(activity, R.string.internal_error);
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static boolean haveSendSMSAndLocationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public static boolean haveSendSMSPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveBackgroundLocationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    public static boolean haveLocationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public static boolean haveCameraPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveGetAccountsPermission(Context context) {
        //READ_CONTACTS permission is needed to GET_ACCOUNT
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveReadContactsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveReadPhoneStatePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveCallPhonePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveWriteStoragePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
}
