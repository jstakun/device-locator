package net.gmsworld.devicelocator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Base64;
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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.broadcastreceivers.SmsReceiver;
import net.gmsworld.devicelocator.fragments.DownloadFullApplicationDialogFragment;
import net.gmsworld.devicelocator.fragments.EmailNotificationDialogFragment;
import net.gmsworld.devicelocator.fragments.FirstTimeUseDialogFragment;
import net.gmsworld.devicelocator.fragments.LoginDialogFragment;
import net.gmsworld.devicelocator.fragments.NewVersionDialogFragment;
import net.gmsworld.devicelocator.fragments.NotificationActivationDialogFragment;
import net.gmsworld.devicelocator.fragments.RemoveDeviceDialogFragment;
import net.gmsworld.devicelocator.fragments.SmsCommandsEnabledDialogFragment;
import net.gmsworld.devicelocator.fragments.SmsCommandsInitDialogFragment;
import net.gmsworld.devicelocator.fragments.SmsNotificationWarningDialogFragment;
import net.gmsworld.devicelocator.model.Device;
import net.gmsworld.devicelocator.services.CommandService;
import net.gmsworld.devicelocator.services.DlFirebaseMessagingService;
import net.gmsworld.devicelocator.services.RouteTrackingService;
import net.gmsworld.devicelocator.services.SmsSenderService;
import net.gmsworld.devicelocator.utilities.AppUtils;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.DevicesUtils;
import net.gmsworld.devicelocator.utilities.DistanceFormatter;
import net.gmsworld.devicelocator.utilities.Files;
import net.gmsworld.devicelocator.utilities.GmsSmartLocationManager;
import net.gmsworld.devicelocator.utilities.LinkMovementMethodFixed;
import net.gmsworld.devicelocator.utilities.LocationAlarmUtils;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.utilities.RouteTrackingServiceUtils;
import net.gmsworld.devicelocator.utilities.Toaster;
import net.gmsworld.devicelocator.views.CommandArrayAdapter;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.ocpsoft.prettytime.PrettyTime;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

public class MainActivity extends AppCompatActivity implements RemoveDeviceDialogFragment.RemoveDeviceDialogListener, NewVersionDialogFragment.NewVersionDialogListener,
        SmsCommandsInitDialogFragment.SmsCommandsInitDialogListener, SmsNotificationWarningDialogFragment.SmsNotificationWarningDialogListener,
        DownloadFullApplicationDialogFragment.DownloadFullApplicationDialogListener, EmailNotificationDialogFragment.EmailNotificationDialogListener, DevicesUtils.DeviceLoadListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int SELECT_CONTACT_INTENT = 3;
    private static final int SHARE_ROUTE_MESSAGE = 1;
    private static final int UPDATE_UI_MESSAGE = 2;

    public static final int MAX_RADIUS = 1000;
    public static final int MIN_RADIUS = 10; //meters

    public static final String USER_LOGIN = "userLogin";
    public static final String DEVICE_NAME = "deviceName";
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
    private final PrettyTime pt = new PrettyTime();
    private BroadcastReceiver onDownloadComplete = null;

    private Toaster toaster;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        settings = new PreferencesUtils(this);

        toaster = new Toaster(this);

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

        initRunningButton();
        initShareRouteButton();
        initRadiusInput();
        initAlarmInput();
        initMotionDetectorButton();
        initPhoneNumberInput();
        initEmailInput();
        initTelegramInput();
        initContactsButton();
        initTelegramButton();
        initTokenInput();
        initPingButton();
        initEmailButton();
        initDeviceNameInput();
        initUserLoginInput(false, true);

        TextView commandLink = findViewById(R.id.docs_link);
        commandLink.setText(Html.fromHtml(getString(R.string.docsLink)));
        commandLink.setMovementMethod(LinkMovementMethodFixed.getInstance());

        if (AppUtils.getInstance().isFullVersion()) {
            toggleSmsBroadcastReceiver();
        } else {
            findViewById(R.id.sms_notification).setVisibility(View.GONE);
        }

        if (motionDetectorRunning && Permissions.haveLocationPermission(this)) {
            isTrackingServiceBound = RouteTrackingServiceUtils.startRouteTrackingService(this, null, radius, null, false, RouteTrackingService.Mode.Normal);
        } else {
            isTrackingServiceBound = false;
        }

        //register firebase token if has changed and not set to server
        final String firebaseToken = settings.getString(DlFirebaseMessagingService.FIREBASE_TOKEN);
        final String newFirebaseToken = settings.getString(DlFirebaseMessagingService.NEW_FIREBASE_TOKEN);
        if (StringUtils.isEmpty(firebaseToken) && StringUtils.isNotEmpty(newFirebaseToken)) {
            String deviceName = settings.getString(DEVICE_NAME);
            if (StringUtils.isEmpty(deviceName)) {
                deviceName = Messenger.getDefaultDeviceName();
                settings.setString(DEVICE_NAME, deviceName);
            }
            Messenger.sendRegistrationToServer(MainActivity.this, settings.getString(USER_LOGIN), deviceName, true);
        }

        firebaseAnalytics = FirebaseAnalytics.getInstance(this);
        firebaseAnalytics.logEvent("main_activity", new Bundle());
    }

    @Override
    public void onNewIntent(Intent intent) {
        //show tracker view
        Log.d(TAG, "onNewIntent()");
        super.onNewIntent(intent);
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

        TextView deviceId = findViewById(R.id.device_id_text);
        deviceId.setText(Html.fromHtml(getString(R.string.deviceIdText, Messenger.getDeviceId(this, false))));
        deviceId.setMovementMethod(LinkMovementMethodFixed.getInstance());

        if (AppUtils.getInstance().isFullVersion()) {
            checkForNewVersion();
        }

        if (settings.contains(NotificationActivationDialogFragment.TELEGRAM_SECRET)) {
            //check for active Telegram registration
            final String telegramSecret = settings.getString(NotificationActivationDialogFragment.TELEGRAM_SECRET);
            //Log.d(TAG, "Found Telegram Secret "  + telegramSecret);
            if (StringUtils.equals(telegramSecret, "none")) {
                settings.remove(NotificationActivationDialogFragment.TELEGRAM_SECRET);
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
                                registerTelegram(telegramInput);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to paste text from clipboard", e);
                }
            } else {
                try {
                    final String deviceSecret = new String(Base64.decode(telegramSecret, Base64.URL_SAFE));
                    if (deviceSecret.startsWith("device:")) {
                        getTelegramChatId(telegramSecret);
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }

            }
        }
        if (settings.getBoolean("isTrackerShown", false)) {
            //show email or telegram registration dialog if still unverified
            if (StringUtils.equalsIgnoreCase(settings.getString(EMAIL_REGISTRATION_STATUS), "unverified") && StringUtils.isNotEmpty(email)) {
                NotificationActivationDialogFragment notificationActivationDialogFragment = NotificationActivationDialogFragment.newInstance(NotificationActivationDialogFragment.Mode.Email, toaster);
                notificationActivationDialogFragment.show(getFragmentManager(), NotificationActivationDialogFragment.TAG);
            }
            if (StringUtils.equalsIgnoreCase(settings.getString(SOCIAL_REGISTRATION_STATUS), "unverified") && StringUtils.isNotEmpty(telegramId)) {
                NotificationActivationDialogFragment notificationActivationDialogFragment = NotificationActivationDialogFragment.newInstance(NotificationActivationDialogFragment.Mode.Telegram, toaster);
                notificationActivationDialogFragment.show(getFragmentManager(), NotificationActivationDialogFragment.TAG);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        RouteTrackingServiceUtils.unbindRouteTrackingService(this, null, isTrackingServiceBound);

        registerEmail((TextView) findViewById(R.id.email), true, false);
        registerTelegram((TextView) findViewById(R.id.telegramId));
        registerPhoneNumber((TextView) findViewById(R.id.phoneNumber));
        registerUserLogin((Spinner) findViewById(R.id.userAccounts), true);
        registerDeviceName((TextView) findViewById(R.id.deviceName), true);

        //reset pin verification time
        settings.setLong("pinVerificationMillis", System.currentTimeMillis());

        if (onDownloadComplete != null) {
            try {
                unregisterReceiver(onDownloadComplete);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SELECT_CONTACT_INTENT && resultCode == RESULT_OK) {
            final String newPhoneNumber = getNumber(data);
            if (StringUtils.isNotEmpty(newPhoneNumber)) {
                final TextView phoneNumberInput = this.findViewById(R.id.phoneNumber);
                phoneNumberInput.setText(newPhoneNumber);
                registerPhoneNumber(phoneNumberInput);
            } else {
                toaster.showActivityToast("If you want to start receiving sms notifications please select phone number from contacts list");
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
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
                settings.setBoolean("isTrackerShown", false);
                settings.setBoolean("isDeviceManagerShown", false);
                findViewById(R.id.smsSettings).setVisibility(View.VISIBLE);
                findViewById(R.id.trackerSettings).setVisibility(View.GONE);
                findViewById(R.id.ll_sms_focus).requestFocus();
                supportInvalidateOptionsMenu();
                showFirstTimeUsageDialog(false, false);
                return true;
            case R.id.tracker:
                Log.d(TAG, "Show tracker settings");
                settings.setBoolean("isTrackerShown", true);
                settings.setBoolean("isDeviceManagerShown", false);
                findViewById(R.id.trackerSettings).setVisibility(View.VISIBLE);
                findViewById(R.id.smsSettings).setVisibility(View.GONE);
                findViewById(R.id.ll_tracker_focus).requestFocus();
                supportInvalidateOptionsMenu();
                showFirstTimeUsageDialog(true, false);
                //show email or telegram registration dialog if still unverified
                if (StringUtils.equalsIgnoreCase(settings.getString(EMAIL_REGISTRATION_STATUS), "unverified") && StringUtils.isNotEmpty(email)) {
                    NotificationActivationDialogFragment notificationActivationDialogFragment = NotificationActivationDialogFragment.newInstance(NotificationActivationDialogFragment.Mode.Email, toaster);
                    notificationActivationDialogFragment.show(getFragmentManager(), NotificationActivationDialogFragment.TAG);
                }
                if (StringUtils.equalsIgnoreCase(settings.getString(SOCIAL_REGISTRATION_STATUS), "unverified") && StringUtils.isNotEmpty(telegramId)) {
                    NotificationActivationDialogFragment notificationActivationDialogFragment = NotificationActivationDialogFragment.newInstance(NotificationActivationDialogFragment.Mode.Telegram, toaster);
                    notificationActivationDialogFragment.show(getFragmentManager(), NotificationActivationDialogFragment.TAG);
                }
                return true;
            case R.id.devices:
                Log.d(TAG, "Show Device Manager settings");
                settings.setBoolean("isTrackerShown", false);
                settings.setBoolean("isDeviceManagerShown", true);
                findViewById(R.id.deviceSettings).setVisibility(View.VISIBLE);
                findViewById(R.id.smsSettings).setVisibility(View.GONE);
                findViewById(R.id.trackerSettings).setVisibility(View.GONE);
                findViewById(R.id.ll_device_focus).requestFocus();
                supportInvalidateOptionsMenu();
                initUserLoginInput(true, false);
                showFirstTimeUsageDialog(false, true);
                return true;
            case R.id.permissions:
                startActivity(new Intent(this, PermissionsActivity.class));
                return true;
            case R.id.map:
                startActivity(new Intent(this, MapsActivity.class));
                return true;
            case R.id.commandLog:
                startActivity(new Intent(this, CommandListActivity.class));
                return true;
            //case R.id.privacyPolicy:
            //    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacyPolicyUrl))));
            //    return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
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

        if (Files.getAuditComands(this) == 0) {
            menu.findItem(R.id.commandLog).setVisible(false);
        }

        return true;
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
                    toaster.showActivityToast(R.string.send_sms_permission);
                }
                break;
            case Permissions.PERMISSIONS_REQUEST_TRACKER_CONTROL:
                if (Permissions.haveLocationPermission(this)) {
                    toggleMotionDetectorRunning();
                } else {
                    toaster.showActivityToast(R.string.send_location_permission);
                }
                break;
            case Permissions.PERMISSIONS_REQUEST_ALARM_CONTROL:
                if (Permissions.haveLocationPermission(this)) {
                    setAlarmChecked(true);
                    //send device location to admin channel
                    Bundle extras = new Bundle();
                    extras.putString("telegramId", getString(R.string.telegram_notification));
                    SmsSenderService.initService(this, false, false, true, null, null, null, null, extras);
                } else {
                    toaster.showActivityToast(R.string.send_location_permission);
                }
                break;
            case Permissions.PERMISSIONS_REQUEST_CALL:
                if (!Permissions.haveCallPhonePermission(this)) {
                    toaster.showActivityToast("Call command won't work without this permission!");
                }
                break;
            case Permissions.PERMISSIONS_REQUEST_CONTACTS:
                if (Permissions.haveReadContactsPermission(this)) {
                    selectContact();
                }
                break;
            case Permissions.PERMISSIONS_WRITE_STORAGE:
                if (Permissions.haveWriteStoragePermission(this)) {
                    downloadApk();
                }
                break;
            case Permissions.PERMISSIONS_REQUEST_GET_EMAIL:
                if (Permissions.haveGetAccountsPermission(this)) {
                    initEmailListDialog();
                }
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

    private void showCard(String action) {
        Log.d(TAG, "showCard(" + action + ")");
        boolean isTrackerShown = settings.getBoolean("isTrackerShown", false);
        boolean isDeviceManagerShown = settings.getBoolean("isDeviceManagerShown", false);

        if (action != null) {
            switch (action) {
                case ACTION_DEVICE_TRACKER:
                    isTrackerShown = true;
                    settings.setBoolean("isTrackerShown", true);
                    settings.setBoolean("isDeviceManagerShown", false);
                    break;
                case ACTION_DEVICE_TRACKER_NOTIFICATION:
                    isTrackerShown = true;
                    settings.setBoolean("isTrackerShown", true);
                    settings.setBoolean("isDeviceManagerShown", false);
                    findViewById(R.id.email).requestFocus();
                    break;
                case ACTION_DEVICE_MANAGER:
                    isTrackerShown = false;
                    isDeviceManagerShown = true;
                    settings.setBoolean("isTrackerShown", false);
                    settings.setBoolean("isDeviceManagerShown", true);
                    break;
                case ACTION_SMS_MANAGER:
                    isTrackerShown = false;
                    isDeviceManagerShown = false;
                    settings.setBoolean("isTrackerShown", false);
                    settings.setBoolean("isDeviceManagerShown", false);
                    break;
                default:
                    break;
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

        showFirstTimeUsageDialog(isTrackerShown, isDeviceManagerShown);
    }

    private void showRemoveDeviceDialogFragment(final Device device) {
        RemoveDeviceDialogFragment removeDeviceDialogFragment = RemoveDeviceDialogFragment.newInstance(this, device);
        removeDeviceDialogFragment.show(getFragmentManager(), RemoveDeviceDialogFragment.TAG);
    }

    private void showEmailNotificationDialogFragment(final String[] userLogins) {
        EmailNotificationDialogFragment emailNotificationDialogFragment = EmailNotificationDialogFragment.newInstance(this, userLogins);
        emailNotificationDialogFragment.show(getFragmentManager(), EmailNotificationDialogFragment.TAG);
    }

    private void initLocationSMSCheckbox() {
        ((Switch) findViewById(R.id.settings_detected_sms)).setChecked(settings.getBoolean(SmsSenderService.SEND_ACKNOWLEDGE_MESSAGE, true));
        ((Switch) findViewById(R.id.settings_gps_sms)).setChecked(settings.getBoolean(SmsSenderService.SEND_LOCATION_MESSAGE, false));
        ((Switch) findViewById(R.id.settings_google_sms)).setChecked(settings.getBoolean(SmsSenderService.SEND_MAP_LINK_MESSAGE, true));
        ((Switch) findViewById(R.id.settings_verify_pin)).setChecked(settings.getBoolean("settings_verify_pin", false));
        ((Switch) findViewById(R.id.settings_alarm)).setChecked(settings.getBoolean(LocationAlarmUtils.ALARM_SETTINGS, false));
    }

    public void onLocationSMSCheckboxClicked(View view) {
        boolean checked = ((Switch) view).isChecked();

        switch (view.getId()) {
            case R.id.settings_detected_sms:
                settings.setBoolean(SmsSenderService.SEND_ACKNOWLEDGE_MESSAGE, checked);
                break;
            case R.id.settings_gps_sms:
                settings.setBoolean(SmsSenderService.SEND_LOCATION_MESSAGE, checked);
                break;
            case R.id.settings_google_sms:
                settings.setBoolean(SmsSenderService.SEND_MAP_LINK_MESSAGE, checked);
                break;
            case R.id.settings_verify_pin:
                settings.setBoolean("settings_verify_pin", checked);
                if (checked && StringUtils.isEmpty(telegramId) && StringUtils.isEmpty(email) && StringUtils.isNotEmpty(phoneNumber)) {
                    toaster.showActivityToast("Please remember your Security PIN and configure Notification settings in order to be able to recover forgotten Security PIN.");
                } else if (checked) {
                    toaster.showActivityToast( "Please remember your Security PIN");
                }
                break;
            case R.id.settings_alarm:
                if (checked && !Permissions.haveLocationPermission(MainActivity.this)) {
                    Permissions.requestLocationPermission(this, Permissions.PERMISSIONS_REQUEST_ALARM_CONTROL);
                    return;
                } else {
                    setAlarmChecked(checked);
                }
                break;
            default:
                break;
        }
    }

    private void setAlarmChecked(boolean checked) {
        settings.setBoolean(LocationAlarmUtils.ALARM_SETTINGS, checked);
        if (checked) {
            LocationAlarmUtils.initWhenDown(this, true);
        } else {
            LocationAlarmUtils.cancel(this);
        }
        updateAlarmText();
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
        if (AppUtils.getInstance().isFullVersion()) {
            //check if sms permission is present
            if (running && !Permissions.haveSendSMSPermission(this)) {
                toggleRunning();
            }
            ((Switch) this.findViewById(R.id.dlSmsSwitch)).setChecked(running);
        } else {
            ((Switch) this.findViewById(R.id.dlSmsSwitch)).setChecked(false);
        }

        ((Switch) this.findViewById(R.id.dlTrackerSwitch)).setChecked(motionDetectorRunning);

        if (Files.getRoutePoints(MainActivity.this) >= 2) {
            ViewCompat.setBackgroundTintList(this.findViewById(R.id.route_button), ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
        } else {
            ViewCompat.setBackgroundTintList(this.findViewById(R.id.route_button), ColorStateList.valueOf(getResources().getColor(R.color.lightGray)));
        }

        ViewCompat.setBackgroundTintList(this.findViewById(R.id.contact_button), ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
        ViewCompat.setBackgroundTintList(this.findViewById(R.id.telegram_button), ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
        ViewCompat.setBackgroundTintList(this.findViewById(R.id.ping_button), ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));

        updateAlarmText();
    }

    public void toggleRunning() {
        //enable Firebase
        if (!this.running) {
            final String firebaseToken = settings.getString(DlFirebaseMessagingService.FIREBASE_TOKEN);
            final String pin = settings.getEncryptedString(PinActivity.DEVICE_PIN);
            if (StringUtils.isEmpty(firebaseToken) && StringUtils.isNotEmpty(pin)) {
                String deviceName = settings.getString(DEVICE_NAME);
                if (StringUtils.isEmpty(deviceName)) {
                    deviceName = Messenger.getDefaultDeviceName();
                    settings.setString(DEVICE_NAME, deviceName);
                }
                Messenger.sendRegistrationToServer(MainActivity.this, settings.getString(USER_LOGIN), deviceName, true);
            } else if (StringUtils.isNotEmpty(firebaseToken)) {
                Log.d(TAG, "Firebase token already set");
            } else {
                Log.e(TAG, "Something is wrong here with either empty pin:" + StringUtils.isEmpty(pin) + " or with empty Firebase token:" + StringUtils.isEmpty(firebaseToken));
            }
        } else {
            settings.remove(DlFirebaseMessagingService.FIREBASE_TOKEN);
        }
        //

        if (!this.running && !Permissions.haveSendSMSPermission(MainActivity.this)) {
            Permissions.requestSendSMSPermission(MainActivity.this, Permissions.PERMISSIONS_REQUEST_SMS_CONTROL);
            return;
        }

        this.running = !this.running;
        saveData();
        updateUI();
        toggleSmsBroadcastReceiver();

        Bundle bundle = new Bundle();
        bundle.putBoolean("running", this.running);
        firebaseAnalytics.logEvent("main_activity_sms_control", bundle);

        //check if location settings are enabled
        if (running && !GmsSmartLocationManager.isLocationEnabled(this)) {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            toaster.showActivityToast("Please enable location services in order to receive device location updates!");
        }

        if (running) {
            SmsCommandsEnabledDialogFragment.newInstance().show(getFragmentManager(), SmsCommandsEnabledDialogFragment.TAG);
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
                Permissions.requestLocationPermission(this, Permissions.PERMISSIONS_REQUEST_TRACKER_CONTROL);
                return;
            }
        }

        this.motionDetectorRunning = !this.motionDetectorRunning;

        Bundle bundle = new Bundle();
        bundle.putBoolean("running", this.motionDetectorRunning);
        firebaseAnalytics.logEvent("main_activity_device_tracker", bundle);

        toogleLocationDetector();
    }

    private void toogleLocationDetector() {
        if (this.motionDetectorRunning) {
            launchMotionDetectorService();
            //check if location service is enabled
            if (!GmsSmartLocationManager.isLocationEnabled(this)) {
                toaster.showActivityToast("Please enable location services in order to receive device location updates!");
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        } else {
            saveData();
            RouteTrackingServiceUtils.stopRouteTrackingService(this, null, isTrackingServiceBound, false, null, null);
            updateUI();
        }

    }

    private void toggleSmsBroadcastReceiver() {
        ComponentName receiver = new ComponentName(getApplicationContext(), SmsReceiver.class);
        PackageManager pm = getApplicationContext().getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                (running) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
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
                        toaster.showActivityToast(R.string.pin_length_error);
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
                                toaster.showActivityToast(R.string.pin_length_error);
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

    //radius seekbar settings ----------------------------------------------------------------------------------------------------------------------

    private void initRadiusInput() {
        SeekBar radiusBar = findViewById(R.id.radiusBar);
        radiusBar.setProgress(radius);

        final TextView motionRadius = findViewById(R.id.motion_radius);
        motionRadius.setText(getString(R.string.motion_radius, radius));

        radiusBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressChangedValue = 0;

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressChangedValue = progress;
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                seekBar.requestFocus();
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                if (progressChangedValue < MIN_RADIUS) {
                    progressChangedValue = MIN_RADIUS;
                }

                radius = progressChangedValue;
                motionRadius.setText(getString(R.string.motion_radius, radius));
                saveData();
                //update route tracking service if running
                if (motionDetectorRunning) {
                    RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, null, isTrackingServiceBound, radius, null);
                }
            }
        });
    }

    //alarm interval settings ------------------------------------------------------------

    private void initAlarmInput() {
        SeekBar alarmBar = findViewById(R.id.alarmBar);
        alarmBar.setProgress(settings.getInt(LocationAlarmUtils.ALARM_INTERVAL, 12));
        updateAlarmText();
        alarmBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressChangedValue = 12;

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressChangedValue = progress;
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                seekBar.requestFocus();
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                if (progressChangedValue < 1) {
                    progressChangedValue = 1;
                } else if (progressChangedValue > 24) {
                    progressChangedValue = 24;
                }

                settings.setInt(LocationAlarmUtils.ALARM_INTERVAL, progressChangedValue);

                LocationAlarmUtils.initWhenDown(MainActivity.this, true);
                updateAlarmText();
            }
        });
        //check if alarm is running
        if (settings.getBoolean(LocationAlarmUtils.ALARM_SETTINGS, false)){
            LocationAlarmUtils.initWhenDown(this, false);
        }
    }

    private void updateAlarmText() {
        final TextView alarmInterval = findViewById(R.id.alarm_interval);
        final int interval = settings.getInt(LocationAlarmUtils.ALARM_INTERVAL, 12);
        final long alarmMillis = settings.getLong(LocationAlarmUtils.ALARM_KEY,0L);
        String alarmText = getResources().getQuantityString(R.plurals.alarm_interval, interval, interval);
        if (alarmMillis > System.currentTimeMillis() && settings.getBoolean(LocationAlarmUtils.ALARM_SETTINGS, false)) {
            alarmText += getString(R.string.alarm_settings_suffix, pt.format(new Date(alarmMillis)));
        }
        alarmInterval.setText(alarmText);
    }

    //user login input setup -------------------------------------------------------------

    public void initUserLoginInput(boolean requestPermission, boolean silent) {
        Log.d(TAG, "initUserLoginInput(" + requestPermission + ")");
        final Spinner userAccounts = this.findViewById(R.id.userAccounts);

        userAccounts.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                Log.d(TAG, "setOnTouchListener");
                view.performClick();
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN && (userAccounts.getAdapter() == null || userAccounts.getAdapter().getCount() == 1)) {
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
                if (!accountNames.contains(a.name)) {
                    accountNames.add(a.name);
                }
            }
            Account[] allAccounts = AccountManager.get(this).getAccounts();
            for (Account a : allAccounts) {
                //Log.d(TAG, "Found account " + a.name);
                if (Patterns.EMAIL_ADDRESS.matcher(a.name).matches() && !StringUtils.equalsIgnoreCase(a.name, email)) {
                    if (!accountNames.contains(a.name)) {
                        accountNames.add(a.name);
                    }
                }
            }
            if (findViewById(R.id.deviceSettings).getVisibility() == View.VISIBLE && accountNames.size() == 1) {
                //show dialog with info What to do if no accounts are registered on the device
                LoginDialogFragment.newInstance().show(getFragmentManager(), LoginDialogFragment.TAG);
                if (settings.contains(USER_LOGIN)) {
                    settings.remove(DevicesUtils.USER_DEVICES, DevicesUtils.USER_DEVICES_TIMESTAMP, MainActivity.USER_LOGIN);
                    onDeleteDevice(Messenger.getDeviceId(this, false), true);
                }
            }
        } else {
            if (settings.contains(USER_LOGIN) && settings.contains(DevicesUtils.USER_DEVICES)) {
                settings.remove(DevicesUtils.USER_DEVICES, DevicesUtils.USER_DEVICES_TIMESTAMP, MainActivity.USER_LOGIN);
                onDeleteDevice(Messenger.getDeviceId(this, false), true);
            }
            if (findViewById(R.id.deviceSettings).getVisibility() == View.VISIBLE && !silent) {
                if (requestPermission) {
                    toaster.showActivityToast("Please grant this permission to list accounts registered on this device");
                    Permissions.requestGetAccountsPermission(this, Permissions.PERMISSIONS_REQUEST_GET_ACCOUNTS);
                } else {
                    LoginDialogFragment.newInstance().show(getFragmentManager(), LoginDialogFragment.TAG);
                }
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
    }

    private synchronized void registerUserLogin(Spinner userLoginSpinner, boolean silent) {
        String newUserLogin = (String) userLoginSpinner.getSelectedItem();
        String userLogin = settings.getString(USER_LOGIN);
        if (!StringUtils.equals(userLogin, newUserLogin)) {
            Bundle bundle = new Bundle();
            bundle.putBoolean("running", StringUtils.isNotEmpty(newUserLogin));
            firebaseAnalytics.logEvent("main_activity_device_manager", bundle);
            String deviceName = settings.getString(DEVICE_NAME);
            if (StringUtils.isEmpty(deviceName)) {
                deviceName = Messenger.getDefaultDeviceName();
                settings.setString(DEVICE_NAME, deviceName);
            }
            if (!Messenger.sendRegistrationToServer(this, newUserLogin, deviceName, false)) {
                if (!silent) {
                    toaster.showActivityToast("Your device can't be registered at the moment!");
                }
            } else {
                if (!silent) {
                    toaster.showActivityToast(R.string.devices_list_loading);
                    if (!Permissions.haveLocationPermission(this)) {
                        Permissions.requestLocationPermission(this, Permissions.PERMISSIONS_LOCATION);
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
            deviceName = Messenger.getDefaultDeviceName();
            settings.setString(DEVICE_NAME, deviceName);
        }
        //Log.d(TAG, "Device name: -" + deviceName + "-");
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
            String normalizedDeviceName = StringUtils.trimToEmpty(newDeviceName).replace(' ', '-').replace(',', '-');
            if (Messenger.sendRegistrationToServer(this, settings.getString(USER_LOGIN), normalizedDeviceName, false)) {
                if (!StringUtils.equals(newDeviceName, normalizedDeviceName)) {
                    EditText deviceNameEdit = findViewById(R.id.deviceName);
                    deviceNameEdit.setText(normalizedDeviceName);
                    settings.setString(DEVICE_NAME, normalizedDeviceName);
                }
                if (!silent) {
                    toaster.showActivityToast(R.string.devices_list_loading);
                    if (!Permissions.haveLocationPermission(this)) {
                        Permissions.requestLocationPermission(this, Permissions.PERMISSIONS_LOCATION);
                    }
                }
            } else {
                if (!silent) {
                    toaster.showActivityToast("Your device can't be registered at the moment!");
                }
            }
        }
    }

    //email input setup ------------------------------------------------------------------

    private void initEmailInput() {
        final TextView emailInput = findViewById(R.id.email);
        emailInput.setText(email);

        emailInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    String currentText = emailInput.getText().toString();
                    if (currentText.isEmpty()) {
                        //paste email from clipboard
                        try {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard != null && clipboard.hasPrimaryClip()) {
                                int clipboardItemCount = clipboard.getPrimaryClip().getItemCount();
                                for (int i = 0; i < clipboardItemCount; i++) {
                                    ClipData.Item item = clipboard.getPrimaryClip().getItemAt(i);
                                    String pasteData = item.getText().toString();
                                    if (!StringUtils.equals(pasteData, email) && Patterns.EMAIL_ADDRESS.matcher(pasteData).matches()) {
                                        emailInput.setText(pasteData);
                                        toaster.showActivityToast("Email address has been pasted from clipboard");
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage(), e);
                        }
                    }
                } else {
                    registerEmail(emailInput, true,false);
                }
            }
        });

        emailInput.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                    registerEmail(v, true,false);
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
                            registerEmail((TextView) v, true,false);
                            break;
                        default:
                            break;
                    }
                }
                return false;
            }
        });
    }

    public synchronized void registerEmail(TextView emailInput, boolean validate, boolean retry) {
        String newEmailAddress = emailInput.getText().toString();
        email = settings.getString(NOTIFICATION_EMAIL);
        if ((!StringUtils.equals(email, newEmailAddress) || retry) && ((StringUtils.isNotEmpty(newEmailAddress) && Patterns.EMAIL_ADDRESS.matcher(newEmailAddress).matches()) || StringUtils.isEmpty(newEmailAddress))) {
            if (Network.isNetworkAvailable(MainActivity.this)) {
                Log.d(TAG, "Setting new email address: " + newEmailAddress);
                email = newEmailAddress;
                saveData();
                //update route tracking service if running
                if (motionDetectorRunning) {
                    RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, null, isTrackingServiceBound, radius, null);
                }

                if (StringUtils.isNotEmpty(email)) {
                    toaster.showActivityToast( "Email verification in progress...");
                    Messenger.sendEmailRegistrationRequest(MainActivity.this, email, validate, 1);
                } else {
                    settings.remove(MainActivity.EMAIL_REGISTRATION_STATUS, NotificationActivationDialogFragment.EMAIL_SECRET);
                    toaster.showActivityToast("No email notifications will be sent...");
                }
            } else {
                toaster.showActivityToast(R.string.no_network_error);
                emailInput.setText("");
            }
        } else if (!StringUtils.equals(email, newEmailAddress)) {
            toaster.showActivityToast(R.string.email_invalid_error);
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
                if (StringUtils.startsWith(newTelegramId, "100")) {
                    telegramId = "-" + newTelegramId;
                } else {
                    telegramId = newTelegramId;
                }
                saveData();
                //update route tracking service if running
                if (motionDetectorRunning) {
                    RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, null, isTrackingServiceBound, radius, null);
                }

                if (StringUtils.isNotEmpty(telegramId)) {
                    toaster.showActivityToast( "Telegram verification in progress...");
                    Messenger.sendTelegramRegistrationRequest(MainActivity.this, telegramId, 1);
                } else {
                    settings.remove(MainActivity.SOCIAL_REGISTRATION_STATUS, NotificationActivationDialogFragment.TELEGRAM_SECRET);
                    toaster.showActivityToast("No Telegram notifications will be sent...");
                }
            } else {
                toaster.showActivityToast(R.string.no_network_error);
                telegramInput.setText("");
            }
        } else if (!StringUtils.equals(telegramId, newTelegramId)) {
            toaster.showActivityToast("Make sure to specify valid Telegram chat id!");
            telegramInput.setText("");
        }
    }

    // phone number input setup ---------------------------------------------------------------

    private void initPhoneNumberInput() {
        final TextView phoneNumberInput = this.findViewById(R.id.phoneNumber);
        phoneNumberInput.setText(phoneNumber);

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
        final String newPhoneNumber = phoneNumberInput.getText().toString();
        if (!StringUtils.equals(phoneNumber, newPhoneNumber) && ((StringUtils.isNotEmpty(newPhoneNumber) && Patterns.PHONE.matcher(newPhoneNumber).matches()) || StringUtils.isEmpty(newPhoneNumber))) {
            Log.d(TAG, "Setting new phone number: " + newPhoneNumber);
            phoneNumber = newPhoneNumber;
            saveData();
            if (motionDetectorRunning) {
                RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, null, isTrackingServiceBound, radius, null);
            }
            if (!Permissions.haveSendSMSPermission(this)) {
                Permissions.requestSendSMSAndLocationPermission(this, 0);
                toaster.showActivityToast(R.string.send_sms_permission);
            } else if (StringUtils.isNotEmpty(phoneNumber)) {
                try {
                    SmsNotificationWarningDialogFragment smsWarningDialog = SmsNotificationWarningDialogFragment.newInstance(this);
                    smsWarningDialog.show(getFragmentManager(), SmsNotificationWarningDialogFragment.TAG);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            } else {
                toaster.showActivityToast("No SMS notifications will be sent...");
            }
        } else if (!StringUtils.equals(phoneNumber, newPhoneNumber)) {
            toaster.showActivityToast("Please enter valid phone number!");
            phoneNumberInput.setText("");
        }
    }

    //------------------------------------------------------------------------------------------------

    private void initShareRouteButton() {
        Button shareRouteButton = this.findViewById(R.id.route_button);

        shareRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Files.getRoutePoints(MainActivity.this) >= 2) {
                    GmsSmartLocationManager.getInstance().executeRouteUploadTask(MainActivity.this, false, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String result, int responseCode, String url) {
                            Log.d(TAG, "Received following response code: " + responseCode + " from url " + url);
                            Message message = loadingHandler.obtainMessage(SHARE_ROUTE_MESSAGE, responseCode, 0);
                            message.sendToTarget();
                        }
                    });
                } else {
                    toaster.showActivityToast("No route is saved yet!");
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
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
            }
        }
        return number;
    }

    private void showFirstTimeUsageDialog(boolean isTrackerShown, boolean isDeviceManagerShown) {
        if (isTrackerShown) {
            //Device Tracker
            if (!settings.contains("DeviceTrackerFirstTimeUseDialog")) {
                settings.setBoolean("DeviceTrackerFirstTimeUseDialog", true);
                FirstTimeUseDialogFragment firstTimeUseDialogFragment = FirstTimeUseDialogFragment.newInstance(R.string.device_tracker_first_time_use, R.drawable.ic_location_on_gray);
                firstTimeUseDialogFragment.show(getFragmentManager(), "DeviceTrackerFirstTimeUseDialog");
            }
        } else if (isDeviceManagerShown) {
            //Device Manager
            if (!settings.contains("DeviceManagerFirstTimeUseDialog")) {
                settings.setBoolean("DeviceManagerFirstTimeUseDialog", true);
                FirstTimeUseDialogFragment firstTimeUseDialogFragment = FirstTimeUseDialogFragment.newInstance(R.string.device_manager_first_time_use, R.drawable.ic_devices_other_gray);
                firstTimeUseDialogFragment.show(getFragmentManager(), "DeviceManagerFirstTimeUseDialog");
            }
        } else if (!running) {
            //SMS Manager
            //Full version
            if (AppUtils.getInstance().isFullVersion()) {
                if (!settings.contains(SmsCommandsInitDialogFragment.TAG)) {
                    settings.setBoolean(SmsCommandsInitDialogFragment.TAG, true);
                    SmsCommandsInitDialogFragment smsCommandsInitDialogFragment = SmsCommandsInitDialogFragment.newInstance(this);
                    smsCommandsInitDialogFragment.show(getFragmentManager(), SmsCommandsInitDialogFragment.TAG);
                }
            } else {
                //GP version
                //DownloadFullApplicationDialogFragment downloadFullApplicationDialogFragment = DownloadFullApplicationDialogFragment.newInstance(this);
                //downloadFullApplicationDialogFragment.show(getFragmentManager(), DownloadFullApplicationDialogFragment.TAG);
                if (!settings.contains("SmsManagerFirstTimeUseDialog")) {
                    settings.setBoolean("SmsManagerFirstTimeUseDialog", true);
                    FirstTimeUseDialogFragment firstTimeUseDialogFragment = FirstTimeUseDialogFragment.newInstance(R.string.sms_manager_first_time_use_gp, R.drawable.ic_devices_other_gray);
                    firstTimeUseDialogFragment.show(getFragmentManager(), "SmsManagerFirstTimeUseDialog");
                }
            }
            //
        }
    }

    private void launchMotionDetectorService() {
        saveData();
        updateUI();
        isTrackingServiceBound = RouteTrackingServiceUtils.startRouteTrackingService(this, null, radius, null, true, RouteTrackingService.Mode.Normal);
        toaster.showActivityToast(getString(R.string.motion_confirm, radius));
    }

    private void initRunningButton() {
        final Switch title = findViewById(R.id.dlSmsSwitch);
        title.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (AppUtils.getInstance().isFullVersion()) {
                    MainActivity.this.toggleRunning();
                    MainActivity.this.clearFocus();
                } else {
                    title.setChecked(false);
                    DownloadFullApplicationDialogFragment downloadFullApplicationDialogFragment = DownloadFullApplicationDialogFragment.newInstance(MainActivity.this);
                    downloadFullApplicationDialogFragment.show(getFragmentManager(), DownloadFullApplicationDialogFragment.TAG);
                }
            }
        });
    }

    private void initMotionDetectorButton() {
        final Switch title = this.findViewById(R.id.dlTrackerSwitch);

        title.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (StringUtils.isEmpty(email) && StringUtils.isEmpty(phoneNumber) && StringUtils.isEmpty(telegramId)) {
                    toaster.showActivityToast(R.string.motion_confirm_empty, radius);
                    title.setChecked(false);
                    //findViewById(R.id.radiusBar).setEnabled(false);
                } else {
                    MainActivity.this.toggleMotionDetectorRunning();
                    //findViewById(R.id.radiusBar).setEnabled(true);
                    MainActivity.this.clearFocus();
                }
            }
        });
    }

    private void initContactsButton() {
        ImageButton contactButton = this.findViewById(R.id.contact_button);

        contactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectContact();
            }
        });
    }

    private void selectContact() {
        if (!Permissions.haveReadContactsPermission(MainActivity.this)) {
            Permissions.requestContactsPermission(MainActivity.this, Permissions.PERMISSIONS_REQUEST_CONTACTS);
        } else {
            try {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
                startActivityForResult(intent, SELECT_CONTACT_INTENT);
                MainActivity.this.clearFocus();
            } catch (ActivityNotFoundException e) {
                toaster.showActivityToast( "Failed to open Contacts list!");
            }
        }
    }

    private void initTelegramButton() {
        ImageButton telegramButton = this.findViewById(R.id.telegram_button);

        telegramButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Messenger.getMyTelegramId(MainActivity.this);
            }
        });
    }

    private void initPingButton() {
        final Button pingButton = this.findViewById(R.id.ping_button);

        pingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (StringUtils.isNotEmpty(phoneNumber) || StringUtils.isNotEmpty(email) || StringUtils.isNotEmpty(telegramId)) {
                    toaster.showActivityToast(R.string.please_wait);
                    registerPhoneNumber((TextView) findViewById(R.id.phoneNumber));
                    registerEmail((TextView) findViewById(R.id.email), true,false);
                    registerTelegram((TextView) findViewById(R.id.telegramId));

                    if (!Messenger.isEmailVerified(settings)) {
                        toaster.showActivityToast("Your email address is still unverified! No email notifications will be sent...");
                    } else {
                        Log.d(TAG, "Email is verified!");
                    }

                    if (!Messenger.isTelegramVerified(settings)) {
                        toaster.showActivityToast("Your Telegram char or channel is still unverified! No Telegram notifications will be sent...");
                    } else {
                        Log.d(TAG, "Telegram is verified!");
                    }

                    if (Network.isNetworkAvailable(MainActivity.this)) {
                        if (SmsSenderService.initService(MainActivity.this, true, true, true, null, Command.HELLO_COMMAND, null, null, null)) {
                            toaster.showActivityToast("Notification sent.");
                        } else {
                            toaster.showActivityToast(R.string.notifiers_error);
                        }
                    } else {
                        toaster.showActivityToast(getString(R.string.no_network_error));
                    }
                } else {
                    toaster.showActivityToast("Please provide Notification settings above.");
                }
            }
        });
    }

    private void initEmailButton() {
        final ImageButton emailButton = this.findViewById(R.id.email_button);

        emailButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Permissions.haveGetAccountsPermission(MainActivity.this)) {
                    initEmailListDialog();
                } else {
                    toaster.showActivityToast("Please grant this permission to list accounts registered on this device");
                    Permissions.requestGetAccountsPermission(MainActivity.this, Permissions.PERMISSIONS_REQUEST_GET_EMAIL);
                }
            }
        });
    }

    private void initEmailListDialog() {
        List<String> accountNames = new ArrayList<>();

        final String userLogin = settings.getString(USER_LOGIN);
        if (StringUtils.isNotEmpty(userLogin)) {
            accountNames.add(userLogin);
        }

        Account[] dlAccounts = AccountManager.get(this).getAccountsByType(getString(R.string.account_type));
        for (Account a : dlAccounts) {
            if (!accountNames.contains(a.name)) {
                accountNames.add(a.name);
            }
        }

        Account[] allAccounts = AccountManager.get(this).getAccounts();
        for (Account a : allAccounts) {
            //Log.d(TAG, "Found account " + a.name);
            if (Patterns.EMAIL_ADDRESS.matcher(a.name).matches() && !StringUtils.equalsIgnoreCase(a.name, email)) {
                if (!accountNames.contains(a.name)) {
                    accountNames.add(a.name);
                }
            }
        }

        if (!accountNames.isEmpty()) {
            showEmailNotificationDialogFragment(accountNames.toArray(new String[accountNames.size()]));
        } else {
            toaster.showActivityToast("No email addresses are registered on this device. Please enter new one!");
        }
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
            ArrayList<Device> userDevices = DevicesUtils.buildDeviceList(settings);
            if (!userDevices.isEmpty()) {
                Collections.sort(userDevices, new DeviceComparator()); //sort by device creation date
                onDeviceListLoaded(userDevices);
            }
            //second load device list and set array adapter
            DevicesUtils.loadDeviceList(this, settings, this);
        } else {
            deviceListEmpty.setText(R.string.devices_list_empty);
        }
    }

    @Override
    public void onDeviceListLoaded(ArrayList<Device> userDevices) {
        final ListView deviceList = findViewById(R.id.deviceList);
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
                        if (!silent) {
                            toaster.showActivityToast( R.string.device_removed);
                        }
                        //current device has been removed
                        if (StringUtils.equals(Messenger.getDeviceId(MainActivity.this, false), imei)) {
                            settings.remove(USER_LOGIN, DevicesUtils.USER_DEVICES, DevicesUtils.USER_DEVICES_TIMESTAMP);
                            if (!silent) {
                                initUserLoginInput(true, false);
                            }
                        }
                        initDeviceList();
                    } else if (!silent) {
                        toaster.showActivityToast(R.string.device_remove_failed);
                    }
                }
            });
        } else {
            toaster.showActivityToast("No network available. Failed to remove device!");
        }
    }

    private void getTelegramChatId(final String telegramSecret) {
        if (Network.isNetworkAvailable(this)) {
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN);
            final Map<String, String> headers = new HashMap<>();
            headers.put("X-GMS-AppId", "2");
            headers.put("X-GMS-Scope", "dl");
            if (StringUtils.isNotEmpty(tokenStr)) {
                final String queryString = "type=getTelegramChatId&telegramSecret=" + telegramSecret;
                Network.get(this, getString(R.string.telegramUrl) + "?" + queryString, headers, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (settings.contains(NotificationActivationDialogFragment.TELEGRAM_SECRET)) {
                            settings.remove(NotificationActivationDialogFragment.TELEGRAM_SECRET);
                            if (responseCode == 200 && results.startsWith("{")) {
                                String secret = null, status = null;
                                JsonElement reply = new JsonParser().parse(results);
                                if (reply != null) {
                                    JsonElement st = reply.getAsJsonObject().get("status");
                                    if (st != null) {
                                        status = st.getAsString();
                                        settings.setString(MainActivity.SOCIAL_REGISTRATION_STATUS, status);
                                    }
                                    JsonElement se = reply.getAsJsonObject().get("secret");
                                    if (se != null) {
                                        secret = se.getAsString();
                                        settings.setString(NotificationActivationDialogFragment.TELEGRAM_SECRET, secret);
                                    }
                                    JsonElement cid = reply.getAsJsonObject().get("chatId");
                                    if (cid != null) {
                                        final long chatId = cid.getAsLong();
                                        final TextView telegramInput = MainActivity.this.findViewById(R.id.telegramId);
                                        telegramId = Long.toString(chatId);
                                        telegramInput.setText(telegramId);
                                        saveData();
                                    }
                                }
                                if (StringUtils.equalsIgnoreCase(status, "registered") || StringUtils.equalsIgnoreCase(status, "verified")) {
                                    toaster.showActivityToast( "Your Telegram chat or channel is already verified.");
                                } else if (StringUtils.equalsIgnoreCase(status, "unverified")) {
                                    //show dialog to enter activation code sent to user
                                    if (StringUtils.isNotEmpty(secret)) {
                                        if (!MainActivity.this.isFinishing()) {
                                            try {
                                                NotificationActivationDialogFragment notificationActivationDialogFragment = NotificationActivationDialogFragment.newInstance(NotificationActivationDialogFragment.Mode.Telegram, toaster);
                                                notificationActivationDialogFragment.show(MainActivity.this.getFragmentManager(), NotificationActivationDialogFragment.TAG);
                                            } catch (Exception e) {
                                                Log.e(TAG, e.getMessage(), e);
                                            }
                                        }
                                    } else {
                                        Messenger.onFailedTelegramRegistration(MainActivity.this, "Oops! Something went wrong on our side. Please register again your Telegram chat or channel!", true);
                                    }
                                } else {
                                    Messenger.onFailedTelegramRegistration(MainActivity.this, "Oops! Something went wrong on our side. Please register again your Telegram chat or channel!", true);
                                }
                            } else if (responseCode >= 400) {
                                Messenger.onFailedTelegramRegistration(MainActivity.this, "Oops! Something went wrong on our side. Please register again your Telegram chat or channel!", true);
                            }
                        } else {
                            Log.d(TAG, "User has canceled Telegram registration!");
                        }
                    }
                });
            } else {
                String queryString = "scope=dl&user=" + Messenger.getDeviceId(this, false);
                Network.get(this, getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            Messenger.getToken(MainActivity.this, results);
                            getTelegramChatId(telegramSecret);
                        } else {
                            Log.d(TAG, "Failed to receive token: " + results);
                        }
                    }
                });
            }
        } else {
            toaster.showActivityToast(R.string.no_network_error);
        }
    }

    private void saveData() {
        settings.setBoolean("running", this.running);
        settings.setBoolean("motionDetectorRunning", this.motionDetectorRunning);
        settings.setInt("radius", this.radius);
        settings.setString(NOTIFICATION_PHONE_NUMBER, phoneNumber);
        settings.setString(NOTIFICATION_EMAIL, email);
        settings.setString(NOTIFICATION_SOCIAL, telegramId);
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
        settings.setInt("useCount", useCount + 1);
    }

    public static void setListViewHeightBasedOnChildren(ListView listView) {
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

    private void checkForNewVersion() {
        final String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN);
        if (StringUtils.isNotEmpty(tokenStr)) {
            final Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + tokenStr);
            Network.post(this, getString(R.string.notificationUrl), "type=v", null, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    if (responseCode == 200 && StringUtils.startsWith(results, "{")) {
                        JsonElement reply = new JsonParser().parse(results);
                        final int version = reply.getAsJsonObject().get("value").getAsInt();
                        final int versionCode = AppUtils.getInstance().getVersionCode(MainActivity.this);
                        if (version > versionCode) {
                            Log.d(TAG, "New version " + version + " is available");
                            try {
                                NewVersionDialogFragment newVersionDialogFragment = NewVersionDialogFragment.newInstance(MainActivity.this);
                                newVersionDialogFragment.show(getFragmentManager(), NewVersionDialogFragment.TAG);
                            } catch (Exception e) {
                                Log.e(TAG, e.getMessage(), e);
                            }
                        } else {
                            Log.d(TAG, "No new version is available");
                        }
                    }
                }
            });
        } else {
            Log.e(TAG, "Can't check for new version");
        }
    }

    public void downloadApk() {

        final String fileName = "device-locator.apk";
        final String destination = "file://" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + fileName;
        final Uri contentUri = Uri.parse(destination);

        //Delete update file if exists
        //final File file = new File(destination);
        //if (file.exists()) {
        //    file.delete();
        //}

        //get url of app on server
        final String downloadUrl = getString(R.string.downloadUrl);

        //set download manager
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request.setDescription(getString(R.string.app_name));
        request.setTitle(fileName);
        request.setMimeType("application/vnd.android.package-archive");
        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        //set destination
        request.setDestinationUri(contentUri);


        // get download service and enqueue file
        final DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        final long downloadId = manager.enqueue(request);

        toaster.showActivityToast(R.string.please_wait);

        //set BroadcastReceiver to install app when .apk is downloaded
        onDownloadComplete = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId == id) {
                    try {
                        Intent install = new Intent(Intent.ACTION_VIEW);
                        install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        install.setDataAndType(contentUri, manager.getMimeTypeForDownloadedFile(downloadId));
                        startActivity(install);
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                        try {
                            startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
                        } catch (Exception ex) {
                            Log.e(TAG, ex.getMessage(), ex);
                        }
                    }
                    toaster.showActivityToast(getString(R.string.app_name) + " Full version has been downloaded. Please uninstall Google Play version and install Full version.");
                    unregisterReceiver(this);
                }
            }
        };
        //register receiver for when .apk download is compete
        registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Override
    public void onDeviceLoadError(int messageId) {
        final TextView deviceListEmpty = findViewById(R.id.deviceListEmpty);
        deviceListEmpty.setText(messageId);
    }

    @Override
    public void onDeviceRemoved() {
        initUserLoginInput(true, false);
    }

    // -----------------------------------------------------------------------------------

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
                            activity.toaster.showActivityToast("Route has been uploaded to server and route map url has been saved to clipboard.");
                        }
                        String[] discs = StringUtils.split(showRouteUrl, "/");
                        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity) == ConnectionResult.SUCCESS) {
                            Log.d(TAG, "Route tokens /: " + showRouteUrl);
                            Intent gmsIntent = new Intent(activity, RouteActivity.class);
                            gmsIntent.putExtra("imei", discs[discs.length - 2]);
                            gmsIntent.putExtra("routeId", discs[discs.length - 1]);
                            gmsIntent.putExtra("now", "false");
                            activity.startActivity(gmsIntent);
                        } else {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(showRouteUrl));
                            activity.startActivity(browserIntent);
                        }

                        Bundle extras = new Bundle();
                        extras.putInt("size", 2); //we know only size > 1
                        SmsSenderService.initService(activity, true, true, true, null, Command.ROUTE_COMMAND, null, null, extras);

                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND);
                        sendIntent.putExtra(Intent.EXTRA_TEXT, activity.getString(R.string.route_share_text, discs[discs.length - 2], showRouteUrl));
                        sendIntent.putExtra(Intent.EXTRA_TITLE, activity.getString(R.string.message, discs[discs.length - 2]) + " - route map link");
                        sendIntent.setType("text/plain");
                        activity.startActivity(sendIntent);
                    } else if (responseCode == 400) {
                        activity.toaster.showActivityToast("Route upload failed due to invalid route file!");
                    } else {
                        activity.toaster.showActivityToast("Route upload failed. Please try again in a few moments!");
                    }
                } else if (msg.what == UPDATE_UI_MESSAGE) {
                    activity.updateUI();
                }
            }
        }
    }

    private class DeviceArrayAdapter extends ArrayAdapter<Device> {

        private final ArrayList<Device> devices;
        private final Location location;

        DeviceArrayAdapter(Context context, int textViewResourceId, ArrayList<Device> devices) {
            super(context, textViewResourceId, devices);
            this.devices = devices;
            this.location = SmartLocation.with(context).location(new LocationGooglePlayServicesWithFallbackProvider(context)).getLastLocation();
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
                viewHolder.deviceLocation = convertView.findViewById(R.id.deviceLocation);
                ViewCompat.setBackgroundTintList(viewHolder.deviceLocation, ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            Device d = devices.get(position);

            String name = d.name;
            if (StringUtils.isEmpty(name)) {
                name = d.imei;
            }
            viewHolder.deviceName.setText(name);

            String desc = getDeviceDesc(d);
            if (desc != null) {
                viewHolder.deviceDesc.setText(desc);
            }

            if (StringUtils.isEmpty(d.geo)) {
                viewHolder.deviceLocation.setVisibility(View.GONE);
            } else {
                viewHolder.deviceLocation.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        showDeviceLocation(devices.get(position));
                    }
                });
            }

            viewHolder.deviceName.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showCommandActivity(position);
                }
            });

            viewHolder.deviceDesc.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showCommandActivity(position);
                }
            });

            viewHolder.deviceRemove.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showRemoveDeviceDialogFragment(devices.get(position));
                }
            });

            viewHolder.deviceRemove.setContentDescription("Remove device " + position);

            return convertView;
        }

        private void showDeviceLocation(Device device) {
            if (StringUtils.isNotEmpty(device.geo)) {
                //send locate command to device
                if (settings.contains(CommandActivity.PIN_PREFIX + device.imei) || StringUtils.equals(device.imei, Messenger.getDeviceId(MainActivity.this, false))) {
                    //send locate command to deviceImei
                    String devicePin = settings.getEncryptedString(CommandActivity.PIN_PREFIX + device.imei);
                    Intent newIntent = new Intent(MainActivity.this, CommandService.class);
                    newIntent.putExtra("command", Command.SHARE_COMMAND);
                    newIntent.putExtra("imei", device.imei);
                    newIntent.putExtra(DEVICE_NAME, device.name);
                    newIntent.putExtra("pin", devicePin);
                    //newIntent.putExtra("args", "silent");
                    startService(newIntent);
                } else {
                    toaster.showActivityToast(R.string.pin_not_saved, device.name);
                }
                if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext()) == ConnectionResult.SUCCESS) {
                    Intent mapIntent = new Intent(MainActivity.this, MapsActivity.class);
                    mapIntent.putExtra("imei", device.imei);
                    startActivity(mapIntent);
                } else {
                    String[] tokens = StringUtils.split(device.geo, " ");
                    String name = "Your+Device";
                    if (StringUtils.isNotEmpty(device.name)) {
                        name = "Device+" + device.name;
                    }
                    Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + tokens[0] + "," + tokens[1] + "(" + name + ")");
                    Intent gmsIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                    gmsIntent.setPackage("com.google.android.apps.maps");
                    if (gmsIntent.resolveActivity(getContext().getPackageManager()) != null) {
                        getContext().startActivity(gmsIntent);
                    } else {
                        Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Messenger.MAPS_URL_PREFIX + tokens[0] + "," + tokens[1]));
                        getContext().startActivity(webIntent);
                    }
                }
            }
        }

        private void showCommandActivity(int selectedPosition) {
            Intent intent = new Intent(MainActivity.this, CommandActivity.class);
            intent.putExtra("index", selectedPosition);
            intent.putParcelableArrayListExtra("devices", devices);
            MainActivity.this.startActivity(intent);
        }

        private String getDeviceDesc(Device device) {
            String message = null;
            if (StringUtils.isNotEmpty(device.geo)) {
                String[] tokens = StringUtils.split(device.geo, " ");
                if (tokens.length >= 3) { //lat lng (acc) timestamp
                    long timestamp = Long.valueOf(tokens[tokens.length - 1]);
                    message = getString(R.string.last_seen) + " " + pt.format(new Date(timestamp));
                    if (location != null) {
                        Location deviceLocation = new Location("");
                        deviceLocation.setLatitude(Location.convert(tokens[0]));
                        deviceLocation.setLongitude(Location.convert(tokens[1]));
                        if (tokens.length > 3) {
                            deviceLocation.setAccuracy(Float.valueOf(tokens[2]));
                        }
                        int dist = (int) location.distanceTo(deviceLocation);
                        if (dist <= 0) {
                            dist = 1;
                        }
                        message += " " + DistanceFormatter.format(dist) + " away";
                    }
                }
            } else {
                try {
                    message = "Last edited " + pt.format(device.getDate());
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
            }
            return message;
        }

        private class ViewHolder {
            TextView deviceName;
            TextView deviceDesc;
            ImageButton deviceRemove;
            ImageButton deviceLocation;
        }
    }

    private static class DeviceComparator implements Comparator<Device> {

        @Override
        public int compare(Device device, Device device2) {
            try {
                //Log.d(TAG, "Comparing " + device.name + ": " + device.creationDate + " with " + device2.name + ": " + device2.creationDate);
                return device2.getDate().compareTo(device.getDate());
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                return 0;
            }
        }
    }
}
