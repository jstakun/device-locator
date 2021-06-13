package net.gmsworld.devicelocator;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;

import net.gmsworld.devicelocator.fragments.SendCommandDialogFragment;
import net.gmsworld.devicelocator.model.Device;
import net.gmsworld.devicelocator.services.CommandService;
import net.gmsworld.devicelocator.utilities.AbstractCommand;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.utilities.Toaster;
import net.gmsworld.devicelocator.views.CommandArrayAdapter;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;

public class CommandActivity extends AppCompatActivity implements SendCommandDialogFragment.SendCommandDialogListener {

    private static final String TAG = CommandActivity.class.getSimpleName();

    public static final String PIN_PREFIX = "pin_";
    public static final String AUTH_NEEDED = "CommandActivityAuthNeeded";

    private FirebaseAnalytics firebaseAnalytics;
    private Device device;
    private PreferencesUtils settings;
    private Toaster toaster;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command);

        final Toolbar toolbar = findViewById(R.id.smsToolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        settings = new PreferencesUtils(this);

        toaster = new Toaster(this);

        final List<Device> devices = getIntent().getParcelableArrayListExtra("devices");

        if (devices == null || devices.isEmpty()) {
            toaster.showActivityToast(R.string.crash_error);
            return;
        }

        int index = getIntent().getIntExtra("index", 0);

        if (index < 0 && index >= devices.size()) {
            Log.d(TAG, "Invalid index " + index + " for devices " + devices.size());
            return;
        }

        device = devices.get(index);

        final Spinner commandSpinner = findViewById(R.id.deviceCommand);
        final EditText args = findViewById(R.id.deviceCommandArgs);
        final EditText pinEdit = findViewById(R.id.devicePin);
        final CheckBox socialSend = findViewById(R.id.social_send);

        //command args

        args.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                    sendCommand(pinEdit.getText().toString(), commandSpinner.getSelectedItem().toString(), args.getText().toString(), socialSend.isChecked());
                }
                return false;
            }
        });

        //commands list

        final CommandArrayAdapter commands = new CommandArrayAdapter(this, R.layout.command_row,  getResources().getStringArray(R.array.device_commands));
        commandSpinner.setAdapter(commands);

        setSelectedCommand(commandSpinner);

        commandSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                final String command = StringUtils.deleteWhitespace(commandSpinner.getSelectedItem().toString());
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

        pinEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String input = charSequence.toString();
                try {
                    //pin is 4 to 8 digits string
                    if (input.length() >= PinActivity.PIN_MIN_LENGTH) {
                        if (!StringUtils.equals(savedPin, input) && StringUtils.isNumeric(input)) {
                            settings.setEncryptedString(PIN_PREFIX + device.imei, input);
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
        //commandLink.setMovementMethod(LinkMovementMethodFixed.getInstance());

        findViewById(R.id.commandView).requestFocus();

        firebaseAnalytics = FirebaseAnalytics.getInstance(this);
        firebaseAnalytics.logEvent("command_activity", new Bundle());
    }

    @Override
    protected void onPause() {
        super.onPause();
        toaster.cancel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //reset pin verification time
        settings.setLong(PinActivity.VERIFICATION_TIMESTAMP, System.currentTimeMillis());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private boolean isValidCommand(String pin, String command, String commandArgs) {
        if (pin.length() == 0) {
            toaster.showActivityToast(R.string.pin_enter);
            return false;
        } else if (pin.length() < PinActivity.PIN_MIN_LENGTH) {
            toaster.showActivityToast(R.string.pin_enter_valid);
            return false;
        } else if (StringUtils.isNotEmpty(device.name) || StringUtils.isNotEmpty(device.imei)) {
            //check if command requires args and validate args
            AbstractCommand c = Command.getCommandByName(command);
            if (c != null) {
                if (StringUtils.isNotEmpty(commandArgs)) {
                    c.setCommandTokens(StringUtils.split(command + " " + commandArgs, " "));
                }
                if (c.hasParameters() && !c.validateTokens()) {
                    toaster.showActivityToast(R.string.enter_command_params);
                    return false;
                }
            }
            return true;
        } else {
            toaster.showActivityToast(R.string.no_device);
            return false;
        }
    }

    private void sendCommand(String pin, String commandStr, String commandArgs, boolean sendSocial) {
        final String command = StringUtils.deleteWhitespace(commandStr);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
        }
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
                toaster.showActivityToast(R.string.no_network_error);
            }
        }
    }

    public void sendCommand(String command, Intent intent) {
        toaster.showActivityToast(R.string.please_wait);
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
                final String item = (String)commandSpinner.getItemAtPosition(i);
                if (StringUtils.equalsIgnoreCase(StringUtils.deleteWhitespace(item), lastCommand)) {
                   commandSpinner.setSelection(i);
                   break;
                }
            }
        }
    }

    public void onUrlClick(final View view) {
        TextView textView = (TextView)view;
        if (textView.getId() == R.id.docs_link) {
            Intent gmsIntent = new Intent(this, WebViewActivity.class);
            gmsIntent.putExtra("url", getString(R.string.showCommandsUrl));
            gmsIntent.putExtra("title", getString(R.string.app_name) + " Commands");
            startActivity(gmsIntent);
        }
    }
}
