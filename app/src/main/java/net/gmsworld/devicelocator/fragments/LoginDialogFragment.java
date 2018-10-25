package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.text.Html;

import net.gmsworld.devicelocator.R;

public class LoginDialogFragment extends DialogFragment {

    public static final String TAG = "LoginDialog";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT); //new Intent(Settings.ACTION_SYNC_SETTINGS)
                intent.putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[]{getString(R.string.account_type)});
                getActivity().startActivity(intent);
            }
        });
        builder.setNegativeButton(R.string.no, null);
        builder.setMessage(Html.fromHtml(getString(R.string.login_dialog_desc)));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        builder.setIcon(R.drawable.ic_account_circle_gray);
        return builder.create();
    }
}

