package net.gmsworld.devicelocator;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import net.gmsworld.devicelocator.Utilities.Network;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class CommandActivity extends AppCompatActivity {

    private static final String TAG = CommandActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command);

        final String name = getIntent().getStringExtra("name");

        final String imei = getIntent().getStringExtra("imei");

        final Spinner spinner = (Spinner) findViewById(R.id.deviceCommand);

        final EditText pinEdit = (EditText) findViewById(R.id.devicePin);

        final EditText args = (EditText) findViewById(R.id.deviceCommandArgs);

        final Button send = findViewById(R.id.sendDeviceCommand);

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String pin = pinEdit.getText().toString();
                if (pin == null || pin.length() < 4) {
                    Toast.makeText(CommandActivity.this,"Please enter valid PIN!", Toast.LENGTH_SHORT).show();
                } else if (StringUtils.isNotEmpty(name) || StringUtils.isNotEmpty(imei)) {
                    String command = spinner.getSelectedItem().toString();
                    //TODO check if command requires args and validate args
                    Toast.makeText(CommandActivity.this,"Sending command " + command + "...", Toast.LENGTH_SHORT).show();

                    final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(CommandActivity.this);
                    String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN_KEY, "");
                    String username = settings.getString("userLogin", "");
                    String content = "imei=" + imei;
                    content += "&command=" + command + "dlt";
                    content += "&pin=" + pin;
                    String url = getString(R.string.deviceManagerUrl);

                    Map<String, String> headers = new HashMap<String, String>();
                    headers.put("Authorization", "Bearer " + tokenStr);

                    Network.post(CommandActivity.this, url, content, null, headers, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            if (responseCode == 200) {
                                Toast.makeText(CommandActivity.this, "Command has been sent to the cloud!" , Toast.LENGTH_SHORT).show();
                            } else {
                                Log.d(TAG, "Received following response " + responseCode + ": " + results + " from " + url);
                            }
                        }
                    });
                    //TODO offer to remember pin for this device
                }
            }
        });
    }
}
