package net.gmsworld.devicelocator;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;

import net.gmsworld.devicelocator.broadcastreceivers.DeviceAdminEventReceiver;
import net.gmsworld.devicelocator.services.CommandService;
import net.gmsworld.devicelocator.services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.services.SmsSenderService;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.Permissions;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.utilities.Toaster;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.ECGenParameterSpec;
import java.util.UUID;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;

public class PinActivity extends AppCompatActivity {

    private static final String TAG = PinActivity.class.getSimpleName();

    public static final int PIN_MIN_LENGTH = 4;
    public static final String DEVICE_PIN = "token";
    public static final String VERIFICATION_TIMESTAMP = "pinVerificationMillis";
    public static final String FAILED_COUNT = "pinFailedCount";
    public static final String VERIFY_PIN = "settings_verify_pin";
    private static final long PIN_VALIDATION_MILLIS = AlarmManager.INTERVAL_HALF_HOUR;
    private static final String KEY_NAME = UUID.randomUUID().toString();

    private enum AuthType {Biometric, Pin};

    private Toaster toaster;
    private PreferencesUtils settings;
    private String action;
    private Bundle extras;
    private int failedFingerprint = 0;
    private String mToBeSignedMessage;
    private BiometricPrompt mBiometricPrompt = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        Intent intent = getIntent();
        if (intent != null) {
            action = intent.getAction();
            extras = intent.getExtras();
        }

        settings = new PreferencesUtils(this);

        toaster = new Toaster(this);

        final String pin = settings.getEncryptedString(DEVICE_PIN);

        final EditText tokenInput = findViewById(R.id.verify_pin_edit);

        tokenInput.addTextChangedListener(new TextWatcher() {

            private int lastLength = 0;

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence,int start, int before, int count) {
                String input = charSequence.toString();
                if (StringUtils.equals(input, pin)) {
                    onAuthenticated();
                } else if (input.length() >= pin.length() && input.length() > lastLength) {
                    onFailed(AuthType.Pin);
                }
                lastLength = input.length();
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        tokenInput.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    toaster.showActivityToast(R.string.pin_invalid);
                    tokenInput.setText("");
                }
                return false;
            }
        });

        final TextView helpText = findViewById(R.id.verify_pin_text);
        helpText.setText(Html.fromHtml(getString(R.string.pin_link)));
        helpText.setMovementMethod(new TextViewLinkHandler() {
            @Override
            public void onLinkClick(String url) {
                if (Network.isNetworkAvailable(PinActivity.this)) {
                    if (SmsSenderService.initService(PinActivity.this, true, true, true, null, Command.PIN_COMMAND, null, null, null)) {
                        toaster.showActivityToast(R.string.pin_sent_ok);
                    } else {
                        //1. send pin to app admin
                        final String secret = RandomStringUtils.random(16, true, true);
                        Bundle extras = new Bundle();
                        extras.putString("email", getString(R.string.app_email));
                        extras.putString("secret", secret);
                        SmsSenderService.initService(PinActivity.this, false, true, false, null, Command.PIN_COMMAND, null, null, extras);
                        //2. send email to app admin
                        final String deviceName = Messenger.getDeviceId(PinActivity.this, true);
                        if (Messenger.composeEmail(PinActivity.this, new String[]{getString(R.string.app_email)}, getString(R.string.pin_recover_mail_title, deviceName), getString(R.string.pin_recover_mail_body, deviceName, secret), false)) {
                            toaster.showActivityToast(R.string.pin_recover_ok);
                        } else {
                            toaster.showActivityToast(R.string.pin_recover_fail);
                        }
                    }
                } else {
                    toaster.showActivityToast(R.string.no_network_error);
                }
            }
        });

        //biometric authentication --------------------------------------

        if (canAuthenticateWithStrongBiometrics()) {
            // Generate keypair and init signature
            Signature signature = null;
            try {
                KeyPair keyPair = generateKeyPair(KEY_NAME, true);
                // Send public key part of key pair to the server, this public key will be used for authentication
                mToBeSignedMessage = Base64.encodeToString(keyPair.getPublic().getEncoded(), Base64.URL_SAFE) +
                        ":" + KEY_NAME + ":" +
                        // Generated by the server to protect against replay attack
                        RandomStringUtils.random(5);
                signature = initSignature(KEY_NAME);
            } catch (Exception e) {
                //throw new RuntimeException(e);
                Log.e(TAG, e.getMessage(), e);
            }

            // Create biometricPrompt
            if (signature != null) {
                showBiometricPrompt(signature);
            }
        }

        //----------------------------------------------------------------

        FirebaseAnalytics.getInstance(this).logEvent("pin_activity", new Bundle());
    }

    @Override
    public void onResume() {
        super.onResume();
        final EditText tokenInput = findViewById(R.id.verify_pin_edit);
        tokenInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(tokenInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        final EditText tokenInput = findViewById(R.id.verify_pin_edit);
        tokenInput.setText("");
        toaster.cancel();
    }

    private void onAuthenticated() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
        }

        toaster.cancel();

        if (StringUtils.equals(action, CommandService.AUTH_NEEDED)) {
            Intent intent = new Intent(this, CommandService.class);
            intent.putExtras(extras);
            startService(intent);
        } else if (StringUtils.equals(action, CommandActivity.AUTH_NEEDED)) {
            Intent intent = new Intent(this, CommandActivity.class);
            intent.putExtras(extras);
            startActivity(intent);
        } else if (StringUtils.equals(action, PermissionsActivity.LOCATION_ACTION)) {
            Intent intent = new Intent(this, PermissionsActivity.class);
            intent.setAction(action);
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, MainActivity.class);
            if (action != null) {
                intent.setAction(action);
            }
            startActivity(intent);
        }
        settings.remove(FAILED_COUNT);
        settings.setLong(VERIFICATION_TIMESTAMP, System.currentTimeMillis());
        finish();
    }

    private void onFailed(AuthType authType) {
        int pinFailedCount = settings.getInt(FAILED_COUNT);
        Log.d(TAG, "Invalid credentials type: " + authType.name());
        if (authType == AuthType.Biometric) {
            failedFingerprint++;
            if (failedFingerprint == 3) {
                if (mBiometricPrompt != null) {
                    mBiometricPrompt.cancelAuthentication();
                }
                toaster.showActivityToast(R.string.pin_enter_valid);
            } else {
                toaster.showActivityToast(R.string.fingerprint_invalid);
            }
        }
        if (pinFailedCount == 3) {
            pinFailedCount = -1;
            //send failed login notification
            Log.d(TAG, "Invalid Security PIN has been entered to unlock the app. SENDING NOTIFICATION!");
            SmsSenderService.initService(PinActivity.this, true, true, true, null, null, null, DeviceAdminEventReceiver.SOURCE, null);
            toaster.showActivityToast(R.string.pin_invalid_entered);
            if (settings.getBoolean(HiddenCaptureImageService.STATUS, false) && HiddenCaptureImageService.isNotBusy()) {
                Intent cameraIntent = new Intent(this, HiddenCaptureImageService.class);
                startService(cameraIntent);
            } else {
                Log.d(TAG, "Camera is disabled. No photo will be taken");
            }
        }
        settings.setInt(FAILED_COUNT, pinFailedCount + 1);
    }

    public static boolean isAuthRequired(PreferencesUtils prefs) {
        final String pin =  prefs.getEncryptedString(PinActivity.DEVICE_PIN);
        final long pinVerificationMillis =  prefs.getLong(VERIFICATION_TIMESTAMP);
        final boolean settingsVerifyPin =  prefs.getBoolean(VERIFY_PIN, false);
        //Log.d(TAG, pin + " " + pinVerificationMillis + " " + settingsVerifyPin);
        return (StringUtils.isNotEmpty(pin) && settingsVerifyPin && System.currentTimeMillis() - pinVerificationMillis > PIN_VALIDATION_MILLIS);
    }

    //biometric authentication ----------------------------------------------------------

    private void showBiometricPrompt(Signature signature) {
        BiometricPrompt.AuthenticationCallback authenticationCallback = getAuthenticationCallback();
        mBiometricPrompt = new BiometricPrompt(this, getMainThreadExecutor(), authenticationCallback);

        // Set prompt info
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setDescription(getString(R.string.biometric_description))
                .setTitle(getString(R.string.app_name))
                .setSubtitle(getString(R.string.biometric_subtitle))
                .setNegativeButtonText("Cancel")
                .build();

        // Show biometric prompt
        if (signature != null) {
            mBiometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(signature));
        }
    }

    private BiometricPrompt.AuthenticationCallback getAuthenticationCallback() {
        // Callback for biometric authentication result
        return new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                Log.e(TAG, "Biometric authentication error " + errorCode + ": " + errString);
                super.onAuthenticationError(errorCode, errString);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                if (result.getCryptoObject() != null && result.getCryptoObject().getSignature() != null) {
                    try {
                        Signature signature = result.getCryptoObject().getSignature();
                        signature.update(mToBeSignedMessage.getBytes());
                        //String signatureString = Base64.encodeToString(signature.sign(), Base64.URL_SAFE);
                        //Log.i(TAG, "Message: " + mToBeSignedMessage);
                        //Log.i(TAG, "Signature (Base64 Encoded): " + signatureString);
                        Log.d(TAG, "Biometric authentication successful");
                        onAuthenticated();
                    } catch (SignatureException e) {
                        Log.e(TAG, e.getMessage(), e);
                        onFailed(AuthType.Biometric);
                    }
                } else {
                    onFailed(AuthType.Biometric);
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                onFailed(AuthType.Biometric);
            }
        };
    }

    private KeyPair generateKeyPair(String keyName, boolean invalidatedByBiometricEnrollment) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");

        KeyGenParameterSpec.Builder builder = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder = new KeyGenParameterSpec.Builder(keyName,
                    KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256,
                            KeyProperties.DIGEST_SHA384,
                            KeyProperties.DIGEST_SHA512)
                    // Require the user to authenticate with a biometric to authorize every use of the key
                    .setUserAuthenticationRequired(true);


            // Generated keys will be invalidated if the biometric templates are added more to user device
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(invalidatedByBiometricEnrollment);
            }

            keyPairGenerator.initialize(builder.build());

            return keyPairGenerator.generateKeyPair();
        } else {
            return null;
        }
    }

    @Nullable
    private KeyPair getKeyPair(String keyName) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(keyName)) {
            // Get public key
            PublicKey publicKey = keyStore.getCertificate(keyName).getPublicKey();
            // Get private key
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyName, null);
            // Return a key pair
            return new KeyPair(publicKey, privateKey);
        }
        return null;
    }

    @Nullable
    private Signature initSignature(String keyName) throws Exception {
        KeyPair keyPair = getKeyPair(keyName);

        if (keyPair != null) {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(keyPair.getPrivate());
            return signature;
        }
        return null;
    }

    private Executor getMainThreadExecutor() {
        return new MainThreadExecutor();
    }

    private boolean canAuthenticateWithStrongBiometrics() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && settings.getBoolean(Permissions.BIOMETRIC_AUTH, true) &&
                BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable r) {
            handler.post(r);
        }
    }

    //----------------------------------------------------------------------------

    private abstract static class TextViewLinkHandler extends LinkMovementMethod {

        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP)
                return super.onTouchEvent(widget, buffer, event);

            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();

            x += widget.getScrollX();
            y += widget.getScrollY();

            Layout layout = widget.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            URLSpan[] link = buffer.getSpans(off, off, URLSpan.class);
            if (link.length != 0) {
                onLinkClick(link[0].getURL());
            }
            return true;
        }

        protected abstract void onLinkClick(String url);
    }
}
