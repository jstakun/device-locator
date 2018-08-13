package net.gmsworld.devicelocator.auth;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import net.gmsworld.devicelocator.LoginActivity;

import org.apache.commons.lang3.StringUtils;

public class GMSWorldAuthenticator extends AbstractAccountAuthenticator {

    private static final String TAG = "GMSWorldAuthenticator";

    private Context context;

    public GMSWorldAuthenticator(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse accountAuthenticatorResponse, String s) {
        return null;
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options) throws NetworkErrorException {
        final Intent intent = new Intent(context, LoginActivity.class);

        // This key can be anything. Try to use your domain/package
        intent.putExtra("accountType", accountType);

        // This key can be anything too. It's just a way of identifying the token's type (used when there are multiple permissions)
        intent.putExtra("authTokenType", authTokenType);

        // This key can be anything too. Used for your reference. Can skip it too.
        //intent.putExtra("is_adding_new_account", true);

        // Copy this exactly from the line below.
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);

        final Bundle bundle = new Bundle();

        bundle.putParcelable(AccountManager.KEY_INTENT, intent);

        return bundle;
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account, Bundle bundle) throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
        AccountManager am = AccountManager.get(context);

        String authToken = am.peekAuthToken(account, authTokenType);

        if (StringUtils.isEmpty(authToken)) {
            // TODO get token and decrypt password
            //authToken = HTTPNetwork.login(account.name, am.getPassword(account));
        }


        if (StringUtils.isNotEmpty(authToken)) {
            final Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
            result.putString(AccountManager.KEY_AUTHTOKEN, authToken);
            return result;
        }

        // If you reach here, person needs to login again. or sign up

        // If we get here, then we couldn't access the user's password - so we
        // need to re-prompt them for their credentials. We do that by creating
        final Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        //intent.putExtra("YOUR ACCOUNT TYPE", account.type);
        //intent.putExtra("full_access", authTokenType);

        Bundle retBundle = new Bundle();
        retBundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return retBundle;
    }

    @Override
    public String getAuthTokenLabel(String s) {
        return null;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account, String s, Bundle bundle) throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account, String[] strings) throws NetworkErrorException {
        return null;
    }
}
