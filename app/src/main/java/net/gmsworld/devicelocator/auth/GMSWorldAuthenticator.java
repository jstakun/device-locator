package net.gmsworld.devicelocator.auth;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.LoginActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.SCUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class GMSWorldAuthenticator extends AbstractAccountAuthenticator {

    private static final String TAG = "GMSWorldAuthenticator";

    private Context context;

    public GMSWorldAuthenticator(Context context) {
        super(context);
        this.context = context;
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
            try {
                String password = new String(SCUtils.decrypt(Base64.decode(am.getPassword(account), Base64.NO_PADDING), context));
                Map<String, String> headers = new HashMap<>();
                final String user_password = account.name + ":" + password;
                final String encodedAuthorization = org.spongycastle.util.encoders.Base64.toBase64String(user_password.getBytes());
                headers.put("Authorization", "Basic " + encodedAuthorization);
                String jsonResponse = Network.get(context, context.getString(R.string.serverUrl) + "s/authenticate?scope=dl", headers);
                JsonElement reply = new JsonParser().parse(jsonResponse);
                authToken = reply.getAsJsonObject().get(DeviceLocatorApp.GMS_TOKEN).getAsString();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
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
        intent.putExtra("accountType", account.type);
        intent.putExtra("authTokenType", authTokenType);

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
    public Bundle editProperties(AccountAuthenticatorResponse accountAuthenticatorResponse, String s) {
        return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account, String[] strings) throws NetworkErrorException {
        return null;
    }
}
