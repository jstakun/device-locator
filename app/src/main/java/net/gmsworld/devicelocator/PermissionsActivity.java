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

import net.gmsworld.devicelocator.fragments.FirstTimeUseDialogFragment;
import net.gmsworld.devicelocator.services.DlFirebaseMessagingService;
import net.gmsworld.devicelocator.services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.utilities.FingerprintHelper;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

import static net.gmsworld.devicelocator.broadcastreceivers.DeviceAdminEventReceiver.DEVICE_ADMIN_ENABLED;

public class PermissionsActivity extends AppCompatActivity {

    private static final String TAG = PermissionsActivity.class.getSimpleName();

    private static final int CALL_PERMISSION = 1;
    private static final int DEVICE_ADMIN = 2;
    private static final int CAMERA_PERMISSION = 3;
    private static final int MANAGE_OVERLAY_WITH_CAMERA = 4;
    private static final int CONTACTS_PERMISSION = 5;
    private static final int RESET_PERMISSION = 6;

    private static final String CURRENT_DEVICE_ID = "currentDeviceId";

    private PreferencesUtils settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);
        settings = new PreferencesUtils(this);

        final Toolbar toolbar = findViewById(R.id.smsToolbar);
        setSupportActionBar(toolbar);

        if (!settings.contains("PermissionsFirstTimeUseDialog")) {
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("PermissionsFirstTimeUseDialog", true).apply();
            FirstTimeUseDialogFragment firstTimeUseDialogFragment = FirstTimeUseDialogFragment.newInstance(R.string.permissions_first_time_use, R.drawable.ic_settings_cell_gray);
            firstTimeUseDialogFragment.show(getFragmentManager(), "PermissionsFirstTimeUseDialog");
        }
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
        } else if ((requestCode == DEVICE_ADMIN || requestCode == RESET_PERMISSION) && resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "Select checkbox next to " + getString(R.string.app_name), Toast.LENGTH_LONG).show();
            Permissions.startDeviceAdminIntent(this);
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
            Log.d(TAG, "Contacts permission callback");
            if (Permissions.haveReadContactsPermission(this)) {
                PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).edit().putBoolean("settings_sms_contacts", true).apply();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume()");
        final String savedDeviceId = settings.getString(CURRENT_DEVICE_ID);
        final String deviceId = Messenger.getDeviceId(this, false);
        if (StringUtils.isNotEmpty(savedDeviceId) && !StringUtils.equals(deviceId, savedDeviceId)) {
            //device name has changed because READ_PHONE_STATE permission was revoked
            registerDevice();
        }

        // device permissions

        //device admin
        Switch deviceAdminPermission = findViewById(R.id.device_admin_permission);
        deviceAdminPermission.setChecked(settings.getBoolean(DEVICE_ADMIN_ENABLED, false));

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

        //TODO hide sms permissions switch
        Switch smsPermission = findViewById(R.id.sms_permission);
        smsPermission.setChecked(Permissions.haveSendSMSPermission(this));

        Switch cameraPermission = findViewById(R.id.camera_permission);
        if (!Permissions.haveCameraPermission(this) || !HiddenCameraUtils.canOverDrawOtherApps(this)) {
            cameraPermission.setChecked(false);
            PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).edit().putBoolean("hiddenCamera", false).apply();
        } else {
            cameraPermission.setChecked(true);
        }

        //TODO hide read contacts permissions switch
        Switch readContactsPermission = findViewById(R.id.read_contacts_permission);
        boolean perm = Permissions.haveReadContactsPermission(this);
        readContactsPermission.setChecked(perm);
        PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).edit().putBoolean("settings_sms_contacts", perm).apply();
        if (!perm && settings.contains(MainActivity.USER_DEVICES) && settings.contains(MainActivity.USER_LOGIN)) {
            //READ_CONTACTS permission has been revoked: remove devices data
            PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).edit().remove(MainActivity.USER_DEVICES).remove(MainActivity.USER_LOGIN).apply();
            deleteDevice(deviceId);
        }

        Switch callPhonePermission = findViewById(R.id.call_phone_permission);
        callPhonePermission.setChecked(Permissions.haveCallPhonePermission(this));

        Switch resetPermission = findViewById(R.id.reset_permission);
        resetPermission.setChecked(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("allowReset", false));

        //other permissions

        Switch useFingerprintPermission = findViewById(R.id.use_fingerprint_permission);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            FingerprintManager fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
            if (fingerprintManager != null && fingerprintManager.isHardwareDetected()) {
                useFingerprintPermission.setChecked(Permissions.haveFingerprintPermission(this) && PreferenceManager.getDefaultSharedPreferences(this).getBoolean(FingerprintHelper.BIOMETRIC_AUTH, true));
            } else {
                useFingerprintPermission.setVisibility(View.GONE);
            }
        } else {
            useFingerprintPermission.setVisibility(View.GONE);
        }

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
                if (checked && !settings.getBoolean(DEVICE_ADMIN_ENABLED, false)) {
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
                    PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(FingerprintHelper.BIOMETRIC_AUTH, checked).apply();
                    FingerprintManager fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
                    if (fingerprintManager != null && fingerprintManager.isHardwareDetected() && checked && !Permissions.haveFingerprintPermission(this)) {
                        Permissions.requestCallPhonePermission(this, 0);
                    } else if (!checked && fingerprintManager != null && fingerprintManager.isHardwareDetected() && Permissions.haveFingerprintPermission(this)) {
                        Permissions.startSettingsIntent(this);
                    } else if (fingerprintManager == null || !fingerprintManager.isHardwareDetected()) {
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
            case R.id.reset_permission:
                if (checked && !settings.getBoolean(DEVICE_ADMIN_ENABLED, false)) {
                    Permissions.startAddDeviceAdminIntent(this, RESET_PERMISSION);
                } else {
                    PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).edit().putBoolean("allowReset", checked).apply();
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
            if (settings.contains(CURRENT_DEVICE_ID)) {
                deleteDevice(settings.getString(CURRENT_DEVICE_ID));
            }
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
        Toast.makeText(this, "Please wait. I'm checking your camera now...", Toast.LENGTH_LONG).show();
        Intent cameraIntent = new Intent(this, HiddenCaptureImageService.class);
        cameraIntent.putExtra("test", true);
        startService(cameraIntent);
    }

    private void deleteDevice(final String deviceId) {
        final String content = "imei=" + deviceId + "&action=delete";
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + settings.getString(DeviceLocatorApp.GMS_TOKEN));
        Network.post(this, getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
            @Override
            public void onGetFinish(String results, int responseCode, String url) {
                if (responseCode == 200) {
                    Log.d(TAG, "Device " + deviceId + " has been removed!");
                    PreferenceManager.getDefaultSharedPreferences(PermissionsActivity.this).edit().remove(MainActivity.USER_DEVICES).remove(CURRENT_DEVICE_ID).apply();
                }
            }
        });
    }
}
