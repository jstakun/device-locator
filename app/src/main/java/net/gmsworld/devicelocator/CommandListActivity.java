package net.gmsworld.devicelocator;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.firebase.analytics.FirebaseAnalytics;

import net.gmsworld.devicelocator.model.Device;
import net.gmsworld.devicelocator.utilities.AbstractCommand;
import net.gmsworld.devicelocator.utilities.DevicesUtils;
import net.gmsworld.devicelocator.utilities.Files;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;
import org.ocpsoft.prettytime.PrettyTime;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class CommandListActivity extends AppCompatActivity {

    final PrettyTime pt = new PrettyTime();

    PreferencesUtils settings;

    ArrayList<Device> devices = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command_list);

        final Toolbar toolbar = findViewById(R.id.smsToolbar);
        setSupportActionBar(toolbar);

        final List<String> commands = Files.readFileByLinesFromContextDir(AbstractCommand.AUDIT_FILE, this);

        final ListView listview = findViewById(R.id.commandList);

        if (!commands.isEmpty()) {

            settings = new PreferencesUtils(this);

            devices = DevicesUtils.buildDeviceList(settings);

            List<Integer> positions = new ArrayList<>();

            List<String> values = new ArrayList<>();

            for (int i = commands.size() - 1; i >= 0; i--) {
                String command = commands.get(i);
                //timestamp sender/to command type message
                //type: 0 - received, 1 - sent
                final String[] tokens = StringUtils.split(command, " ");
                final long timestamp = Long.parseLong(tokens[0]);
                String sender = tokens[1];
                String commandName = null;
                String type = null;
                if (tokens.length > 2) {
                    commandName = tokens[2];
                    type = tokens[3];
                }
                String message;
                int position;
                if (StringUtils.equals(type, "1")) {
                    if (StringUtils.startsWith(sender, Messenger.CID_SEPARATOR)) {
                        sender = sender.substring(Messenger.CID_SEPARATOR.length());
                    }
                    final String deviceName = DevicesUtils.getDeviceName(devices, sender);
                    message = "Command " + StringUtils.capitalize(commandName) + " has been sent to " + deviceName + "\n" + pt.format(new Date(timestamp));
                    position = DevicesUtils.getDevicePosition(devices, sender);
                } else {
                    if (StringUtils.startsWith(sender, Messenger.CID_SEPARATOR) && StringUtils.isNotEmpty(commandName)) {
                        sender = sender.substring(Messenger.CID_SEPARATOR.length());
                        final String deviceName = DevicesUtils.getDeviceName(devices, sender);
                        position = DevicesUtils.getDevicePosition(devices, sender);
                        if (StringUtils.startsWith(commandName, "replyto:")) {
                            message = "Reply to command " + StringUtils.capitalize(commandName.substring(8));
                        } else {
                            if (commandName.endsWith("dlapp")) {
                                message = "Command " + StringUtils.capitalize(commandName.substring(0, commandName.length()-5));
                            } else {
                                message = "Command " + StringUtils.capitalize(commandName);
                            }
                        }
                        message += " has been received from " + deviceName + "\n" + pt.format(new Date(timestamp));
                    } else {
                        //old format - leave it as it is
                        message = "Command " + sender + "\n" + "has been received from unknown device\n" + pt.format(new Date(timestamp));
                        position = -1;
                    }
                }
                values.add(message);
                positions.add(position);
            }

            final CommandArrayAdapter adapter = new CommandArrayAdapter(this, android.R.layout.simple_list_item_1, values, positions);
            listview.setAdapter(adapter);

            listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
                    if (id >= 0) {
                        showCommandActivity((int) id);
                    }
                }
            });
        } else {
            listview.setAdapter(null);
            listview.setEmptyView(findViewById(R.id.commandEmpty));
        }

        FirebaseAnalytics.getInstance(this).logEvent("command_list_activity", new Bundle());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //Log.d(TAG, "onCreateOptionsMenu()");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        menu.findItem(R.id.commandLog).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent = new Intent(this, MainActivity.class);
        switch (item.getItemId()) {
            case R.id.sms:
                intent.setAction(MainActivity.ACTION_SMS_MANAGER);
                startActivity(intent);
                finish();
                return true;
            case R.id.tracker:
                intent.setAction(MainActivity.ACTION_DEVICE_TRACKER_NOTIFICATION);
                startActivity(intent);
                finish();
                return true;
            case R.id.devices:
                intent.setAction(MainActivity.ACTION_DEVICE_MANAGER);
                startActivity(intent);
                finish();
                return true;
            case R.id.map:
                startActivity(new Intent(this, MapsActivity.class));
                finish();
                return true;
            case R.id.permissions:
                startActivity(new Intent(this, PermissionsActivity.class));
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showCommandActivity(int selectedPosition) {
        Intent intent = new Intent(CommandListActivity.this, CommandActivity.class);
        intent.putExtra("index", selectedPosition);
        intent.putParcelableArrayListExtra("devices", devices);
        CommandListActivity.this.startActivity(intent);
    }

    private static class CommandArrayAdapter extends ArrayAdapter<String> {

        final List<Integer> deviceIds;

        public CommandArrayAdapter(Context context, int textViewResourceId, List<String> objects, List<Integer> ids) {
            super(context, textViewResourceId, objects);
            this.deviceIds = ids;
        }

        @Override
        public long getItemId(int position) {
            return deviceIds.get(position);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
