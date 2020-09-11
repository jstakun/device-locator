package net.gmsworld.devicelocator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;

import net.gmsworld.devicelocator.fragments.EmailActivationDialogFragment;
import net.gmsworld.devicelocator.fragments.EmailNotificationDialogFragment;
import net.gmsworld.devicelocator.fragments.NotificationActivationDialogFragment;
import net.gmsworld.devicelocator.services.SmsSenderService;
import net.gmsworld.devicelocator.utilities.LinkMovementMethodFixed;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.utilities.Toaster;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity implements NotificationActivationDialogFragment.NotificationActivationDialogListener, EmailNotificationDialogFragment.EmailNotificationDialogListener {

    public static final String PRIVACY_POLICY = "privacy_policy";

    protected static final String ACTION_VERIFY = "VERIFY";
    protected static final String ACTION_INVALID = "INVALID";

    private static final String TAG = RegisterActivity.class.getSimpleName();

    private PreferencesUtils settings;

    private Toaster toaster;

    private String action = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        setContentView(R.layout.activity_register);

        settings = new PreferencesUtils(this);

        toaster = new Toaster(this);

        TextView privacyPolicyLink = findViewById(R.id.privacy_policy_text);
        privacyPolicyLink.setText(Html.fromHtml(getString(R.string.privacy_policy_text)));
        privacyPolicyLink.setMovementMethod(LinkMovementMethodFixed.getInstance());

        initEmailButton();

        initRegisterButton();

        initEmailInput(false);

        Intent intent = getIntent();

        if (intent != null) {
            action = intent.getAction();
        }

        FirebaseAnalytics.getInstance(this).logEvent("register_activity", new Bundle());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");

        Switch privacyPolicyPermission = findViewById(R.id.privacy_policy);
        privacyPolicyPermission.setChecked(settings.getBoolean(PRIVACY_POLICY, false));

        Switch accessFineLocationPermission = findViewById(R.id.location_policy);
        accessFineLocationPermission.setChecked(Permissions.haveLocationPermission(this));

        if (StringUtils.equals(action, ACTION_VERIFY)) {
            Log.d(TAG, "Sending email registration confirmation request...");
            NotificationActivationDialogFragment notificationActivationDialogFragment = NotificationActivationDialogFragment.newInstance(NotificationActivationDialogFragment.Mode.Email, toaster, this);
            notificationActivationDialogFragment.onEnteredActivationCode(this, settings, settings.getString(NotificationActivationDialogFragment.EMAIL_SECRET));
        } else {
            if (StringUtils.equals(action, ACTION_INVALID)) {
                Log.d(TAG, "Invalid link has been clicked...");
                initEmailInput(true);
            } else {
                initEmailInput(false);
            }
        }
    }

    @Override
    protected void onNewIntent (Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent()");

        if (intent != null) {
            action = intent.getAction();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case Permissions.PERMISSIONS_REQUEST_GET_EMAIL:
                if (Permissions.haveGetAccountsPermission(this)) {
                    initEmailListDialog();
                }
                break;
            case Permissions.PERMISSIONS_LOCATION:
                //send device location to admin channel
                Bundle extras = new Bundle();
                extras.putString("telegramId", getString(R.string.telegram_notification));
                SmsSenderService.initService(this, false, false, true, null, null, null, null, extras);
                break;
            default:
                break;
        }
    }

    public void onSwitchSelected(View view) {
        boolean checked = ((Switch) view).isChecked();

        switch (view.getId()) {
            case R.id.privacy_policy:
                settings.setBoolean(PRIVACY_POLICY, checked);
                break;
            case R.id.location_policy:
                if (checked && !Permissions.haveLocationPermission(this)) {
                    Permissions.requestLocationPermission(this, Permissions.PERMISSIONS_LOCATION);
                } else if (!checked) {
                    Permissions.startSettingsIntent(this , "Location");
                }
                break;
            default:
                break;
        }
    }

    private void initEmailButton() {
        final ImageButton emailButton = this.findViewById(R.id.email_button);

        emailButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Permissions.haveGetAccountsPermission(RegisterActivity.this)) {
                    initEmailListDialog();
                } else {
                    toaster.showActivityToast("Please grant this permission to list accounts registered on this device");
                    Permissions.requestGetAccountsPermission(RegisterActivity.this, Permissions.PERMISSIONS_REQUEST_GET_EMAIL);
                }
            }
        });
    }

    private void initEmailListDialog() {
        List<String> accountNames = new ArrayList<>();

        Account[] dlAccounts = AccountManager.get(this).getAccountsByType(getString(R.string.account_type));
        for (Account a : dlAccounts) {
            if (!accountNames.contains(a.name)) {
                accountNames.add(a.name);
            }
        }

        Account[] allAccounts = AccountManager.get(this).getAccounts();
        for (Account a : allAccounts) {
            //Log.d(TAG, "Found account " + a.name);
            if (Patterns.EMAIL_ADDRESS.matcher(a.name).matches()) {
                if (!accountNames.contains(a.name)) {
                    accountNames.add(a.name);
                }
            }
        }

        if (!accountNames.isEmpty()) {
            EmailNotificationDialogFragment emailNotificationDialogFragment = EmailNotificationDialogFragment.newInstance(this, accountNames.toArray(new String[accountNames.size()]));
            emailNotificationDialogFragment.show(getFragmentManager(), EmailNotificationDialogFragment.TAG);
        } else {
            toaster.showActivityToast("No email addresses are registered on this device. Please enter new one!");
        }
    }

    private void initEmailInput(boolean retry) {
        final TextView emailInput = findViewById(R.id.email);
        final String email = settings.getString(MainActivity.NOTIFICATION_EMAIL);
        if (StringUtils.isNotEmpty(email)) {
            emailInput.setText(email);
        }

        emailInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                   registerEmail(emailInput, true, false);
                }
            }
        });

        emailInput.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                    registerEmail(emailInput, true, false);
                }
                return false;
            }
        });

        emailInput.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                //Log.d(TAG, "Soft keyboard event " + keyCode);
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_BACK:
                            registerEmail(emailInput, true, false);
                            break;
                        default:
                            break;
                    }
                }
                return false;
            }
        });

        if (StringUtils.isNotEmpty(email) && !Messenger.isEmailVerified(settings)) {
            if (settings.getBoolean(EmailActivationDialogFragment.TAG, false)) {
                settings.remove(EmailActivationDialogFragment.TAG);
                Messenger.sendEmailRegistrationRequest(this, email, false, 1);
            }
            showEmailActivationDialogFragment(retry);
        }
    }

    public void showEmailActivationDialogFragment(boolean retry) {
        if (!isFinishing()) {
            EmailActivationDialogFragment emailActivationDialogFragment = (EmailActivationDialogFragment) getFragmentManager().findFragmentByTag(EmailActivationDialogFragment.TAG);
            EmailActivationDialogFragment.Mode mode;
            if (retry) {
                mode = EmailActivationDialogFragment.Mode.Retry;
            } else {
                mode = EmailActivationDialogFragment.Mode.Initial;
            }
            if (emailActivationDialogFragment == null) {
                emailActivationDialogFragment = EmailActivationDialogFragment.newInstance(toaster, mode);
                toaster.cancel();
                FragmentManager fm = getFragmentManager();
                if (fm != null) {
                    //emailActivationDialogFragment.show(fm, EmailActivationDialogFragment.TAG);
                    FragmentTransaction ft = fm.beginTransaction();
                    ft.add(emailActivationDialogFragment, EmailActivationDialogFragment.TAG);
                    ft.commitAllowingStateLoss();
                } else {
                    Log.e(TAG, "FragmentManager is null!");
                }
            } else {
                emailActivationDialogFragment.setToaster(toaster);
                emailActivationDialogFragment.setMode(mode);
            }
        }
    }

    private void initRegisterButton() {
        Button registerButton = findViewById(R.id.register_button);
        final FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(this);
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final TextView email = findViewById(R.id.email);
                final String newEmailAddress = email.getText().toString();
                if (StringUtils.isNotEmpty(newEmailAddress) && Patterns.EMAIL_ADDRESS.matcher(newEmailAddress).matches()) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
                    }
                    if (settings.getBoolean(PRIVACY_POLICY, false)) {
                        if (Permissions.haveLocationPermission(RegisterActivity.this)) {
                            registerEmail(email, true, true);
                            firebaseAnalytics.logEvent("register_ok", new Bundle());
                        } else {
                            toaster.showActivityToast(R.string.location_policy_toast);
                            firebaseAnalytics.logEvent("register_location_permission", new Bundle());
                        }
                    } else {
                        toaster.showActivityToast(R.string.privacy_policy_toast);
                        firebaseAnalytics.logEvent("register_privacy_policy", new Bundle());
                    }
                } else {
                    toaster.showActivityToast(R.string.email_invalid_error);
                    email.setText("");
                    firebaseAnalytics.logEvent("register_invalid_email", new Bundle());
                }
            }
        });
    }

    public void openMainActivity() {
        Log.d(TAG, "openMainActivity()");
        toaster.cancel();
        settings.setString(MainActivity.USER_LOGIN, settings.getString(MainActivity.NOTIFICATION_EMAIL));
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(MainActivity.ACTION_DEVICE_MANAGER);
        startActivity(intent);
        finish();
    }

    @Override
    public void registerEmail(TextView emailInput, boolean validate, boolean sendRequest) {
        final String newEmailAddress = StringUtils.lowerCase(emailInput.getText().toString());
        if (StringUtils.isNotEmpty(newEmailAddress) && Patterns.EMAIL_ADDRESS.matcher(newEmailAddress).matches()) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
            }
            if (sendRequest || (settings.getBoolean(PRIVACY_POLICY, false) && Permissions.haveLocationPermission(RegisterActivity.this))) {
                if (Network.isNetworkAvailable(RegisterActivity.this)) {
                    Log.d(TAG, "Setting new email address: " + newEmailAddress);
                    settings.setString(MainActivity.NOTIFICATION_EMAIL, newEmailAddress);
                    toaster.showActivityToast("Email verification in progress...");
                    Messenger.sendEmailRegistrationRequest(RegisterActivity.this, newEmailAddress, validate, 1);
                } else {
                    toaster.showActivityToast(R.string.no_network_error);
                    emailInput.setText("");
                }
            }
        } else {
            toaster.showActivityToast(R.string.email_invalid_error);
            emailInput.setText("");
        }
    }
}