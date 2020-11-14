package net.gmsworld.devicelocator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.utilities.LinkMovementMethodFixed;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.utilities.SCUtils;
import net.gmsworld.devicelocator.utilities.Toaster;

import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Base64;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

public class LoginActivity extends AppCompatActivity  {

    private UserLoginTask mAuthTask = null;

    private static final String TAG = LoginActivity.class.getSimpleName();

    private TextView mEmailView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;
    private Toaster toaster;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        // Set up the login form.
        mEmailView = findViewById(R.id.email);

        mPasswordView = findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button mEmailSignInButton = findViewById(R.id.email_sign_in_button);
        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        ViewCompat.setBackgroundTintList(mEmailSignInButton, ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);

        TextView register = findViewById(R.id.register);
        register.setText(Html.fromHtml(getString(R.string.registerLink)));
        register.setMovementMethod(LinkMovementMethodFixed.getInstance());

        TextView reset = findViewById(R.id.reset);
        reset.setText(Html.fromHtml(getString(R.string.resetLink)));

        toaster = new Toaster(this);

        FirebaseAnalytics.getInstance(this).logEvent("login_activity", new Bundle());
    }

    private void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (StringUtils.isEmpty(password)) {
            mPasswordView.setError(getString(R.string.error_field_required));
            focusView = mPasswordView;
            cancel = true;
        } else if (!isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (StringUtils.isEmpty(email)) {
            mEmailView.setError(getString(R.string.error_field_required));
            focusView = mEmailView;
            cancel = true;
        } else if (!isEmailValid(email)) {
            mEmailView.setError(getString(R.string.error_invalid_email));
            focusView = mEmailView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
            }
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            if (Network.isNetworkAvailable(this)) {
                showProgress(true);
                mAuthTask = new UserLoginTask(email, password, this);
                mAuthTask.execute((Void) null);
            } else {
                toaster.showActivityToast(R.string.no_network_error);
            }
        }
    }

    public void resetPassword(final View v) {
        String email = mEmailView.getText().toString();
        if (StringUtils.isNotEmpty(email)) {
            if (Network.isNetworkAvailable(this)) {
                toaster.showActivityToast(R.string.please_wait);
                PreferencesUtils settings = new PreferencesUtils(this);
                final String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN);
                if (StringUtils.isNotEmpty(tokenStr)) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer " + tokenStr);
                    String deviceId = Messenger.getDeviceId(this, false);
                    if (StringUtils.isNotEmpty(deviceId)) {
                        headers.put("X-GMS-DeviceId", deviceId);
                    }
                    headers.put("X-GMS-UseCount", Integer.toString(settings.getInt("useCount", 1)));
                    final String queryString = "type=reset&login=" + email;
                    Network.post(this, getString(R.string.notificationUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            if (responseCode == 200 && StringUtils.startsWith(results, "{")) {
                                JsonElement reply = new JsonParser().parse(results);
                                String status = null;
                                if (reply != null) {
                                    JsonElement st = reply.getAsJsonObject().get("status");
                                    if (st != null) {
                                        status = st.getAsString();
                                    }
                                }
                                if (StringUtils.equals(status, "ok")) {
                                    toaster.showActivityToast(R.string.check_email_message);
                                } else {
                                    toaster.showActivityToast(R.string.internal_error);
                                }
                            } else if (responseCode == 400) {
                                toaster.showActivityToast(R.string.failed_reset);
                            } else {
                                toaster.showActivityToast(R.string.internal_error);
                            }
                        }
                    });
                } else {
                    String queryString = "scope=dl&user=" + Messenger.getDeviceId(this, false);
                    Network.get(this, getString(R.string.tokenUrl) + "?" + queryString, null, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            if (responseCode == 200) {
                                if (StringUtils.isNotEmpty(Messenger.getToken(LoginActivity.this, results))) {
                                    resetPassword(v);
                                } else {
                                    toaster.showActivityToast(R.string.internal_error);
                                    Log.e(TAG, "Failed to parse token!");
                                }
                            } else {
                                toaster.showActivityToast(R.string.internal_error);
                                Log.d(TAG, "Failed to receive token: " + results);
                            }
                        }
                    });
                }
            } else {
                toaster.showActivityToast(R.string.no_network_error);
            }
        } else {
            toaster.showActivityToast(R.string.enter_login);
        }
    }

    private static boolean isEmailValid(String email) {
        String regex = "^[a-zA-Z0-9_-]{4,24}$";
        return (email.matches(regex));
    }

    private static boolean isPasswordValid(String password) {
        String regex = "^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9_-]{6,24}$";
        return (password.matches(regex));
    }

    private void showProgress(final boolean show) {
        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProgressView.animate().setDuration(shortAnimTime).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE); }
            });
    }

    public void onUrlClick(final View view) {
        TextView textView = (TextView)view;
        if (textView.getId() == R.id.register) {
            Intent gmsIntent = new Intent(this, WebViewActivity.class);
            gmsIntent.putExtra("url", getString(R.string.registerLink));
            gmsIntent.putExtra("title", getString(R.string.app_name) + " Registration");
            startActivity(gmsIntent);
        }
    }

    private static class UserLoginTask extends AsyncTask<Void, Void, Boolean> {

        private final String mEmail;
        private final String mPassword;
        private final WeakReference<LoginActivity> context;

        UserLoginTask(String email, String password, LoginActivity context) {
            mEmail = email;
            mPassword = password;
            this.context = new WeakReference<>(context);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Context c = context.get();
            Map<String, String> headers = new HashMap<>();
            final String user_password = mEmail + ":" + mPassword;
            final String encodedAuthorization = Base64.toBase64String(user_password.getBytes());
            headers.put("Authorization", "Basic " + encodedAuthorization);
            Network.get(c, c.getString(R.string.authnUrl) + "?scope=dl", headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    final LoginActivity activity = context.get();
                    if (activity != null) {
                        activity.showProgress(false);
                        activity.mAuthTask = null;
                        if (responseCode == 200 && StringUtils.startsWith(results, "{")) {
                            JsonElement reply = new JsonParser().parse(results);
                            String gmsToken = reply.getAsJsonObject().get(DeviceLocatorApp.GMS_TOKEN).getAsString();
                            if (StringUtils.isNotEmpty(gmsToken)) {
                                try {
                                    String pwd = android.util.Base64.encodeToString(SCUtils.encrypt(mPassword.getBytes(), activity), android.util.Base64.NO_PADDING);
                                    createAccount(mEmail, pwd, gmsToken, activity);
                                    activity.toaster.showActivityToast(R.string.login_successful);
                                    activity.finish();
                                } catch (Exception e) {
                                    Log.e(TAG, "Unable to encrypt password", e);
                                    activity.toaster.showActivityToast(R.string.sign_in_error);
                                }
                            } else {
                                Log.e(TAG, "Oops! Something went wrong. Token has been empty!");
                                activity.toaster.showActivityToast(R.string.sign_in_error);
                            }
                        } else {
                            activity.mPasswordView.setError(activity.getString(R.string.error_incorrect_password));
                            activity.mPasswordView.requestFocus();
                        }
                    }
                }
            });
            return true;
        }

        @Override
        protected void onCancelled() {
            final LoginActivity activity = context.get();
            if (activity != null) {
                activity.mAuthTask = null;
                activity.showProgress(false);
            }
        }

        private static void createAccount(String login, String password, String authToken, Context context) {
            Account account = new Account(login, context.getString(R.string.account_type));
            AccountManager am = AccountManager.get(context);
            am.addAccountExplicitly(account, password, null);
            am.setAuthToken(account, "full_access", authToken);
        }
    }
}

