package net.gmsworld.devicelocator.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import net.gmsworld.devicelocator.DeviceLocatorApp;
import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.R;
import net.gmsworld.devicelocator.utilities.Messenger;
import net.gmsworld.devicelocator.utilities.Network;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class NotificationActivationDialogFragment extends DialogFragment {

    public enum Mode {Email, Telegram};

    public static final String EMAIL_SECRET = "emailSecret";
    public static final String TELEGRAM_SECRET = "telegramSecret";

    public static final String TAG = "NotificationActivationDialog";

    //default mode is Email
    private Mode mode = Mode.Email;

    public static NotificationActivationDialogFragment newInstance(Mode mode) {
        NotificationActivationDialogFragment frag = new NotificationActivationDialogFragment();
        if (mode != null) {
            frag.mode = mode;
        }
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();

        View dialogView = inflater.inflate(R.layout.activation_dialog, null);

        String s = null;
        if (mode == Mode.Telegram) {
            s = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(TELEGRAM_SECRET, "");
            ((TextView)dialogView.findViewById(R.id.activationTitle)).setText(R.string.activation_telegram_title);
            ((TextView)dialogView.findViewById(R.id.activationDescription)).setText(R.string.activation_telegram_desc);
        } else {
            s = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(EMAIL_SECRET, "");
        }
        final String secret = s;

        String[] tokens = StringUtils.split(secret,".");
        String acc = null;
        if (tokens.length == 2 && tokens[1].length() == 4 && StringUtils.isNumeric(tokens[1])) {
            acc = tokens[1];
        }
        final String activationCode = acc;

        EditText activationCodeInput = dialogView.findViewById(R.id.activationCode);

        activationCodeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence code, int i, int i1, int i2) {
                //Log.d(TAG, "Comparing " + code + " with " + activationCode);
                if (code.length() == 4 && StringUtils.equals(code, activationCode)) {
                    if (Network.isNetworkAvailable(getActivity())) {
                        String tokenStr = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(DeviceLocatorApp.GMS_TOKEN, "");
                        Map<String, String> headers = new HashMap<>();
                        headers.put("Authorization", "Bearer " + tokenStr);
                        Toast.makeText(getActivity(), R.string.please_wait, Toast.LENGTH_LONG).show();
                        final String verifyUrl = getActivity().getString(R.string.verifyUrl) + "/" + secret;
                        final Context context = getActivity();
                        Network.get(context, verifyUrl, headers, new Network.OnGetFinishListener() {
                            @Override
                            public void onGetFinish(String results, int responseCode, String url) {
                                if (responseCode == 200) {
                                    if (mode == Mode.Telegram) {
                                        Toast.makeText(context, "Your Telegram chat or channel has been verified.", Toast.LENGTH_LONG).show();
                                        PreferenceManager.getDefaultSharedPreferences(context).edit()
                                                .putString(MainActivity.SOCIAL_REGISTRATION_STATUS, "verified")
                                                .remove(TELEGRAM_SECRET)
                                                .apply();
                                    } else {
                                        Toast.makeText(context, "Your email address has been verified.", Toast.LENGTH_LONG).show();
                                        PreferenceManager.getDefaultSharedPreferences(context).edit()
                                                .putString(MainActivity.EMAIL_REGISTRATION_STATUS, "verified")
                                                .remove(EMAIL_SECRET)
                                                .apply();
                                    }
                                } else {
                                    if (mode == Mode.Telegram) {
                                        Toast.makeText(context, "Failed to verify Telegram chat or channel! Please try again in a few moments.", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(context, "Failed to verify email address! Please try again in a few moments.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }
                        });
                        NotificationActivationDialogFragment.this.dismiss();
                    } else {
                        Toast.makeText(getActivity(), "No network available. Failed to verify!", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        builder.setView(dialogView)
                .setPositiveButton(R.string.send_code_again, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (mode == Mode.Telegram) {
                            //send telegram registration
                            String telegramId = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(MainActivity.NOTIFICATION_SOCIAL, "");
                            if (StringUtils.isNotEmpty(telegramId)) {
                                Messenger.sendTelegramRegistrationRequest(getActivity(), telegramId, 1);
                                Toast.makeText(getActivity(), R.string.please_wait, Toast.LENGTH_LONG).show();
                                NotificationActivationDialogFragment.this.dismiss();
                            } else {
                                Toast.makeText(getActivity(), "Failed to send Telegram channel or chat registration request!", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            //send registration email again
                            String email = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(MainActivity.NOTIFICATION_EMAIL, "");
                            if (StringUtils.isNotEmpty(email)) {
                                Messenger.sendEmailRegistrationRequest(getActivity(), email, 1);
                                Toast.makeText(getActivity(), R.string.please_wait, Toast.LENGTH_LONG).show();
                                NotificationActivationDialogFragment.this.dismiss();
                            } else {
                                Toast.makeText(getActivity(), "Failed to send email registration request!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                })
                .setNegativeButton(R.string.forget_me, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (mode == Mode.Telegram) {
                            PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                                    .remove(MainActivity.NOTIFICATION_SOCIAL)
                                    .remove(MainActivity.SOCIAL_REGISTRATION_STATUS)
                                    .remove(TELEGRAM_SECRET)
                                    .apply();
                            final TextView telegramInput = getActivity().findViewById(R.id.telegramId);
                            telegramInput.setText("");
                        } else {
                            PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                                    .remove(MainActivity.NOTIFICATION_EMAIL)
                                    .remove("emailRegistrationMillis")
                                    .remove(MainActivity.EMAIL_REGISTRATION_STATUS)
                                    .remove(EMAIL_SECRET)
                                    .apply();
                            final TextView emailInput = getActivity().findViewById(R.id.email);
                            emailInput.setText("");
                        }
                    }
                });

        return builder.create();
    }
}
