package net.gmsworld.devicelocator;

import android.os.Bundle;
import android.preference.PreferenceManager;
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

import net.gmsworld.devicelocator.Utilities.AbstractCommand;
import net.gmsworld.devicelocator.Utilities.Command;
import net.gmsworld.devicelocator.Utilities.Messenger;
import net.gmsworld.devicelocator.Utilities.Network;
import net.gmsworld.devicelocator.Utilities.PreferencesUtils;
import net.gmsworld.devicelocator.Views.CommandArrayAdapter;

import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class CommandActivity extends AppCompatActivity {

    private static final String TAG = CommandActivity.class.getSimpleName();

    private static final String PIN_PREFIX = "pin_";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command);

        final String name = getIntent().getStringExtra("name");

        final String imei = getIntent().getStringExtra("imei");

        final Spinner spinner = findViewById(R.id.deviceCommand);
        final CommandArrayAdapter commands = new CommandArrayAdapter(this, R.layout.command_row,  getResources().getStringArray(R.array.device_commands));
        spinner.setAdapter(commands);

        final EditText pinEdit = findViewById(R.id.devicePin);

        final EditText args = findViewById(R.id.deviceCommandArgs);

        final Button send = findViewById(R.id.sendDeviceCommand);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                final String command = spinner.getSelectedItem().toString();
                AbstractCommand c = Command.getCommandByName(command);
                if (c != null && !c.hasParmeters()) {
                    args.setHint(R.string.params_no_hint);
                    args.setInputType(InputType.TYPE_NULL);
                } else {
                    args.setHint(R.string.params_yes_hint);
                    args.setInputType(InputType.TYPE_CLASS_TEXT);
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

        final PreferencesUtils prefs = new PreferencesUtils(this);

        String savedPin = prefs.getEncryptedString(PIN_PREFIX + imei);
        if (savedPin.length() >= PinActivity.PIN_MIN_LENGTH && StringUtils.isNumeric(savedPin)) {
            pinEdit.setText(savedPin);
        }

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String pin = pinEdit.getText().toString();
                if (pin.length() < PinActivity.PIN_MIN_LENGTH) {
                    Toast.makeText(CommandActivity.this,"Please enter valid PIN!", Toast.LENGTH_SHORT).show();
                } else if (StringUtils.isNotEmpty(name) || StringUtils.isNotEmpty(imei)) {
                    final String command = spinner.getSelectedItem().toString();
                    //check if command requires args and validate args
                    boolean validArgs = true, needArgs = false;
                    String commandArgs = args.getText().toString();
                    AbstractCommand c = Command.getCommandByName(command);
                    if (c != null) {
                        if (StringUtils.isNotEmpty(commandArgs)) {
                            c.setCommandTokens(StringUtils.split(command + " " + commandArgs, " "));
                        }

                        if (c.hasParmeters()) {
                            needArgs = true;
                            if (!c.validateTokens()) {
                                validArgs = false;
                            }
                        }
                    }
                    if (!validArgs) {
                        Toast.makeText(CommandActivity.this,"Please provide valid command parameters!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(CommandActivity.this, "Sending command " + command + " to device " + (StringUtils.isNotEmpty(name) ? name : imei) + "...", Toast.LENGTH_SHORT).show();
                        String tokenStr = prefs.getString(DeviceLocatorApp.GMS_TOKEN);
                        String content = "imei=" + imei;
                        content += "&command=" + command + "dlapp";
                        content += "&pin=" + pin;
                        content += "&correlationId=" + Messenger.getDeviceId(CommandActivity.this, false) + "+=+" + prefs.getEncryptedString(PinActivity.DEVICE_PIN);
                        if (needArgs && StringUtils.isNotEmpty(commandArgs)) {
                            try {
                                content += "&args=" + URLEncoder.encode(commandArgs, "UTF-8");
                            } catch (Exception e) {
                                Log.e(TAG, e.getMessage(), e);
                            }
                        }

                        Map<String, String> headers = new HashMap<String, String>();
                        headers.put("Authorization", "Bearer " + tokenStr);

                        Network.post(CommandActivity.this, getString(R.string.deviceManagerUrl), content, null, headers, new Network.OnGetFinishListener() {
                            @Override
                            public void onGetFinish(String results, int responseCode, String url) {
                                if (responseCode == 200) {
                                    Toast.makeText(CommandActivity.this, "Command " + command + " has been sent to the device " + (StringUtils.isNotEmpty(name) ? name : imei) + "!", Toast.LENGTH_SHORT).show();
                                } else {
                                    Log.d(TAG, "Received following response " + responseCode + ": " + results + " from " + url);
                                    Toast.makeText(CommandActivity.this, "Failed to send command " + command + " to the device " + (StringUtils.isNotEmpty(name) ? name : imei) + "!", Toast.LENGTH_SHORT).show();

                                }
                            }
                        });
                        prefs.setEncryptedString(PIN_PREFIX + imei, pin);
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //reset pin verification time
        PreferenceManager.getDefaultSharedPreferences(this).edit().putLong("pinVerificationMillis", System.currentTimeMillis()).apply();
    }
}
