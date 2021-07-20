package net.gmsworld.devicelocator.utilities;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import net.gmsworld.devicelocator.R;

public class Toaster {

    private static final String TAG = Toaster.class.getName();

    private Activity activity;
    private Service service;
    private Toast toast;

    //TODO replace static methods with interface implemented in activity
    public static void showToast(final Context context, final int messageId, final Object... args) {
        Toaster toaster;
        if (context instanceof Service) {
            toaster = new Toaster((Service)context);
            toaster.showServiceToast(messageId, args);
        } else if (context instanceof Activity) {
            toaster = new Toaster((Activity)context);
            toaster.showActivityToast(messageId, args);
        } else {
            Log.e(TAG, "Can show toast for "  + context.getClass().getName());
        }
    }

    public static void showToast(final Context context, final String text) {
        Toaster toaster;
        if (context instanceof Service) {
            toaster = new Toaster((Service)context);
            toaster.showServiceToast(text);
        } else if (context instanceof Activity) {
            toaster = new Toaster((Activity)context);
            toaster.showActivityToast(text);
        } else {
            Log.e(TAG, "Can show toast for "  + context.getClass().getName());
        }
    }

    public Toaster(Activity activity) {
        this.activity = activity;
    }

    public Toaster(Service service) {
        this.service = service;
    }

    public void showActivityToast(int resId, final Object... args) {
        if (activity != null) {
            showActivityToast(activity.getString(resId, args));
        } else {
            Log.e(TAG, "Unable to show Toast");
        }
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
        if (service != null) {
            showServiceToast(service.getString(messageId, args));
        } else {
            Log.e(TAG, "Unable to show Toast");
        }
    }

    public void showServiceToast(final String text) {
        if (service != null) {
            Handler toastHandler = new Handler(Looper.getMainLooper());
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
        } else {
            Log.e(TAG, "Unable to show Toast");
        }
    }

    public void cancel() {
        if (toast != null) {
            toast.cancel();
        }
    }
}
