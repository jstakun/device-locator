package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Html;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.Permissions;

public class DownloadFullApplicationDialogFragment extends DialogFragment {

    public static final String TAG = "DownloadFullAppDialog";

    private DownloadFullApplicationDialogFragment.DownloadFullApplicationDialogListener initListener;

    public interface DownloadFullApplicationDialogListener {
        void downloadApk();
    }

    public static DownloadFullApplicationDialogFragment newInstance(DownloadFullApplicationDialogListener initListener) {
        DownloadFullApplicationDialogFragment frag = new DownloadFullApplicationDialogFragment();
        frag.initListener = initListener;
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setPositiveButton("Download", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                if (Permissions.haveWriteStoragePermission(getActivity())) {
                    initListener.downloadApk();
                } else {
                    Permissions.requestWriteStoragePermission(getActivity(), Permissions.PERMISSIONS_WRITE_STORAGE);
                }
            }
        });
        builder.setNegativeButton("No thanks", null);
        builder.setMessage(Html.fromHtml(getString(R.string.download_full_app)));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        builder.setIcon(R.drawable.ic_warning_gray);
        return builder.create();
    }
}
