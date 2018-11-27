package net.gmsworld.devicelocator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.SCUtils;

import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Base64;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity  {

    private UserLoginTask mAuthTask = null;

    private static final String TAG = LoginActivity.class.getSimpleName();

    private TextView mEmailView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;

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
        register.setMovementMethod(LinkMovementMethod.getInstance());

        TextView reset = findViewById(R.id.reset);
        reset.setText(Html.fromHtml(getString(R.string.resetLink)));
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
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
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
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            if (Network.isNetworkAvailable(this)) {
                showProgress(true);
                mAuthTask = new UserLoginTask(email, password, this);
                mAuthTask.execute((Void) null);
            } else {
                Toast.makeText(this, R.string.no_network_error, Toast.LENGTH_LONG).show();
            }
        }
    }

    public void resetPassword(final View v) {
        String email = mEmailView.getText().toString();
        if (StringUtils.isNotEmpty(email)) {
            if (Network.isNetworkAvailable(this)) {
                Toast.makeText(this, R.string.please_wait, Toast.LENGTH_LONG).show();
                final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
                String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN, "");
                if (StringUtils.isNotEmpty(tokenStr)) {
                    Map<String, String> headers = new HashMap<String, String>();
                    headers.put("Authorization", "Bearer " + tokenStr);
                    headers.put("X-GMS-AppId", "2");
                    String deviceId = Messenger.getDeviceId(this, false);
                    if (StringUtils.isNotEmpty(deviceId)) {
                        headers.put("X-GMS-DeviceId", deviceId);
                    }
                    headers.put("X-GMS-UseCount", Integer.toString(settings.getInt("useCount", 1)));
                    String queryString = "type=reset&login=" + email;
                    Network.post(this, getString(R.string.notificationUrl), queryString, null, headers, new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String results, int responseCode, String url) {
                            if (responseCode == 200) {
                                JsonElement reply = new JsonParser().parse(results);
                                String status = null;
                                if (reply != null) {
                                    JsonElement st = reply.getAsJsonObject().get("status");
                                    if (st != null) {
                                        status = st.getAsString();
                                    }
                                }
                                if (StringUtils.equals(status, "ok")) {
                                    Toast.makeText(LoginActivity.this, "Please check your email inbox for password reset instructions", Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(LoginActivity.this, R.string.internal_error, Toast.LENGTH_LONG).show();
                                }
                            } else if (responseCode == 400) {
                                Toast.makeText(LoginActivity.this, "Failed to reset password. Is your login valid?", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(LoginActivity.this, R.string.internal_error, Toast.LENGTH_LONG).show();
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
                                    Toast.makeText(LoginActivity.this, R.string.internal_error, Toast.LENGTH_LONG).show();
                                    Log.e(TAG, "Failed to parse token!");
                                }
                            } else {
                                Toast.makeText(LoginActivity.this, R.string.internal_error, Toast.LENGTH_LONG).show();
                                Log.d(TAG, "Failed to receive token: " + results);
                            }
                        }
                    });
                }
            } else {
                Toast.makeText(this, R.string.no_network_error, Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Please enter your login", Toast.LENGTH_LONG).show();
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

    static class UserLoginTask extends AsyncTask<Void, Void, Boolean> {

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
                        if (responseCode == 200) {
                            JsonElement reply = new JsonParser().parse(results);
                            String gmsToken = reply.getAsJsonObject().get(DeviceLocatorApp.GMS_TOKEN).getAsString();
                            if (StringUtils.isNotEmpty(gmsToken)) {
                                try {
                                    String pwd = android.util.Base64.encodeToString(SCUtils.encrypt(mPassword.getBytes(), activity), android.util.Base64.NO_PADDING);
                                    createAccount(mEmail, pwd, gmsToken, activity);
                                    Toast.makeText(activity, "Login successful!", Toast.LENGTH_LONG).show();
                                    activity.finish();
                                } catch (Exception e) {
                                    Log.e(TAG, "Unable to encrypt password", e);
                                    Toast.makeText(activity, "Internal error. Click SIGN-IN button again.", Toast.LENGTH_LONG).show();
                                }
                            } else {
                               Log.e(TAG, "Oops! Something went wrong. Token has been empty!");
                               Toast.makeText(activity, "Internal error. Click SIGN-IN button again.", Toast.LENGTH_LONG).show();
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

