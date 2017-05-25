package net.gmsworld.devicelocator;

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
import android.text.TextWatcher;
import android.util.Log;
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
import net.gmsworld.devicelocator.Utilities.Permissions;
import net.gmsworld.devicelocator.Utilities.RouteTrackingServiceUtils;

import org.apache.commons.lang3.RandomStringUtils;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final int SEND_LOCATION_INTENT = 1;
    private static final int MOTION_DETECTOR_INTENT = 2;
    private static final int SELECT_CONTACT_INTENT = 3;

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
        loadingHandler = new LoadingHandler();
        mMessenger = new Messenger(loadingHandler);
        if (motionDetectorRunning) {
            isTrackingServiceBound = RouteTrackingServiceUtils.startRouteTrackingService(this, mConnection, radius, phoneNumber, email, telegramId, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Log.d(TAG, "MainActivity onDestroy()");
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
        if (this.running == false && !Permissions.haveSendSMSAndLocationPermission(MainActivity.this)) {
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

        }
        if ((currentRadius <= 0 || currentRadius > 1000) && this.motionDetectorRunning == false) {
            //can't start application with no radius
            Toast.makeText(getApplicationContext(), "Please specify radius between 1 and 1000 meters", Toast.LENGTH_SHORT).show();
            return;
        }

        if (this.motionDetectorRunning == false && !Permissions.haveAllRequiredPermission(MainActivity.this)) {
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
        initKeywordInput();
        initRadiusInput();
        initMotionDetectorButton();
        initPhoneNumberInput();
        initEmailInput();
        initTelegramInput();
        initContactButton();
        initTokenInput();
        initGpsRadioGroup();
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
                if (input != null) {
                    try {
                        token = input;
                        saveData();
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
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
                if (input != null && input.length() > 0) {
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
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private void initEmailInput() {
        final TextView emailInput = (TextView) this.findViewById(R.id.email);
        emailInput.setText(email);

        emailInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                email = emailInput.getText().toString();
                saveData();
                //update route tracking service if running
                if (motionDetectorRunning) {
                    RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, mConnection, isTrackingServiceBound, radius, phoneNumber, email, telegramId);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private void initTelegramInput() {
        final TextView telegramInput = (TextView) this.findViewById(R.id.telegramId);
        telegramInput.setText(telegramId);

        telegramInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                telegramId = telegramInput.getText().toString();
                saveData();
                //update route tracking service if running
                if (motionDetectorRunning) {
                    RouteTrackingServiceUtils.resetRouteTrackingService(MainActivity.this, mConnection, isTrackingServiceBound, radius, phoneNumber, email, telegramId);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private void initPhoneNumberInput() {
        final TextView phoneNumberInput = (TextView) this.findViewById(R.id.phoneNumber);
        phoneNumberInput.setText(this.phoneNumber);

        phoneNumberInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                phoneNumber = phoneNumberInput.getText().toString();
                saveData();
            }

            @Override
            public void afterTextChanged(Editable editable) {

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
                } else if (requestCode == MOTION_DETECTOR_INTENT) {
                    launchMotionDetectorService();
                }
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

    private class LoadingHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            //MainActivity.this.launchSmsService();
            //Toast.makeText(context.get(), "Your device has moved more that accepted radius!", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Device has moved more that accepted radius!");
        }
    }
}
