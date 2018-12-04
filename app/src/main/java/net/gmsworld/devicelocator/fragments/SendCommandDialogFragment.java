package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Html;

import net.gmsworld.devicelocator.R;

public class SendCommandDialogFragment extends DialogFragment {

    public static final String TAG = "SendCommandDialogFragment";

    private int message;
    private String command;
    private Intent intent;
    private SendCommandDialogListener listener;

    public interface SendCommandDialogListener {
        void sendCommand(String command, Intent intent);
    }

    public static SendCommandDialogFragment newInstance(int message, String command, Intent intent, SendCommandDialogListener listener) {
        SendCommandDialogFragment sendCommandDialogFragment = new SendCommandDialogFragment();
        sendCommandDialogFragment.message = message;
        sendCommandDialogFragment.command = command;
        sendCommandDialogFragment.listener = listener;
        sendCommandDialogFragment.intent = intent;
        return sendCommandDialogFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                listener.sendCommand(command, intent);
            }
        });
        builder.setMessage(Html.fromHtml("<font color=\"#808080\">" + getActivity().getString(message) + "</font>"));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        builder.setIcon(R.drawable.ic_warning_gray);
        return builder.create();
    }
}
