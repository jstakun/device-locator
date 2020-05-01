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
import android.widget.Toast;

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

    private static final String TAG = Permissions.class.getSimpleName();

    /*public static void checkAndRequestPermissionsAtStartup(Activity activity) {
        ArrayList<String> permissions = new ArrayList<>();
        permissions.add(0, Manifest.permission.SEND_SMS);
        permissions.add(1, Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(2, Manifest.permission.READ_CONTACTS);
        permissions.add(3, Manifest.permission.READ_PHONE_STATE);
        permissions.add(4, Manifest.permission.MODIFY_AUDIO_SETTINGS);
        permissions.add(5, Manifest.permission.CALL_PHONE);
        permissions.add(6, Manifest.permission.BIND_DEVICE_ADMIN);
        permissions.add(7, Manifest.permission.GET_ACCOUNTS);

        ArrayList<String> neededPermissions = new ArrayList<>();
        for (int i = 0; i < permissions.size(); i++) {
            if (ContextCompat.checkSelfPermission(activity, permissions.get(i)) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(permissions.get(i));
            }
        }

        if(neededPermissions.size() == 0) {
            return;
        }

        String[] arr = new String[neededPermissions.size()];
        arr = neededPermissions.toArray(arr);

        ActivityCompat.requestPermissions(activity, arr, 1);
    }*/

    public static void requestSendSMSAndLocationPermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.ACCESS_FINE_LOCATION}, requestCode);
        } catch (Throwable e) {
            Toast.makeText(activity, R.string.internal_error, Toast.LENGTH_LONG).show();
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestSendSMSPermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS}, requestCode);
        } catch (Throwable e) {
            Toast.makeText(activity, R.string.internal_error, Toast.LENGTH_LONG).show();
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestLocationPermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, requestCode);
        } catch (Throwable e) {
            Toast.makeText(activity, R.string.internal_error, Toast.LENGTH_LONG).show();
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestContactsPermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_CONTACTS}, requestCode);
        } catch (Throwable e) {
            Toast.makeText(activity, R.string.internal_error, Toast.LENGTH_LONG).show();
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestCallPhonePermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CALL_PHONE}, requestCode);
        } catch (Throwable e) {
            Toast.makeText(activity, R.string.internal_error, Toast.LENGTH_LONG).show();
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestCameraPermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, requestCode);
        } catch (Throwable e) {
            Toast.makeText(activity, R.string.internal_error, Toast.LENGTH_LONG).show();
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestGetAccountsPermission(Activity activity, int requestCode) {
        try {
            //READ_CONTACTS permission is needed to GET_ACCOUNT
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_CONTACTS}, requestCode);
            //ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.GET_ACCOUNTS}, requestCode);
        } catch (Throwable e) {
            Toast.makeText(activity, R.string.internal_error, Toast.LENGTH_LONG).show();
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestReadPhoneStatePermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_PHONE_STATE}, requestCode);
        } catch (Throwable e) {
            Toast.makeText(activity, R.string.internal_error, Toast.LENGTH_LONG).show();
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void requestWriteStoragePermission(Activity activity, int requestCode) {
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
        } catch (Throwable e) {
            Toast.makeText(activity, R.string.internal_error, Toast.LENGTH_LONG).show();
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static boolean haveSendSMSAndLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveSendSMSPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveCameraPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveGetAccountsPermission(Context context) {
        //READ_CONTACTS permission is needed to GET_ACCOUNT
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        //return ContextCompat.checkSelfPermission(context, Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED;
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

    public static boolean haveFingerprintPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveWriteStoragePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static void startSettingsIntent(Context context, String permission) {
        try {
            Toast.makeText(context, "Click on Permissions and select " + permission, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName()));
            //https://stackoverflow.com/questions/31955872/how-to-jump-to-the-manage-permission-page-in-settings-app-with-code
            //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Unable to open Application Settings on your device!", Toast.LENGTH_LONG).show();
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

    public static void startNotificationPolicyAccessIntent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toast.makeText(context, "Please grant \"Do Not Disturb\" access to " + context.getString(R.string.app_name), Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            try {
                context.startActivity(intent);
            } catch (Exception e){
                Log.e(TAG, e.getMessage(), e);
            }

        } else {
            Toast.makeText(context, "This permission is granted by default on your device!", Toast.LENGTH_LONG).show();
        }
    }

    public static void startDeviceAdminIntent(Context context) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings"));
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(Permissions.class.getSimpleName(), e.getMessage(), e);
        }
    }
}
