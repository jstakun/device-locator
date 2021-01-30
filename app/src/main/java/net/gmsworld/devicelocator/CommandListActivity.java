package net.gmsworld.devicelocator;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class CommandListActivity extends AppCompatActivity {

    private final PrettyTime pt = new PrettyTime();
    private static ArrayList<Device> devices = null;
    private static final String TAG = CommandListActivity.class.getSimpleName();

    PreferencesUtils settings;
    ListView listview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command_list);

        final Toolbar toolbar = findViewById(R.id.smsToolbar);
        setSupportActionBar(toolbar);

        listview = findViewById(R.id.commandList);
        listview.setEmptyView(findViewById(R.id.commandEmpty));

        settings = new PreferencesUtils(this);
        devices = DevicesUtils.buildDeviceList(settings);

        FirebaseAnalytics.getInstance(this).logEvent("command_list_activity", new Bundle());
    }

    @Override
    protected void onResume() {
        super.onResume();
        final List<String> commands = Files.readFileByLinesFromContextDir(AbstractCommand.AUDIT_FILE, this);

        if (!commands.isEmpty()) {
            List<Integer> positions = new ArrayList<>();
            List<String> values = new ArrayList<>();
            List<String> types = new ArrayList<>();

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
                    if (tokens.length > 3) {
                        type = tokens[3];
                    }
                }
                String message;
                int position = -1;
                if (StringUtils.equals(type, "1")) {
                    if (StringUtils.startsWith(sender, Messenger.CID_SEPARATOR)) {
                        sender = sender.substring(Messenger.CID_SEPARATOR.length());
                    }
                    final String deviceName = DevicesUtils.getDeviceName(devices, sender);
                    message = "Command " + StringUtils.capitalize(commandName) + "\nsent to " + deviceName + "\n" + pt.format(new Date(timestamp));
                    position = DevicesUtils.getDevicePosition(devices, sender);
                } else {
                    if (StringUtils.startsWith(sender, Messenger.CID_SEPARATOR) && StringUtils.isNotEmpty(commandName)) {
                        sender = sender.substring(Messenger.CID_SEPARATOR.length());
                        String deviceName = null;
                        if (sender.equals("Telegram:" + getString(R.string.app_telegram))) {
                            deviceName = "Admin";
                            position = -1000;
                        } else {
                            deviceName = DevicesUtils.getDeviceName(devices, sender);
                            position = DevicesUtils.getDevicePosition(devices, sender);
                        }
                        if (StringUtils.startsWith(commandName, "replyto:")) {
                            message = "Reply to command " + StringUtils.capitalize(commandName.substring(8));
                        } else {
                            if (commandName.endsWith("dlapp")) {
                                message = "Command " + StringUtils.capitalize(commandName.substring(0, commandName.length() - 5));
                            } else if (commandName.endsWith("dladmindlt")) {
                                message = "Command " + StringUtils.capitalize(commandName.substring(0, commandName.length() - 10));
                            } else {
                                message = "Command " + StringUtils.capitalize(commandName);
                            }
                        }
                        message += "\nsent from " + deviceName + "\n" + pt.format(new Date(timestamp));
                    } else {
                        //old format - leave it as it is
                        message = "Command " + sender + "\nreceived from unknown device\n" + pt.format(new Date(timestamp));
                    }
                }
                values.add(message);
                positions.add(position);
                types.add(type);
            }

            final CommandArrayAdapter adapter = new CommandArrayAdapter(this, R.layout.command_log_row, values, positions, types);
            listview.setAdapter(adapter);
            listview.setVerticalScrollBarEnabled(false);
            MainActivity.setListViewHeightBasedOnChildren(listview);
        } else {
            listview.setAdapter(null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
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
            case R.id.privacyPolicy:
                Intent gmsIntent = new Intent(this, WebViewActivity.class);
                gmsIntent.putExtra("url", getString(R.string.privacyPolicyUrl));
                gmsIntent.putExtra("title", getString(R.string.app_name) + " " + getString(R.string.privacy_policy));
                startActivity(gmsIntent);
                return true;
            case R.id.donateUs:
                Messenger.viewDonateUrl(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private static class CommandArrayAdapter extends ArrayAdapter<String> {

        final List<Integer> deviceIds;
        private final Context context;
        private final List<String> types;

        public CommandArrayAdapter(Context context, int textViewResourceId, List<String> objects, List<Integer> ids, List<String> types) {
            super(context, textViewResourceId, objects);
            this.deviceIds = ids;
            this.context = context;
            this.types = types;
        }

        @Override
        public long getItemId(int position) {
            return deviceIds.get(position);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            return createView(position, convertView, parent);
        }

        @Override
        public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
            return createView(position, convertView, parent);
        }

        private View createView(final int position, View convertView, ViewGroup parent) {
            CommandArrayAdapter.ViewHolder viewHolder; // view lookup cache stored in tag
            if (convertView == null) {
                // If there's no view to re-use, inflate a brand new view for row
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.command_log_row, parent, false);
                viewHolder = new CommandArrayAdapter.ViewHolder();
                viewHolder.logText = convertView.findViewById(R.id.log);
                viewHolder.type = convertView.findViewById(R.id.type);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (CommandArrayAdapter.ViewHolder) convertView.getTag();
            }

            viewHolder.logText.setText(getItem(position));
            viewHolder.logText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showCommandActivity(deviceIds.get(position));
                }
            });

            viewHolder.type.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showCommandActivity(deviceIds.get(position));
                }
            });
            if (StringUtils.equals(types.get(position), "1")) {
                viewHolder.type.setImageResource(R.drawable.cloud_upload);
            } else {
                viewHolder.type.setImageResource(R.drawable.cloud_download);
            }

            return convertView;
        }

        private void showCommandActivity(int selectedPosition) {
            if (selectedPosition >= 0) {
                Intent intent = new Intent(context, CommandActivity.class);
                intent.putExtra("index", selectedPosition);
                intent.putParcelableArrayListExtra("devices", devices);
                context.startActivity(intent);
            } else if (selectedPosition == -1000) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.telegramWebUrl)));
                    context.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }

        private static class ViewHolder {
            TextView logText;
            ImageView type;
        }
    }
}
