package net.gmsworld.devicelocator;

import android.app.KeyguardManager;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import net.gmsworld.devicelocator.broadcastreceivers.DeviceAdminEventReceiver;
import net.gmsworld.devicelocator.services.HiddenCaptureImageService;
import net.gmsworld.devicelocator.services.SmsSenderService;
import net.gmsworld.devicelocator.utilities.Command;
import net.gmsworld.devicelocator.utilities.FingerprintHelper;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;

import org.apache.commons.lang3.StringUtils;

public class PinActivity extends AppCompatActivity {

    private static final String TAG = PinActivity.class.getSimpleName();

    static final int PIN_MIN_LENGTH = 4;

    static final int PIN_VALIDATION_MILLIS = 30 * 60 * 1000; //30 mins

    public static final String DEVICE_PIN = "token";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        //fingerprint authentication

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            FingerprintManager fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);

            new FingerprintHelper(keyguardManager, fingerprintManager, this).init();
            findViewById(R.id.deviceFingerprintCard).setVisibility(View.VISIBLE);
        }

        //-----------------------------------------------------------------

        final EditText tokenInput = findViewById(R.id.verify_pin_edit);

        final PreferencesUtils settings = new PreferencesUtils(this);

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
                    startActivity(new Intent(PinActivity.this, MainActivity.class));
                    PreferenceManager.getDefaultSharedPreferences(PinActivity.this).edit()
                            .remove("pinFailedCount")
                            .putLong("pinVerificationMillis", System.currentTimeMillis())
                            .apply();
                    finish();
                } else if (input.length() == pin.length()) {
                    int pinFailedCount = settings.getInt("pinFailedCount");
                    Toast.makeText(PinActivity.this, "Invalid pin!", Toast.LENGTH_SHORT).show();
                    if (pinFailedCount == 2) {
                        pinFailedCount = -1;
                        //send failed login notification
                        Log.d(TAG, "Wrong pin has been entered to unlock the app. SENDING NOTIFICATION!");
                        Intent newIntent = new Intent(PinActivity.this, SmsSenderService.class);
                        newIntent.putExtra("phoneNumber", phoneNumber);
                        newIntent.putExtra("email", email);
                        newIntent.putExtra("telegramId", telegramId);
                        newIntent.putExtra("source", DeviceAdminEventReceiver.SOURCE);
                        startService(newIntent);
                        if (settings.getBoolean("hiddenCamera", false)) {
                            Intent cameraIntent = new Intent(PinActivity.this, HiddenCaptureImageService.class);
                            PinActivity.this.startService(cameraIntent);
                        } else {
                            Log.d(TAG, "Camera is disabled. No photo will be taken");
                        }
                    }
                    PreferenceManager.getDefaultSharedPreferences(PinActivity.this).edit()
                            .putInt("pinFailedCount", pinFailedCount + 1)
                            .apply();
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
                    Toast.makeText(PinActivity.this, "No valid pin entered", Toast.LENGTH_SHORT).show();
                    tokenInput.setText("");
                }
                return false;
            }
        });

        final TextView helpText = findViewById(R.id.verify_pin_text);
        helpText.setText(Html.fromHtml(getString(R.string.pinLink)));
        helpText.setMovementMethod(new TextViewLinkHandler() {
            @Override
            public void onLinkClick(String url) {
                if (StringUtils.isNotEmpty(telegramId) || StringUtils.isNotEmpty(email) || StringUtils.isNotEmpty(phoneNumber)) {
                    Intent newIntent = new Intent(PinActivity.this, SmsSenderService.class);
                    newIntent.putExtra("telegramId", telegramId);
                    newIntent.putExtra("email", email);
                    newIntent.putExtra("command", Command.PIN_COMMAND);
                    newIntent.putExtra("phoneNumber", phoneNumber);
                    startService(newIntent);
                    Toast.makeText(PinActivity.this, "Security PIN has been sent to notifiers", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(PinActivity.this, "Notifiers hasn't been set. Unable to send Security PIN.", Toast.LENGTH_SHORT).show();
                }
            }
        });
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
}
