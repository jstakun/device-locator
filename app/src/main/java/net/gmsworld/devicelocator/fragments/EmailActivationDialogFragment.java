package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.utilities.Toaster;

import org.apache.commons.lang3.StringUtils;

import androidx.appcompat.app.AlertDialog;

public class EmailActivationDialogFragment extends DialogFragment {

    public static final String TAG = "EmailActivationDialog";

    private Toaster toaster;

    public static EmailActivationDialogFragment newInstance(Toaster toaster) {
        EmailActivationDialogFragment instance = new EmailActivationDialogFragment();
        instance.toaster = toaster;
        return instance;
    }

    public void setToaster(Toaster toaster) {
        this.toaster = toaster;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        alertDialogBuilder.setMessage(Html.fromHtml(getString(R.string.email_registration)));
        alertDialogBuilder.setIcon(R.drawable.ic_warning_gray);
        alertDialogBuilder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));

        final PreferencesUtils settings = new PreferencesUtils(getActivity());

        alertDialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        toaster.showActivityToast(R.string.please_wait);
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_APP_EMAIL);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            startActivity(Intent.createChooser(intent, "Open Mail Inbox"));
                        }
                        toaster.showActivityToast("Please open newest message from " + getActivity().getString(R.string.app_email)  + " and click the link to confirm your registration");
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Log.d(TAG, "Sending again email registration request ...");
                        final String email = settings.getString(MainActivity.NOTIFICATION_EMAIL);
                        if (StringUtils.isNotEmpty(email)) {
                            Messenger.sendEmailRegistrationRequest(getActivity(), email, true, 1);
                            toaster.showActivityToast(R.string.please_wait);
                            EmailActivationDialogFragment.this.dismiss();
                        } else {
                            toaster.showActivityToast("Failed to send email registration request!");
                        }
                        //old Forget Me action
                        /*Log.d(TAG, "Cancelling email registration request ...");
                        settings.remove(MainActivity.NOTIFICATION_EMAIL, MainActivity.EMAIL_REGISTRATION_STATUS, NotificationActivationDialogFragment.EMAIL_SECRET);
                        final TextView emailInput = getActivity().findViewById(R.id.email);
                        emailInput.setText("");
                        EmailActivationDialogFragment.this.dismiss();*/
                    }
                });

        return alertDialogBuilder.create();
    }
}
