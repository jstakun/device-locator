package net.gmsworld.devicelocator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.broadcastreceivers.SmsReceiver;
import net.gmsworld.devicelocator.fragments.LoginDialogFragment;
import net.gmsworld.devicelocator.fragments.NotificationActivationDialogFragment;
import net.gmsworld.devicelocator.fragments.RemoveDeviceDialogFragment;
import net.gmsworld.devicelocator.fragments.SmsCommandsEnabledDialogFragment;
import net.gmsworld.devicelocator.fragments.SmsCommandsInitDialogFragment;
import net.gmsworld.devicelocator.fragments.SmsNotificationWarningDialogFragment;
import net.gmsworld.devicelocator.model.Device;
import net.gmsworld.devicelocator.services.DlFirebaseMessagingService;
import net.gmsworld.devicelocator.services.RouteTrackingService;
import net.gmsworld.devicelocator.services.SmsSenderService;
import net.gmsworld.devicelocator.utilities.AbstractLocationManager;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.Files;
import net.gmsworld.devicelocator.utilities.GmsSmartLocationManager;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.utilities.RouteTrackingServiceUtils;
import net.gmsworld.devicelocator.views.CommandArrayAdapter;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements RemoveDeviceDialogFragment.RemoveDeviceDialogListener,
        SmsCommandsInitDialogFragment.SmsCommandsInitDialogListener, SmsNotificationWarningDialogFragment.SmsNotificationWarningDialogListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int SELECT_CONTACT_INTENT = 3;
    private static final int SHARE_ROUTE_MESSAGE = 1;
    private static final int UPDATE_UI_MESSAGE = 2;
    private static final int MIN_RADIUS = 10; //meters
    private static final int MAX_RADIUS = 1000;

    public static final String USER_LOGIN = "userLogin";
    public static final String DEVICE_NAME = "deviceName";
    public static final String USER_DEVICES = "userDevices";
    public static final String NOTIFICATION_EMAIL = "email";
    public static final String NOTIFICATION_PHONE_NUMBER = "phoneNumber";
    public static final String NOTIFICATION_SOCIAL = "telegramId";

    public static final String EMAIL_REGISTRATION_STATUS = "emailStatus";
    public static final String SOCIAL_REGISTRATION_STATUS = "telegramStatus";

    public static final String ACTION_DEVICE_TRACKER = "net.gmsworld.devicelocator.ActionDeviceTracker";
    public static final String ACTION_DEVICE_TRACKER_NOTIFICATION = "net.gmsworld.devicelocator.ActionDeviceTrackerNotification";
    public static final String ACTION_DEVICE_MANAGER = "net.gmsworld.devicelocator.ActionDeviceManager";
    public static final String ACTION_SMS_MANAGER = "net.gmsworld.devicelocator.ActionSmsManager";

    private Boolean running = null;
    private int radius = RouteTrackingService.DEFAULT_RADIUS;
    private boolean motionDetectorRunning = false;
    private String phoneNumber = null, email = null, telegramId = null;
    private PreferencesUtils settings;
    private final Handler loadingHandler = new UIHandler(this);
    private boolean isTrackingServiceBound = false;
    private FirebaseAnalytics firebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        settings = new PreferencesUtils(this);

        setContentView(R.layout.activity_main);

        restoreSavedData();

        final Toolbar toolbar = findViewById(R.id.smsToolbar);
        setSupportActionBar(toolbar);

        //show card: sms, tracker, devices

        if (getIntent() != null) {
            showCard(getIntent().getAction());
        } else {
            showCard(ACTION_SMS_MANAGER);
        }

        //

        initApp();

        toggleBroadcastReceiver(); //set sms broadcast receiver

        if (motionDetectorRunning) {
            isTrackingServiceBound = RouteTrackingServiceUtils.startRouteTrackingService(this, null, radius, phoneNumber, email, telegramId, null, false, RouteTrackingService.Mode.Normal);
        }

        firebaseAnalytics = FirebaseAnalytics.getInstance(this);

        //show email or telegram registration dialog if still unverified
        if (StringUtils.equalsIgnoreCase(settings.getString(EMAIL_REGISTRATION_STATUS), "unverified") && StringUtils.isNotEmpty(email)) {
            NotificationActivationDialogFragment notificationActivationDialogFragment = NotificationActivationDialogFragment.newInstance(NotificationActivationDialogFragment.Mode.Email);
            notificationActivationDialogFragment.show(getFragmentManager(), NotificationActivationDialogFragment.TAG);
        } else if (StringUtils.equalsIgnoreCase(settings.getString(SOCIAL_REGISTRATION_STATUS), "unverified") && StringUtils.isNotEmpty(telegramId)) {
            NotificationActivationDialogFragment notificationActivationDialogFragment = NotificationActivationDialogFragment.newInstance(NotificationActivationDialogFragment.Mode.Telegram);
            notificationActivationDialogFragment.show(getFragmentManager(), NotificationActivationDialogFragment.TAG);
        }
    }

    @Override
    public void onNewIntent (Intent intent) {
        //show tracker view
        Log.d(TAG, "onNewIntent()");
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                Log.d(TAG, "getIntent().getAction(): " + action);
                showCard(action);
                supportInvalidateOptionsMenu();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");

        initLocationSMSCheckbox();
        updateUI();
        initDeviceList();

        //paste Telegram id
        boolean telegramPaste = settings.getBoolean("telegramPaste", false);
        if (telegramPaste) {
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("telegramPaste", false).apply();
            final TextView telegramInput = this.findViewById(R.id.telegramId);
            //paste telegram id from clipboard
            try {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip()) {
                    int clipboardItemCount = clipboard.getPrimaryClip().getItemCount();
                    for (int i = 0; i < clipboardItemCount; i++) {
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

        RouteTrackingServiceUtils.unbindRouteTrackingService(this, null, isTrackingServiceBound);

        registerEmail((TextView) findViewById(R.id.email), false);
        registerTelegram((TextView) findViewById(R.id.telegramId));
        registerPhoneNumber((TextView) findViewById(R.id.phoneNumber));
        registerUserLogin((Spinner) findViewById(R.id.userAccounts), true);
        registerDeviceName((TextView) findViewById(R.id.deviceName), true);

        //reset pin verification time
        PreferenceManager.getDefaultSharedPreferences(this).edit().putLong("pinVerificationMillis", System.currentTimeMillis()).apply();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SELECT_CONTACT_INTENT && resultCode == RESULT_OK) {
            phoneNumber = getNumber(data);
            if (StringUtils.isNotEmpty(phoneNumber)) {
                initPhoneNumberInput();
            } else {
                Toast.makeText(this, "Please select phone number from contacts list", Toast.LENGTH_SHORT).show();
            }
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
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                        .putBoolean("isTrackerShown", false)
                        .putBoolean("isDeviceManagerShown", false).apply();
                findViewById(R.id.smsSettings).setVisibility(View.VISIBLE);
                findViewById(R.id.trackerSettings).setVisibility(View.GONE);
                findViewById(R.id.ll_sms_focus).requestFocus();
                supportInvalidateOptionsMenu();
                return true;
            case R.id.tracker:
                Log.d(TAG, "Show tracker settings");
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                        .putBoolean("isTrackerShown", true)
                        .putBoolean("isDeviceManagerShown", false).apply();
                findViewById(R.id.trackerSettings).setVisibility(View.VISIBLE);
                findViewById(R.id.smsSettings).setVisibility(View.GONE);
                findViewById(R.id.ll_tracker_focus).requestFocus();
                supportInvalidateOptionsMenu();
                return true;
            case R.id.devices:
                Log.d(TAG, "Show Device Manager settings");
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                        .putBoolean("isTrackerShown", false)
                        .putBoolean("isDeviceManagerShown", true).apply();
                findViewById(R.id.deviceSettings).setVisibility(View.VISIBLE);
                findViewById(R.id.smsSettings).setVisibility(View.GONE);
                findViewById(R.id.trackerSettings).setVisibility(View.GONE);
                findViewById(R.id.ll_device_focus).requestFocus();
                supportInvalidateOptionsMenu();
                initUserLoginInput(true, false);
                return true;
            case R.id.permissions:
                startActivity(new Intent(this, PermissionsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        //Log.d(TAG, "onPrepareOptionsMenu()");
        //menu.findItem(R.id.camera).setVisible(settings.getBoolean("loginTracker", false));

        final boolean isTrackerShown = settings.getBoolean("isTrackerShown", false);
        final boolean isDeviceTrackerShown = settings.getBoolean("isDeviceManagerShown", false);

        if (isTrackerShown) {
            menu.findItem(R.id.sms).setVisible(true);
            menu.findItem(R.id.devices).setVisible(true);
            menu.findItem(R.id.tracker).setVisible(false);
        } else if (isDeviceTrackerShown) {
            menu.findItem(R.id.tracker).setVisible(true);
            menu.findItem(R.id.sms).setVisible(true);
            menu.findItem(R.id.devices).setVisible(false);
        } else {
            menu.findItem(R.id.tracker).setVisible(true);
            menu.findItem(R.id.devices).setVisible(true);
            menu.findItem(R.id.sms).setVisible(false);
        }

        return true;
    }

    private void showCard(String action) {
        boolean isTrackerShown = settings.getBoolean("isTrackerShown", false);
        boolean isDeviceManagerShown = settings.getBoolean("isDeviceManagerShown", false);

        if (action != null) {
            if (action.equals(ACTION_DEVICE_TRACKER)) {
                isTrackerShown = true;
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                            .putBoolean("isTrackerShown", true)
                            .putBoolean("isDeviceManagerShown", false).apply();
            } else if (action.equals(ACTION_DEVICE_TRACKER_NOTIFICATION)) {
                isTrackerShown = true;
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                            .putBoolean("isTrackerShown", true)
                            .putBoolean("isDeviceManagerShown", false).apply();
                findViewById(R.id.email).requestFocus();
            } else if (action.equals(ACTION_DEVICE_MANAGER)) {
                isDeviceManagerShown = true;
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                            .putBoolean("isTrackerShown", false)
                            .putBoolean("isDeviceManagerShown", true).apply();
            } else if (action.equals(ACTION_SMS_MANAGER)) {
                isTrackerShown = false;
                isDeviceManagerShown = false;
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                            .putBoolean("isTrackerShown", false)
                            .putBoolean("isDeviceManagerShown", false).apply();
            }
        }

        if (isTrackerShown) {
            findViewById(R.id.trackerSettings).setVisibility(View.VISIBLE);
            findViewById(R.id.smsSettings).setVisibility(View.GONE);
            findViewById(R.id.deviceSettings).setVisibility(View.GONE);
        } else if (isDeviceManagerShown) {
            findViewById(R.id.deviceSettings).setVisibility(View.VISIBLE);
            findViewById(R.id.trackerSettings).setVisibility(View.GONE);
            findViewById(R.id.smsSettings).setVisibility(View.GONE);
        } else {
            findViewById(R.id.smsSettings).setVisibility(View.VISIBLE);
            findViewById(R.id.trackerSettings).setVisibility(View.GONE);
            findViewById(R.id.deviceSettings).setVisibility(View.GONE);
        }
    }

    private void showRemoveDeviceDialogFragment(final Device device) {
        RemoveDeviceDialogFragment removeDeviceDialogFragment = RemoveDeviceDialogFragment.newInstance(this, device);
        removeDeviceDialogFragment.show(getFragmentManager(), RemoveDeviceDialogFragment.TAG);
    }

    private void initLocationSMSCheckbox() {
        ((Switch) findViewById(R.id.settings_detected_sms)).setChecked(settings.getBoolean(SmsSenderService.SEND_ACKNOWLEDGE_MESSAGE, true));
        ((Switch) findViewById(R.id.settings_gps_sms)).setChecked(settings.getBoolean(SmsSenderService.SEND_LOCATION_MESSAGE, false));
        ((Switch) findViewById(R.id.settings_google_sms)).setChecked(settings.getBoolean(SmsSenderService.SEND_MAP_LINK_MESSAGE, true));
        ((Switch) findViewById(R.id.settings_verify_pin)).setChecked(settings.getBoolean("settings_verify_pin", false));
        ((Switch) findViewById(R.id.settings_sms_without_pin)).setChecked(settings.getBoolean("settings_sms_without_pin", true));
        //check if READ_CONTACTS permission is set
        if (settings.getBoolean("settings_sms_contacts", false) && !Permissions.haveReadContactsPermission(this)) {
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("settings_sms_contacts", false).apply();
        }
    }

    public void onLocationSMSCheckboxClicked(View view) {
        boolean checked = ((Switch) view).isChecked();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();

        switch (view.getId()) {
            case R.id.settings_detected_sms:
                editor.putBoolean(SmsSenderService.SEND_ACKNOWLEDGE_MESSAGE, checked);
                break;
            case R.id.settings_gps_sms:
                editor.putBoolean(SmsSenderService.SEND_LOCATION_MESSAGE, checked);
                break;
            case R.id.settings_google_sms:
                editor.putBoolean(SmsSenderService.SEND_MAP_LINK_MESSAGE, checked);
                break;
            case R.id.settings_verify_pin:
                editor.putBoolean("settings_verify_pin", checked);
                if (checked && StringUtils.isEmpty(telegramId) && StringUtils.isEmpty(email) && StringUtils.isNotEmpty(phoneNumber)) {
                    Toast.makeText(this, "Please remember your Security PIN and set Notification settings in order to be able to recover forgotten Security PIN.", Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.settings_sms_without_pin:
                editor.putBoolean("settings_sms_without_pin", checked);
                if (!checked) {
                    Toast.makeText(this, "Be careful. From now on Security PIN is not required when sending SMS command!", Toast.LENGTH_LONG).show();
                }
                break;
            default:
                break;
        }

        editor.apply();
    }

    private void clearFocus() {
        View current = getCurrentFocus();
        if (current != null) {
            current.clearFocus();
        }

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
        }
    }

    private void updateUI() {
        //check if sms permission is present
        if (running && !Permissions.haveSendSMSPermission(this)) {
            toggleRunning();
        }
        ((Switch) this.findViewById(R.id.dlSmsSwitch)).setChecked(running);

        ((Switch) this.findViewById(R.id.dlTrackerSwitch)).setChecked(motionDetectorRunning);

        if (Files.hasRoutePoints(AbstractLocationManager.ROUTE_FILE, MainActivity.this, 2)) {
            ViewCompat.setBackgroundTintList(this.findViewById(R.id.route_button), ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
        } else {
            ViewCompat.setBackgroundTintList(this.findViewById(R.id.route_button), ColorStateList.valueOf(getResources().getColor(R.color.lightGray)));
        }

        ViewCompat.setBackgroundTintList(this.findViewById(R.id.contact_button), ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
        ViewCompat.setBackgroundTintList(this.findViewById(R.id.telegram_button), ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
        ViewCompat.setBackgroundTintList(this.findViewById(R.id.ping_button), ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
    }

    public void toggleRunning() {
        //enable Firebase
        if (!this.running) {
            final String firebaseToken = settings.getString(DlFirebaseMessagingService.FIREBASE_TOKEN);
            final String pin = settings.getEncryptedString(PinActivity.DEVICE_PIN);
            if (StringUtils.isEmpty(firebaseToken) && StringUtils.isNotEmpty(pin)) {
                DlFirebaseMessagingService.sendRegistrationToServer(MainActivity.this, settings.getString(USER_LOGIN), settings.getString(DEVICE_NAME), true);
            } else if (StringUtils.isNotEmpty(firebaseToken)) {
                Log.d(TAG, "Firebase token already set");
            } else {
                Log.e(TAG, "Something is wrong here with either empty pin:" + StringUtils.isEmpty(pin) + " or with empty Firebase token:" + StringUtils.isEmpty(firebaseToken));
            }
        } else {
            PreferenceManager.getDefaultSharedPreferences(this).edit().remove(DlFirebaseMessagingService.FIREBASE_TOKEN).apply();
        }
        //

        if (!this.running && !Permissions.haveSendSMSPermission(MainActivity.this)) {
            Permissions.requestSendSMSPermission(MainActivity.this, Permissions.PERMISSIONS_REQUEST_SMS_CONTROL);
            return;
        }

        this.running = !this.running;
        saveData();
        updateUI();
        toggleBroadcastReceiver();

        Bundle bundle = new Bundle();
        bundle.putBoolean("running", this.running);
        firebaseAnalytics.logEvent("sms_control", bundle);

        //check if location settings are enabled
        if (running && !GmsSmartLocationManager.isLocationEnabled(this)) {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            Toast.makeText(getApplicationContext(), "Please enable location services in order to receive device location updates!", Toast.LENGTH_SHORT).show();
        }

        if (running) {
            SmsCommandsEnabledDialogFragment smsCommandsEnabledDialogFragment = new SmsCommandsEnabledDialogFragment();
            smsCommandsEnabledDialogFragment.show(getFragmentManager(), SmsCommandsEnabledDialogFragment.TAG);
        }
    }

    private void toggleMotionDetectorRunning() {
        if (StringUtils.isNotEmpty(phoneNumber)) {
            if (!this.motionDetectorRunning && !Permissions.haveSendSMSAndLocationPermission(MainActivity.this)) {
                Permissions.requestSendSMSAndLocationPermission(MainActivity.this, Permissions.PERMISSIONS_REQUEST_TRACKER_CONTROL);
                return;
            }
        } else {
            if (!this.motionDetectorRunning && !Permissions.haveLocationPermission(MainActivity.this)) {
                Permissions.requestLocationPermission(MainActivity.this, Permissions.PERMISSIONS_REQUEST_TRACKER_CONTROL);
                return;
            }
        }

        this.motionDetectorRunning = !this.motionDetectorRunning;

        Bundle bundle = new Bundle();
        bundle.putBoolean("running", this.motionDetectorRunning);
        firebaseAnalytics.logEvent("device_tracker", bundle);

        toogleLocationDetector();
    }

    private void toogleLocationDetector() {
        if (this.motionDetectorRunning) {
            launchMotionDetectorService();
            //check if location service is enabled
            if (!GmsSmartLocationManager.isLocationEnabled(this)) {
                Toast.makeText(getApplicationContext(), "Please enable location services in order to receive device location updates!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        } else {
            saveData();
            RouteTrackingServiceUtils.stopRouteTrackingService(this, null, isTrackingServiceBound, false, null, null, null, null, null);
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
        initDeviceNameInput();
        initUserLoginInput(false, true);

        TextView commandLink = findViewById(R.id.docs_link);
        commandLink.setText(Html.fromHtml(getString(R.string.docsLink)));
        commandLink.setMovementMethod(LinkMovementMethod.getInstance());

        initRemoteControl();
    }


    //pin input --------------------------------------------------------------------------------------------------

    private void initTokenInput() {
        final TextView tokenInput = this.findViewById(R.id.token);
        final String pin = settings.getEncryptedString(PinActivity.DEVICE_PIN);
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
                    if (input.length() >= PinActivity.PIN_MIN_LENGTH) {
                        if (!StringUtils.equals(pin, input) && StringUtils.isNumeric(input)) {
                            settings.setEncryptedString(PinActivity.DEVICE_PIN, input);
                            saveData();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        tokenInput.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                    if (v.getText().length() < PinActivity.PIN_MIN_LENGTH) {
                        Toast.makeText(MainActivity.this, R.string.pin_length_error, Toast.LENGTH_LONG).show();
                        v.setText(pin);
                    }
                }
                return false;
            }
        });

        tokenInput.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                //Log.d(TAG, "Soft keyboard event " + keyCode);
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_BACK:
                            TextView tokenInput = (TextView) v;
                            if (tokenInput.getText().length() < PinActivity.PIN_MIN_LENGTH) {
                                Toast.makeText(MainActivity.this, R.string.pin_length_error, Toast.LENGTH_LONG).show();
                                tokenInput.setText(pin);
                            }
                            break;
                        default:
                            break;
                    }
                }
                return false;
            }
        });
    }

    // --------------------------------------------------------------------------------------------------------------------------------------------

    private void initRadiusInput() {
        SeekBar radiusBar = findViewById(R.id.radiusBar);
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
                    RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, null, isTrackingServiceBound, radius, phoneNumber, email, telegramId, null);
                }
            }
        });

    }

    //user login input setup -------------------------------------------------------------

    private void initUserLoginInput(boolean requestPermission, boolean silent) {
        Log.d(TAG, "initUserLoginInput(" + requestPermission + ")");
        final Spinner userAccounts = this.findViewById(R.id.userAccounts);

        userAccounts.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN && userAccounts.getAdapter() == null) {
                    initUserLoginInput(true, false);
                }
                return false;
            }
        });

        List<String> accountNames = new ArrayList<>();
        accountNames.add("");

        //add notification email to the list only if verified
        String emailStatus = settings.getString(EMAIL_REGISTRATION_STATUS);
        if (!StringUtils.equalsIgnoreCase(emailStatus, "unverified") && StringUtils.isNotEmpty(email)) {
            accountNames.add(email);
        }

        if (Permissions.haveGetAccountsPermission(this)) {
            //Log.d(TAG, "GET_ACCOUNTS permission is set");
            Account[] dlAccounts = AccountManager.get(this).getAccountsByType(getString(R.string.account_type));
            for (Account a : dlAccounts) {
                accountNames.add(a.name);
            }
            Account[] allAccounts = AccountManager.get(this).getAccounts();
            for (Account a : allAccounts) {
                Log.d(TAG, "Found account " + a.name);
                if (Patterns.EMAIL_ADDRESS.matcher(a.name).matches() && !StringUtils.equalsIgnoreCase(a.name, email)) {
                    accountNames.add(a.name);
                }
            }

            int index = 0;
            if (accountNames.size() > 1) {
                String userLogin = settings.getString(USER_LOGIN);
                if (StringUtils.isNotEmpty(userLogin)) {
                    for (int i = 0; i < accountNames.size(); i++) {
                        if (StringUtils.equalsIgnoreCase(userLogin, accountNames.get(i))) {
                            index = i;
                            break;
                        }
                    }
                } else {
                    index = 1;
                }
            } else if (findViewById(R.id.deviceSettings).getVisibility() == View.VISIBLE) {
                //show dialog with info What to do if no account is created
                showLoginDialogFragment();
                if (settings.contains(USER_LOGIN)) {
                    PreferenceManager.getDefaultSharedPreferences(this).edit().remove(MainActivity.USER_DEVICES).remove(MainActivity.USER_LOGIN).apply();
                    onDeleteDevice(Messenger.getDeviceId(this, false), true);
                }
            }

            final CommandArrayAdapter accs = new CommandArrayAdapter(this, R.layout.command_row, accountNames);
            userAccounts.setAdapter(accs);

            if (index > 0) {
                userAccounts.setSelection(index);
            }

            userAccounts.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    registerUserLogin(userAccounts, false);
                }

                public void onNothingSelected(AdapterView<?> adapterView) {
                }
            });
        } else {
            //Log.d(TAG, "Device settings view is visible");
            if (settings.contains(USER_LOGIN) && settings.contains(USER_DEVICES)) {
                PreferenceManager.getDefaultSharedPreferences(this).edit().remove(MainActivity.USER_DEVICES).remove(MainActivity.USER_LOGIN).apply();
                onDeleteDevice(Messenger.getDeviceId(this, false), true);
            }
            if (findViewById(R.id.deviceSettings).getVisibility() == View.VISIBLE && !silent) {
                if (requestPermission) {
                    Toast.makeText(this, "Please grant this permission to list accounts registered on this device", Toast.LENGTH_LONG).show();
                    Permissions.requestGetAccountsPermission(this, Permissions.PERMISSIONS_REQUEST_GET_ACCOUNTS);
                } else {
                    showLoginDialogFragment();
                }
            }
        }
    }

    private synchronized void registerUserLogin(Spinner userLoginSpinner, boolean silent) {
        String newUserLogin = (String) userLoginSpinner.getSelectedItem();
        String userLogin = settings.getString(USER_LOGIN);
        if (!StringUtils.equals(userLogin, newUserLogin)) {
            Bundle bundle = new Bundle();
            bundle.putBoolean("running", StringUtils.isNotEmpty(newUserLogin));
            firebaseAnalytics.logEvent("device_manager", bundle);
            if (!DlFirebaseMessagingService.sendRegistrationToServer(this, newUserLogin, settings.getString(DEVICE_NAME), false)) {
                if (!silent) {
                    Toast.makeText(this, "Your device can't be registered at the moment!", Toast.LENGTH_LONG).show();
                }
            } else {
                if (!silent) {
                    Toast.makeText(this, "Synchronizing device...", Toast.LENGTH_LONG).show();
                    if (!Permissions.haveLocationPermission(this)) {
                        Permissions.requestLocationPermission(this, 0);
                    }
                }
            }
        }
    }

    //device name input setup ------------------------------------------------------------

    private void initDeviceNameInput() {
        final TextView deviceNameInput = this.findViewById(R.id.deviceName);
        String deviceName = settings.getString(DEVICE_NAME);
        if (StringUtils.isEmpty(deviceName)) {
            deviceName = Messenger.getDeviceName();
        }
        Log.d(TAG, "Device name: -" + deviceName + "-");
        deviceNameInput.setText(deviceName);

        deviceNameInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    registerDeviceName(deviceNameInput, false);
                }
            }
        });

        deviceNameInput.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                    registerDeviceName(v, false);
                }
                return false;
            }
        });

        deviceNameInput.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                //Log.d(TAG, "Soft keyboard event " + keyCode);
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_BACK:
                            registerDeviceName((TextView) v, false);
                            break;
                        default:
                            break;
                    }
                }
                return false;
            }
        });
    }

    private synchronized void registerDeviceName(TextView deviceNameInput, boolean silent) {
        String newDeviceName = deviceNameInput.getText().toString();
        String deviceName = settings.getString(DEVICE_NAME);
        if (!StringUtils.equals(deviceName, newDeviceName)) {
            String normalizedDeviceName = StringUtils.trimToEmpty(newDeviceName).replace(' ', '-');
            if (DlFirebaseMessagingService.sendRegistrationToServer(this, settings.getString(USER_LOGIN), normalizedDeviceName, false)) {
                if (!StringUtils.equals(newDeviceName, normalizedDeviceName)) {
                    EditText deviceNameEdit = findViewById(R.id.deviceName);
                    deviceNameEdit.setText(normalizedDeviceName);
                    PreferenceManager.getDefaultSharedPreferences(this).edit().putString(DEVICE_NAME, normalizedDeviceName).apply();
                }
                if (!silent) {
                    Toast.makeText(this, "Synchronizing device...", Toast.LENGTH_LONG).show();
                    if (!Permissions.haveLocationPermission(this)) {
                        Permissions.requestLocationPermission(this, 0);
                    }
                }
            } else {
                if (!silent) {
                    Toast.makeText(this, "Your device can't be registered at the moment!", Toast.LENGTH_LONG).show();
                }
           }
        }
    }

    //email input setup ------------------------------------------------------------------

    private void initEmailInput() {
        final TextView emailInput = this.findViewById(R.id.email);
        emailInput.setText(email);

        emailInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    registerEmail(emailInput, false);
                } else {
                    //paste email from clipboard
                    String currentText = emailInput.getText().toString();
                    if (currentText.isEmpty()) {
                        try {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard != null && clipboard.hasPrimaryClip()) {
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
        email = settings.getString(NOTIFICATION_EMAIL);
        if ((!StringUtils.equals(email, newEmailAddress) || retry) && ((StringUtils.isNotEmpty(newEmailAddress) && Patterns.EMAIL_ADDRESS.matcher(newEmailAddress).matches()) || StringUtils.isEmpty(newEmailAddress))) {
            if (Network.isNetworkAvailable(MainActivity.this)) {
                Log.d(TAG, "Setting new email address: " + newEmailAddress);
                email = newEmailAddress;
                saveData();
                //update route tracking service if running
                if (motionDetectorRunning) {
                    RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, null, isTrackingServiceBound, radius, phoneNumber, email, telegramId, null);
                }

                if (StringUtils.isNotEmpty(email)) {
                    Toast.makeText(MainActivity.this, "Email verification in progress...", Toast.LENGTH_SHORT).show();
                    net.gmsworld.devicelocator.utilities.Messenger.sendEmailRegistrationRequest(MainActivity.this, email, 1);
                } else {
                    Toast.makeText(MainActivity.this, "No email notifications will be sent...", Toast.LENGTH_LONG).show();
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
        final TextView telegramInput = this.findViewById(R.id.telegramId);
        telegramInput.setText(telegramId);

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
        telegramId = settings.getString(NOTIFICATION_SOCIAL);
        if (!StringUtils.equals(telegramId, newTelegramId) && (StringUtils.isEmpty(newTelegramId) || Messenger.isValidTelegramId(newTelegramId))) {
            if (Network.isNetworkAvailable(MainActivity.this)) {
                Log.d(TAG, "Setting new telegram chat id: " + newTelegramId);
                telegramId = newTelegramId;
                saveData();
                //update route tracking service if running
                if (motionDetectorRunning) {
                    RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, null, isTrackingServiceBound, radius, phoneNumber, email, telegramId, null);
                }

                if (StringUtils.isNotEmpty(telegramId)) {
                    Toast.makeText(MainActivity.this, "Telegram verification in progress...", Toast.LENGTH_LONG).show();
                    net.gmsworld.devicelocator.utilities.Messenger.sendTelegramRegistrationRequest(MainActivity.this, telegramId, 1);
                } else {
                    Toast.makeText(MainActivity.this, "No Telegram notifications will be sent...", Toast.LENGTH_LONG).show();
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
        final TextView phoneNumberInput = this.findViewById(R.id.phoneNumber);
        phoneNumberInput.setText(this.phoneNumber);

        phoneNumberInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    registerPhoneNumber((TextView) v);
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

    public synchronized void registerPhoneNumber(TextView phoneNumberInput) {
        String newPhoneNumber = phoneNumberInput.getText().toString();
        if (!StringUtils.equals(phoneNumber, newPhoneNumber) && ((StringUtils.isNotEmpty(newPhoneNumber) && Patterns.PHONE.matcher(newPhoneNumber).matches()) || StringUtils.isEmpty(newPhoneNumber))) {
            Log.d(TAG, "Setting new phone number: " + newPhoneNumber);
            phoneNumber = newPhoneNumber;
            saveData();
            if (motionDetectorRunning) {
                RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, null, isTrackingServiceBound, radius, phoneNumber, email, telegramId, null);
            }
            if (!Permissions.haveSendSMSPermission(this)) {
                Permissions.requestSendSMSAndLocationPermission(this, 0);
                Toast.makeText(this, R.string.send_sms_permission, Toast.LENGTH_SHORT).show();
            } else if (StringUtils.isNotEmpty(phoneNumber)) {
                SmsNotificationWarningDialogFragment smsWarningDialog = SmsNotificationWarningDialogFragment.newInstance(this);
                smsWarningDialog.show(getFragmentManager(), SmsNotificationWarningDialogFragment.TAG);
            } else {
                Toast.makeText(MainActivity.this, "No SMS notifications will be sent...", Toast.LENGTH_LONG).show();
            }
        } else if (!StringUtils.equals(phoneNumber, newPhoneNumber)) {
            Toast.makeText(getApplicationContext(), "Make sure to specify valid phone number!", Toast.LENGTH_SHORT).show();
            phoneNumberInput.setText("");
        }
    }

    //------------------------------------------------------------------------------------------------

    private void initShareRouteButton() {
        Button shareRouteButton = this.findViewById(R.id.route_button);

        shareRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Files.hasRoutePoints(AbstractLocationManager.ROUTE_FILE, MainActivity.this, 2)) {
                    GmsSmartLocationManager.getInstance().executeRouteUploadTask(MainActivity.this, false, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String result, int responseCode, String url) {
                            Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                            Message message = loadingHandler.obtainMessage(SHARE_ROUTE_MESSAGE, responseCode, 0);
                            message.sendToTarget();
                        }
                    });
                } else {
                    Toast.makeText(getApplicationContext(), "No route is saved yet!", Toast.LENGTH_SHORT).show();
                }
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

    private void initRemoteControl() {
        if (!running) {
            if (!PreferenceManager.getDefaultSharedPreferences(this).contains("smsDialog")) {
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("smsDialog", true).apply();
                SmsCommandsInitDialogFragment smsCommandsInitDialogFragment = SmsCommandsInitDialogFragment.newInstance(this);
                smsCommandsInitDialogFragment.show(getFragmentManager(), SmsCommandsInitDialogFragment.TAG);
            }
        }
    }

    private void launchMotionDetectorService() {
        saveData();
        updateUI();
        isTrackingServiceBound = RouteTrackingServiceUtils.startRouteTrackingService(this, null, radius, phoneNumber, email, telegramId, null, true, RouteTrackingService.Mode.Normal);
        Toast.makeText(getApplicationContext(), getString(R.string.motion_confirm, radius), Toast.LENGTH_LONG).show();
    }

    private void initRunningButton() {
        Switch title = this.findViewById(R.id.dlSmsSwitch);

        title.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.toggleRunning();
                MainActivity.this.clearFocus();
            }
        });
    }

    private void initMotionDetectorButton() {
        final Switch title = this.findViewById(R.id.dlTrackerSwitch);

        title.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (StringUtils.isEmpty(email) && StringUtils.isEmpty(phoneNumber) && StringUtils.isEmpty(telegramId)) {
                    Toast.makeText(getApplicationContext(), getString(R.string.motion_confirm_empty, radius), Toast.LENGTH_LONG).show();
                    title.setChecked(false);
                } else {
                    MainActivity.this.toggleMotionDetectorRunning();
                    MainActivity.this.clearFocus();
                }
            }
        });
    }

    private void initContactButton() {
        ImageButton contactButton = this.findViewById(R.id.contact_button);

        contactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!Permissions.haveReadContactsPermission(MainActivity.this)) {
                    Permissions.requestContactsPermission(MainActivity.this, 0);
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
        ImageButton telegramButton = this.findViewById(R.id.telegram_button);
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
                    PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().putBoolean("telegramPaste", true).apply();
                    Toast.makeText(MainActivity.this, "In order to get your Chat ID please select Device Locator bot now.", Toast.LENGTH_LONG).show();
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, appName + " not found on this device");
                    Toast.makeText(MainActivity.this, "This function requires installed Telegram Messenger on your device.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void initPingButton() {
        final Button pingButton = this.findViewById(R.id.ping_button);

        pingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (StringUtils.isNotEmpty(phoneNumber) || StringUtils.isNotEmpty(email) || StringUtils.isNotEmpty(telegramId)) {
                    if (Network.isNetworkAvailable(MainActivity.this)) {
                        Toast.makeText(MainActivity.this, R.string.please_wait, Toast.LENGTH_LONG).show();
                        registerPhoneNumber((TextView) findViewById(R.id.phoneNumber));
                        registerEmail((TextView) findViewById(R.id.email), false);
                        registerTelegram((TextView) findViewById(R.id.telegramId));

                        if (StringUtils.isNotEmpty(phoneNumber)) {
                            Intent newIntent = new Intent(MainActivity.this, SmsSenderService.class);
                            newIntent.putExtra("phoneNumber", phoneNumber);
                            newIntent.putExtra("command", Command.HELLO_COMMAND);
                            MainActivity.this.startService(newIntent);
                        }
                        if (StringUtils.isNotEmpty(email) || StringUtils.isNotEmpty(telegramId)) {
                            Intent newIntent = new Intent(MainActivity.this, SmsSenderService.class);
                            newIntent.putExtra("telegramId", telegramId);
                            newIntent.putExtra("email", email);
                            newIntent.putExtra("command", Command.HELLO_COMMAND);
                            MainActivity.this.startService(newIntent);
                        }
                    } else {
                        Toast.makeText(MainActivity.this, getString(R.string.no_network_error), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Please provide notification settings above.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    public void initDeviceList() {
        final ListView deviceList = findViewById(R.id.deviceList);
        final TextView deviceListEmpty = findViewById(R.id.deviceListEmpty);
        deviceList.setEmptyView(deviceListEmpty);
        deviceList.setAdapter(null);
        String userLogin = settings.getString(USER_LOGIN);
        Log.d(TAG, "initDeviceList(" + userLogin + ")");

        if (StringUtils.isNotEmpty(userLogin)) {
            //first load devices from cache
            Set<String> deviceSet = settings.getStringSet(USER_DEVICES, null);
            if (deviceSet != null && !deviceSet.isEmpty()) {
                ArrayList<Device> userDevices = new ArrayList<>();
                for (String device : deviceSet) {
                    Device d = Device.fromString(device);
                    if (d != null) {
                        userDevices.add(d);
                    }
                }
                Collections.sort(userDevices, new DeviceComparator());
                populateDeviceList(userDevices, deviceList);
            }
            //second load device list and set array adapter
            loadDeviceList(new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    if (responseCode == 200 && StringUtils.startsWith(results, "{")) {
                        JsonElement reply = new JsonParser().parse(results);
                        JsonArray devices = reply.getAsJsonObject().get("devices").getAsJsonArray();
                        boolean thisDeviceOnList = false;
                        if (devices.size() > 0) {
                            ArrayList<Device> userDevices = new ArrayList<>();
                            final String imei = Messenger.getDeviceId(MainActivity.this, false);
                            Iterator<JsonElement> iter = devices.iterator();
                            while (iter.hasNext()) {
                                JsonObject deviceObject = iter.next().getAsJsonObject();
                                if (StringUtils.isNotEmpty(deviceObject.get("token").getAsString())) {
                                    Device device = new Device();
                                    if (deviceObject.has("name")) {
                                        device.name = deviceObject.get("name").getAsString();
                                    }
                                    device.imei = deviceObject.get("imei").getAsString();
                                    device.creationDate = deviceObject.get("creationDate").getAsString();
                                    if (StringUtils.equals(device.imei, imei)) {
                                        thisDeviceOnList = true;
                                    }
                                    userDevices.add(device);
                                }
                            }
                            if (thisDeviceOnList) {
                                populateDeviceList(userDevices, deviceList);
                                Set<String> deviceSet = new HashSet<>();
                                for (Device device : userDevices) {
                                    deviceSet.add(device.toString());
                                }
                                PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().putStringSet(USER_DEVICES, deviceSet).apply();
                            } else {
                                deviceListEmpty.setText(R.string.devices_list_empty);
                            }
                        } else {
                            deviceListEmpty.setText(R.string.devices_list_empty);
                        }
                        if (!thisDeviceOnList) {
                            //this device has been removed from other device
                            PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().remove(USER_LOGIN).remove(USER_DEVICES).apply();
                            initUserLoginInput(true, false);
                        }
                    } else {
                        deviceListEmpty.setText(R.string.devices_list_loading_failed);
                    }
                }
            });
        } else {
            deviceListEmpty.setText(R.string.devices_list_empty);
        }
    }

    private void loadDeviceList(final Network.OnGetFinishListener onGetFinishListener) {
        if (Network.isNetworkAvailable(this)) {
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN);
            final Map<String, String> headers = new HashMap<>();
            headers.put("X-GMS-AppId", "2");
            headers.put("X-GMS-Scope", "dl");
            if (StringUtils.isNotEmpty(tokenStr)) {
                String userLogin = settings.getString(USER_LOGIN);
                if (StringUtils.isNotEmpty(userLogin)) {
                    headers.put("Authorization", "Bearer " + tokenStr);
                    String content = "username=" + userLogin + "&action=list";
                    Network.post(this, getString(R.string.deviceManagerUrl), content, null, headers, onGetFinishListener);
                } else {
                    Log.e(TAG, "User login is unset. No device list will be loaded");
                }
            } else {
                String queryString = "scope=dl&user=" + Messenger.getDeviceId(this, false);
                Network.get(this, getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            Messenger.getToken(MainActivity.this, results);
                            loadDeviceList(onGetFinishListener);
                        } else {
                            Log.d(TAG, "Failed to receive token: " + results);
                        }
                    }
                });
            }
        }
    }

    private void populateDeviceList(final ArrayList<Device> userDevices, final ListView deviceList) {
        final DeviceArrayAdapter adapter = new DeviceArrayAdapter(MainActivity.this, android.R.layout.simple_list_item_1, userDevices);
        Log.d(TAG, "Found " + userDevices.size() + " devices");
        deviceList.setAdapter(adapter);
        setListViewHeightBasedOnChildren(deviceList);
    }

    public void onDeleteDevice(final String imei, final boolean silent) {
        if (Network.isNetworkAvailable(this)) {
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN);
            String content = "imei=" + imei + "&action=delete";

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + tokenStr);

            Network.post(this, getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    if (responseCode == 200) {
                        if (! silent) {
                            Toast.makeText(MainActivity.this, "Device has been removed!", Toast.LENGTH_SHORT).show();
                        }
                        //current device has been removed
                        if (StringUtils.equals(Messenger.getDeviceId(MainActivity.this, false), imei)) {
                            PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().remove(USER_LOGIN).remove(USER_DEVICES).apply();
                            if (! silent) {
                                initUserLoginInput(true, false);
                            }
                        }
                        initDeviceList();
                    } else if (! silent) {
                        Toast.makeText(MainActivity.this, "Failed to remove device!", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } else {
            Toast.makeText(MainActivity.this, "No network available. Failed to remove device!", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveData() {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean("running", this.running)
                .putBoolean("motionDetectorRunning", this.motionDetectorRunning)
                .putInt("radius", this.radius)
                .putString(NOTIFICATION_PHONE_NUMBER, phoneNumber)
                .putString(NOTIFICATION_EMAIL, email)
                .putString(NOTIFICATION_SOCIAL, telegramId)
                .apply();
    }

    private void restoreSavedData() {
        this.running = settings.getBoolean("running", false);
        //this.keyword = settings.getString("keyword", "");
        String pin = settings.getEncryptedString(PinActivity.DEVICE_PIN);
        if (StringUtils.isEmpty(pin)) {
            pin = RandomStringUtils.random(PinActivity.PIN_MIN_LENGTH, false, true);
            settings.setEncryptedString(PinActivity.DEVICE_PIN, pin);
        }

        this.motionDetectorRunning = settings.getBoolean("motionDetectorRunning", false);
        this.radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
        if (this.radius > MAX_RADIUS) {
            this.radius = MAX_RADIUS;
        }
        this.phoneNumber = settings.getString(NOTIFICATION_PHONE_NUMBER);
        this.email = settings.getString(NOTIFICATION_EMAIL);
        this.telegramId = settings.getString(NOTIFICATION_SOCIAL);
        //testing use count
        int useCount = settings.getInt("useCount", 0);
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt("useCount", useCount + 1).apply();
    }

    private void showLoginDialogFragment() {
        LoginDialogFragment loginDialogFragment = new LoginDialogFragment();
        loginDialogFragment.show(getFragmentManager(), LoginDialogFragment.TAG);
    }

    private static void setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null) {
            // pre-condition
            return;
        }

        int totalHeight = 0;
        for (int i = 0; i < listAdapter.getCount(); i++) {
            View listItem = listAdapter.getView(i, null, listView);
            listItem.measure(0, 0);
            totalHeight += listItem.getMeasuredHeight();
        }

        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
        listView.setLayoutParams(params);
        listView.requestLayout();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case Permissions.PERMISSIONS_REQUEST_GET_ACCOUNTS:
                initUserLoginInput(false, true);
                break;
            case Permissions.PERMISSIONS_REQUEST_SMS_CONTROL:
                if (Permissions.haveSendSMSPermission(this)) {
                    toggleRunning();
                } else {
                    Toast.makeText(this, R.string.send_sms_permission, Toast.LENGTH_SHORT).show();
                }
                break;
            case Permissions.PERMISSIONS_REQUEST_TRACKER_CONTROL:
                if (Permissions.haveLocationPermission(this)) {
                    toggleMotionDetectorRunning();
                } else {
                    Toast.makeText(this, R.string.send_location_permission, Toast.LENGTH_SHORT).show();
                }
                break;
            case Permissions.PERMISSIONS_REQUEST_CALL:
                if (!Permissions.haveCallPhonePermission(this)) {
                    Toast.makeText(this, "Call command won't work without this permission!", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    private static class UIHandler extends Handler {

        private final WeakReference<MainActivity> mainActivity;

        UIHandler(MainActivity activity) {
            mainActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mainActivity.get();
            if (activity != null) {
                if (msg.what == SHARE_ROUTE_MESSAGE) {
                    int responseCode = msg.arg1;
                    if (responseCode == 200) {
                        String showRouteUrl = RouteTrackingServiceUtils.getRouteUrl(activity);
                        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            ClipData urlClip = ClipData.newPlainText("text", showRouteUrl);
                            clipboard.setPrimaryClip(urlClip);
                            Toast.makeText(activity, "Route has been uploaded to server and route map url has been saved to clipboard.", Toast.LENGTH_LONG).show();
                        }
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(showRouteUrl));
                        activity.startActivity(browserIntent);
                        final Intent newIntent = new Intent(activity, SmsSenderService.class);
                        if (StringUtils.isNotEmpty(activity.phoneNumber)) {
                            newIntent.putExtra("phoneNumber", activity.phoneNumber);
                        }
                        if (StringUtils.isNotEmpty(activity.telegramId)) {
                            newIntent.putExtra("telegramId", activity.telegramId);
                        }
                        if (StringUtils.isNotEmpty(activity.email)) {
                            newIntent.putExtra("email", activity.email);
                        }
                        newIntent.putExtra("command", Command.ROUTE_COMMAND);
                        newIntent.putExtra("size", 2); //we know only size > 1
                        activity.startService(newIntent);
                    } else {
                        Toast.makeText(activity, "Route upload failed. Please try again in a few moments", Toast.LENGTH_LONG).show();
                    }
                } else if (msg.what == UPDATE_UI_MESSAGE) {
                    activity.updateUI();
                }
            }
        }
    }

    private class DeviceArrayAdapter extends ArrayAdapter<Device> {

        private final ArrayList<Device> devices;

        DeviceArrayAdapter(Context context, int textViewResourceId, ArrayList<Device> devices) {
            super(context, textViewResourceId, devices);
            this.devices = devices;
        }

        @NonNull
        @Override
        public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
            ViewHolder viewHolder; // view lookup cache stored in tag
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) MainActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.device_row, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.deviceName = convertView.findViewById(R.id.deviceName);
                viewHolder.deviceDesc = convertView.findViewById(R.id.deviceDesc);
                viewHolder.deviceRemove = convertView.findViewById(R.id.deviceRemove);
                ViewCompat.setBackgroundTintList(viewHolder.deviceRemove, ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            String name = devices.get(position).name;
            if (StringUtils.isEmpty(name)) {
                name = devices.get(position).imei;
            }
            viewHolder.deviceName.setText(name);
            viewHolder.deviceDesc.setText(getString(R.string.last_edited_on, devices.get(position).creationDate.split("T")[0]));

            viewHolder.deviceName.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showCommandActivity(devices.get(position));
                }
            });

            viewHolder.deviceDesc.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showCommandActivity(devices.get(position));
                }
            });

            viewHolder.deviceRemove.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showRemoveDeviceDialogFragment(devices.get(position));
                }
            });

            return convertView;
        }

        private void showCommandActivity(Device device) {
            Intent intent = new Intent(MainActivity.this, CommandActivity.class);
            intent.putExtra("name", device.name);
            intent.putExtra("imei", device.imei);
            intent.putParcelableArrayListExtra("devices", devices);
            MainActivity.this.startActivity(intent);
        }

        private class ViewHolder {
            TextView deviceName;
            TextView deviceDesc;
            ImageButton deviceRemove;
        }
    }

    private class DeviceComparator implements Comparator<Device> {

        final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

        @Override
        public int compare(Device device, Device device2) {
            try {
                return formatter.parse(device2.creationDate).compareTo(formatter.parse(device.creationDate));
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                return 0;
            }
        }
    }
}
