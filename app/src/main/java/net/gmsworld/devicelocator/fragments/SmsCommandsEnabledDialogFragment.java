package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Html;

import net.gmsworld.devicelocator.PermissionsActivity;
import net.gmsworld.devicelocator.R;

public class SmsCommandsEnabledDialogFragment extends DialogFragment {

    public static final String TAG = "SmsCommandsEnabledDialog";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setNegativeButton("Later", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //Toast.makeText(getActivity(), "Your Security PIN is " + settings.getEncryptedString(PinActivity.DEVICE_PIN), Toast.LENGTH_LONG).show();
            }
        });
        builder.setPositiveButton("Permissions", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Intent intent = new Intent(getActivity(), PermissionsActivity.class);
                startActivity(intent);
            }
        });
        builder.setMessage(Html.fromHtml(getString(R.string.commands_enabled_prompt)));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        builder.setIcon(R.drawable.ic_sms_gray);
        return builder.create();
    }
}