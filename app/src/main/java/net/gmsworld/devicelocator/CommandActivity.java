package net.gmsworld.devicelocator;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.location.Location;
import android.os.Bundle;
import android.text.Html;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;

import net.gmsworld.devicelocator.fragments.SendCommandDialogFragment;
import net.gmsworld.devicelocator.model.Device;
import net.gmsworld.devicelocator.services.CommandService;
import net.gmsworld.devicelocator.utilities.AbstractCommand;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.LinkMovementMethodFixed;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.views.CommandArrayAdapter;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

public class CommandActivity extends AppCompatActivity implements OnLocationUpdatedListener, SendCommandDialogFragment.SendCommandDialogListener {

    private static final String TAG = CommandActivity.class.getSimpleName();

    public static final String PIN_PREFIX = "pin_";
    private Toast commandToast;

    private FirebaseAnalytics firebaseAnalytics;
    private Device device;
    private PreferencesUtils settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command);

        settings = new PreferencesUtils(this);

        final List<Device> devices = getIntent().getParcelableArrayListExtra("devices");

        if (devices == null || devices.isEmpty()) {
            showToast(R.string.crash_error);
            return;
        }

        device = devices.get(getIntent().getIntExtra("index", 0));

        final Spinner commandSpinner = findViewById(R.id.deviceCommand);
        final EditText args = findViewById(R.id.deviceCommandArgs);
        final EditText pinEdit = findViewById(R.id.devicePin);
        final CheckBox socialSend = findViewById(R.id.social_send);

        //commands list

        args.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                    sendCommand(pinEdit.getText().toString(), commandSpinner.getSelectedItem().toString(), args.getText().toString(), socialSend.isChecked());
                }
                return false;
            }
        });

        final CommandArrayAdapter commands = new CommandArrayAdapter(this, R.layout.command_row,  getResources().getStringArray(R.array.device_commands));
        commandSpinner.setAdapter(commands);

        setSelectedCommand(commandSpinner);

        commandSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                final String command = commandSpinner.getSelectedItem().toString();
                AbstractCommand c = Command.getCommandByName(command);
                if (c != null && c.hasParameters()) {
                    String defaultArgs = c.getDefaultArgs();
                    if (StringUtils.isNotEmpty(defaultArgs)) {
                        args.setText(defaultArgs);
                    } else {
                        args.setHint(R.string.params_yes_hint);
                        args.setText("");
                    }
                    args.setInputType(InputType.TYPE_CLASS_TEXT);
                } else {
                    args.setHint(R.string.params_no_hint);
                    args.setText("");
                    args.setInputType(InputType.TYPE_NULL);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {

            }
        });

        //pin

        String savedPin;
        if (StringUtils.equals(device.imei, Messenger.getDeviceId(this, false))) {
            savedPin = settings.getEncryptedString(PinActivity.DEVICE_PIN);
        } else {
            savedPin = settings.getEncryptedString(PIN_PREFIX + device.imei);
        }
        if (savedPin.length() >= PinActivity.PIN_MIN_LENGTH && StringUtils.isNumeric(savedPin)) {
            pinEdit.setText(savedPin);
        } else {
            pinEdit.setText("");
        }

        //devices list

        List<String> deviceNames = new ArrayList<>();
        for (int i = 0; i < devices.size(); i++) {
            deviceNames.add(StringUtils.isNotEmpty(devices.get(i).name) ? devices.get(i).name : devices.get(i).imei);
        }

        final Spinner deviceSpinner = findViewById(R.id.deviceList);
        final CommandArrayAdapter devicesAdapter = new CommandArrayAdapter(this, R.layout.command_row,  deviceNames);
        deviceSpinner.setAdapter(devicesAdapter);

        for (int i = 0; i < devices.size(); i++) {
            if (StringUtils.equalsIgnoreCase(device.name, devices.get(i).name) || StringUtils.equalsIgnoreCase(device.imei, devices.get(i).imei)) {
                deviceSpinner.setSelection(i);
                break;
            }
        }

        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                device = devices.get(position);
                String savedPin;
                if (StringUtils.equals(device.imei, Messenger.getDeviceId(CommandActivity.this, false))) {
                    savedPin = settings.getEncryptedString(PinActivity.DEVICE_PIN);
                } else {
                    savedPin = settings.getEncryptedString(PIN_PREFIX + device.imei);
                }
                if (savedPin.length() >= PinActivity.PIN_MIN_LENGTH && StringUtils.isNumeric(savedPin)) {
                    pinEdit.setText(savedPin);
                } else {
                    pinEdit.setText("");
                }
                setSelectedCommand(commandSpinner);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        //social checkbox

        if (Messenger.isAppInstalled(this, Messenger.TELEGRAM_PACKAGE)) {
            socialSend.setVisibility(View.VISIBLE);
        }

        //send button

        final Button send = findViewById(R.id.sendDeviceCommand);
        ViewCompat.setBackgroundTintList(send, ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendCommand(pinEdit.getText().toString(), commandSpinner.getSelectedItem().toString(), args.getText().toString(), socialSend.isChecked());
            }
        });

        //docs link

        TextView commandLink = findViewById(R.id.docs_link);
        commandLink.setText(Html.fromHtml(getString(R.string.docsLink)));
        commandLink.setMovementMethod(LinkMovementMethodFixed.getInstance());

        findViewById(R.id.commandView).requestFocus();

        firebaseAnalytics = FirebaseAnalytics.getInstance(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SmartLocation.with(this).location(new LocationGooglePlayServicesWithFallbackProvider(this)).oneFix().start(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SmartLocation.with(this).location(new LocationGooglePlayServicesWithFallbackProvider(this)).stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //reset pin verification time
        settings.setLong("pinVerificationMillis", System.currentTimeMillis());
    }

    @Override
    public void onLocationUpdated(Location location) {
        Log.d(TAG, "Location found with accuracy " + location.getAccuracy() + " m");
    }

    private boolean isValidCommand(String pin, String command, String commandArgs) {
        if (pin.length() == 0) {
            showToast(R.string.pin_enter);
            return false;
        } else if (pin.length() < PinActivity.PIN_MIN_LENGTH) {
            showToast(R.string.pin_enter_valid);
            return false;
        } else if (StringUtils.isNotEmpty(device.name) || StringUtils.isNotEmpty(device.imei)) {
            //check if command requires args and validate args
            AbstractCommand c = Command.getCommandByName(command);
            if (c != null) {
                if (StringUtils.isNotEmpty(commandArgs)) {
                    c.setCommandTokens(StringUtils.split(command + " " + commandArgs, " "));
                }
                if (c.hasParameters() && !c.validateTokens()) {
                    showToast(R.string.enter_command_params);
                    return false;
                }
            }
            return true;
        } else {
            showToast(R.string.no_device);
            return false;
        }
    }

    private void sendCommand(String pin, String command, String commandArgs, boolean sendSocial) {
        if (isValidCommand(pin, command, commandArgs)) {
            if (sendSocial) {
                //command pin imei -p args
                String message = command + " " + pin + " " + device.imei;
                if (StringUtils.isNotEmpty(commandArgs)) {
                    message += " -p " + commandArgs;
                }
                Messenger.sendTelegramMessage(CommandActivity.this, message);
            } else if (Network.isNetworkAvailable(CommandActivity.this)) {
                settings.setEncryptedString(PIN_PREFIX + device.imei, pin);
                Intent newIntent = new Intent(this, CommandService.class);
                AbstractCommand c = Command.getCommandByName(command);
                if ((c == null || c.hasParameters()) && StringUtils.isNotEmpty(commandArgs)) {
                    newIntent.putExtra("args", commandArgs);
                }
                newIntent.putExtra("command", command);
                newIntent.putExtra("imei", device.imei);
                newIntent.putExtra("pin", pin);
                if (StringUtils.isNotEmpty(device.name)) {
                    newIntent.putExtra(MainActivity.DEVICE_NAME, device.name);
                }
                if (c != null && c.getConfirmation() > 0) {
                    SendCommandDialogFragment sendCommandDialogFragment = SendCommandDialogFragment.newInstance(c.getConfirmation(), command, newIntent, this);
                    sendCommandDialogFragment.show(getFragmentManager(), SendCommandDialogFragment.TAG);
                } else {
                    sendCommand(command, newIntent);
                }
            } else {
                showToast(R.string.no_network_error);
            }
        }
    }

    public void sendCommand(String command, Intent intent) {
        showToast(R.string.please_wait);
        firebaseAnalytics.logEvent("cloud_command_sent_" + command.toLowerCase(), new Bundle());
        startService(intent);
    }

    private void setSelectedCommand(Spinner commandSpinner) {
        String lastCommand = settings.getString(device.imei + CommandService.LAST_COMMAND_SUFFIX);
        Log.d(TAG, "Found last command " + lastCommand);
        if (StringUtils.isNotEmpty(lastCommand)) {
            AbstractCommand c = Command.getCommandByName(lastCommand);
            if (c != null && c.hasOppositeCommand()) {
                lastCommand = c.getOppositeCommand().substring(0, c.getOppositeCommand().length()-2);
            }
            for (int i = 0;i < commandSpinner.getAdapter().getCount();i++) {
                //Log.d(TAG, "Comparing " + commandSpinner.getItemAtPosition(i) + " with " + lastCommand);
                if (StringUtils.equalsIgnoreCase((String)commandSpinner.getItemAtPosition(i), lastCommand)) {
                   commandSpinner.setSelection(i);
                   break;
                }
            }
        }
    }

    private void showToast(int resId) {
        if (commandToast != null) {
            commandToast.cancel();
        }

        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast_layout, (ViewGroup) findViewById(R.id.toast_container));
        TextView toastText = layout.findViewById(R.id.toast_text);
        toastText.setText(resId);

        commandToast = new Toast(this);
        commandToast.setDuration(Toast.LENGTH_LONG);
        commandToast.setView(layout);
        commandToast.show();
    }
}
