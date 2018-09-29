package net.gmsworld.devicelocator;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Switch;
import android.widget.Toast;

import com.androidhiddencamera.HiddenCameraUtils;

import net.gmsworld.devicelocator.utilities.Permissions;

public class PermissionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    @Override
    protected void onResume() {
        super.onResume();

        // --------------------------

        Switch deviceAdminPermission = findViewById(R.id.device_admin_permission);
        deviceAdminPermission.setChecked(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("loginTracker", false));

        Switch manageOverlayPermission = findViewById(R.id.manage_overlay_permission);
        manageOverlayPermission.setChecked(HiddenCameraUtils.canOverDrawOtherApps(this));

        Switch notificationPolicyAccessPermission = findViewById(R.id.notification_policy_access_permission);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            notificationPolicyAccessPermission.setChecked(notificationManager.isNotificationPolicyAccessGranted());
        } else {
            notificationPolicyAccessPermission.setChecked(true);
        }

        // ----------------------------

        Switch accessFineLocationPermission = findViewById(R.id.access_fine_location_permission);
        accessFineLocationPermission.setChecked(Permissions.haveLocationPermission(this));

        Switch smsPermission = findViewById(R.id.sms_permission);
        smsPermission.setChecked(Permissions.haveSendSMSPermission(this));

        Switch cameraPermission = findViewById(R.id.camera_permission);
        cameraPermission.setChecked(Permissions.haveCameraPermission(this));

        Switch readContactsPermission = findViewById(R.id.read_contacts_permission);
        readContactsPermission.setChecked(Permissions.haveReadContactsPermission(this));

        Switch callPhonePermission = findViewById(R.id.call_phone_permission);
        callPhonePermission.setChecked(Permissions.haveCallPhonePermission(this));

        Switch useFingerprintPermission = findViewById(R.id.use_fingerprint_permission);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            FingerprintManager fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
            if (fingerprintManager != null && fingerprintManager.isHardwareDetected() && !Permissions.haveFingerprintPermission(this)) {
                useFingerprintPermission.setChecked(false);
            }  else {
                useFingerprintPermission.setChecked(true);
            }
        } else {
            useFingerprintPermission.setChecked(true);
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
                if (checked && !HiddenCameraUtils.canOverDrawOtherApps(this)) {
                    Permissions.startAddDeviceAdminIntent(this, 0);
                } else if (!checked) {
                    Permissions.startDeviceAdminIntent(this);
                }
                break;
            case R.id.manage_overlay_permission:
                Permissions.startManageOverlayIntent(this, 0);
                break;
            case R.id.notification_policy_access_permission:
                Permissions.startNotificationPolicyAccessIntent(this);
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
                if (checked && !Permissions.haveCameraPermission(this)) {
                    Permissions.requestCameraPermission(this);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this);
                }
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
                    Permissions.requestCallPhonePermission(this, 0);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this);
                }
                break;
            case R.id.read_phone_state_permission:
                if (checked && !Permissions.haveReadPhoneStatePermission(this)) {
                    Permissions.requestReadPhoneStatePermission(this);
                    //TODO this will change deviceId!
                    //Send device registration request to the backend by appending -1 to device name
                    //and call DlFirebaseMessagingService.sendRegistrationToServer
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
            default: break;
        }
    }
}
