package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.Permissions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LocationPermissionDialogFragment extends DialogFragment {

    private int code;

    public static LocationPermissionDialogFragment newInstance(int code) {
        LocationPermissionDialogFragment instance = new LocationPermissionDialogFragment();
        instance.code = code;
        return instance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.FullScreenDialog);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fullscreen_dialog, container, false);
        TextView message = view.findViewById(R.id.message);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final String msg = getString(R.string.location_service_permission, getString(R.string.app_name))
                             + " " + getString(R.string.permission_settings, "Location")
                             + " Finally please select \"Allow all the time\" option." ;
            message.setText(Html.fromHtml(msg));
        } else {
            message.setText(Html.fromHtml(getString(R.string.location_service_permission)));
        }
        Button cancel = view.findViewById(R.id.buttonCancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });

        Button ok = view.findViewById(R.id.buttonOK);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Permissions.requestLocationPermission(getActivity(), code);
                dismiss();
            }
        });
        return view;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

        /*@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIcon(R.drawable.ic_location_on_gray);
        builder.setMessage(Html.fromHtml(getString(R.string.location_service_permission)));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Permissions.requestLocationPermission(getActivity(), code);
            }
        });
        builder.setNegativeButton("Cancel", null);
        return builder.create();
    }*/
}
