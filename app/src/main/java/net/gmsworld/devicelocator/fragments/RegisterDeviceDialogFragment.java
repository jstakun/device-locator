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
import net.gmsworld.devicelocator.utilities.Toaster;

import androidx.appcompat.app.AlertDialog;

public class RegisterDeviceDialogFragment extends DialogFragment {
    public static final String TAG = "RegisterDeviceDialog";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        //builder.setNegativeButton("Later", null);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Intent intent = new Intent(getActivity(), MainActivity.class);
                intent.setAction(MainActivity.ACTION_DEVICE_MANAGER);
                startActivity(intent);
            }
        });
        builder.setMessage(Html.fromHtml(getString(R.string.register_device)));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        builder.setIcon(R.drawable.ic_warning_gray);//R.drawable.ic_help_gray);
        return builder.create();
    }

    public static void showRegisterDeviceDialogFragment(Activity activity, Toaster toaster) {
        if (!activity.isFinishing()) {
            RegisterDeviceDialogFragment registerDeviceDialogFragment = (RegisterDeviceDialogFragment) activity.getFragmentManager().findFragmentByTag(RegisterDeviceDialogFragment.TAG);
            if (registerDeviceDialogFragment == null) {
                registerDeviceDialogFragment = new RegisterDeviceDialogFragment();
                if (toaster != null) {
                    toaster.cancel();
                }
                FragmentManager fm = activity.getFragmentManager();
                if (fm != null) {
                    FragmentTransaction ft = fm.beginTransaction();
                    ft.add(registerDeviceDialogFragment, RegisterDeviceDialogFragment.TAG);
                    ft.commitAllowingStateLoss();
                } else {
                    Log.e(TAG, "FragmentManager is null!");
                }
            }
        }
    }
}
