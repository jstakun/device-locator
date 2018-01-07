package net.gmsworld.devicelocator.Utilities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.Services.SmsSenderService;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

/**
 * Created by jstakun on 10/25/17.
 */

public abstract class AbstractCommand {

    static final String TAG = "DeviceCommand";

    protected enum Finder {STARTS, EQUALS}

    private String smsCommand = null;

    private String smsShortCommand= null;

    private final Finder finder;

    String[] commandTokens;

    AbstractCommand(String smsCommand, String smsShortCommand, Finder finder) {
        this.smsCommand = smsCommand;
        this.smsShortCommand = smsShortCommand;
        this.finder = finder;
    }

    protected abstract void onSmsCommandFound(String sender, Context context);

    protected abstract void onSmsSocialCommandFound(String sender, Context context);

    boolean validateTokens() {
        return false;
    }

    public boolean findCommand(Context context, Intent intent) {
        String sender = null;
        if (StringUtils.isNotEmpty(smsCommand)) {
            sender = getSenderAddress(context, intent, smsCommand);
        }
        if (sender == null && StringUtils.isNotEmpty(smsShortCommand)) {
            sender = getSenderAddress(context, intent, smsShortCommand);
        }
        if (sender != null) {
            onSmsCommandFound(sender, context);
            return true;
        } else if (StringUtils.isNotEmpty(smsCommand) &&  (StringUtils.isNotEmpty(PreferenceManager.getDefaultSharedPreferences(context).getString("telegramId", "")) || StringUtils.isNotEmpty(PreferenceManager.getDefaultSharedPreferences(context).getString("email", "")))) {
            sender = getSenderAddress(context, intent, smsCommand + "t");
            if (sender == null && StringUtils.isNotEmpty(smsShortCommand)) {
                sender = getSenderAddress(context, intent, smsShortCommand + "t");
            }
            if (sender != null) {
                onSmsSocialCommandFound(sender, context);
                return true;
            }
        }
        return false;
    }

    public boolean findCommand(Context context, String message) {
        if (StringUtils.isNotEmpty(smsCommand) && (StringUtils.isNotEmpty(PreferenceManager.getDefaultSharedPreferences(context).getString("telegramId", "")) || StringUtils.isNotEmpty(PreferenceManager.getDefaultSharedPreferences(context).getString("email", "")))) {
            if (findKeyword(context, smsCommand + "t", message)) {
                onSmsSocialCommandFound(null, context);
                return true;
            } else if (findKeyword(context, smsShortCommand + "t", message)) {
                onSmsSocialCommandFound(null, context);
                return true;
            }
        }
        return false;
    }

    private boolean findKeyword(Context context, String keyword, String message) {
        if (finder.equals(Finder.EQUALS)) {
            return findCommand(context, message, keyword);
        } else if (finder.equals(Finder.STARTS)) {
            return findCommand(context, message, keyword) && validateTokens();
        } else {
            return false;
        }
    }

    private ArrayList<SmsMessage> getMessagesWithKeyword(Context context, String keyword, Bundle bundle) {
        ArrayList<SmsMessage> list = new ArrayList<SmsMessage>();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            for (int i = 0; i < pdus.length; i++) {
                SmsMessage sms;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    String format = bundle.getString("format");
                    sms = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                } else {
                    sms = SmsMessage.createFromPdu((byte[]) pdus[i]);
                }
                if (findCommand(context, sms.getMessageBody(), keyword) && (finder.equals(Finder.EQUALS) || (finder.equals(Finder.STARTS) && validateTokens()))) {
                    list.add(sms);
                }
            }
        }
        return list;
    }

    private String getSenderAddress(Context context, Intent intent, String command) {
        try {
            ArrayList<SmsMessage> list = null;
            switch (finder) {
                case EQUALS:
                case STARTS:
                    list =  getMessagesWithKeyword(context, command, intent.getExtras());
                    break;
                default:
                    Log.d(TAG, "No command finder set");
                    break;
            }
            if (list != null && list.size() > 0) {
                return list.get(0).getOriginatingAddress();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return null;
    }

    private boolean findCommand(Context context, String message, String command) {
        //<command><token> <args> or <command> <token> <args>
        commandTokens = message.split(" ");
        final String token = PreferenceManager.getDefaultSharedPreferences(context).getString(MainActivity.DEVICE_PIN, "");
        if (commandTokens.length >= 1) {
            return (StringUtils.equalsIgnoreCase(commandTokens[0], command + token) || (StringUtils.equalsIgnoreCase(commandTokens[0], command) && StringUtils.equals(commandTokens[1], token)));
        } else {
            return false;
        }
    }

    void sendSocialNotification(final Context context, final String command) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final String email = settings.getString("email", "");
        final String telegramId = settings.getString("telegramId", "");
        Intent newIntent = new Intent(context, SmsSenderService.class);
        newIntent.putExtra("telegramId", telegramId);
        newIntent.putExtra("email", email);
        if (StringUtils.isNotEmpty(command)) {
            newIntent.putExtra("command", command);
        }
        context.startService(newIntent);
    }

    void sendSmsNotification(final Context context, final String sender, final String command) {
        Intent newIntent = new Intent(context, SmsSenderService.class);
        newIntent.putExtra("phoneNumber", sender);
        if (StringUtils.isNotEmpty(command)) {
            newIntent.putExtra("command", command);
        }
        context.startService(newIntent);
    }
}
