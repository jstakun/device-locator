package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Html;

import net.gmsworld.devicelocator.R;

public class NewVersionDialogFragment extends DialogFragment {

    public static final String TAG = "NewVersionDialog";

    public static NewVersionDialogFragment newInstance() {
        NewVersionDialogFragment newVersionDialogFragment = new NewVersionDialogFragment();
        return newVersionDialogFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setNegativeButton("Not now", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //nothing to do
            }
        });
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getActivity().getString(R.string.downloadUrl)));
                getActivity().startActivity(intent);
            }
        });
        builder.setMessage(Html.fromHtml(getString(R.string.new_version_prompt)));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        builder.setIcon(R.drawable.ic_warning_gray);
        return builder.create();
    }
}
