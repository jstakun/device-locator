package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.widget.Toast;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.AppUtils;
import net.gmsworld.devicelocator.utilities.Network;

import java.util.HashMap;
import java.util.Map;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class QuotaLimitNotificationDialogFragment extends DialogFragment {

    private String commandName, queryString, tokenStr, deviceId, deviceName;

    public static final String TAG = "QuotaLimitDialog";

    public QuotaLimitNotificationDialogFragment() {}

    public static QuotaLimitNotificationDialogFragment newInstance(String commandName, String queryString, String tokenStr, String deviceName) {
        QuotaLimitNotificationDialogFragment newQuotaLimitNotificationDialog = new QuotaLimitNotificationDialogFragment(commandName, queryString, tokenStr, deviceName);
        return newQuotaLimitNotificationDialog;
    }

    public QuotaLimitNotificationDialogFragment(String commandName, String queryString, String tokenStr, String deviceName) {
        this.commandName = commandName;
        this.queryString = queryString;
        this.tokenStr = tokenStr;
        this.deviceName = deviceName;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
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
        if (Network.isNetworkAvailable(context)) {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-GMS-AppId", "2");
            headers.put("X-GMS-AppVersionId", Integer.toString(AppUtils.getInstance().getVersionCode(context)));
            headers.put("Authorization", "Bearer " + tokenStr);
            Network.post(context, getString(R.string.deviceManagerUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                    @Override
                    public void onGetFinish(String results, int responseCode, String url) {
                        if (responseCode == 200) {
                            Toast.makeText(context, "Quota limit for command " + commandName + " has been reset!", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(context, "Failed to reset quota. Please send command " + commandName + "again!", Toast.LENGTH_LONG).show();
                        }
                    }
                });
        } else {
            Toast.makeText(context, R.string.no_network_error, Toast.LENGTH_LONG).show();
        }
    }
}
