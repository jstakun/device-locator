package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.Permissions;

import androidx.appcompat.app.AlertDialog;

public class NewVersionDialogFragment extends DialogFragment {

    public static final String TAG = "NewVersionDialog";

    private NewVersionDialogFragment.NewVersionDialogListener newVersionDialogListener;

    public interface NewVersionDialogListener {
        void downloadApk();
    }

    public static NewVersionDialogFragment newInstance(NewVersionDialogListener newVersionDialogListener) {
        NewVersionDialogFragment newVersionDialogFragment = new NewVersionDialogFragment();
        newVersionDialogFragment.newVersionDialogListener = newVersionDialogListener;
        return newVersionDialogFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setNegativeButton("No thanks", null);
        builder.setPositiveButton("Download", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                if (Permissions.haveWriteStoragePermission(getActivity())) {
                    newVersionDialogListener.downloadApk();
                } else {
                    Permissions.requestWriteStoragePermission(getActivity(), Permissions.PERMISSIONS_WRITE_STORAGE);
                }
            }
        });
        builder.setMessage(Html.fromHtml(getString(R.string.new_version_prompt, getString(R.string.app_name))));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        builder.setIcon(R.drawable.ic_warning_gray);
        return builder.create();
    }
}
