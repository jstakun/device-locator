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
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
    private static final int CONTACTS_PERMISSION = 5;

    private static final String CURRENT_DEVICE_ID = "currentDeviceId";

    private PreferencesUtils settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);
        settings = new PreferencesUtils(this);

        final Toolbar toolbar = findViewById(R.id.smsToolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //RESULT_OK = -1. RESULT_CANCELLED = 0
        Log.d(TAG, "onActivityResult() - requestCode: " + requestCode + ", resultCode: " + resultCode);
        if (requestCode == DEVICE_ADMIN && resultCode == RESULT_OK) {
            Log.d(TAG, "Device Admin callback");
            if (StringUtils.isEmpty(settings.getString(MainActivity.NOTIFICATION_EMAIL)) && StringUtils.isEmpty(settings.getString(MainActivity.NOTIFICATION_SOCIAL)) && StringUtils.isEmpty(settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER))) {
                Toast.makeText(this, R.string.notifiers_missing, Toast.LENGTH_LONG).show();
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
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode ==  CAMERA_PERMISSION) {
            Log.d(TAG, "Camera permission callback");
            startCameraTest();
        } else if (requestCode == CALL_PERMISSION) {
            Log.d(TAG, "Call permission callback");
            //device is registered in onResume()
        } else if (requestCode == CONTACTS_PERMISSION) {
            Log.d(TAG, "Contects permission callback");
            if (Permissions.haveReadContactsPermission(this)) {
                PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).edit().putBoolean("settings_sms_contacts", true).apply();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume()");
        final String deviceId = PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).getString(CURRENT_DEVICE_ID, null);
        if (deviceId != null && !StringUtils.equals(Messenger.getDeviceId(this, false), deviceId)) {
            registerDevice();
        }
        // device permissions

        //device admin
        Switch deviceAdminPermission = findViewById(R.id.device_admin_permission);
        deviceAdminPermission.setChecked(settings.getBoolean("loginTracker", false));

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
        smsPermission.setChecked(Permissions.haveSendSMSPermission(this));

        Switch cameraPermission = findViewById(R.id.camera_permission);
        if (!Permissions.haveCameraPermission(this) || !HiddenCameraUtils.canOverDrawOtherApps(this)) {
            cameraPermission.setChecked(false);
            PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).edit().putBoolean("hiddenCamera", false).apply();
        } else {
            cameraPermission.setChecked(true);
        }

        Switch readContactsPermission = findViewById(R.id.read_contacts_permission);
        boolean perm = Permissions.haveReadContactsPermission(this);
        readContactsPermission.setChecked(perm);
        PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).edit().putBoolean("settings_sms_contacts", perm).apply();

        Switch callPhonePermission = findViewById(R.id.call_phone_permission);
        callPhonePermission.setChecked(Permissions.haveCallPhonePermission(this));

        //other permissions

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

    @Override
    public void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).edit().remove(CURRENT_DEVICE_ID).apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //Log.d(TAG, "onCreateOptionsMenu()");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        menu.findItem(R.id.permissions).setVisible(false);
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
            default:
                return super.onOptionsItemSelected(item);
        }
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
                try {
                    Permissions.startManageOverlayIntent(this, 0);
                } catch (Exception e) {
                    Toast.makeText(this, "This permission is enabled by default on your device.", Toast.LENGTH_LONG).show();
                }
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
                    Permissions.requestContactsPermission(this, CONTACTS_PERMISSION);
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
                PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).edit().putString(CURRENT_DEVICE_ID, Messenger.getDeviceId(this, false)).apply();
                if (checked && !Permissions.haveCallPhonePermission(this)) {
                    Permissions.requestCallPhonePermission(this, CALL_PERMISSION);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this);
                }
                break;
            case R.id.read_phone_state_permission:
                PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).edit().putString(CURRENT_DEVICE_ID, Messenger.getDeviceId(this, false)).apply();
                if (checked && !Permissions.haveReadPhoneStatePermission(this)) {
                    Permissions.requestReadPhoneStatePermission(this, CALL_PERMISSION);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this);
                }
                break;
            case R.id.get_accounts_permission:
                if (checked && !Permissions.haveGetAccountsPermission(this)) {
                    Permissions.requestGetAccountsPermission(this, Permissions.PERMISSIONS_REQUEST_GET_ACCOUNTS);
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
            final String deviceId = settings.getString(CURRENT_DEVICE_ID);
            final String content = "imei=" + deviceId + "&action=delete";
            Log.d(TAG, "---------------------" + content);
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + settings.getString(DeviceLocatorApp.GMS_TOKEN));
            Network.post(this, getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    if (responseCode == 200) {
                        Log.d(TAG, "Device " + deviceId + " has been removed!");
                        PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).edit().remove(MainActivity.USER_DEVICES).apply();
                    }
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
