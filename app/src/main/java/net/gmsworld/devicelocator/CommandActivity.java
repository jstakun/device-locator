package net.gmsworld.devicelocator;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import net.gmsworld.devicelocator.Utilities.AbstractCommand;
import net.gmsworld.devicelocator.Utilities.Command;
import net.gmsworld.devicelocator.Utilities.Messenger;
import net.gmsworld.devicelocator.Utilities.Network;

import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class CommandActivity extends AppCompatActivity {

    private static final String TAG = CommandActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command);

        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(CommandActivity.this);

        final String name = getIntent().getStringExtra("name");

        final String imei = getIntent().getStringExtra("imei");

        final Spinner spinner = (Spinner) findViewById(R.id.deviceCommand);
        final CommandArrayAdapter commands = new CommandArrayAdapter(this, R.layout.command_row,  getResources().getStringArray(R.array.device_commands));
        spinner.setAdapter(commands);

        final EditText pinEdit = (EditText) findViewById(R.id.devicePin);

        final EditText args = (EditText) findViewById(R.id.deviceCommandArgs);

        final Button send = findViewById(R.id.sendDeviceCommand);

        String savedPin = settings.getString(imei + "_pin", "");
        if (savedPin.length() >= 4) {
            pinEdit.setText(savedPin);
        }

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String pin = pinEdit.getText().toString();
                if (pin == null || pin.length() < 4) {
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
                        String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
                        String content = "imei=" + imei;
                        content += "&command=" + command + "dlapp";
                        content += "&pin=" + pin;
                        content += "&correlationId=" + Messenger.getDeviceId(CommandActivity.this, false) + "+=+" + settings.getString(MainActivity.DEVICE_PIN, "");
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
                        settings.edit().putString(imei + "_pin", pin).apply();
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

    private class CommandArrayAdapter extends ArrayAdapter<String> {

        private final Context context;

        public CommandArrayAdapter(Context context, int textViewResourceId, String[] commands) {
            super(context, textViewResourceId, commands);
            this.context = context;
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return createView(position, convertView, parent);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return createView(position, convertView, parent);
        }

        public View createView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.command_row, parent, false);
            TextView commandName = rowView.findViewById(R.id.commandName);
            commandName.setText(getItem(position));
            return rowView;
        }
    }
}
