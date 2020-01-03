package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;

import androidx.appcompat.app.AlertDialog;

public class RegisterDeviceDialogFragment extends DialogFragment {
    public static final String TAG = "RegisterDeviceDialog";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setNegativeButton("Later", null);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Intent intent = new Intent(getActivity(), MainActivity.class);
                intent.setAction(MainActivity.ACTION_DEVICE_MANAGER);
                startActivity(intent);
            }
        });
        builder.setMessage(Html.fromHtml(getString(R.string.register_device)));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        builder.setIcon(R.drawable.ic_help_gray);
        return builder.create();
    }
}
