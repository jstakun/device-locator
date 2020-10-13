package net.gmsworld.devicelocator.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
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

    public enum Mode {Initial, Retry};

    private Toaster toaster;

    private Mode mode;

    public void setToaster(Toaster toaster) {
        this.toaster = toaster;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        if (mode == Mode.Initial) {
            alertDialogBuilder.setMessage(Html.fromHtml(getString(R.string.email_registration_initial)));
        } else if (mode == Mode.Retry) {
            alertDialogBuilder.setMessage(Html.fromHtml(getString(R.string.email_registration_retry)));
        }
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
                        settings.setBoolean(TAG, true);
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
                    }
                });

        return alertDialogBuilder.create();
    }

    public static void showEmailActivationDialogFragment(boolean retry, Activity activity, Toaster toaster) {
        if (!activity.isFinishing()) {
            EmailActivationDialogFragment emailActivationDialogFragment = (EmailActivationDialogFragment) activity.getFragmentManager().findFragmentByTag(EmailActivationDialogFragment.TAG);
            EmailActivationDialogFragment.Mode mode;
            if (retry) {
                mode = EmailActivationDialogFragment.Mode.Retry;
            } else {
                mode = EmailActivationDialogFragment.Mode.Initial;
            }
            if (emailActivationDialogFragment == null) {
                emailActivationDialogFragment = new EmailActivationDialogFragment();
                emailActivationDialogFragment.setMode(mode);
                emailActivationDialogFragment.setToaster(toaster);
                toaster.cancel();
                FragmentManager fm = activity.getFragmentManager();
                if (fm != null) {
                    //emailActivationDialogFragment.show(fm, EmailActivationDialogFragment.TAG);
                    FragmentTransaction ft = fm.beginTransaction();
                    ft.add(emailActivationDialogFragment, EmailActivationDialogFragment.TAG);
                    ft.commitAllowingStateLoss();
                } else {
                    Log.e(TAG, "FragmentManager is null!");
                }
            } else {
                emailActivationDialogFragment.setToaster(toaster);
                emailActivationDialogFragment.setMode(mode);
            }
        }
    }
}
