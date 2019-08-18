package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;

import net.gmsworld.devicelocator.R;

import androidx.appcompat.app.AlertDialog;

public class EmailNotificationDialogFragment extends DialogFragment {

    public static final String TAG = "EmailNotificationDialog";
    private String userLogin = null;

    public static EmailNotificationDialogFragment newInstance(String userLogin) {
        EmailNotificationDialogFragment emailNotificationDialogFragment = new EmailNotificationDialogFragment();
        emailNotificationDialogFragment.userLogin = userLogin;
        return emailNotificationDialogFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        final TextView emailInput = getActivity().findViewById(R.id.email);
        alertDialogBuilder.setMessage(Html.fromHtml(getString(R.string.email_notification_message, userLogin)));
        alertDialogBuilder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        alertDialogBuilder.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        emailInput.setText(userLogin);
                    }
                });
        alertDialogBuilder.setNegativeButton(R.string.no, null);
        alertDialogBuilder.setIcon(R.drawable.ic_warning_gray);
        return alertDialogBuilder.create();
    }
}
