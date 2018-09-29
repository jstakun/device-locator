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
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.broadcastreceivers.DeviceAdminEventReceiver;

public class Permissions {

    public static final int PERMISSIONS_REQUEST_GET_ACCOUNTS = 1001;

    public static final int PERMISSIONS_REQUEST_SMS_CONTROL = 1002;

    public static final int PERMISSIONS_REQUEST_TRACKER_CONTROL = 1003;

    public static final int PERMISSIONS_REQUEST_SMS_CONTACTS = 1004;

    public static final int PERMISSIONS_REQUEST_CALL = 1005;

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
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.ACCESS_FINE_LOCATION}, requestCode);
    }

    public static void requestSendSMSPermission(Activity activity, int requestCode) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS}, requestCode);
    }

    public static void requestLocationPermission(Activity activity, int requestCode) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, requestCode);
    }

    public static void requestContactsPermission(Activity activity, int requestCode) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_CONTACTS}, requestCode);
    }

    public static void requestCallPhonePermission(Activity activity, int requestCode) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CALL_PHONE}, requestCode);
    }

    public static void requestCameraPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, 6);
    }

    public static void requestGetAccountsPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.GET_ACCOUNTS}, PERMISSIONS_REQUEST_GET_ACCOUNTS);
    }

    public static void requestReadPhoneStatePermission(Activity activity) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_PHONE_STATE}, PERMISSIONS_REQUEST_GET_ACCOUNTS);
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
        return ContextCompat.checkSelfPermission(context, Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED;
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

    public static void startSettingsIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName()));
        //TODO open permissions fragment
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
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
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            context.startActivity(intent);
        } else {
            Toast.makeText(context, "This permission is granted by default on your device!", Toast.LENGTH_LONG).show();
        }
    }

    public static void startDeviceAdminIntent(Context context) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings"));
        context.startActivity(intent);
    }
}
