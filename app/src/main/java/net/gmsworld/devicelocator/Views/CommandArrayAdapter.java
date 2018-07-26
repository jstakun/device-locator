package net.gmsworld.devicelocator.Views;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import net.gmsworld.devicelocator.R;

import java.util.List;

public class CommandArrayAdapter extends ArrayAdapter<String> {

    private final Context context;

    public CommandArrayAdapter(Context context, int textViewResourceId, String[] commands) {
        super(context, textViewResourceId, commands);
        this.context = context;
    }

    public CommandArrayAdapter(Context context, int textViewResourceId, List<String> commands) {
        super(context, textViewResourceId, commands);
        this.context = context;
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

    private View createView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder; // view lookup cache stored in tag
        if (convertView == null) {
            // If there's no view to re-use, inflate a brand new view for row
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.command_row, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.commandName = convertView.findViewById(R.id.commandName);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        viewHolder.commandName.setText(getItem(position));
        return convertView;
    }

    private static class ViewHolder {
        TextView commandName;
    }
}
