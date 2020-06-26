package net.gmsworld.devicelocator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;

import net.gmsworld.devicelocator.fragments.EmailNotificationDialogFragment;
import net.gmsworld.devicelocator.fragments.NotificationActivationDialogFragment;
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

    private static final String TAG = RegisterActivity.class.getSimpleName();

    private PreferencesUtils settings;

    private Toaster toaster;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        settings = new PreferencesUtils(this);

        toaster = new Toaster(this);

        TextView privacyPolicyLink = findViewById(R.id.privacy_policy_text);
        privacyPolicyLink.setText(Html.fromHtml(getString(R.string.privacy_policy_text)));
        privacyPolicyLink.setMovementMethod(LinkMovementMethodFixed.getInstance());

        initEmailButton();

        initRegisterButton();

        initEmailInput();
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

    @Override
    protected void onResume() {
        super.onResume();

        Switch privacyPolicyPermission = findViewById(R.id.privacy_policy);
        privacyPolicyPermission.setChecked(settings.getBoolean(PRIVACY_POLICY, false));

        Switch accessFineLocationPermission = findViewById(R.id.location_policy);
        accessFineLocationPermission.setChecked(Permissions.haveLocationPermission(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case Permissions.PERMISSIONS_REQUEST_GET_EMAIL:
                if (Permissions.haveGetAccountsPermission(this)) {
                    initEmailListDialog();
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
            showEmailNotificationDialogFragment(accountNames.toArray(new String[accountNames.size()]));
        } else {
            toaster.showActivityToast("No email addresses are registered on this device. Please enter a new one!");
        }
    }

    private void initEmailInput() {
        final TextView emailInput = findViewById(R.id.email);
        final String email = settings.getString(MainActivity.NOTIFICATION_EMAIL);
        if (StringUtils.isNotEmpty(email)) {
            emailInput.setText(email);
        }

        if (StringUtils.isNotEmpty(email) && !Messenger.isEmailVerified(settings)) {
            NotificationActivationDialogFragment notificationActivationDialogFragment = NotificationActivationDialogFragment.newInstance(NotificationActivationDialogFragment.Mode.Email, toaster, this);
            notificationActivationDialogFragment.show(getFragmentManager(), NotificationActivationDialogFragment.TAG);
        }
    }

    private void showEmailNotificationDialogFragment(final String[] userLogins) {
        EmailNotificationDialogFragment emailNotificationDialogFragment = EmailNotificationDialogFragment.newInstance(this, userLogins);
        emailNotificationDialogFragment.show(getFragmentManager(), EmailNotificationDialogFragment.TAG);
    }

    private void initRegisterButton() {
        Button registerButton = findViewById(R.id.register_button);
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final TextView email = findViewById(R.id.email);
                final String newEmailAddress = email.getText().toString();
                if (StringUtils.isNotEmpty(newEmailAddress) && Patterns.EMAIL_ADDRESS.matcher(newEmailAddress).matches()) {
                    if (settings.getBoolean(PRIVACY_POLICY, false)) {
                        if (Permissions.haveLocationPermission(RegisterActivity.this)) {
                            registerEmail(email, true, true);
                        } else {
                            toaster.showActivityToast(R.string.location_policy_toast);
                        }
                    } else {
                        toaster.showActivityToast(R.string.privacy_policy_toast);
                    }
                } else {
                    toaster.showActivityToast(R.string.email_invalid_error);
                    email.setText("");
                }
            }
        });
    }

    public void openMainActivity() {
        toaster.cancel();
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void registerEmail(TextView emailInput, boolean validate, boolean sendRequest) {
        final String newEmailAddress = emailInput.getText().toString();
        if (StringUtils.isNotEmpty(newEmailAddress) && Patterns.EMAIL_ADDRESS.matcher(newEmailAddress).matches()) {
            if (sendRequest) {
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