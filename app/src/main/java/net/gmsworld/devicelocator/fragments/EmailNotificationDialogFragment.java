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
    private String[] userLogins = null;
    private int checkedItem = 0;
    private EmailNotificationDialogListener initListener;

    public interface EmailNotificationDialogListener {
        void registerEmail(TextView emailInput, boolean validate, boolean retry);
    }

    public static EmailNotificationDialogFragment newInstance(EmailNotificationDialogListener initListener, String... userLogin) {
        EmailNotificationDialogFragment emailNotificationDialogFragment = new EmailNotificationDialogFragment();
        emailNotificationDialogFragment.userLogins = userLogin;
        emailNotificationDialogFragment.initListener = initListener;
        return emailNotificationDialogFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        final TextView emailInput = getActivity().findViewById(R.id.email);

        if (userLogins.length == 1) {
            alertDialogBuilder.setMessage(Html.fromHtml(getString(R.string.email_notification_message, userLogins[checkedItem])));
            alertDialogBuilder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
            alertDialogBuilder.setPositiveButton(R.string.yes,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            emailInput.setText(userLogins[checkedItem]);
                            initListener.registerEmail(emailInput, false, false);
                        }
                    });
            alertDialogBuilder.setNegativeButton(R.string.no, null);
            alertDialogBuilder.setIcon(R.drawable.ic_warning_gray);
        } else {
            alertDialogBuilder.setTitle("Choose notification email");// add a radio button list
            alertDialogBuilder.setSingleChoiceItems(userLogins, checkedItem, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    checkedItem = which;
                }
            });
            alertDialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    emailInput.setText(userLogins[checkedItem]);
                    initListener.registerEmail(emailInput, false,false);
                }
            });
            alertDialogBuilder.setNegativeButton("Cancel", null);
            alertDialogBuilder.setIcon(R.drawable.ic_warning_gray);
        }
        return alertDialogBuilder.create();
    }
}
