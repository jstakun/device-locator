package net.gmsworld.devicelocator;

import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

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
import java.util.HashMap;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

public class CommandListActivity extends AppCompatActivity {

    PrettyTime pt = new PrettyTime();

    PreferencesUtils settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command_list);

        final List<String> commands = Files.readFileByLinesFromContextDir(AbstractCommand.AUDIT_FILE, this);

        final ListView listview = findViewById(R.id.commandList);

        if (!commands.isEmpty()) {

            settings = new PreferencesUtils(this);

            List<Device> devices = DevicesUtils.buildDeviceList(settings);

            List<String> values = new ArrayList<String>();

            for (int i = commands.size() - 1; i >= 0; i--) {
                String command = commands.get(i);
                String[] tokens = StringUtils.split(command, " ");
                final long timestamp = Long.valueOf(tokens[0]);
                final String sender = tokens[1];
                if (StringUtils.startsWith(sender, Messenger.CID_SEPARATOR)) {
                    final String deviceName = DevicesUtils.getDeviceName(devices, sender.substring(Messenger.CID_SEPARATOR.length()));
                    String commandName = tokens[2];
                    String message = null;
                    if (StringUtils.startsWith(commandName, "replyto:")) {
                        message = "Reply to command " + commandName.substring(8);
                    } else {
                        message = "Command " + tokens[2];
                    }
                    message += " has been sent from " + deviceName + "\n" + pt.format(new Date(timestamp));
                    values.add(message);
                } else {
                    final String message = "Command " + tokens[1] + "\n" +
                            "has been sent from unknown\n" +
                            pt.format(new Date(timestamp));
                    values.add(message);
                }
            }

            final CommandArrayAdapter adapter = new CommandArrayAdapter(this,
                    android.R.layout.simple_list_item_1, values);
            listview.setAdapter(adapter);

            /*listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> parent, final View view,
                                    int position, long id) {
                    final String item = (String) parent.getItemAtPosition(position);
                    view.animate().setDuration(2000).alpha(0)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                commands.remove(item);
                                adapter.notifyDataSetChanged();
                                view.setAlpha(1);
                            }
                        });
                }

            });*/
        } else {
            listview.setAdapter(null);
            listview.setEmptyView(findViewById(R.id.commandEmpty));
        }
    }

    private class CommandArrayAdapter extends ArrayAdapter<String> {

        HashMap<String, Integer> mIdMap = new HashMap<String, Integer>();

        public CommandArrayAdapter(Context context, int textViewResourceId, List<String> objects) {
            super(context, textViewResourceId, objects);
            for (int i = 0; i < objects.size(); ++i) {
                mIdMap.put(objects.get(i), i);
            }
        }

        @Override
        public long getItemId(int position) {
            String item = getItem(position);
            return mIdMap.get(item);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

    }
}
