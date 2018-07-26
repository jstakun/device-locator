package net.gmsworld.devicelocator;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import net.gmsworld.devicelocator.Utilities.AbstractCommand;
import net.gmsworld.devicelocator.Utilities.Command;
import net.gmsworld.devicelocator.Utilities.Messenger;
import net.gmsworld.devicelocator.Utilities.Network;
import net.gmsworld.devicelocator.Utilities.SCUtils;
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

        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(CommandActivity.this);

        final String name = getIntent().getStringExtra("name");

        final String imei = getIntent().getStringExtra("imei");

        final Spinner spinner = findViewById(R.id.deviceCommand);
        final CommandArrayAdapter commands = new CommandArrayAdapter(this, R.layout.command_row,  getResources().getStringArray(R.array.device_commands));
        spinner.setAdapter(commands);

        final EditText pinEdit = findViewById(R.id.devicePin);

        final EditText args = findViewById(R.id.deviceCommandArgs);

        final Button send = findViewById(R.id.sendDeviceCommand);

        TextView commandLink = findViewById(R.id.docs_link);
        commandLink.setText(Html.fromHtml(getString(R.string.docsLink)));
        commandLink.setMovementMethod(LinkMovementMethod.getInstance());

        findViewById(R.id.commandView).requestFocus();

        String savedPin = settings.getString(PIN_PREFIX + imei, "");
        if (StringUtils.isNotEmpty(savedPin)) {
            try {
                savedPin = new String(SCUtils.decrypt(Base64.decode(savedPin, Base64.NO_PADDING), this));
            } catch (Exception e) {
                Log.d(TAG, e.getMessage(), e);
            }
        }
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

                        if (c.getFinder().equals(AbstractCommand.Finder.STARTS)) {
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
                        String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN, "");
                        String content = "imei=" + imei;
                        content += "&command=" + command + "dlapp";
                        content += "&pin=" + pin;
                        content += "&correlationId=" + Messenger.getDeviceId(CommandActivity.this, false) + "+=+" + settings.getString(PinActivity.DEVICE_PIN, "");
                        if (needArgs && StringUtils.isNotEmpty(commandArgs)) {
                            try {
                                content += "&args=" + URLEncoder.encode(commandArgs, "UTF-8");
                            } catch (Exception e) {
                                Log.e(TAG, e.getMessage(), e);
                            }
                        }
                        String url = getString(R.string.deviceManagerUrl);

                        Map<String, String> headers = new HashMap<String, String>();
                        headers.put("Authorization", "Bearer " + tokenStr);

                        Network.post(CommandActivity.this, url, content, null, headers, new Network.OnGetFinishListener() {
                            @Override
                            public void onGetFinish(String results, int responseCode, String url) {
                                if (responseCode == 200) {
                                    Toast.makeText(CommandActivity.this, "Command " + command + " has been sent to device " + (StringUtils.isNotEmpty(name) ? name : imei) + "!", Toast.LENGTH_SHORT).show();
                                } else {
                                    Log.d(TAG, "Received following response " + responseCode + ": " + results + " from " + url);
                                }
                            }
                        });
                        try {
                            byte[] encPin = SCUtils.encrypt(pin.getBytes(), CommandActivity.this);
                            settings.edit().putString(PIN_PREFIX + imei, Base64.encodeToString(encPin, Base64.NO_PADDING)).apply();
                        } catch (Exception e) {
                            Log.d(TAG, e.getMessage(), e);
                        }
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
