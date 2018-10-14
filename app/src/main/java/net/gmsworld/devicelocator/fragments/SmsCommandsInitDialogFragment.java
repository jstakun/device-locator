package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Html;

import net.gmsworld.devicelocator.R;

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
        builder.setMessage(Html.fromHtml(getString(R.string.enable_sms_command_prompt)));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        return builder.create();
    }
}
