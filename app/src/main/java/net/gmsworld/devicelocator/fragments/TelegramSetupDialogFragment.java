package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.Messenger;

import androidx.appcompat.app.AlertDialog;

public class TelegramSetupDialogFragment extends DialogFragment {

    public static final String TAG = "TelegramSetupDialog";
    public static final String TELEGRAM_FAILED_SETUP_COUNT = "TelegramFailedSetupCount";

    public static TelegramSetupDialogFragment newInstance() {
        TelegramSetupDialogFragment telegramSetupDialogFragment = new TelegramSetupDialogFragment();
        return telegramSetupDialogFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setNegativeButton("Not now", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity)getActivity()).clearTelegramInput(true, null);
                }
            }
        });
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Messenger.getMyTelegramId(getActivity());
            }
        });
        builder.setMessage(Html.fromHtml(getString(R.string.telegram_init_prompt)));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        builder.setIcon(R.drawable.ic_warning_gray);
        return builder.create();
    }
}
