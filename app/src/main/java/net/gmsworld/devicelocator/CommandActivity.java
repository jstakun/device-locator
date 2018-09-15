package net.gmsworld.devicelocator;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;

import net.gmsworld.devicelocator.model.Device;
import net.gmsworld.devicelocator.services.CommandService;
import net.gmsworld.devicelocator.utilities.AbstractCommand;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.views.CommandArrayAdapter;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

public class CommandActivity extends AppCompatActivity implements OnLocationUpdatedListener {

    private static final String TAG = CommandActivity.class.getSimpleName();

    public static final String PIN_PREFIX = "pin_";

    private FirebaseAnalytics firebaseAnalytics;

    private String name, imei;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command);

        final PreferencesUtils prefs = new PreferencesUtils(this);

        name = getIntent().getStringExtra("name");

        imei = getIntent().getStringExtra("imei");

        final List<Device> devices = getIntent().getParcelableArrayListExtra("devices");

        final Spinner commandSpinner = findViewById(R.id.deviceCommand);
        final CommandArrayAdapter commands = new CommandArrayAdapter(this, R.layout.command_row,  getResources().getStringArray(R.array.device_commands));
        commandSpinner.setAdapter(commands);

        List<String> deviceNames = new ArrayList<>();
        if (devices != null) {
            for (int i = 0; i < devices.size(); i++) {
                deviceNames.add(StringUtils.isNotEmpty(devices.get(i).name) ? devices.get(i).name : devices.get(i).imei);
            }
        }

        final Spinner deviceSpinner = findViewById(R.id.deviceList);
        final CommandArrayAdapter devicesAdapter = new CommandArrayAdapter(this, R.layout.command_row,  deviceNames);
        deviceSpinner.setAdapter(devicesAdapter);

        if (devices != null) {
            for (int i = 0; i < devices.size(); i++) {
                if (StringUtils.equalsIgnoreCase(name, devices.get(i).name) || StringUtils.equalsIgnoreCase(imei, devices.get(i).imei)) {
                    deviceSpinner.setSelection(i);
                    break;
                }
            }
        }

        final EditText pinEdit = findViewById(R.id.devicePin);

        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                name = devices.get(position).name;
                imei = devices.get(position).imei;
                String savedPin;
                if (StringUtils.equals(imei, Messenger.getDeviceId(CommandActivity.this, false))) {
                    savedPin = prefs.getEncryptedString(PinActivity.DEVICE_PIN);
                } else {
                    savedPin = prefs.getEncryptedString(PIN_PREFIX + imei);
                }
                if (savedPin.length() >= PinActivity.PIN_MIN_LENGTH && StringUtils.isNumeric(savedPin)) {
                    pinEdit.setText(savedPin);
                } else {
                    pinEdit.setText("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        final EditText args = findViewById(R.id.deviceCommandArgs);

        final Button send = findViewById(R.id.sendDeviceCommand);
        ViewCompat.setBackgroundTintList(send, ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));

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

        TextView commandLink = findViewById(R.id.docs_link);
        commandLink.setText(Html.fromHtml(getString(R.string.docsLink)));
        commandLink.setMovementMethod(LinkMovementMethod.getInstance());

        findViewById(R.id.commandView).requestFocus();

        String savedPin;
        if (StringUtils.equals(imei, Messenger.getDeviceId(this, false))) {
            savedPin = prefs.getEncryptedString(PinActivity.DEVICE_PIN);
        } else {
            savedPin = prefs.getEncryptedString(PIN_PREFIX + imei);
        }
        if (savedPin.length() >= PinActivity.PIN_MIN_LENGTH && StringUtils.isNumeric(savedPin)) {
            pinEdit.setText(savedPin);
        } else {
            pinEdit.setText("");
        }

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String pin = pinEdit.getText().toString();
                if (pin.length() < PinActivity.PIN_MIN_LENGTH) {
                    Toast.makeText(CommandActivity.this,"Please enter valid PIN!", Toast.LENGTH_SHORT).show();
                } else if (StringUtils.isNotEmpty(name) || StringUtils.isNotEmpty(imei)) {
                    final String command = commandSpinner.getSelectedItem().toString();
                    //check if command requires args and validate args
                    boolean validArgs = true, needArgs = false;
                    String commandArgs = args.getText().toString();
                    AbstractCommand c = Command.getCommandByName(command);
                    if (c != null) {
                        if (StringUtils.isNotEmpty(commandArgs)) {
                            c.setCommandTokens(StringUtils.split(command + " " + commandArgs, " "));
                        }

                        if (c.hasParameters()) {
                            needArgs = true;
                            if (!c.validateTokens()) {
                                validArgs = false;
                            }
                        }
                    }
                    if (!validArgs) {
                        Toast.makeText(CommandActivity.this,"Please provide valid command parameters!", Toast.LENGTH_SHORT).show();
                    } else {
                        firebaseAnalytics.logEvent("cloud_command_sent_" + command.toLowerCase(), new Bundle());
                        prefs.setEncryptedString(PIN_PREFIX + imei, pin);
                        Intent newIntent = new Intent(CommandActivity.this, CommandService.class);
                        if (needArgs && StringUtils.isNotEmpty(commandArgs)) {
                            newIntent.putExtra("args", commandArgs);
                        }
                        newIntent.putExtra("command", command);
                        newIntent.putExtra("imei", imei);
                        newIntent.putExtra("pin", pin);
                        if (StringUtils.isNotEmpty(name)) {
                            newIntent.putExtra(MainActivity.DEVICE_NAME, name);
                        }
                        startService(newIntent);
                    }
                }
            }
        });

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
        PreferenceManager.getDefaultSharedPreferences(this).edit().putLong("pinVerificationMillis", System.currentTimeMillis()).apply();
    }

    @Override
    public void onLocationUpdated(Location location) {
        Log.d(TAG, "Location found with accuracy " + location.getAccuracy() + " m");
    }
}
