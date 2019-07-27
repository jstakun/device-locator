package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;

import net.gmsworld.devicelocator.R;

import androidx.appcompat.app.AlertDialog;

public class SmsNotificationWarningDialogFragment extends DialogFragment {

    public static final String TAG = "SmsNotificationWarningDialog";

    private SmsNotificationWarningDialogListener initListener;

    public interface SmsNotificationWarningDialogListener {
        void registerPhoneNumber(TextView phoneNumberInput);
    }

    public static SmsNotificationWarningDialogFragment newInstance(SmsNotificationWarningDialogListener initListener) {
        SmsNotificationWarningDialogFragment frag = new SmsNotificationWarningDialogFragment();
        frag.initListener = initListener;
        return frag;
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setPositiveButton(R.string.yes, null);
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                TextView phoneNumberInput = getActivity().findViewById(R.id.phoneNumber);
                phoneNumberInput.setText("");
                initListener.registerPhoneNumber(phoneNumberInput);
            }
        });
        builder.setMessage(Html.fromHtml(getString(R.string.sms_notification_warning)));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        builder.setIcon(R.drawable.ic_warning_gray);
        return builder.create();
    }
}
