package net.gmsworld.devicelocator.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;

import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.android.play.core.tasks.Task;

import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.utilities.Toaster;

import androidx.appcompat.app.AlertDialog;

public class AppReviewDialogFragment extends DialogFragment {

    public static final String TAG = "AppReviewDialog";

    private ReviewInfo reviewInfo;
    private PreferencesUtils settings;
    private Toaster toaster;

    public static AppReviewDialogFragment newInstance(PreferencesUtils settings, Toaster toaster, Activity activity) {
        AppReviewDialogFragment appReviewDialogFragment = new AppReviewDialogFragment();
        appReviewDialogFragment.settings = settings;
        appReviewDialogFragment.toaster = toaster;
        appReviewDialogFragment.initAppReview(activity);
        return appReviewDialogFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //init review dialog
                if (reviewInfo != null) {
                    showAppReview(getActivity());
                } else {
                    final int useCount = settings.getInt("useCount", 0);
                    settings.setInt("appReview", useCount + 10);
                    toaster.showActivityToast(R.string.internal_error);
                }
            }
        });
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                final int useCount = settings.getInt("useCount", 0);
                settings.setInt("appReview", useCount + 10);
            }
        });
        builder.setMessage(Html.fromHtml(getString(R.string.review_dialog_desc)));
        builder.setTitle(Html.fromHtml(getString(R.string.app_name_html)));
        builder.setIcon(R.drawable.ic_account_circle_gray);
        return builder.create();
    }

    private void initAppReview(Activity activity) {
        ReviewManager manager = ReviewManagerFactory.create(activity); //ReviewManagerFactory.create(activity);//new FakeReviewManager(activity);//
        Task<ReviewInfo> request = manager.requestReviewFlow();
        request.addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    reviewInfo = task.getResult();
                    Log.d(TAG, "Received app review info object");
                } else {
                    Log.e(TAG, "Failed to get app review info object");
                }
        });
    }

    private void showAppReview(Activity activity) {
        if (reviewInfo != null) {
            final int useCount = settings.getInt("useCount", 0);
            ReviewManager manager = ReviewManagerFactory.create(activity);
            Task<Void> flow = manager.launchReviewFlow(activity, reviewInfo);
            flow.addOnCompleteListener(flowTask -> {
                settings.setInt("appReview", -1);
                Log.d(TAG, "App review has been done");
                reviewInfo  = null;

            });
            flow.addOnFailureListener(flowTask -> {
                settings.setInt("appReview", useCount + 10);
                Log.e(TAG, flowTask.getMessage());
                reviewInfo = null;
            });
        } else {
            Log.d(TAG,"Review info object is null");
        }
    }
}
