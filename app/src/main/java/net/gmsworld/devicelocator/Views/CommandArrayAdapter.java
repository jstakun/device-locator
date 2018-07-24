package net.gmsworld.devicelocator.Views;

import android.content.Context;
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


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return createView(position, convertView, parent);
    }

    public View createView(int position, View convertView, ViewGroup parent) {
        //TODO add view holder
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.command_row, parent, false);
        TextView commandName = rowView.findViewById(R.id.commandName);
        commandName.setText(getItem(position));
        return rowView;
    }
}
