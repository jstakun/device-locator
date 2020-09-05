package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;
import net.gmsworld.devicelocator.utilities.PreferencesUtils;
import net.gmsworld.devicelocator.utilities.Toaster;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

import androidx.appcompat.app.AlertDialog;

public class NotificationActivationDialogFragment extends DialogFragment {

    public enum Mode {Email, Telegram};

    public static final String EMAIL_SECRET = "emailSecret";
    public static final String TELEGRAM_SECRET = "telegramSecret";

    public static final String TAG = "NotifActivationDialog";

    private Toaster toaster;

    private NotificationActivationDialogListener initListener;

    //default mode is Email
    private Mode mode = Mode.Email;

    public interface NotificationActivationDialogListener {
        void openMainActivity();
    }

    public static NotificationActivationDialogFragment newInstance(Mode mode, Toaster toaster) {
        NotificationActivationDialogFragment frag = new NotificationActivationDialogFragment();
        if (mode != null) {
            frag.mode = mode;
        }
        frag.toaster = toaster;
        return frag;
    }

    public static NotificationActivationDialogFragment newInstance(Mode mode, Toaster toaster, NotificationActivationDialogListener initListener) {
        NotificationActivationDialogFragment frag = newInstance(mode, toaster);
        frag.initListener = initListener;
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.activation_dialog, null);

        final PreferencesUtils settings = new PreferencesUtils(getActivity());

        String s;
        if (mode == Mode.Telegram) {
            s = settings.getString(TELEGRAM_SECRET);
            ((TextView)dialogView.findViewById(R.id.activationTitle)).setText(R.string.activation_telegram_title);
            ((TextView)dialogView.findViewById(R.id.activationDescription)).setText(R.string.activation_telegram_desc);
        } else {
            s = settings.getString(EMAIL_SECRET);
        }
        final String secret = s;

        String[] tokens = StringUtils.split(secret,".");
        String acc = null;
        if (tokens.length == 2 && tokens[1].length() == 4 && StringUtils.isNumeric(tokens[1])) {
            acc = tokens[1];
        }
        final String activationCode = acc;

        final EditText activationCodeInput = dialogView.findViewById(R.id.activationCode);

        activationCodeInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    String currentText = activationCodeInput.getText().toString();
                    if (currentText.isEmpty()) {
                        //paste code from clipboard
                        try {
                            ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard != null && clipboard.hasPrimaryClip()) {
                                int clipboardItemCount = clipboard.getPrimaryClip().getItemCount();
                                for (int i = 0; i < clipboardItemCount; i++) {
                                    ClipData.Item item = clipboard.getPrimaryClip().getItemAt(i);
                                    final String code = item.getText().toString();
                                    if (code.length() == 4 && StringUtils.equals(code, activationCode)) {
                                        activationCodeInput.setText(code);
                                        toaster.showActivityToast("Code has been pasted from clipboard");
                                        onEnteredActivationCode(getActivity(), settings, secret);
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage(), e);
                        }
                    }
                }
            }
        });

        activationCodeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence code, int i, int i1, int i2) {
                //Log.d(TAG, "Comparing " + code + " with " + activationCode);
                if (code.length() == 4 && StringUtils.equals(code, activationCode)) {
                    onEnteredActivationCode(getActivity(), settings, secret);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        activationCodeInput.requestFocus();

        builder.setView(dialogView)
                .setPositiveButton(R.string.send_code_again, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (mode == Mode.Telegram) {
                            Log.d(TAG, "Sending again Telegram registration request ...");
                            final String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL);
                            if (StringUtils.isNotEmpty(telegramId)) {
                                Messenger.sendTelegramRegistrationRequest(getActivity(), telegramId, 1);
                                toaster.showActivityToast(R.string.please_wait);
                                NotificationActivationDialogFragment.this.dismiss();
                            } else {
                                toaster.showActivityToast("Failed to send Telegram channel or chat registration request!");
                            }
                        } else {
                            Log.d(TAG, "Sending again email registration request ...");
                            final String email = settings.getString(MainActivity.NOTIFICATION_EMAIL);
                            if (StringUtils.isNotEmpty(email)) {
                                Messenger.sendEmailRegistrationRequest(getActivity(), email, true, 1);
                                toaster.showActivityToast(R.string.please_wait);
                                NotificationActivationDialogFragment.this.dismiss();
                            } else {
                                toaster.showActivityToast("Failed to send email registration request!");
                            }
                        }
                    }
                })
                .setNegativeButton(R.string.forget_me, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (mode == Mode.Telegram) {
                            Log.d(TAG, "Cancelling Telegram registration request ...");
                            settings.remove(MainActivity.NOTIFICATION_SOCIAL, MainActivity.SOCIAL_REGISTRATION_STATUS, TELEGRAM_SECRET);
                            final TextView telegramInput = getActivity().findViewById(R.id.telegramId);
                            telegramInput.setText("");
                            NotificationActivationDialogFragment.this.dismiss();
                        } else {
                            Log.d(TAG, "Cancelling email registration request ...");
                            settings.remove(MainActivity.NOTIFICATION_EMAIL, MainActivity.EMAIL_REGISTRATION_STATUS, EMAIL_SECRET);
                            final TextView emailInput = getActivity().findViewById(R.id.email);
                            emailInput.setText("");
                            NotificationActivationDialogFragment.this.dismiss();
                        }
                    }
                });

        return builder.create();
    }

    public void onEnteredActivationCode(final Context context, final PreferencesUtils settings, final String secret) {
        if (Network.isNetworkAvailable(context)) {
            String tokenStr = settings.getString(DeviceLocatorApp.GMS_TOKEN);
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + tokenStr);
            toaster.showActivityToast(R.string.please_wait);
            final String verifyUrl = context.getString(R.string.verifyUrl) + "/" + secret;
            Network.get(context, verifyUrl, headers, new Network.OnGetFinishListener() {
                @Override
                public void onGetFinish(String results, int responseCode, String url) {
                    if (responseCode == 200) {
                        NotificationActivationDialogFragment.this.dismiss();
                        if (mode == Mode.Telegram) {
                            toaster.showActivityToast("Your Telegram chat or channel has been verified.");
                            settings.setString(MainActivity.SOCIAL_REGISTRATION_STATUS, "verified");
                            settings.remove(TELEGRAM_SECRET, TelegramSetupDialogFragment.TELEGRAM_FAILED_SETUP_COUNT);
                        } else {
                            toaster.showActivityToast("Your email address has been verified.");
                            settings.setString(MainActivity.EMAIL_REGISTRATION_STATUS, "verified");
                            settings.remove(EMAIL_SECRET);
                        }
                        if (initListener != null) {
                            initListener.openMainActivity();
                        }
                    } else {
                        if (mode == Mode.Telegram) {
                            toaster.showActivityToast("Failed to verify Telegram chat or channel! Please try again in a few moments.");
                        } else {
                            toaster.showActivityToast("Failed to verify email address! Please try again in a few moments.");
                        }
                    }
                }
            });
            if (NotificationActivationDialogFragment.this.isVisible()) {
                NotificationActivationDialogFragment.this.dismiss();
            }
        } else {
            toaster.showActivityToast("No network available. Failed to send request! Please try again in a few moments");
        }
    }
}
