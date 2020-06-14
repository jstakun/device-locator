package net.gmsworld.devicelocator.utilities;

import android.app.Activity;
import android.app.Service;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import net.gmsworld.devicelocator.R;

public class Toaster {

    private Activity activity;
    private Handler toastHandler;
    private Service service;
    private Toast toast;

    public Toaster(Activity activity) {
        this.activity = activity;
    }

    public Toaster(Service service) {
        this.service = service;
        toastHandler = new Handler(service.getMainLooper());
    }

    public void showActivityToast(int resId, final Object... args) {
        showActivityToast(activity.getString(resId, args));
    }

    public void showActivityToast(String text) {
        if (toast != null) {
            toast.cancel();
        }

        LayoutInflater inflater = activity.getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast_layout, (ViewGroup) activity.findViewById(R.id.toast_container));
        TextView toastText = layout.findViewById(R.id.toast_text);
        toastText.setText(text);

        toast = new Toast(activity);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }

    public void showServiceToast(final int messageId, final Object... args) {
        showServiceToast(service.getString(messageId, args));
    }

    public void showServiceToast(final String text) {
        toastHandler.post(new Runnable() {
            @Override
            public void run() {
                if (toast != null) {
                    toast.cancel();
                }

                LayoutInflater inflater = LayoutInflater.from(service);
                View layout = inflater.inflate(R.layout.toast_layout, null);
                TextView toastText = layout.findViewById(R.id.toast_text);
                toastText.setText(text);

                toast = new Toast(service);
                toast.setDuration(Toast.LENGTH_LONG);
                toast.setView(layout);
                toast.show();
            }
        });
    }

    public void cancel() {
        if (toast != null) {
            toast.cancel();
        }
    }
}
