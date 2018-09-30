package net.gmsworld.devicelocator;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.Toast;

import com.androidhiddencamera.HiddenCameraUtils;

import net.gmsworld.devicelocator.services.DlFirebaseMessagingService;
import net.gmsworld.devicelocator.services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class PermissionsActivity extends AppCompatActivity {

    private static final String TAG = PermissionsActivity.class.getSimpleName();

    private static final int CALL_PERMISSION = 1;
    private static final int DEVICE_ADMIN = 2;
    private static final int CAMERA_PERMISSION = 3;
    private static final int MANAGE_OVERLAY_WITH_CAMERA = 4;

    private PreferencesUtils settings;

    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);
        settings = new PreferencesUtils(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //RESULT_OK = -1. RESULT_CANCELLED = 0
        Log.d(TAG, "onActivityResult() - requestCode: " + requestCode + ", resultCode: " + resultCode);
        if (requestCode == DEVICE_ADMIN && resultCode == RESULT_OK) {
            Log.d(TAG, "Device Admin callback");
            if (StringUtils.isEmpty(settings.getString(MainActivity.NOTIFICATION_EMAIL)) && StringUtils.isEmpty(settings.getString(MainActivity.NOTIFICATION_SOCIAL)) && StringUtils.isEmpty(settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER))) {
                Toast.makeText(this, "Please specify who should be notified in case of failed login!", Toast.LENGTH_LONG).show();
                //TODO show notifications card if no notifiers are set
                //findViewById(R.id.trackerSettings).setVisibility(View.VISIBLE);
                //findViewById(R.id.smsSettings).setVisibility(View.GONE);
                //findViewById(R.id.deviceSettings).setVisibility(View.GONE);
                //findViewById(R.id.email).requestFocus();
            }
        } else if (requestCode == MANAGE_OVERLAY_WITH_CAMERA) {
            Log.d(TAG, "Manage overlay with camera permission callback");
            if (HiddenCameraUtils.canOverDrawOtherApps(this)) {
                onCameraPermissionChecked(true);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode ==  CAMERA_PERMISSION) {
            Log.d(TAG, "Camera permission callback");
            startCameraTest();
        } else if (requestCode == CALL_PERMISSION) {
            Log.d(TAG, "Call permission callback");
            String newDeviceId = Messenger.getDeviceId(this, false);
            if (!StringUtils.equals(newDeviceId, deviceId)) {
                registerDevice();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // --------------------------

        Switch deviceAdminPermission = findViewById(R.id.device_admin_permission);
        deviceAdminPermission.setChecked(settings.getBoolean("loginTracker", false));

        Switch manageOverlayPermission = findViewById(R.id.manage_overlay_permission);
        manageOverlayPermission.setChecked(HiddenCameraUtils.canOverDrawOtherApps(this));

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

        // ----------------------------

        Switch accessFineLocationPermission = findViewById(R.id.access_fine_location_permission);
        accessFineLocationPermission.setChecked(Permissions.haveLocationPermission(this));

        Switch smsPermission = findViewById(R.id.sms_permission);
        smsPermission.setChecked(Permissions.haveSendSMSPermission(this));

        Switch cameraPermission = findViewById(R.id.camera_permission);
        if (!Permissions.haveCameraPermission(this) || !HiddenCameraUtils.canOverDrawOtherApps(this)) {
            cameraPermission.setChecked(false);
            PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).edit().putBoolean("hiddenCamera", false).apply();
        } else {
            cameraPermission.setChecked(true);
        }

        Switch readContactsPermission = findViewById(R.id.read_contacts_permission);
        readContactsPermission.setChecked(Permissions.haveReadContactsPermission(this));

        Switch callPhonePermission = findViewById(R.id.call_phone_permission);
        callPhonePermission.setChecked(Permissions.haveCallPhonePermission(this));

        Switch useFingerprintPermission = findViewById(R.id.use_fingerprint_permission);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            FingerprintManager fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
            if (fingerprintManager != null && fingerprintManager.isHardwareDetected()) {
                if (!Permissions.haveFingerprintPermission(this)) {
                    useFingerprintPermission.setChecked(false);
                } else {
                    useFingerprintPermission.setChecked(true);
                }
            } else {
                useFingerprintPermission.setVisibility(View.GONE);
            }
        } else {
            useFingerprintPermission.setVisibility(View.GONE);
        }

        useFingerprintPermission.setChecked(Permissions.haveFingerprintPermission(this));

        Switch readPhoneStatePermission = findViewById(R.id.read_phone_state_permission);
        readPhoneStatePermission.setChecked(Permissions.haveReadPhoneStatePermission(this));

        Switch getAccountsPermission = findViewById(R.id.get_accounts_permission);
        getAccountsPermission.setChecked(Permissions.haveGetAccountsPermission(this));
    }

    public void onPermissionSwitchSelected(View view) {
        boolean checked = ((Switch) view).isChecked();

        switch (view.getId()) {
            case R.id.device_admin_permission:
                if (checked && !settings.getBoolean("loginTracker", false)) {
                    Permissions.startAddDeviceAdminIntent(this, DEVICE_ADMIN);
                } else if (!checked) {
                    Permissions.startDeviceAdminIntent(this);
                }
                break;
            case R.id.manage_overlay_permission:
                Permissions.startManageOverlayIntent(this, 0);
                break;
            case R.id.notification_policy_access_permission:
                try {
                    Permissions.startNotificationPolicyAccessIntent(this);
                } catch (Exception e) {
                    Toast.makeText(this, "This permission is enabled by default on your device.", Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.access_fine_location_permission:
                if (checked && !Permissions.haveLocationPermission(this)) {
                    Permissions.requestLocationPermission(this, 0);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this);
                }
                break;
            case R.id.sms_permission:
                if (checked && !Permissions.haveSendSMSPermission(this)) {
                    Permissions.requestSendSMSPermission(this, 0);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this);
                }
                break;
            case R.id.camera_permission:
                onCameraPermissionChecked(checked);
                break;
            case R.id.read_contacts_permission:
                if (checked && !Permissions.haveReadContactsPermission(this)) {
                    Permissions.requestContactsPermission(this, 0);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this);
                }
                break;
            case R.id.use_fingerprint_permission:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    FingerprintManager fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
                    if (fingerprintManager != null && fingerprintManager.isHardwareDetected() && checked && !Permissions.haveFingerprintPermission(this)) {
                        Permissions.requestCallPhonePermission(this, 0);
                    } else if (!checked && fingerprintManager != null && fingerprintManager.isHardwareDetected() && Permissions.haveFingerprintPermission(this)) {
                        Permissions.startSettingsIntent(this);
                    } else {
                        Toast.makeText(this, "Your device has no fingerprint reader!", Toast.LENGTH_LONG).show();
                    }
                }
                break;
            case R.id.call_phone_permission:
                if (checked && !Permissions.haveCallPhonePermission(this)) {
                    deviceId = Messenger.getDeviceId(this, false);
                    Permissions.requestCallPhonePermission(this, CALL_PERMISSION);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this);
                }
                break;
            case R.id.read_phone_state_permission:
                if (checked && !Permissions.haveReadPhoneStatePermission(this)) {
                    deviceId = Messenger.getDeviceId(this, false);
                    Permissions.requestReadPhoneStatePermission(this);
                    String newDeviceId = Messenger.getDeviceId(this, false);
                    if (!StringUtils.equals(newDeviceId, deviceId)) {
                        registerDevice();
                    }
                } else if (!checked) {
                    Permissions.startSettingsIntent(this);
                }
                break;
            case R.id.get_accounts_permission:
                if (checked && !Permissions.haveGetAccountsPermission(this)) {
                    Permissions.requestGetAccountsPermission(this);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this);
                }
                break;
            default:
                break;
        }
    }

    private void registerDevice() {
        PreferencesUtils settings = new PreferencesUtils(this);
        final String userLogin = settings.getString(MainActivity.USER_LOGIN);
        if (StringUtils.isNotEmpty(userLogin)) {
            Toast.makeText(this, "Synchronizing device...", Toast.LENGTH_LONG).show();
        }
        if (DlFirebaseMessagingService.sendRegistrationToServer(this, userLogin, settings.getString(MainActivity.DEVICE_NAME), true)) {
            //delete old device
            final String content = "imei=" + deviceId + "&action=delete";
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + settings.getString(DeviceLocatorApp.GMS_TOKEN));
            Network.post(this, getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                }
            });
        } else {
            Toast.makeText(this, "Your device can't be registered at the moment!", Toast.LENGTH_LONG).show();
        }
    }

    private void onCameraPermissionChecked(boolean checked) {
        if (checked) {
            Log.d(TAG, "Camera is currently off");
            if (!HiddenCameraUtils.canOverDrawOtherApps(this)) {
                Toast.makeText(this, "In order to use Camera please first grant drawing over other applications permission.", Toast.LENGTH_LONG).show();
                Permissions.startManageOverlayIntent(this, MANAGE_OVERLAY_WITH_CAMERA);
            } else if (Permissions.haveCameraPermission(this)) {
                startCameraTest();
            } else if (!Permissions.haveCameraPermission(this)) {
                Permissions.requestCameraPermission(this, CAMERA_PERMISSION);
            }
        } else {
            Log.d(TAG, "Camera is currently on");
            Permissions.startSettingsIntent(PermissionsActivity.this);
        }
    }

    private void startCameraTest() {
        Toast.makeText(this, "Please wait. Device Locator is checking your camera...", Toast.LENGTH_LONG).show();
        Intent cameraIntent = new Intent(this, HiddenCaptureImageService.class);
        cameraIntent.putExtra("test", true);
        startService(cameraIntent);
    }
}
