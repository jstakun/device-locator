package net.gmsworld.devicelocator;

import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Html;
import android.text.InputFilter;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.androidhiddencamera.HiddenCameraUtils;
import com.google.firebase.iid.FirebaseInstanceId;

import net.gmsworld.devicelocator.BroadcastReceivers.DeviceAdminEventReceiver;
import net.gmsworld.devicelocator.BroadcastReceivers.SmsReceiver;
import net.gmsworld.devicelocator.Services.DlFirebaseInstanceIdService;
import net.gmsworld.devicelocator.Services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.Services.RouteTrackingService;
import net.gmsworld.devicelocator.Services.SmsSenderService;
import net.gmsworld.devicelocator.Utilities.AbstractLocationManager;
import net.gmsworld.devicelocator.Utilities.Command;
import net.gmsworld.devicelocator.Utilities.Files;
import net.gmsworld.devicelocator.Utilities.GmsSmartLocationManager;
import net.gmsworld.devicelocator.Utilities.Messenger;
import net.gmsworld.devicelocator.Utilities.Network;
import net.gmsworld.devicelocator.Utilities.Permissions;
import net.gmsworld.devicelocator.Utilities.RouteTrackingServiceUtils;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.ref.WeakReference;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int SEND_LOCATION_INTENT = 1;
    //private static final int MOTION_DETECTOR_INTENT = 2;
    private static final int SELECT_CONTACT_INTENT = 3;

    private static final int ENABLE_ADMIN_INTENT = 12;

    private static final int ACTION_MANAGE_OVERLAY_INTENT = 13;

    private static final int SHARE_ROUTE_MESSAGE = 1;

    private static final int MIN_RADIUS = 10; //meters

    private static final int MAX_RADIUS = 1000;

    public static final String DEVICE_PIN = "token";

    private Boolean running = null;

    private int radius = RouteTrackingService.DEFAULT_RADIUS;
    private boolean motionDetectorRunning = false;
    private String phoneNumber = null, email = null, telegramId = null, pin = null, oldPin = null;

    private final Handler loadingHandler = new UIHandler(this);

    //private Messenger mMessenger;
    private boolean isTrackingServiceBound = false;

    private AlertDialog pinDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");
        Permissions.checkAndRequestPermissionsAtStartup(this); //check marshmallow permissions

        setContentView(R.layout.activity_main);
        restoreSavedData();
        initApp();
        updateUI();
        toggleBroadcastReceiver(); //set broadcast receiver for sms
        //scrollTop();
        //mMessenger = new Messenger(loadingHandler);
        if (motionDetectorRunning) {
            isTrackingServiceBound = RouteTrackingServiceUtils.startRouteTrackingService(this, mConnection, radius, phoneNumber, email, telegramId, false, false);
        }

        boolean isTrackerShown = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("isTrackerShown", false);
        setupToolbar(R.id.smsToolbar);
        if (isTrackerShown) {
            findViewById(R.id.trackerSettings).setVisibility(View.VISIBLE);
            findViewById(R.id.smsSettings).setVisibility(View.GONE);
        } else {
            findViewById(R.id.smsSettings).setVisibility(View.VISIBLE);
            findViewById(R.id.trackerSettings).setVisibility(View.GONE);
        }

        //send email registration request once every day if still unverified
        String emailStatus = PreferenceManager.getDefaultSharedPreferences(this).getString("emailStatus", null);
        long emailRegistrationMillis = PreferenceManager.getDefaultSharedPreferences(this).getLong("emailRegistrationMillis", System.currentTimeMillis());
        if (StringUtils.equalsIgnoreCase(emailStatus, "unverified") && StringUtils.isNotEmpty(email) && (System.currentTimeMillis() - emailRegistrationMillis) > 1000 * 60 * 60 * 24 ) {
            registerEmail((TextView)findViewById(R.id.email), true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");

        //show pin dialog only if not shown in last 10 minutes
        long pinVerificationMillis = PreferenceManager.getDefaultSharedPreferences(this).getLong("pinVerificationMillis", 0);
        boolean settingsVerifyPin = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("settings_verify_pin", false);
        if (StringUtils.isNotEmpty(pin) && settingsVerifyPin && System.currentTimeMillis() - pinVerificationMillis > 10 * 60 * 1000) {
            showPinDialog();
        }

        //paste Telegram id
        boolean telegramPaste = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("telegramPaste", false);
        if (telegramPaste) {
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("telegramPaste", false).commit();
            final TextView telegramInput = (TextView) this.findViewById(R.id.telegramId);
            //paste telegram id from clipboard
            try {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard.hasPrimaryClip()) {
                    int clipboardItemCount = clipboard.getPrimaryClip().getItemCount();
                    for (int i=0;i<clipboardItemCount; i++) {
                        ClipData.Item item = clipboard.getPrimaryClip().getItemAt(i);
                        String pasteData = item.getText().toString();
                        Log.d(TAG, "Clipboard text at " + i + ": " + pasteData);
                        if (Messenger.isValidTelegramId(pasteData)) {
                            telegramInput.setText(pasteData);
                            Toast.makeText(getApplicationContext(), "Pasted Telegram chat or channel ID from clipboard!", Toast.LENGTH_SHORT).show();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to paste text from clipboard", e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        RouteTrackingServiceUtils.unbindRouteTrackingService(this, mConnection, isTrackingServiceBound);

        registerEmail((TextView) findViewById(R.id.email), false);
        registerTelegram((TextView) findViewById(R.id.telegramId));
        registerPhoneNumber((TextView) findViewById(R.id.phoneNumber));

        //update cloud platform,
        if (oldPin != null) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            String firebaseToken = settings.getString(DlFirebaseInstanceIdService.FIREBASE_TOKEN, "");
            if (StringUtils.isNotEmpty(firebaseToken)) {
                settings.edit().remove(DlFirebaseInstanceIdService.FIREBASE_TOKEN).commit(); //remove token so that it will be added again after successful registration
                if (Network.isNetworkAvailable(MainActivity.this)) {
                    DlFirebaseInstanceIdService.sendRegistrationToServer(MainActivity.this, firebaseToken, pin, oldPin);
                }
            }
        }

        if (pinDialog != null) {
            pinDialog.dismiss();
            pinDialog = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == ENABLE_ADMIN_INTENT) {
                if (phoneNumber == null && telegramId == null && email == null) {
                    Toast.makeText(MainActivity.this, "Please specify who should be notified in case of failed login!", Toast.LENGTH_LONG).show();
                    findViewById(R.id.phoneNumber).requestFocus();
                } else {
                    Toast.makeText(MainActivity.this, "Done", Toast.LENGTH_LONG).show();
                }
                //
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("loginTracker", true).commit();
                getSupportActionBar().invalidateOptionsMenu();
                //open dialog to enable photo on failed login
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        onCameraItemSelected();
                    }
                });
                builder.setNegativeButton(R.string.no, null);
                builder.setMessage(Html.fromHtml(getString(R.string.take_photo_prompt)));
                builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
                AlertDialog dialog = builder.create();
                dialog.show();
            } else {
                phoneNumber = getNumber(data);
                initPhoneNumberInput();
                if (phoneNumber != null) {
                    if (requestCode == SEND_LOCATION_INTENT) {
                        launchService();
                    }
                } else {
                    Toast.makeText(this, "Please select phone number from contacts list", Toast.LENGTH_SHORT).show();
                }
            }
        }
        if (requestCode == ACTION_MANAGE_OVERLAY_INTENT && HiddenCameraUtils.canOverDrawOtherApps(this)) {
            Toast.makeText(MainActivity.this, "Please wait. Device Locator is checking your camera...", Toast.LENGTH_LONG).show();
            Intent cameraIntent = new Intent(this, HiddenCaptureImageService.class);
            cameraIntent.putExtra("test", true);
            startService(cameraIntent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //Log.d(TAG, "onCreateOptionsMenu()");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sms:
                Log.d(TAG, "Show sms settings");
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("isTrackerShown", false).commit();
                findViewById(R.id.smsSettings).setVisibility(View.VISIBLE);
                findViewById(R.id.trackerSettings).setVisibility(View.GONE);
                findViewById(R.id.ll_top_focus).requestFocus();
                getSupportActionBar().invalidateOptionsMenu();
                return true;
            case R.id.tracker:
                Log.d(TAG, "Show tracker settings");
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("isTrackerShown", true).commit();
                findViewById(R.id.trackerSettings).setVisibility(View.VISIBLE);
                findViewById(R.id.smsSettings).setVisibility(View.GONE);
                findViewById(R.id.ll_bottom_focus).requestFocus();
                getSupportActionBar().invalidateOptionsMenu();
                return true;
            case R.id.loginTracker:
                onLoginTrackerItemSelected();
                return true;
            case R.id.camera:
                if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("loginTracker", false)) {
                    onCameraItemSelected();
                } else {
                    Toast.makeText(this, "First please enable failed login notification service!", Toast.LENGTH_LONG).show();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        //Log.d(TAG, "onPrepareOptionsMenu()");
        menu.findItem(R.id.camera).setVisible(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("loginTracker", false));
        boolean isTrackerShown = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("isTrackerShown", false);

        if (!isTrackerShown) {
            menu.findItem(R.id.sms).setVisible(false);
            menu.findItem(R.id.tracker).setVisible(true);
        } else  {
            menu.findItem(R.id.tracker).setVisible(false);
            menu.findItem(R.id.sms).setVisible(true);
        }

        return true;
    }

    @Override
    public void onNewIntent (Intent intent) {
        //show tracker view
        Log.d(TAG, "onNewIntent()");
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("isTrackerShown", true).commit();
        findViewById(R.id.trackerSettings).setVisibility(View.VISIBLE);
        findViewById(R.id.smsSettings).setVisibility(View.GONE);
        getSupportActionBar().invalidateOptionsMenu();
    }

    private void initLocationSMSCheckbox() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        ((CheckBox) findViewById(R.id.settings_detected_sms)).setChecked(settings.getBoolean("settings_detected_sms", true));
        ((CheckBox) findViewById(R.id.settings_gps_sms)).setChecked(settings.getBoolean("settings_gps_sms", false));
        ((CheckBox) findViewById(R.id.settings_google_sms)).setChecked(settings.getBoolean("settings_google_sms", true));
        ((CheckBox) findViewById(R.id.settings_verify_pin)).setChecked(settings.getBoolean("settings_verify_pin", false));
        ((CheckBox) findViewById(R.id.settings_sms_contacts)).setChecked(settings.getBoolean("settings_sms_contacts", false));
    }

    public void onLocationSMSCheckboxClicked(View view) {
        boolean checked = ((CheckBox) view).isChecked();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();

        switch (view.getId()) {
            case R.id.settings_detected_sms:
                editor.putBoolean("settings_detected_sms", checked);
                break;
            case R.id.settings_gps_sms:
                editor.putBoolean("settings_gps_sms", checked);
                break;
            case R.id.settings_google_sms:
                editor.putBoolean("settings_google_sms", checked);
                break;
            case R.id.settings_verify_pin:
                editor.putBoolean("settings_verify_pin", checked);
                break;
            case R.id.settings_sms_contacts:
                editor.putBoolean("settings_sms_contacts", checked);
                if (checked && !Permissions.haveReadContactsPermission(this)) {
                    Permissions.requestContactsPermission(this);
                }
                break;
            default:
                break;
        }

        editor.commit();
    }

    private void onLoginTrackerItemSelected() {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        boolean loginTracker = settings.getBoolean("loginTracker", false);
        final ComponentName deviceAdmin = new ComponentName(this, DeviceAdminEventReceiver.class);

        if (loginTracker) {
            //disable tracking
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
                    devicePolicyManager.removeActiveAdmin(deviceAdmin);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putBoolean("loginTracker", false);
                    editor.putBoolean("hiddenCamera", false);
                    editor.commit();
                    getSupportActionBar().invalidateOptionsMenu();
                    Toast.makeText(MainActivity.this, "Done", Toast.LENGTH_LONG).show();
                }
            });
            builder.setNegativeButton(R.string.no, null);
            builder.setMessage(Html.fromHtml(getString(R.string.disable_fln_prompt)));
            builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
            //enable tracking
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_grant_explanation));
            startActivityForResult(intent, ENABLE_ADMIN_INTENT);
        }
    }

    private void onCameraItemSelected() {
        boolean hiddenCamera = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("hiddenCamera", false);

        if (!hiddenCamera) {
            Log.d(TAG, "Camera is off");
            if (!Permissions.haveCameraPermission(this)) {
                Permissions.requestCameraPermission(this);
            }

            if (!HiddenCameraUtils.canOverDrawOtherApps(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, ACTION_MANAGE_OVERLAY_INTENT);
            } else if (Permissions.haveCameraPermission(this)) {
                Toast.makeText(MainActivity.this, "Please wait. Device Locator is checking your camera...", Toast.LENGTH_LONG).show();
                Intent cameraIntent = new Intent(this, HiddenCaptureImageService.class);
                cameraIntent.putExtra("test", true);
                startService(cameraIntent);
            }
        } else {
            Log.d(TAG, "Camera is on");
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().putBoolean("hiddenCamera", false).commit();
                    Toast.makeText(MainActivity.this, "Camera disabled", Toast.LENGTH_LONG).show();
                }
            });
            builder.setNegativeButton(R.string.no, null);
            builder.setMessage(Html.fromHtml(getString(R.string.disable_photo_prompt)));
            builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    private void clearFocus() {
        View current = getCurrentFocus();
        if (current != null) {
            current.clearFocus();
        }

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
    }

    private void updateUI() {
        ((Button) this.findViewById(R.id.running_button)).setText((running) ? getString(R.string.stop) : getString(R.string.start));
        ViewCompat.setBackgroundTintList(this.findViewById(R.id.running_button), ColorStateList.valueOf(getResources().getColor((running) ? R.color.colorAccent : R.color.colorPrimary)));
        ViewCompat.setBackgroundTintList(this.findViewById(R.id.send_button), ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));

        ((Button) this.findViewById(R.id.motion_button)).setText((motionDetectorRunning) ? getString(R.string.stop) : getString(R.string.start));
        ViewCompat.setBackgroundTintList(this.findViewById(R.id.motion_button), ColorStateList.valueOf(getResources().getColor((motionDetectorRunning) ? R.color.colorAccent : R.color.colorPrimary)));

        ViewCompat.setBackgroundTintList(this.findViewById(R.id.route_button), ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
        ViewCompat.setBackgroundTintList(this.findViewById(R.id.contact_button), ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
        ViewCompat.setBackgroundTintList(this.findViewById(R.id.telegram_button), ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
    }

    private void toggleRunning() {
        //enable Firebase
        if (!this.running) {
            final String firebaseToken = PreferenceManager.getDefaultSharedPreferences(this).getString(DlFirebaseInstanceIdService.FIREBASE_TOKEN, "");
            final String pin = PreferenceManager.getDefaultSharedPreferences(this).getString(MainActivity.DEVICE_PIN, "");
            if (StringUtils.isEmpty(firebaseToken) && StringUtils.isNotEmpty(pin)) {
                new Thread(new Runnable() {
                    public void run() {
                        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
                        DlFirebaseInstanceIdService.sendRegistrationToServer(MainActivity.this, refreshedToken, pin, null);
                    }
                }).start();
            } else if (StringUtils.isNotEmpty(firebaseToken)) {
                Log.d(TAG, "Firebase token already set");
            } else {
                Log.e(TAG, "Something is wrong here with either empty pin:" + StringUtils.isEmpty(pin) + " or with empty Firebase token:" + StringUtils.isEmpty(firebaseToken));
            }
        }
        //

        if (!this.running && !Permissions.haveSendSMSAndLocationPermission(MainActivity.this)) {
            Permissions.requestSendSMSAndLocationPermission(MainActivity.this);
            Toast.makeText(this, R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!this.running && !Permissions.haveCallPhonePermission(MainActivity.this)) {
            Permissions.requestCallPhonePermission(MainActivity.this);
        }

        this.running = !this.running;
        saveData();
        updateUI();
        toggleBroadcastReceiver();

        //check if location settings are enabled
        if (!GmsSmartLocationManager.isLocationEnabled(this)) {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            Toast.makeText(getApplicationContext(), "Please enable location services in order to receive device location updates!", Toast.LENGTH_SHORT).show();
        }

        if (running) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setPositiveButton("OK", null);
            builder.setNegativeButton("Commands",  new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(getString(R.string.commandsUrl)));
                    startActivity(intent);
                }
            });
            builder.setMessage(Html.fromHtml(getString(R.string.commands_enabled_prompt)));
            builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    private void toggleMotionDetectorRunning() {
        if (StringUtils.isNotEmpty(phoneNumber)) {
            if (!this.motionDetectorRunning && !Permissions.haveSendSMSAndLocationPermission(MainActivity.this)) {
                Permissions.requestSendSMSAndLocationPermission(MainActivity.this);
                Toast.makeText(this, R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            if (!this.motionDetectorRunning && !Permissions.haveLocationPermission(MainActivity.this)) {
                Permissions.requestLocationPermission(MainActivity.this);
                Toast.makeText(getApplicationContext(), "Location permission is needed", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        this.motionDetectorRunning = !this.motionDetectorRunning;
        toogleLocationDetector();
    }

    private void toogleLocationDetector() {
        if (this.motionDetectorRunning) {
            launchMotionDetectorService();
            //check if location settings are enabled
            if (!GmsSmartLocationManager.isLocationEnabled(this)) {
                Toast.makeText(getApplicationContext(), "Please enable location services in order to receive device location updates!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        } else {
            saveData();
            RouteTrackingServiceUtils.stopRouteTrackingService(this, mConnection, isTrackingServiceBound, false, null, null, null, null);
            updateUI();
        }
    }

    private void toggleBroadcastReceiver() {
        ComponentName receiver = new ComponentName(getApplicationContext(), SmsReceiver.class);
        PackageManager pm = getApplicationContext().getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                (running) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private void initApp() {
        initRunningButton();
        initSendLocationButton();
        initShareRouteButton();
        initRadiusInput();
        initMotionDetectorButton();
        initPhoneNumberInput();
        initEmailInput();
        initTelegramInput();
        initContactButton();
        initTelegramButton();
        initTokenInput();
        initPingButton();
        initLocationSMSCheckbox();

        TextView commandLink = (TextView) findViewById(R.id.docs_link);
        commandLink.setText(Html.fromHtml(getString(R.string.docsLink)));
        commandLink.setMovementMethod(LinkMovementMethod.getInstance());

        initRemoteControl();
    }

    private void initTokenInput() {
        final TextView tokenInput = (TextView) this.findViewById(R.id.token);
        tokenInput.setText(pin);

        tokenInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String input = charSequence.toString();
                try {
                    //token is 4 to 8 digits string
                    if (input.length() >= 4) {
                        if (!StringUtils.equals(pin, input)) {
                            oldPin = pin;
                            pin = input;
                            saveData();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, R.string.pin_lenght_error, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private void initRadiusInput() {
        SeekBar radiusBar=(SeekBar)findViewById(R.id.radiusBar);
        radiusBar.setProgress(radius);

        ((TextView) this.findViewById(R.id.motion_radius)).setText(getString(R.string.motion_radius, radius));

        radiusBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressChangedValue = 0;

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressChangedValue = progress;
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                seekBar.requestFocus();
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                //Toast.makeText(MainActivity.this, "Radius has been set to " + progressChangedValue + " meters.", Toast.LENGTH_SHORT).show();
                //minimal value is 10
                if (progressChangedValue < MIN_RADIUS) {
                    progressChangedValue = MIN_RADIUS;
                }

                radius = progressChangedValue;
                ((TextView) MainActivity.this.findViewById(R.id.motion_radius)).setText(getString(R.string.motion_radius, radius));
                saveData();
                //update route tracking service if running
                if (motionDetectorRunning) {
                    RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, mConnection, isTrackingServiceBound, radius, phoneNumber, email, telegramId);
                }
            }
        });


    }

    //email input setup ------------------------------------------------------------------

    private void initEmailInput() {
        final TextView emailInput = (TextView) this.findViewById(R.id.email);
        emailInput.setText(email);

        /*emailInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                //newEmailAddress = charSequence.toString();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });*/

        emailInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if(!hasFocus) {
                    registerEmail(emailInput, false);
                } else {
                    //paste email from clipboard
                    String currentText = emailInput.getText().toString();
                    if (currentText.isEmpty()) {
                        try {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard.hasPrimaryClip()) {
                                int clipboardItemCount = clipboard.getPrimaryClip().getItemCount();
                                for (int i = 0; i < clipboardItemCount; i++) {
                                    ClipData.Item item = clipboard.getPrimaryClip().getItemAt(i);
                                    String pasteData = item.getText().toString();
                                    if (!StringUtils.equals(pasteData, email) && Patterns.EMAIL_ADDRESS.matcher(pasteData).matches()) {
                                        emailInput.setText(pasteData);
                                        Toast.makeText(getApplicationContext(), "Pasted email address from clipboard!", Toast.LENGTH_SHORT).show();
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to paste text from clipboard", e);
                        }
                    }
                }
            }
        });

        emailInput.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                    registerEmail(v, false);
                }
                return false;
            }
        });

        emailInput.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                //Log.d(TAG, "Soft keyboard event " + keyCode);
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_BACK:
                            registerEmail((TextView) v, false);
                            break;
                        default:
                            break;
                    }
                }
                return false;
            }
        });
    }

    private synchronized void registerEmail(TextView emailInput, boolean retry) {
        String newEmailAddress = emailInput.getText().toString();
        if ((!StringUtils.equals(email, newEmailAddress) || retry) && ((StringUtils.isNotEmpty(newEmailAddress) && Patterns.EMAIL_ADDRESS.matcher(newEmailAddress).matches()) || StringUtils.isEmpty(newEmailAddress))) {
            if (Network.isNetworkAvailable(MainActivity.this)) {
                Log.d(TAG, "Setting new email address: " + newEmailAddress);
                Toast.makeText(MainActivity.this, "Email verification in progress...", Toast.LENGTH_LONG).show();
                email = newEmailAddress;
                saveData();
                //update route tracking service if running
                if (motionDetectorRunning) {
                    RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, mConnection, isTrackingServiceBound, radius, phoneNumber, email, telegramId);
                }

                if (StringUtils.isNotEmpty(email)) {
                    net.gmsworld.devicelocator.Utilities.Messenger.sendEmailRegistrationRequest(MainActivity.this, email, 1);
                }
            } else {
                Toast.makeText(MainActivity.this, R.string.no_network_error, Toast.LENGTH_LONG).show();
                emailInput.setText("");
            }
        } else if (!StringUtils.equals(email, newEmailAddress)) {
            Toast.makeText(getApplicationContext(), "Make sure to specify valid email address!", Toast.LENGTH_SHORT).show();
            emailInput.setText("");
        }
    }

    //telegram input setup -----------------------------------------------------------------

    private void initTelegramInput() {
        final TextView telegramInput = (TextView) this.findViewById(R.id.telegramId);
        telegramInput.setText(telegramId);

        /*telegramInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                //newTelegramId = charSequence.toString();
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });*/

        telegramInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    registerTelegram(telegramInput);
                }
            }
        });

        telegramInput.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                    registerTelegram(v);
                }
                return false;
            }
        });

        telegramInput.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                //Log.d(TAG, "Soft keyboard event " + keyCode);
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_BACK:
                            registerTelegram((TextView) v);
                            break;
                        default:
                            break;
                    }
                }
                return false;
            }
        });
    }

    private synchronized void registerTelegram(TextView telegramInput) {
        String newTelegramId = telegramInput.getText().toString();
        if (!StringUtils.equals(telegramId, newTelegramId) && (StringUtils.isEmpty(newTelegramId) || Messenger.isValidTelegramId(newTelegramId))) {
            if (Network.isNetworkAvailable(MainActivity.this)) {
                Log.d(TAG, "Setting new telegram chat id: " + newTelegramId);
                Toast.makeText(MainActivity.this, "Telegram verification in progress...", Toast.LENGTH_LONG).show();
                telegramId = newTelegramId;
                saveData();
                //update route tracking service if running
                if (motionDetectorRunning) {
                    RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, mConnection, isTrackingServiceBound, radius, phoneNumber, email, telegramId);
                }

                if (StringUtils.isNotEmpty(telegramId)) {
                    net.gmsworld.devicelocator.Utilities.Messenger.sendTelegramRegistrationRequest(MainActivity.this, telegramId, 1);
                }
            } else {
                Toast.makeText(MainActivity.this, R.string.no_network_error, Toast.LENGTH_LONG).show();
                telegramInput.setText("");
            }
        } else if (!StringUtils.equals(telegramId, newTelegramId)) {
            Toast.makeText(getApplicationContext(), "Make sure to specify valid Telegram chat id!", Toast.LENGTH_SHORT).show();
            telegramInput.setText("");
        }
    }

    // phone number input setup ---------------------------------------------------------------

    private void initPhoneNumberInput() {
        final TextView phoneNumberInput = (TextView) this.findViewById(R.id.phoneNumber);
        phoneNumberInput.setText(this.phoneNumber);

        /*phoneNumberInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                //newPhoneNumber = charSequence.toString();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });*/

        phoneNumberInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    registerPhoneNumber((TextView)v);
                }
            }
        });

        phoneNumberInput.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                    registerPhoneNumber(v);
                }
                return false;
            }
        });

        phoneNumberInput.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                //Log.d(TAG, "Soft keyboard event " + keyCode);
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_BACK:
                            registerPhoneNumber((TextView) v);
                            break;
                        default:
                            break;
                    }
                }
                return false;
            }
        });
    }

    private synchronized void registerPhoneNumber(TextView phoneNumberInput) {
        String newPhoneNumber = phoneNumberInput.getText().toString();
        if (!StringUtils.equals(phoneNumber, newPhoneNumber) && ((StringUtils.isNotEmpty(newPhoneNumber) && Patterns.PHONE.matcher(newPhoneNumber).matches()) || StringUtils.isEmpty(newPhoneNumber))) {
            Log.d(TAG, "Setting new phone number: " + newPhoneNumber);
            phoneNumber = newPhoneNumber;
            saveData();
            if (motionDetectorRunning) {
                RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, mConnection, isTrackingServiceBound, radius, phoneNumber, email, telegramId);
            }
            if (!Permissions.haveSendSMSPermission(this)) {
                Permissions.requestSendSMSAndLocationPermission(this);
                Toast.makeText(this, R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
            }
        } else if (!StringUtils.equals(phoneNumber, newPhoneNumber)) {
            Toast.makeText(getApplicationContext(), "Make sure to specify valid phone number!", Toast.LENGTH_SHORT).show();
            phoneNumberInput.setText("");
        }
    }

    //------------------------------------------------------------------------------------------------

    private void initShareRouteButton() {
        Button shareRouteButton = (Button) this.findViewById(R.id.route_button);

        shareRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Files.hasRoutePoints(AbstractLocationManager.ROUTE_FILE, MainActivity.this, 2)) {
                    final String title = RouteTrackingServiceUtils.getRouteId(MainActivity.this);
                    GmsSmartLocationManager.getInstance().executeRouteUploadTask(MainActivity.this, title, null, false, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String result, int responseCode, String url) {
                            Log.d(TAG, "Received following response code: "+ responseCode + " from url " + url);
                            Message message = loadingHandler.obtainMessage(SHARE_ROUTE_MESSAGE, responseCode, 0);
                            message.sendToTarget();
                        }
                    });
                } else {
                    Toast.makeText(getApplicationContext(), "No route is saved yet. Please make sure device location tracking is started and try again later.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void initSendLocationButton() {
        Button sendLocationButton = (Button) this.findViewById(R.id.send_button);

        sendLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /* start the contact picker */

                if (phoneNumber != null && phoneNumber.length() > 0) {
                    launchService();
                } else {
                    if (!Permissions.haveReadContactsPermission(MainActivity.this)) {
                        Permissions.requestContactsPermission(MainActivity.this);
                        Toast.makeText(getApplicationContext(), R.string.read_contacts_permission, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!Permissions.haveSendSMSAndLocationPermission(MainActivity.this)) {
                        Permissions.requestSendSMSAndLocationPermission(MainActivity.this);
                        Toast.makeText(MainActivity.this, R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
                    startActivityForResult(intent, SEND_LOCATION_INTENT);
                }
                MainActivity.this.clearFocus();
            }
        });
    }

    private String getNumber(Intent data) {
        String number = null;
        if (data != null) {
            Uri uri = data.getData();

            if (uri != null) {
                Cursor c = null;
                try {
                    c = getContentResolver().query(uri, new String[]{
                                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                                    ContactsContract.CommonDataKinds.Phone.TYPE},
                            null, null, null);

                    if (c != null && c.moveToFirst()) {
                        number = c.getString(0);
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
            }
        }
        return number;
    }

    private void launchService() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setPositiveButton(R.string.send, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                launchSmsService();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.setMessage(Html.fromHtml(getString(R.string.location_prompt, phoneNumber)));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void initRemoteControl() {
        if (!running) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            final SharedPreferences.Editor editor = settings.edit();
            if (!settings.contains("smsDialog")) {
                editor.putBoolean("smsDialog", true);
                editor.commit();
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        toggleRunning();
                    }
                });
                builder.setNegativeButton(R.string.no, null);
                builder.setMessage(Html.fromHtml(getString(R.string.enable_sms_command_prompt)));
                builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        }
    }

    private void launchSmsService() {
        if (!Permissions.haveSendSMSAndLocationPermission(MainActivity.this)) {
            Permissions.requestSendSMSAndLocationPermission(MainActivity.this);
            Toast.makeText(this, R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent newIntent = new Intent(MainActivity.this, SmsSenderService.class);
        newIntent.putExtra("phoneNumber", phoneNumber);
        newIntent.putExtra("email", email);
        newIntent.putExtra("telegramId", telegramId);
        MainActivity.this.startService(newIntent);
    }

    private void launchMotionDetectorService() {
        saveData();
        updateUI();
        isTrackingServiceBound = RouteTrackingServiceUtils.startRouteTrackingService(this, mConnection, radius, phoneNumber, email, telegramId, true, false);
        Toast.makeText(getApplicationContext(), getString(R.string.motion_confirm, radius), Toast.LENGTH_LONG).show();
    }

    private void initRunningButton() {
        Button runningButton = (Button) this.findViewById(R.id.running_button);

        runningButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.toggleRunning();
                MainActivity.this.clearFocus();
            }
        });
    }

    private void initMotionDetectorButton() {
        Button runningButton = (Button) this.findViewById(R.id.motion_button);

        runningButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.toggleMotionDetectorRunning();
                MainActivity.this.clearFocus();
            }
        });
    }

    private void initContactButton() {
        ImageButton contactButton = (ImageButton) this.findViewById(R.id.contact_button);

        contactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!Permissions.haveReadContactsPermission(MainActivity.this)) {
                    Permissions.requestContactsPermission(MainActivity.this);
                    Toast.makeText(getApplicationContext(), R.string.read_contacts_permission, Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
                startActivityForResult(intent, SELECT_CONTACT_INTENT);
                MainActivity.this.clearFocus();
            }
        });
    }

    private void initTelegramButton() {
        ImageButton telegramButton = (ImageButton) this.findViewById(R.id.telegram_button);
        final String appName = "org.telegram.messenger";

        telegramButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setData(Uri.parse("http://telegram.me/device_locator_bot"));
                //Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=device_locator_bot"));
                intent.setType("text/plain");
                intent.putExtra(android.content.Intent.EXTRA_TEXT, "/getmyid");
                try {
                    MainActivity.this.getPackageManager().getPackageInfo(appName, PackageManager.GET_ACTIVITIES);
                    intent.setPackage(appName);
                    //MainActivity.this.startActivity(Intent.createChooser(intent, "Get Chat ID"));
                    MainActivity.this.startActivity(intent);
                    PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().putBoolean("telegramPaste",true).commit();
                    Toast.makeText(MainActivity.this, "In order to get your Chat ID please select Device Locator bot now.", Toast.LENGTH_LONG).show();
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, appName + " not found on this device");
                    Toast.makeText(MainActivity.this, "This function requires installed Telegram Messenger on your device.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void initPingButton() {
        Button pingButton = (Button) this.findViewById(R.id.ping_button);

        pingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (StringUtils.isNotEmpty(phoneNumber) || StringUtils.isNotEmpty(email) || StringUtils.isNotEmpty(telegramId)) {
                    Toast.makeText(MainActivity.this, "Please wait...", Toast.LENGTH_LONG).show();
                    if (StringUtils.isNotEmpty(phoneNumber)) {
                        Intent newIntent = new Intent(MainActivity.this, SmsSenderService.class);
                        newIntent.putExtra("phoneNumber", phoneNumber);
                        newIntent.putExtra("command", Command.PING_COMMAND);
                        MainActivity.this.startService(newIntent);
                    }
                    if (StringUtils.isNotEmpty(email) || StringUtils.isNotEmpty(telegramId)) {
                        Intent newIntent = new Intent(MainActivity.this, SmsSenderService.class);
                        newIntent.putExtra("telegramId", telegramId);
                        newIntent.putExtra("email", email);
                        newIntent.putExtra("command", Command.PING_COMMAND);
                        MainActivity.this.startService(newIntent);
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Please provide notification settings above.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void saveData() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("running", this.running);
        editor.putString(DEVICE_PIN, pin);
        editor.putBoolean("motionDetectorRunning" , this.motionDetectorRunning);
        editor.putInt("radius" , this.radius);
        editor.putString("phoneNumber", phoneNumber);
        editor.putString("email", email);
        editor.putString("telegramId", telegramId);

        editor.commit();
    }

    private void restoreSavedData() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        this.running = settings.getBoolean("running", false);
        //this.keyword = settings.getString("keyword", "");
        this.pin = settings.getString(DEVICE_PIN, "");
        if (StringUtils.isEmpty(this.pin)) {
            this.pin = RandomStringUtils.random(4, false, true);
            settings.edit().putString(DEVICE_PIN, this.pin).commit();
        }

        this.motionDetectorRunning = settings.getBoolean("motionDetectorRunning", false);
        this.radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
        if (this.radius > MAX_RADIUS) {
            this.radius = MAX_RADIUS;
        }
        this.phoneNumber = settings.getString("phoneNumber", "");
        this.email = settings.getString("email", "");
        this.telegramId = settings.getString("telegramId", "");
        //testing use count
        int useCount = settings.getInt("useCount", 0);
        settings.edit().putInt("useCount", useCount+1).commit();
    }

    private void setupToolbar(int toolbarId) {
        final Toolbar toolbar = (Toolbar) findViewById(toolbarId);
        setSupportActionBar(toolbar);
    }

    private void showPinDialog() {
        if (pinDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            LayoutInflater li = LayoutInflater.from(this);
            View pinView = li.inflate(R.layout.verify_pin_dialog, null);

            final EditText tokenInput = (EditText) pinView.findViewById(R.id.verify_pin_edit);

            tokenInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(pin.length())});

            tokenInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    String input = charSequence.toString();
                    if (StringUtils.equals(input, pin)) {
                        //Log.d(TAG, "Security PIN verified!");
                        pinDialog.dismiss();
                        PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit()
                                .remove("pinFailedCount")
                                .putLong("pinVerificationMillis", System.currentTimeMillis())
                                .commit();
                    } else if (input.length() == pin.length()) {
                        int pinFailedCount = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getInt("pinFailedCount", 0);
                        if (pinFailedCount == 2) {
                            pinFailedCount = -1;
                            //send failed login notification
                            Log.d(TAG, "Wrong pin has been entered to unlock the app. SENDING NOTIFICATION!");
                            Intent newIntent = new Intent(MainActivity.this, SmsSenderService.class);
                            newIntent.putExtra("phoneNumber", phoneNumber);
                            newIntent.putExtra("email", email);
                            newIntent.putExtra("telegramId", telegramId);
                            newIntent.putExtra("source", DeviceAdminEventReceiver.SOURCE);
                            MainActivity.this.startService(newIntent);
                            if (PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getBoolean("hiddenCamera", false)) {
                                Intent cameraIntent = new Intent(MainActivity.this, HiddenCaptureImageService.class);
                                MainActivity.this.startService(cameraIntent);
                            } else {
                                Log.d(TAG, "Camera is disable. No photo will be taken");
                            }
                        }
                        PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit()
                                .putInt("pinFailedCount", pinFailedCount+1)
                                .commit();
                    }
                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });

            final TextView helpText = (TextView) pinView.findViewById(R.id.verify_pin_text);
            helpText.setText(Html.fromHtml(getString(R.string.pinLink)));
            helpText.setMovementMethod(new TextViewLinkHandler() {
                @Override
                public void onLinkClick(String url) {
                    if (StringUtils.isNotEmpty(telegramId) || StringUtils.isNotEmpty(email) || StringUtils.isNotEmpty(phoneNumber)) {
                        Intent newIntent = new Intent();
                        newIntent.putExtra("telegramId", telegramId);
                        newIntent.putExtra("command", Command.PIN_COMMAND);
                        newIntent.putExtra("phoneNumber", phoneNumber);
                        newIntent.putExtra("email", email);
                        Messenger.sendCommandMessage(MainActivity.this, newIntent);
                        Toast.makeText(MainActivity.this, "Security PIN has been sent to notifiers", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "No notifier has been set. Unable to send Security PIN.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));

            builder.setView(pinView);

            //builder.setCancelable(false);

            pinDialog = builder.create();

            pinDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {

                public void onCancel(DialogInterface dialog) {
                    //Log.d(TAG, "Closing App!");
                    MainActivity.this.finish();
                }
            });
        }

        //show keyboard
        pinDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        pinDialog.show();
    }

    //----------------------------- route tracking service -----------------------------------

    private final ServiceConnection mConnection = null; /*new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName arg0, IBinder service) {
            try {
                Message msg = Message.obtain(null, RouteTrackingService.COMMAND_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                new Messenger(service).send(msg);
            }
            catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };*/

    private static class UIHandler extends Handler {

        private final WeakReference<MainActivity> mainActivity;

        public UIHandler(MainActivity activity) {
            mainActivity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mainActivity.get();
            if (activity != null && msg.what == SHARE_ROUTE_MESSAGE) {
                int responseCode = msg.arg1;
                if (responseCode == 200) {
                    String showRouteUrl = RouteTrackingServiceUtils.getRouteUrl(activity);
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData urlClip = ClipData.newPlainText("text", showRouteUrl);
                    clipboard.setPrimaryClip(urlClip);
                    Toast.makeText(activity, "Route has been uploaded to server and route map url has been saved to clipboard.", Toast.LENGTH_LONG).show();
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(showRouteUrl));
                    activity.startActivity(browserIntent);
                    String message = "Check your route at " + showRouteUrl;
                    if (StringUtils.isNotEmpty(activity.phoneNumber)) {
                        Messenger.sendSMS(activity, activity.phoneNumber, message);
                    }
                    if (StringUtils.isNotEmpty(activity.email)) {
                        String msgtitle = activity.getString(R.string.message);
                        String deviceId = Messenger.getDeviceId(activity);
                        if (deviceId != null) {
                            msgtitle += " installed on device " + deviceId +  " - route map link";
                        }
                        Messenger.sendEmail(activity, null, activity.email, message, msgtitle, 1, new HashMap<String, String>());
                    }
                    if (StringUtils.isNotEmpty(activity.telegramId)) {
                        Messenger.sendTelegram(activity, null, activity.telegramId, message, 1, new HashMap<String, String>());
                    }
                } else {
                    Toast.makeText(activity, "Route upload failed. Please try again in a few moments", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private abstract class TextViewLinkHandler extends LinkMovementMethod {

        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP)
                return super.onTouchEvent(widget, buffer, event);

            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();

            x += widget.getScrollX();
            y += widget.getScrollY();

            Layout layout = widget.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            URLSpan[] link = buffer.getSpans(off, off, URLSpan.class);
            if (link.length != 0) {
                onLinkClick(link[0].getURL());
            }
            return true;
        }

        abstract public void onLinkClick(String url);
    }
}
