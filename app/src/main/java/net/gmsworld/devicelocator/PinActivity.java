package net.gmsworld.devicelocator;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.InputFilter;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import net.gmsworld.devicelocator.broadcastreceivers.DeviceAdminEventReceiver;
import net.gmsworld.devicelocator.services.CommandService;
import net.gmsworld.devicelocator.services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.services.SmsSenderService;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.FingerprintHelper;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import androidx.appcompat.app.AppCompatActivity;

public class PinActivity extends AppCompatActivity implements FingerprintHelper.AuthenticationCallback {

    private static final String TAG = PinActivity.class.getSimpleName();

    public static final int PIN_MIN_LENGTH = 4;
    private static final int PIN_VALIDATION_MILLIS = 30 * 60 * 1000; //30 mins
    public static final String DEVICE_PIN = "token";

    private FingerprintHelper fingerprintHelper;
    private PreferencesUtils settings;
    private String action;
    private Bundle extras;
    private int failedFingerprint = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        if (getIntent() != null) {
            action = getIntent().getAction();
            extras = getIntent().getExtras();
        }

        settings = new PreferencesUtils(this);

        //fingerprint authentication

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            FingerprintManager fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
            if (keyguardManager != null && fingerprintManager != null) {
                fingerprintHelper = new FingerprintHelper(keyguardManager, fingerprintManager, this);
                if (fingerprintHelper.init(this, settings)) {
                    findViewById(R.id.deviceFingerprintCard).setVisibility(View.VISIBLE);
                } else {
                    fingerprintHelper = null;
                }
            }
        }

        //-----------------------------------------------------------------

        final EditText tokenInput = findViewById(R.id.verify_pin_edit);

        final String pin = settings.getEncryptedString(DEVICE_PIN);
        final String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER);
        final String email = settings.getString(MainActivity.NOTIFICATION_EMAIL);
        final String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL);

        tokenInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(pin.length())});

        tokenInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String input = charSequence.toString();
                if (StringUtils.equals(input, pin)) {
                    onAuthenticated();
                } else if (input.length() == pin.length()) {
                    onFailed(FingerprintHelper.AuthType.Pin);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        tokenInput.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    Toast.makeText(PinActivity.this, "Invalid pin entered", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(PinActivity.this, R.string.pin_sent_ok, Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(PinActivity.this, R.string.pin_recover_ok, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(PinActivity.this, R.string.pin_recover_fail, Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    Toast.makeText(PinActivity.this, R.string.no_network_error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (fingerprintHelper != null) {
            fingerprintHelper.startListening();
        } else {
            final EditText tokenInput = findViewById(R.id.verify_pin_edit);
            tokenInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(tokenInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (fingerprintHelper != null) {
            fingerprintHelper.stopListening();
        }
    }

    @Override
    public void onAuthenticated() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
        }

        if (StringUtils.equals(action, CommandService.AUTH_NEEDED)) {
            Intent intent = new Intent(this, CommandService.class);
            intent.putExtras(extras);
            startService(intent);
        } else {
            Intent intent = new Intent(this, MainActivity.class);
            if (action != null) {
                intent.setAction(action);
            }
            startActivity(intent);
        }
        settings.remove("pinFailedCount");
        settings.setLong("pinVerificationMillis", System.currentTimeMillis());
        finish();
    }

    @Override
    public void onError(int errMsgId, CharSequence errString) {
        Log.e(TAG, "Fingerprint authentication error occurred " + errMsgId + ": " + errString);
    }

    @Override
    public void onFailed(FingerprintHelper.AuthType authType) {
        int pinFailedCount = settings.getInt("pinFailedCount");
        Log.d(TAG, "Invalid credentials " + authType.name());
        if (authType == FingerprintHelper.AuthType.Fingerprint) {
            failedFingerprint++;
            if (failedFingerprint == 3) {
                findViewById(R.id.deviceFingerprintCard).setVisibility(View.GONE);
                Toast.makeText(this, getString(R.string.enter_pin), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.fingerprint_invalid, Toast.LENGTH_SHORT).show();
            }
        } else if (authType == FingerprintHelper.AuthType.Pin) {
            Toast.makeText(this, R.string.pin_invalid, Toast.LENGTH_SHORT).show();
        }
        if (pinFailedCount == 2) {
            pinFailedCount = -1;
            //send failed login notification
            Log.d(TAG, "Wrong pin has been entered to unlock the app. SENDING NOTIFICATION!");
            SmsSenderService.initService(PinActivity.this, true, true, true, null, null, null, DeviceAdminEventReceiver.SOURCE, null);
            if (settings.getBoolean(HiddenCaptureImageService.STATUS, false) && HiddenCaptureImageService.isNotBusy()) {
                Intent cameraIntent = new Intent(this, HiddenCaptureImageService.class);
                startService(cameraIntent);
            } else {
                Log.d(TAG, "Camera is disabled. No photo will be taken");
            }
        }
        settings.setInt("pinFailedCount", pinFailedCount + 1);
    }

    private abstract class TextViewLinkHandler extends LinkMovementMethod {

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

    public static boolean isAuthRequired(PreferencesUtils prefs) {
        final String pin =  prefs.getEncryptedString(PinActivity.DEVICE_PIN);
        final long pinVerificationMillis =  prefs.getLong("pinVerificationMillis");
        final boolean settingsVerifyPin =  prefs.getBoolean("settings_verify_pin", false);
        return (StringUtils.isNotEmpty(pin) && settingsVerifyPin && System.currentTimeMillis() - pinVerificationMillis > PinActivity.PIN_VALIDATION_MILLIS);
    }
}
