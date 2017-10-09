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
import android.support.v4.view.TintableBackgroundView;
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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.androidhiddencamera.HiddenCameraUtils;

import net.gmsworld.devicelocator.BroadcastReceivers.DeviceAdminEventReceiver;
import net.gmsworld.devicelocator.BroadcastReceivers.SmsReceiver;
import net.gmsworld.devicelocator.Services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.Services.RouteTrackingService;
import net.gmsworld.devicelocator.Services.SmsSenderService;
import net.gmsworld.devicelocator.Utilities.AbstractLocationManager;
import net.gmsworld.devicelocator.Utilities.Files;
import net.gmsworld.devicelocator.Utilities.GmsSmartLocationManager;
import net.gmsworld.devicelocator.Utilities.Messenger;
import net.gmsworld.devicelocator.Utilities.Network;
import net.gmsworld.devicelocator.Utilities.Permissions;
import net.gmsworld.devicelocator.Utilities.RouteTrackingServiceUtils;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int SEND_LOCATION_INTENT = 1;
    //private static final int MOTION_DETECTOR_INTENT = 2;
    private static final int SELECT_CONTACT_INTENT = 3;

    private static final int ENABLE_ADMIN_INTENT = 12;

    private static final int ACTION_MANAGE_OVERLAY_INTENT = 13;

    private static final int SHARE_ROUTE_MESSAGE = 1;

    private static final int MAX_RADIUS = 10000; //meters

    private Boolean running = null;

    private int radius = RouteTrackingService.DEFAULT_RADIUS;
    private boolean motionDetectorRunning = false;
    private String phoneNumber = null, email = null, telegramId = null, token = null;
    private String newEmailAddress = null, newTelegramId = null, newPhoneNumber = null;

    private Handler loadingHandler;
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
        loadingHandler = new UIHandler();
        //mMessenger = new Messenger(loadingHandler);
        if (motionDetectorRunning) {
            isTrackingServiceBound = RouteTrackingServiceUtils.startRouteTrackingService(this, mConnection, radius, phoneNumber, email, telegramId, false);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        long pinVerificationMillis = PreferenceManager.getDefaultSharedPreferences(this).getLong("pinVerificationMillis", 0);
        boolean settingsVerifyPin = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("settings_verify_pin", false);
        //Log.d(TAG, "Checking if Security PIN should be verified...");
        if (StringUtils.isNotEmpty(token) && settingsVerifyPin && System.currentTimeMillis() - pinVerificationMillis > 10 * 60 * 1000) {
            //show pin dialog only if not shown in last 10 minutes
            showPinDialog();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        RouteTrackingServiceUtils.unbindRouteTrackingService(this, mConnection, isTrackingServiceBound);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();

        if (!StringUtils.equals(email, newEmailAddress) && ((StringUtils.isNotEmpty(newEmailAddress) && Patterns.EMAIL_ADDRESS.matcher(newEmailAddress).matches()) || (StringUtils.isEmpty(newEmailAddress) && newEmailAddress != null))) {
            Log.d(TAG, "New email has been set: " + newEmailAddress);
            if (Network.isNetworkAvailable(MainActivity.this)) {
                editor.putString("email", newEmailAddress);
                if (StringUtils.isNotEmpty(newEmailAddress)) {
                    net.gmsworld.devicelocator.Utilities.Messenger.sendEmailRegistrationRequest(MainActivity.this, newEmailAddress, 1);
                }
            } else {
                Toast.makeText(MainActivity.this, "No network available! Please connect to networking service and set email again.", Toast.LENGTH_LONG).show();
            }
        }

        if (!StringUtils.equals(phoneNumber, newPhoneNumber) && ((StringUtils.isNotEmpty(newPhoneNumber) && Patterns.PHONE.matcher(newPhoneNumber).matches()) || (StringUtils.isEmpty(newPhoneNumber) && newPhoneNumber != null))) {
            Log.d(TAG, "New phone number has been set: " + newPhoneNumber);
            editor.putString("phoneNumber", newPhoneNumber);
        }

        if (!StringUtils.equals(telegramId, newTelegramId) && ((NumberUtils.isCreatable(newTelegramId) && StringUtils.isNotEmpty(newTelegramId)) || (StringUtils.isEmpty(newTelegramId) && newTelegramId != null))) {
            Log.d(TAG, "New telegram id has been set: " + newTelegramId);
            if (Network.isNetworkAvailable(MainActivity.this)) {
                editor.putString("telegramId", newTelegramId);
                if (StringUtils.isNotEmpty(newTelegramId)) {
                    net.gmsworld.devicelocator.Utilities.Messenger.sendTelegramRegistrationRequest(MainActivity.this, newTelegramId, 1);
                }
            } else {
                Toast.makeText(MainActivity.this, "No network available! Please connect to networking service and set Telegram chat or channel id again.", Toast.LENGTH_LONG).show();
            }
        }

        editor.commit();
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
                getSupportActionBar().invalidateOptionsMenu();
                return true;
            case R.id.tracker:
                Log.d(TAG, "Show tracker settings");
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("isTrackerShown", true).commit();
                findViewById(R.id.trackerSettings).setVisibility(View.VISIBLE);
                findViewById(R.id.smsSettings).setVisibility(View.GONE);
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

    /*private void scrollTop() {
        final ScrollView scrollView = (ScrollView) this.findViewById(R.id.scrollview);
        scrollView.post(new Runnable() {
            public void run() {
                scrollView.scrollTo(0, 0);
            }
        });
    }*/

    private void initLocationSMSCheckbox() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        ((CheckBox) findViewById(R.id.settings_detected_sms)).setChecked(settings.getBoolean("settings_detected_sms", true));
        ((CheckBox) findViewById(R.id.settings_gps_sms)).setChecked(settings.getBoolean("settings_gps_sms", true));
        ((CheckBox) findViewById(R.id.settings_google_sms)).setChecked(settings.getBoolean("settings_google_sms", true));
        ((CheckBox) findViewById(R.id.settings_verify_pin)).setChecked(settings.getBoolean("settings_verify_pin", false));
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
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Please grant this permission to enable failed login notification service.");
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
        ((TintableBackgroundView) (Button)this.findViewById(R.id.running_button)).setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor((running) ? R.color.colorAccent : R.color.colorPrimary)));
        ((TintableBackgroundView) (Button)this.findViewById(R.id.send_button)).setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));

        ((Button) this.findViewById(R.id.motion_button)).setText((motionDetectorRunning) ? getString(R.string.stop) : getString(R.string.start));
        ((TintableBackgroundView) (Button)this.findViewById(R.id.motion_button)).setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor((motionDetectorRunning) ? R.color.colorAccent : R.color.colorPrimary)));

        ((TintableBackgroundView) (Button)this.findViewById(R.id.route_button)).setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
        ((TintableBackgroundView) (ImageButton)this.findViewById(R.id.contact_button)).setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
        ((TintableBackgroundView) (ImageButton)this.findViewById(R.id.telegram_button)).setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
    }

    private void toggleRunning() {
        //String currentKeyword = ((TextView) this.findViewById(R.id.keyword)).getText() + "";
        if (!this.running && !Permissions.haveSendSMSAndLocationPermission(MainActivity.this)) {
            Permissions.requestSendSMSAndLocationPermission(MainActivity.this);
            Toast.makeText(getApplicationContext(), R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!this.running && !Permissions.haveCallPhonePermission(MainActivity.this)) {
            Permissions.requestCallPhonePermission(MainActivity.this);
        }

        this.running = !this.running;
        saveData();
        updateUI();
        toggleBroadcastReceiver();

        if (running) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setPositiveButton("OK", null);
            builder.setNegativeButton("Commands",  new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("http://www.gms-world.net/dl"));
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
        int currentRadius = -1;
        try {
            currentRadius = Integer.parseInt(((TextView) this.findViewById(R.id.radius)).getText() + "");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        if ((currentRadius <= 0 || currentRadius > MAX_RADIUS) && !motionDetectorRunning) {
            //can't start application with no radius
            Toast.makeText(getApplicationContext(), "Please specify radius between 1 and " + MAX_RADIUS + " meters", Toast.LENGTH_SHORT).show();
            return;
        }

        if (StringUtils.isNotEmpty(phoneNumber)) {
            if (!this.motionDetectorRunning && !Permissions.haveSendSMSAndLocationPermission(MainActivity.this)) {
                Permissions.requestSendSMSAndLocationPermission(MainActivity.this);
                Toast.makeText(getApplicationContext(), R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
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
        } else {
            saveData();
            RouteTrackingServiceUtils.stopRouteTrackingService(this, mConnection, isTrackingServiceBound);
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
        //initGpsRadioGroup();
        initLocationSMSCheckbox();
        TextView commandLink = (TextView) findViewById(R.id.docsLink);
        commandLink.setMovementMethod(LinkMovementMethod.getInstance());
        commandLink.setText(Html.fromHtml(getString(R.string.docsLink)));
        initRemoteControl();
    }

    private void initTokenInput() {
        final TextView tokenInput = (TextView) this.findViewById(R.id.token);
        tokenInput.setText(token);

        tokenInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String input = charSequence.toString();
                try {
                    //token is empty or up to 8 digits string
                    token = input;
                    saveData();
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
        final TextView radiusInput = (TextView) this.findViewById(R.id.radius);
        radiusInput.setText(Integer.toString(this.radius));

        radiusInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String input = charSequence.toString();
                try {
                    radius = Integer.parseInt(input);
                    saveData();
                    //update route tracking service if running
                    if (motionDetectorRunning) {
                        RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, mConnection, isTrackingServiceBound, radius, phoneNumber, email, telegramId);
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

    //email input setup ------------------------------------------------------------------

    private void initEmailInput() {
        final TextView emailInput = (TextView) this.findViewById(R.id.email);
        emailInput.setText(email);

        emailInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                newEmailAddress = charSequence.toString();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        emailInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if(!hasFocus) {
                    newEmailAddress = emailInput.getText().toString();
                    if (!StringUtils.equals(email, newEmailAddress) && ((StringUtils.isNotEmpty(newEmailAddress) && Patterns.EMAIL_ADDRESS.matcher(newEmailAddress).matches()) || StringUtils.isEmpty(newEmailAddress))) {
                        if (Network.isNetworkAvailable(MainActivity.this)) {
                            Log.d(TAG, "Setting new email address: " + newEmailAddress);
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
    }

    //telegram input setup -----------------------------------------------------------------

    private void initTelegramInput() {
        final TextView telegramInput = (TextView) this.findViewById(R.id.telegramId);
        telegramInput.setText(telegramId);

        telegramInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                newTelegramId = charSequence.toString();
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        telegramInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    newTelegramId = telegramInput.getText().toString();
                    if (!StringUtils.equals(telegramId, newTelegramId) && ((NumberUtils.isCreatable(newTelegramId) && StringUtils.isNotEmpty(newTelegramId)) || StringUtils.isEmpty(newTelegramId))) {
                        if (Network.isNetworkAvailable(MainActivity.this)) {
                            Log.d(TAG, "Setting new telegram chat id: " + newTelegramId);
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
                } else {
                    //paste telegramid from clipboard
                    String currentText = telegramInput.getText().toString();
                    if (currentText.isEmpty()) {
                        try {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard.hasPrimaryClip()) {
                                int clipboardItemCount = clipboard.getPrimaryClip().getItemCount();
                                for (int i=0;i<clipboardItemCount; i++) {
                                    ClipData.Item item = clipboard.getPrimaryClip().getItemAt(i);
                                    String pasteData = item.getText().toString();
                                    Log.d(TAG, "Clipboard text at " + i + ": " + pasteData);
                                    if (NumberUtils.isCreatable(pasteData) && StringUtils.isNotEmpty(pasteData)) {
                                        telegramInput.setText(pasteData);
                                        Toast.makeText(getApplicationContext(), "Pasted Chat ID from clipboard!", Toast.LENGTH_SHORT).show();
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
    }

    // phone number input setup ---------------------------------------------------------------

    private void initPhoneNumberInput() {
        final TextView phoneNumberInput = (TextView) this.findViewById(R.id.phoneNumber);
        phoneNumberInput.setText(this.phoneNumber);

        phoneNumberInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                newPhoneNumber = charSequence.toString();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        phoneNumberInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    newPhoneNumber = phoneNumberInput.getText().toString();
                    if (!StringUtils.equals(phoneNumber, newPhoneNumber) && ((StringUtils.isNotEmpty(newPhoneNumber) && Patterns.PHONE.matcher(newPhoneNumber).matches()) || StringUtils.isEmpty(newPhoneNumber))) {
                        Log.d(TAG, "Setting new phone number: " + newPhoneNumber);
                        phoneNumber = newPhoneNumber;
                        saveData();
                        if (motionDetectorRunning) {
                            RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, mConnection, isTrackingServiceBound, radius, phoneNumber, email, telegramId);
                        }
                    } else if (!StringUtils.equals(phoneNumber, newPhoneNumber)) {
                        Toast.makeText(getApplicationContext(), "Make sure to specify valid phone number!", Toast.LENGTH_SHORT).show();
                        phoneNumberInput.setText("");
                    }
                }
            }
        });
    }

    //------------------------------------------------------------------------------------------------

    /*private void initGpsRadioGroup() {
        final RadioGroup gpsAccuracyGroup = (RadioGroup) this.findViewById(R.id.gpsAccuracyGroup);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        int gpsAccuracy = settings.getInt("gpsAccuracy", 1);

        if (gpsAccuracy == 1) {
            gpsAccuracyGroup.check(R.id.radio_gps_high);
        } else {
            gpsAccuracyGroup.check(R.id.radio_gps_low);
        }
    }*/

    private void initShareRouteButton() {
        Button shareRouteButton = (Button) this.findViewById(R.id.route_button);

        shareRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Files.isRouteTracked(AbstractLocationManager.ROUTE_FILE, MainActivity.this, 2)) {
                    final long now = System.currentTimeMillis();
                    final String title = "devicelocatorroute_" + Messenger.getDeviceId(MainActivity.this) + "_" + now;
                    GmsSmartLocationManager.getInstance().executeRouteUploadTask(MainActivity.this, title, null, now, false, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String result, int responseCode, String url) {
                            Log.d(TAG, "Received following response code: "+ responseCode + " from url " + url);
                            Message message = loadingHandler.obtainMessage(SHARE_ROUTE_MESSAGE, responseCode, 0, title);
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
                        Toast.makeText(getApplicationContext(), R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
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
            Toast.makeText(getApplicationContext(), R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
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
        isTrackingServiceBound = RouteTrackingServiceUtils.startRouteTrackingService(this, mConnection, radius, phoneNumber, email, telegramId, true);
        Toast.makeText(getApplicationContext(), "You'll receive notification when this device will move up " + radius + " meters", Toast.LENGTH_LONG).show();
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
                    Toast.makeText(getApplicationContext(), "In order to get your Chat ID please select Device Locator bot now.", Toast.LENGTH_LONG).show();
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, appName + " not found on this device");
                    Toast.makeText(getApplicationContext(), "This function requires installed Telegram Messenger on your device.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void saveData() {
        try {
            this.radius = Integer.parseInt(((TextView) this.findViewById(R.id.radius)).getText() + "");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("running", this.running);
        editor.putString("token", token);
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
        this.token = settings.getString("token", RandomStringUtils.random(4, false, true));

        this.motionDetectorRunning = settings.getBoolean("motionDetectorRunning", false);
        this.radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
        this.phoneNumber = settings.getString("phoneNumber", "");
        this.email = settings.getString("email", "");
        this.telegramId = settings.getString("telegramId", "");
    }

    protected void setupToolbar(int toolbarId) {
        final Toolbar toolbar = (Toolbar) findViewById(toolbarId);
        setSupportActionBar(toolbar);
    }

    /*public void onGpsRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.radio_gps_high:
                if (checked)
                    editor.putInt("gpsAccuracy", 1);
                    if (motionDetectorRunning) {
                        RouteTrackingServiceUtils.setGpsAccuracy(this, RouteTrackingService.COMMAND_GPS_HIGH);
                    }
                    break;
            case R.id.radio_gps_low:
                if (checked)
                    editor.putInt("gpsAccuracy", 0);
                    if (motionDetectorRunning) {
                        RouteTrackingServiceUtils.setGpsAccuracy(this, RouteTrackingService.COMMAND_GPS_BALANCED);
                    }
                    break;
        }

        editor.commit();
    }*/

    private void showPinDialog() {
        if (pinDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            LayoutInflater li = LayoutInflater.from(this);
            View pinView = li.inflate(R.layout.verify_pin_dialog, null);

            final EditText tokenInput = (EditText) pinView.findViewById(R.id.verify_pin_edit);

            tokenInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(token.length())});

            tokenInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    String input = charSequence.toString();
                    if (StringUtils.equals(input, token)) {
                        Log.d(TAG, "Security PIN verified!");
                        pinDialog.dismiss();
                        PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().putLong("pinVerificationMillis", System.currentTimeMillis()).commit();
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
                        newIntent.putExtra("command", SmsReceiver.PIN_COMMAND);
                        newIntent.putExtra("phoneNumber", phoneNumber);
                        Messenger.sendCommandMessage(MainActivity.this, newIntent);
                        if (StringUtils.isNotEmpty(email)) {
                            Messenger.sendEmail(MainActivity.this, email, "Your Security PIN is " + token, "Message from Device Locator", 1);
                        }
                        Toast.makeText(MainActivity.this, "Security PIN has been sent to notifiers", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "No notifier has been set. Unable to send Security PIN.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));

            builder.setView(pinView);

            builder.setCancelable(false);

            pinDialog = builder.create();
        }

        //show keyboard
        pinDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        pinDialog.show();
    }

    //----------------------------- route tracking service -----------------------------------

    private ServiceConnection mConnection = null; /*new ServiceConnection() {

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

    private class UIHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == SHARE_ROUTE_MESSAGE) {
                int responseCode = msg.arg1;
                String title = (String)msg.obj;
                if (responseCode == 200) {
                    String showRouteUrl = MainActivity.this.getString(R.string.showRouteUrl) + "/" + title;
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData urlClip = ClipData.newPlainText("text", showRouteUrl);
                    clipboard.setPrimaryClip(urlClip);
                    Toast.makeText(MainActivity.this, "Route has been uploaded to server and route map url has been saved to clipboard.", Toast.LENGTH_LONG).show();
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(showRouteUrl));
                    startActivity(browserIntent);
                    String message = "Check out your route at " + showRouteUrl;
                    if (StringUtils.isNotEmpty(MainActivity.this.phoneNumber)) {
                        net.gmsworld.devicelocator.Utilities.Messenger.sendSMS(MainActivity.this, MainActivity.this.phoneNumber, message);
                    }
                    if (StringUtils.isNotEmpty(MainActivity.this.email)) {
                        net.gmsworld.devicelocator.Utilities.Messenger.sendEmail(MainActivity.this, MainActivity.this.email, message, "Message from Device Locator", 1);
                    }
                    if (StringUtils.isNotEmpty(MainActivity.this.telegramId)) {
                        net.gmsworld.devicelocator.Utilities.Messenger.sendTelegram(MainActivity.this, MainActivity.this.telegramId, message, 1);
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Route upload failed. Please try again in a few moments", Toast.LENGTH_LONG).show();
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
