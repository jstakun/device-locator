package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;

import net.gmsworld.devicelocator.R;

import androidx.appcompat.app.AlertDialog;

public class SmsCommandsInitDialogFragment extends DialogFragment {

    public static final String TAG = "SmsCommandsInitDialog";

    private SmsCommandsInitDialogListener initListener;

    public interface SmsCommandsInitDialogListener {
        void toggleRunning();
    }

    public static SmsCommandsInitDialogFragment newInstance(SmsCommandsInitDialogListener initListener) {
        SmsCommandsInitDialogFragment frag = new SmsCommandsInitDialogFragment();
        frag.initListener = initListener;
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                initListener.toggleRunning();
            }
        });
        builder.setNegativeButton(R.string.no, null);
        builder.setMessage(Html.fromHtml(getString(R.string.sms_manager_first_time_use)));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        builder.setIcon(R.drawable.ic_sms_gray);
        return builder.create();
    }
}
