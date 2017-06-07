package net.gmsworld.devicelocator;

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
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v4.view.TintableBackgroundView;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.gson.Gson;
import net.gmsworld.devicelocator.BroadcastReceivers.SmsReceiver;
import net.gmsworld.devicelocator.Fragments.LDPlaceAutocompleteFragment;
import net.gmsworld.devicelocator.Model.LDPlace;
import net.gmsworld.devicelocator.Services.RouteTrackingService;
import net.gmsworld.devicelocator.Services.SmsSenderService;
import net.gmsworld.devicelocator.Utilities.Files;
import net.gmsworld.devicelocator.Utilities.GmsLocationManager;
import net.gmsworld.devicelocator.Utilities.Network;
import net.gmsworld.devicelocator.Utilities.Permissions;
import net.gmsworld.devicelocator.Utilities.RouteTrackingServiceUtils;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final int SEND_LOCATION_INTENT = 1;
    //private static final int MOTION_DETECTOR_INTENT = 2;
    private static final int SELECT_CONTACT_INTENT = 3;

    private static final int SHARE_ROUTE_MESSAGE = 1;

    private static final int MAX_RADIUS = 10000; //meters

    private Boolean running = null;
    private String keyword = null;
    private LDPlace place = null;

    private int radius = RouteTrackingService.DEFAULT_RADIUS;
    private boolean motionDetectorRunning = false;
    private String phoneNumber = null;
    private String email = null;
    private String telegramId = null;
    private String token = null;

    private Handler loadingHandler;
    private Messenger mMessenger;
    private boolean isTrackingServiceBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Permissions.checkAndRequestPermissions(this); //check marshmallow permissions
        setContentView(R.layout.activity_main);
        restoreSavedData();
        setupToolbar();
        initApp();
        updateUI();
        toggleBroadcastReceiver(); //set broadcast receiver for sms
        scrollTop();
        loadingHandler = new UIHandler();
        mMessenger = new Messenger(loadingHandler);
        if (motionDetectorRunning) {
            isTrackingServiceBound = RouteTrackingServiceUtils.startRouteTrackingService(this, mConnection, radius, phoneNumber, email, telegramId, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivity onDestroy()");
        RouteTrackingServiceUtils.unbindRouteTrackingService(this, mConnection, isTrackingServiceBound);
    }

    private void scrollTop() {
        final ScrollView scrollView = (ScrollView) this.findViewById(R.id.scrollview);

        scrollView.post(new Runnable() {
            public void run() {
                scrollView.scrollTo(0, 0);
            }
        });
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
        ((Button) this.findViewById(R.id.running_button)).setText((running) ? getResources().getString(R.string.stop) : getResources().getString(R.string.start));
        ((TintableBackgroundView) (Button) this.findViewById(R.id.running_button)).setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor((running) ? R.color.colorAccent : R.color.colorPrimary)));
        ((TintableBackgroundView) (Button) this.findViewById(R.id.send_button)).setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));

        ((Button) this.findViewById(R.id.motion_button)).setText((motionDetectorRunning) ? getResources().getString(R.string.stop) : getResources().getString(R.string.start));
        ((TintableBackgroundView) (Button) this.findViewById(R.id.motion_button)).setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor((motionDetectorRunning) ? R.color.colorAccent : R.color.colorPrimary)));
    }

    private void toggleRunning() {
        String currentKeyword = ((TextView) this.findViewById(R.id.keyword)).getText() + "";
        if (!this.running && !Permissions.haveSendSMSAndLocationPermission(MainActivity.this)) {
            Permissions.requestSendSMSAndLocationPermission(MainActivity.this);
            Toast.makeText(getApplicationContext(), R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
            return;
        }

        this.running = !this.running;
        saveData();
        updateUI();
        toggleBroadcastReceiver();

        if (running) {
            Toast.makeText(getApplicationContext(), "From now you can send remote control commands to this device", Toast.LENGTH_LONG).show();
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

        if (!this.motionDetectorRunning && !Permissions.haveAllRequiredPermission(MainActivity.this)) {
            Permissions.requestAllRequiredPermission(MainActivity.this);
            Toast.makeText(getApplicationContext(), R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
            return;
        }

        this.motionDetectorRunning = !this.motionDetectorRunning;
        toogleLocationDetector();
    }

    private void toogleLocationDetector() {
        if (this.motionDetectorRunning) {
            //if (StringUtils.isEmpty(phoneNumber) && StringUtils.isEmpty(email) && StringUtils.isEmpty(telegramId)) {
            launchMotionDetectorService();
            //} else {
            //Intent intent = new Intent(Intent.ACTION_PICK);
            //intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
            //startActivityForResult(intent, MOTION_DETECTOR_INTENT);
            //}
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
        initDestinationPlace();
        initRunningButton();
        initSendLocationButton();
        initShareRouteButton();
        initKeywordInput();
        initRadiusInput();
        initMotionDetectorButton();
        initPhoneNumberInput();
        initEmailInput();
        initTelegramInput();
        initContactButton();
        initTokenInput();
        initGpsRadioGroup();
        TextView commandLink = (TextView) findViewById(R.id.docsLink);
        commandLink.setMovementMethod(LinkMovementMethod.getInstance());
        commandLink.setText(Html.fromHtml(getResources().getString(R.string.docsLink)));
        TextView telegramLink = (TextView) findViewById(R.id.telegramLink);
        telegramLink.setMovementMethod(LinkMovementMethod.getInstance());
        telegramLink.setText(Html.fromHtml(getResources().getString(R.string.telegramLink)));
    }

    private void initKeywordInput() {
        final TextView keywordInput = (TextView) this.findViewById(R.id.keyword);
        keywordInput.setText(this.keyword);

        keywordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (keywordInput.getText().length() == 0) {
                    MainActivity.this.stop();
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
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

    private void initEmailInput() {
        final TextView emailInput = (TextView) this.findViewById(R.id.email);
        emailInput.setText(email);

        /*emailInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });*/

        emailInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if(!hasFocus) {
                    String newEmailAddress = emailInput.getText().toString();
                    if ((StringUtils.isNotEmpty(newEmailAddress) && Patterns.EMAIL_ADDRESS.matcher(newEmailAddress).matches()) || StringUtils.isEmpty(newEmailAddress)) {
                        Log.d(TAG, "Setting new email address: " + newEmailAddress);
                        email = newEmailAddress;
                        saveData();
                        //update route tracking service if running
                        if (motionDetectorRunning) {
                            RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, mConnection, isTrackingServiceBound, radius, phoneNumber, email, telegramId);
                        }
                    } else if (StringUtils.isNotEmpty(newEmailAddress)) {
                        Toast.makeText(getApplicationContext(), "Make sure to specify valid email address!", Toast.LENGTH_SHORT).show();
                        emailInput.setText("");
                    }
                }
            }
        });
    }

    private void initTelegramInput() {
        final TextView telegramInput = (TextView) this.findViewById(R.id.telegramId);
        telegramInput.setText(telegramId);

        /*telegramInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });*/

        telegramInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    String newTelegramId = telegramInput.getText().toString();
                    if (StringUtils.isNumeric(newTelegramId)) {
                        Log.d(TAG, "Setting new telegram chat id: " + newTelegramId);
                        telegramId = newTelegramId;
                        saveData();
                        //update route tracking service if running
                        if (motionDetectorRunning) {
                            RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, mConnection, isTrackingServiceBound, radius, phoneNumber, email, telegramId);
                        }
                    } else if (StringUtils.isNotEmpty(newTelegramId)) {
                        Toast.makeText(getApplicationContext(), "Make sure to specify valid Telegram chat id!", Toast.LENGTH_SHORT).show();
                        telegramInput.setText("");
                    }
                }
            }
        });
    }

    private void initPhoneNumberInput() {
        final TextView phoneNumberInput = (TextView) this.findViewById(R.id.phoneNumber);
        phoneNumberInput.setText(this.phoneNumber);

        /*phoneNumberInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });*/

        phoneNumberInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    String newPhoneNumber = phoneNumberInput.getText().toString();
                    //if (StringUtils.isNotEmpty(newPhoneNumber)) {
                        Log.d(TAG, "Setting new phone number: " + newPhoneNumber);
                        phoneNumber = newPhoneNumber;
                        saveData();
                        if (motionDetectorRunning) {
                            RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, mConnection, isTrackingServiceBound, radius, phoneNumber, email, telegramId);
                        }
                    //} else {
                    //    Toast.makeText(getApplicationContext(), "Make sure to specify valid phone number!", Toast.LENGTH_SHORT).show();
                    //    phoneNumberInput.setText("");
                    //}
                }
            }
        });
    }

    private void initGpsRadioGroup() {
        final RadioGroup gpsAccuracyGroup = (RadioGroup) this.findViewById(R.id.gpsAccuracyGroup);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        int gpsAccuracy = settings.getInt("gpsAccuracy", 0);

        if (gpsAccuracy == 1) {
            gpsAccuracyGroup.check(R.id.radio_gps_high);
        } else {
            gpsAccuracyGroup.check(R.id.radio_gps_low);
        }

    }

    private void stop() {
        if (this.running) {
            this.toggleRunning();
        }
    }

    private void initShareRouteButton() {
        Button shareRouteButton = (Button) this.findViewById(R.id.route_button);

        shareRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Files.countLinesFromContextDir(GmsLocationManager.ROUTE_FILE, MainActivity.this) > 1) {
                    final String title = "devicelocatorroute_" + Network.getDeviceId(MainActivity.this) + "_" + System.currentTimeMillis();
                    int routeSize = GmsLocationManager.getInstance().uploadRouteToServer(MainActivity.this, title, "", -1, false, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String result, int responseCode, String url) {
                            Log.d(TAG, "Received following response code: "+ responseCode + " from url " + url);
                            Message message = loadingHandler.obtainMessage(SHARE_ROUTE_MESSAGE, responseCode, 0, title);
                            message.sendToTarget();
                        }
                    });
                    if (routeSize <= 1) {
                        Toast.makeText(getApplicationContext(), "No route is saved yet. Please make sure device location tracking is started and try again after some time.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "No route is saved yet. Please make sure device location tracking is started and try again after some time.", Toast.LENGTH_LONG).show();
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

    /* read the contact from the contact picker */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            phoneNumber = getNumber(data);
            initPhoneNumberInput();
            if (phoneNumber != null) {
                if (requestCode == SEND_LOCATION_INTENT) {
                    launchService();
                } //else if (requestCode == MOTION_DETECTOR_INTENT) {
                    //launchMotionDetectorService();
                //}
            } else {
                Toast.makeText(getApplicationContext(), "Please select phone number from contacts list", Toast.LENGTH_SHORT).show();
            }
        }
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
        builder.setMessage(this.getResources().getString(R.string.are_you_sure) + " " + phoneNumber + "?");
        AlertDialog dialog = builder.create();
        dialog.show();
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
                Log.d(TAG, "Motion button has been clicked");
                MainActivity.this.toggleMotionDetectorRunning();
                MainActivity.this.clearFocus();
            }
        });
    }

    private void initContactButton() {
        Button contactButton = (Button) this.findViewById(R.id.contact_button);

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

    private void initDestinationPlace() {
        final LDPlaceAutocompleteFragment destination = (LDPlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.destination_autocomplete);

        destination.setHint(getResources().getString(R.string.destination));
        destination.setText((this.place == null) ? "" : this.place.getName());

        destination.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                MainActivity.this.setPlace(place);
            }

            @Override
            public void onError(Status status) {
                MainActivity.this.setPlace(null);
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.destination_error), Toast.LENGTH_SHORT).show();
            }
        });

        destination.setOnPlaceClearListener(new LDPlaceAutocompleteFragment.PlaceClearListener() {
            @Override
            public void cleared() {
                MainActivity.this.setPlace(null);
            }
        });
    }


    private void saveData() {
        this.keyword = ((TextView) this.findViewById(R.id.keyword)).getText() + "";
        try {
            this.radius = Integer.parseInt(((TextView) this.findViewById(R.id.radius)).getText() + "");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("running", this.running);
        editor.putString("keyword", this.keyword);
        editor.putString("token", token);
        editor.putBoolean("motionDetectorRunning" , this.motionDetectorRunning);
        editor.putInt("radius" , this.radius);
        editor.putString("phoneNumber", phoneNumber);
        editor.putString("email", email);
        editor.putString("telegramId", telegramId);

        Gson gson = new Gson();
        editor.putString("place", gson.toJson(place, LDPlace.class));

        editor.commit();
    }

    private void restoreSavedData() {
        PreferenceManager.setDefaultValues(this, R.xml.settings_preferences, false);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        this.running = settings.getBoolean("running", false);
        this.keyword = settings.getString("keyword", "");
        this.token = settings.getString("token", RandomStringUtils.random(4, false, true));

        this.motionDetectorRunning = settings.getBoolean("motionDetectorRunning", false);
        this.radius = settings.getInt("radius", RouteTrackingService.DEFAULT_RADIUS);
        this.phoneNumber = settings.getString("phoneNumber", "");
        this.email = settings.getString("email", "");
        this.telegramId = settings.getString("telegramId", "");

        String json = settings.getString("place", "");
        Gson gson = new Gson();
        this.place = gson.fromJson(json, LDPlace.class);
    }

    private void setPlace(Place place) {
        if (place == null) {
            this.place = null;
        } else {
            this.place = new LDPlace(place.getName() + "", place.getId(), place.getLatLng().latitude, place.getLatLng().longitude);
        }

        this.saveData();
    }

    protected void setupToolbar() {
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    public void onGpsRadioButtonClicked(View view) {
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
                        RouteTrackingServiceUtils.setHighGpsAccuracy(this);
                    }
                    break;
            case R.id.radio_gps_low:
                if (checked)
                    editor.putInt("gpsAccuracy", 0);
                    if (motionDetectorRunning) {
                        RouteTrackingServiceUtils.setBalancedGpsAccuracy(this);
                    }
                    break;
        }

        editor.commit();
    }

    //----------------------------- route tracking service -----------------------------------

    private ServiceConnection mConnection = new ServiceConnection() {

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

    };

    private class UIHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == SHARE_ROUTE_MESSAGE) {
                int responseCode = msg.arg1;
                String title = (String)msg.obj;
                if (responseCode == 200) {
                    String showRouteUrl = MainActivity.this.getResources().getString(R.string.showRouteUrl) + "/" + title;
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData urlClip = ClipData.newPlainText("text", showRouteUrl);
                    clipboard.setPrimaryClip(urlClip);
                    Toast.makeText(getApplicationContext(), "Route has been uploaded to server. Route map url has been saved to clipboard.", Toast.LENGTH_LONG).show();
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
                    Toast.makeText(getApplicationContext(), "Route upload failed. Please try again in a few moments", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
