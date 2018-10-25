package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Html;

import net.gmsworld.devicelocator.R;

public class FirstTimeUseDialogFragment extends DialogFragment {

    private int messageId;
    private int iconId;

    public static FirstTimeUseDialogFragment newInstance(int messageId, int iconId) {
        FirstTimeUseDialogFragment firstTimeUseDialogFragment = new FirstTimeUseDialogFragment();
        firstTimeUseDialogFragment.messageId = messageId;
        firstTimeUseDialogFragment.iconId = iconId;
        return firstTimeUseDialogFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setPositiveButton("Ok", null);
        builder.setMessage(Html.fromHtml(getString(messageId)));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        if (iconId > 0) {
            builder.setIcon(iconId);
        }
        return builder.create();
    }
}
