package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.Toaster;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class QuotaLimitNotificationDialogFragment extends DialogFragment {

    private String commandName, queryString, tokenStr, deviceName;

    private Toaster toaster;

    public static final String TAG = "QuotaLimitDialog";

    public QuotaLimitNotificationDialogFragment() {}

    public static QuotaLimitNotificationDialogFragment newInstance(String commandName, String queryString, String tokenStr, String deviceName, Toaster toaster) {
        QuotaLimitNotificationDialogFragment newQuotaLimitNotificationDialog = new QuotaLimitNotificationDialogFragment(commandName, queryString, tokenStr, deviceName, toaster);
        return newQuotaLimitNotificationDialog;
    }

    public QuotaLimitNotificationDialogFragment(String commandName, String queryString, String tokenStr, String deviceName, Toaster toaster) {
        this.commandName = commandName;
        this.queryString = queryString;
        this.tokenStr = tokenStr;
        this.deviceName = deviceName;
        this.toaster = toaster;
    }

    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        alertDialogBuilder.setMessage("You have reached quota limit for sending commmand " + commandName +
                " to the device " + deviceName + ". Do you want to increase your limit?");
        alertDialogBuilder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        alertDialogBuilder.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            sendCommand(queryString + "&action=reset_quota", commandName, tokenStr);
                            getActivity().finish();
                        }
        });
        alertDialogBuilder.setNegativeButton(R.string.no,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getActivity().finish();
                    }
                });
        alertDialogBuilder.setIcon(R.drawable.ic_help_gray);
        return alertDialogBuilder.create();
    }

    private void sendCommand(final String queryString, final String commandName, final String tokenStr) {
        final Context context = getContext();
        if (context != null && Network.isNetworkAvailable(context)) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + tokenStr);
            Network.post(context, getString(R.string.deviceManagerUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            toaster.showActivityToast("Quota limit for command " + commandName + " has been reset!");
                        } else {
                            toaster.showActivityToast("Failed to reset quota. Please send command " + commandName + "again!");
                        }
                    }
                });
        } else {
            toaster.showActivityToast(R.string.no_network_error);
        }
    }
}
