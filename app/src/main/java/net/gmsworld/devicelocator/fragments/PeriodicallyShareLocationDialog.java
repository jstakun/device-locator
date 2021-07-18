package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.LocationAlarmUtils;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import androidx.appcompat.app.AlertDialog;

public class PeriodicallyShareLocationDialog extends DialogFragment {
    public static final String TAG = "PeriodicallyShareLocationDialog";

    private PreferencesUtils settings;

    public static PeriodicallyShareLocationDialog newInstance(PreferencesUtils settings) {
        PeriodicallyShareLocationDialog periodicallyShareLocationDialog = new PeriodicallyShareLocationDialog();
        periodicallyShareLocationDialog.settings = settings;
        return periodicallyShareLocationDialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        alertDialogBuilder.setMessage(Html.fromHtml(getString(R.string.periodically_share_location)));
        alertDialogBuilder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        alertDialogBuilder.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        settings.setBoolean(LocationAlarmUtils.ALARM_SETTINGS, true);
                        settings.setBoolean(LocationAlarmUtils.ALARM_SILENT, true);
                        settings.setInt(LocationAlarmUtils.ALARM_INTERVAL, 1);
                        LocationAlarmUtils.initWhenDown(getActivity(), true);
                    }
                });
        alertDialogBuilder.setNegativeButton(R.string.no, null);
        alertDialogBuilder.setIcon(R.drawable.ic_help_gray);
        return alertDialogBuilder.create();
    }
}
