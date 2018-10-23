package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.model.Device;

import org.apache.commons.lang3.StringUtils;

public class RemoveDeviceDialogFragment extends DialogFragment {

    public static final String TAG = "RemoveDeviceDialog";

    private RemoveDeviceDialogListener parent;
    private Device device;

    public interface RemoveDeviceDialogListener {
        void onDeleteDevice(String imei);
    }

    public static RemoveDeviceDialogFragment newInstance(RemoveDeviceDialogListener removeListener, Device device) {
        RemoveDeviceDialogFragment removeDeviceDialogFragment = new RemoveDeviceDialogFragment();
        removeDeviceDialogFragment.parent = removeListener;
        removeDeviceDialogFragment.device = device;
        return removeDeviceDialogFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        alertDialogBuilder.setMessage("Remove device " + (StringUtils.isNotEmpty(device.name) ? device.name : device.imei) + " from the list?");
        alertDialogBuilder.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        parent.onDeleteDevice(device.imei);
                    }
                });
        alertDialogBuilder.setNegativeButton(R.string.no, null);
        alertDialogBuilder.setIcon(R.drawable.ic_warning_gray);
        return alertDialogBuilder.create();
    }
}