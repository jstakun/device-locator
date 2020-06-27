package net.gmsworld.devicelocator;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Switch;

import com.androidhiddencamera.HiddenCameraUtils;
import com.google.firebase.analytics.FirebaseAnalytics;

import net.gmsworld.devicelocator.broadcastreceivers.DeviceAdminEventReceiver;
import net.gmsworld.devicelocator.fragments.FirstTimeUseDialogFragment;
import net.gmsworld.devicelocator.services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.services.SmsSenderService;
import net.gmsworld.devicelocator.utilities.AppUtils;
import net.gmsworld.devicelocator.utilities.DevicesUtils;
import net.gmsworld.devicelocator.utilities.Files;
import net.gmsworld.devicelocator.utilities.FingerprintHelper;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.utilities.Toaster;

import org.apache.commons.lang3.StringUtils;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class PermissionsActivity extends AppCompatActivity {

    private static final String TAG = PermissionsActivity.class.getSimpleName();

    private static final int CALL_PERMISSION = 1;
    private static final int DEVICE_ADMIN = 2;
    private static final int CAMERA_PERMISSION = 3;
    private static final int MANAGE_OVERLAY_WITH_CAMERA = 4;
    private static final int CONTACTS_PERMISSION = 5;
    private static final int RESET_PERMISSION = 6;

    private PreferencesUtils settings;
    private Toaster toaster;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);
        settings = new PreferencesUtils(this);
        toaster = new Toaster(this);

        final Toolbar toolbar = findViewById(R.id.smsToolbar);
        setSupportActionBar(toolbar);

        if (!settings.contains("PermissionsFirstTimeUseDialog")) {
            settings.setBoolean("PermissionsFirstTimeUseDialog", true);
            FirstTimeUseDialogFragment firstTimeUseDialogFragment = FirstTimeUseDialogFragment.newInstance(R.string.permissions_first_time_use, R.drawable.ic_settings_cell_gray);
            firstTimeUseDialogFragment.show(getFragmentManager(), "PermissionsFirstTimeUseDialog");
        }

        FirebaseAnalytics.getInstance(this).logEvent("permissions_activity", new Bundle());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //RESULT_OK = -1. RESULT_CANCELLED = 0
        Log.d(TAG, "onActivityResult() - requestCode: " + requestCode + ", resultCode: " + resultCode);
        if (requestCode == DEVICE_ADMIN && resultCode == RESULT_OK) {
            Log.d(TAG, "Device Admin callback");
            if (StringUtils.isEmpty(settings.getString(MainActivity.NOTIFICATION_EMAIL)) && StringUtils.isEmpty(settings.getString(MainActivity.NOTIFICATION_SOCIAL)) && StringUtils.isEmpty(settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER))) {
                toaster.showActivityToast(R.string.notifiers_missing);
                Intent mainIntent = new Intent(this, MainActivity.class);
                mainIntent.setAction(MainActivity.ACTION_DEVICE_TRACKER_NOTIFICATION);
                startActivity(mainIntent);
                finish();
            }
        } else if (requestCode == MANAGE_OVERLAY_WITH_CAMERA) {
            Log.d(TAG, "Manage overlay with camera permission callback");
            if (HiddenCameraUtils.canOverDrawOtherApps(this)) {
                onCameraPermissionChecked(true);
            }
        } else if ((requestCode == DEVICE_ADMIN || requestCode == RESET_PERMISSION) && resultCode == RESULT_CANCELED) {
            toaster.showActivityToast("Select checkbox next to " + getString(R.string.app_name));
            Permissions.startDeviceAdminIntent(this);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case CAMERA_PERMISSION:
                 Log.d(TAG, "Camera permission callback");
                 startCameraTest();
                 break;
            case CALL_PERMISSION:
                 Log.d(TAG, "Call permission callback");
                 break;
            case CONTACTS_PERMISSION:
                 Log.d(TAG, "Contacts permission callback");
                 break;
            case Permissions.PERMISSIONS_LOCATION:
                 //send device location to admin channel
                 Bundle extras = new Bundle();
                 extras.putString("telegramId", getString(R.string.telegram_notification));
                 SmsSenderService.initService(this, false, false, true, null, null, null, null, extras);
                 break;
             default:
                 break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume()");
        final String savedDeviceId = settings.getString(DevicesUtils.CURRENT_DEVICE_ID);
        final String deviceId = Messenger.getDeviceId(this, false);
        if (StringUtils.isNotEmpty(savedDeviceId) && !StringUtils.equals(deviceId, savedDeviceId)) {
            //device name has changed because READ_PHONE_STATE permission was revoked
            DevicesUtils.registerDevice(this, settings, toaster);
        }

        // device permissions

        //device admin
        Switch deviceAdminPermission = findViewById(R.id.device_admin_permission);
        deviceAdminPermission.setChecked(settings.getBoolean(DeviceAdminEventReceiver.DEVICE_ADMIN_ENABLED, false));

        //manage overlay
        Switch manageOverlayPermission = findViewById(R.id.manage_overlay_permission);
        manageOverlayPermission.setChecked(HiddenCameraUtils.canOverDrawOtherApps(this));

        //do not disturb
        Switch notificationPolicyAccessPermission = findViewById(R.id.notification_policy_access_permission);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationPolicyAccessPermission.setChecked(notificationManager.isNotificationPolicyAccessGranted());
            } else {
                notificationPolicyAccessPermission.setVisibility(View.GONE);
            }
        } else {
            notificationPolicyAccessPermission.setVisibility(View.GONE);
        }

        // application permissions

        Switch accessFineLocationPermission = findViewById(R.id.access_fine_location_permission);
        accessFineLocationPermission.setChecked(Permissions.haveLocationPermission(this));

        Switch smsPermission = findViewById(R.id.sms_permission);
        if (AppUtils.getInstance().isFullVersion()) {
            smsPermission.setChecked(Permissions.haveSendSMSPermission(this));
        } else {
            smsPermission.setVisibility(View.GONE);
        }

        Switch cameraPermission = findViewById(R.id.camera_permission);
        if (!Permissions.haveCameraPermission(this) || !HiddenCameraUtils.canOverDrawOtherApps(this)) {
            cameraPermission.setChecked(false);
            settings.setBoolean(HiddenCaptureImageService.STATUS, false);
        } else {
            cameraPermission.setChecked(true);
        }

        Switch writeStoragePermission = findViewById(R.id.write_storage_permission);
        writeStoragePermission.setChecked(Permissions.haveWriteStoragePermission(this));

        Switch readContactsPermission = findViewById(R.id.read_contacts_permission);
        if (AppUtils.getInstance().isFullVersion()) {
            boolean perm = Permissions.haveReadContactsPermission(this);
            readContactsPermission.setChecked(perm);
            if (!perm && settings.contains(DevicesUtils.USER_DEVICES) && settings.contains(MainActivity.USER_LOGIN)) {
                //READ_CONTACTS permission has been revoked: remove this device old data
                settings.remove(DevicesUtils.USER_DEVICES, DevicesUtils.USER_DEVICES_TIMESTAMP, DevicesUtils.USER_DEVICES_TIMESTAMP, MainActivity.USER_LOGIN);
                DevicesUtils.deleteDevice(this, settings, deviceId);
            }
        } else {
            readContactsPermission.setVisibility(View.GONE);
        }

        Switch callPhonePermission = findViewById(R.id.call_phone_permission);
        callPhonePermission.setChecked(Permissions.haveCallPhonePermission(this));

        Switch resetPermission = findViewById(R.id.reset_permission);
        resetPermission.setChecked(settings.getBoolean("allowReset", false));

        ((Switch) findViewById(R.id.settings_sms_without_pin)).setChecked(settings.getBoolean("settings_sms_without_pin", true));

        //other permissions

        Switch useFingerprintPermission = findViewById(R.id.use_fingerprint_permission);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            FingerprintManager fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
            if (fingerprintManager != null && fingerprintManager.isHardwareDetected()) {
                useFingerprintPermission.setChecked(Permissions.haveFingerprintPermission(this) && settings.getBoolean(FingerprintHelper.BIOMETRIC_AUTH, true));
            } else {
                useFingerprintPermission.setVisibility(View.GONE);
            }
        } else {
            useFingerprintPermission.setVisibility(View.GONE);
        }

        Switch readPhoneStatePermission = findViewById(R.id.read_phone_state_permission);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            readPhoneStatePermission.setChecked(Permissions.haveReadPhoneStatePermission(this));
        } else {
            readPhoneStatePermission.setVisibility(View.GONE);
        }

        Switch getAccountsPermission = findViewById(R.id.get_accounts_permission);
        getAccountsPermission.setChecked(Permissions.haveGetAccountsPermission(this));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        settings.remove(DevicesUtils.CURRENT_DEVICE_ID);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //Log.d(TAG, "onCreateOptionsMenu()");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        menu.findItem(R.id.permissions).setVisible(false);

        if (Files.getAuditComands(this) == 0) {
            menu.findItem(R.id.commandLog).setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent = new Intent(this, MainActivity.class);
        switch (item.getItemId()) {
            case R.id.sms:
                intent.setAction(MainActivity.ACTION_SMS_MANAGER);
                startActivity(intent);
                finish();
                return true;
            case R.id.tracker:
                intent.setAction(MainActivity.ACTION_DEVICE_TRACKER_NOTIFICATION);
                startActivity(intent);
                finish();
                return true;
            case R.id.devices:
                intent.setAction(MainActivity.ACTION_DEVICE_MANAGER);
                startActivity(intent);
                finish();
                return true;
            case R.id.map:
                startActivity(new Intent(this, MapsActivity.class));
                finish();
                return true;
            case R.id.commandLog:
                startActivity(new Intent(this, CommandListActivity.class));
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onPermissionSwitchSelected(View view) {
        boolean checked = ((Switch) view).isChecked();

        switch (view.getId()) {
            case R.id.device_admin_permission:
                if (checked && !settings.getBoolean(DeviceAdminEventReceiver.DEVICE_ADMIN_ENABLED, false)) {
                    Permissions.startAddDeviceAdminIntent(this, DEVICE_ADMIN);
                } else if (!checked) {
                    Permissions.startDeviceAdminIntent(this);
                }
                break;
            case R.id.manage_overlay_permission:
                try {
                    Permissions.startManageOverlayIntent(this, 0);
                } catch (Exception e) {
                    toaster.showActivityToast("This permission is enabled by default on your device.");
                }
                break;
            case R.id.notification_policy_access_permission:
                try {
                    Permissions.startNotificationPolicyAccessIntent(this);
                } catch (Exception e) {
                    toaster.showActivityToast( "This permission is enabled by default on your device.");
                }
                break;
            case R.id.access_fine_location_permission:
                if (checked && !Permissions.haveLocationPermission(this)) {
                    Permissions.requestLocationPermission(this, Permissions.PERMISSIONS_LOCATION);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this , "Location");
                }
                break;
            case R.id.sms_permission:
                if (checked && !Permissions.haveSendSMSPermission(this)) {
                    Permissions.requestSendSMSPermission(this, 0);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this, "SMS");
                }
                break;
            case R.id.camera_permission:
                onCameraPermissionChecked(checked);
                break;
            case R.id.read_contacts_permission:
                if (checked && !Permissions.haveReadContactsPermission(this)) {
                    Permissions.requestContactsPermission(this, CONTACTS_PERMISSION);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this, "Contacts");
                }
                break;
            case R.id.use_fingerprint_permission:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    settings.setBoolean(FingerprintHelper.BIOMETRIC_AUTH, checked);
                    FingerprintManager fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
                    if (fingerprintManager != null && fingerprintManager.isHardwareDetected() && checked && !Permissions.haveFingerprintPermission(this)) {
                        Permissions.requestCallPhonePermission(this, 0);
                    } else if (!checked && fingerprintManager != null && fingerprintManager.isHardwareDetected() && Permissions.haveFingerprintPermission(this)) {
                        Permissions.startSettingsIntent(this, "Biometric");
                    } else if (fingerprintManager == null || !fingerprintManager.isHardwareDetected()) {
                        toaster.showActivityToast("Your device has no fingerprint reader!");
                    }
                }
                break;
            case R.id.call_phone_permission:
                settings.setString(DevicesUtils.CURRENT_DEVICE_ID, Messenger.getDeviceId(this, false));
                if (checked && !Permissions.haveCallPhonePermission(this)) {
                    Permissions.requestCallPhonePermission(this, CALL_PERMISSION);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this, "Phone");
                }
                break;
            case R.id.read_phone_state_permission:
                settings.setString(DevicesUtils.CURRENT_DEVICE_ID, Messenger.getDeviceId(this, false));
                if (checked && !Permissions.haveReadPhoneStatePermission(this)) {
                    Permissions.requestReadPhoneStatePermission(this, CALL_PERMISSION);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this, "Phone");
                }
                break;
            case R.id.get_accounts_permission:
                if (checked && !Permissions.haveGetAccountsPermission(this)) {
                    Permissions.requestGetAccountsPermission(this, Permissions.PERMISSIONS_REQUEST_GET_ACCOUNTS);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this, "Contacts");
                }
                break;
            case R.id.reset_permission:
                if (checked && !settings.getBoolean(DeviceAdminEventReceiver.DEVICE_ADMIN_ENABLED, false)) {
                    Permissions.startAddDeviceAdminIntent(this, RESET_PERMISSION);
                } else {
                    settings.setBoolean("allowReset", checked);
                }
                break;
            case R.id.settings_sms_without_pin:
                settings.setBoolean("settings_sms_without_pin", checked);
                if (!checked) {
                    toaster.showActivityToast( "Be careful. From now on Security PIN is not required to send command to your device!");
                }
                break;
            case R.id.write_storage_permission:
                if (checked && !Permissions.haveWriteStoragePermission(this)) {
                    Permissions.requestWriteStoragePermission(this, Permissions.PERMISSIONS_WRITE_STORAGE);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this, "Storage");
                }
            default:
                break;
        }
    }

    private void onCameraPermissionChecked(boolean checked) {
        if (checked) {
            Log.d(TAG, "Camera is currently off");
            if (!HiddenCameraUtils.canOverDrawOtherApps(this)) {
                toaster.showActivityToast( "In order to use Camera please first grant drawing over other applications permission.");
                Permissions.startManageOverlayIntent(this, MANAGE_OVERLAY_WITH_CAMERA);
            } else if (Permissions.haveCameraPermission(this)) {
                startCameraTest();
            } else if (!Permissions.haveCameraPermission(this)) {
                Permissions.requestCameraPermission(this, CAMERA_PERMISSION);
            }
        } else {
            Log.d(TAG, "Camera is currently on");
            Permissions.startSettingsIntent(PermissionsActivity.this, "Camera");
        }
    }

    private void startCameraTest() {
        toaster.showActivityToast( "Please wait. I'm checking your camera now...");
        Intent cameraIntent = new Intent(this, HiddenCaptureImageService.class);
        cameraIntent.putExtra("test", true);
        startService(cameraIntent);
    }
}
