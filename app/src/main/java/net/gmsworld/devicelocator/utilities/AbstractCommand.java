package net.gmsworld.devicelocator.utilities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.PinActivity;
import net.gmsworld.devicelocator.services.SmsSenderService;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

/**
 * Created by jstakun on 10/25/17.
 */

public abstract class AbstractCommand {

    static final String TAG = "DeviceCommand";

    protected enum Finder {STARTS, EQUALS}

    private final String smsCommand;

    private final String smsShortCommand;

    private final Finder finder;

    String[] commandTokens;

    AbstractCommand(String smsCommand, String smsShortCommand, Finder finder) {
        this.smsCommand = smsCommand;
        this.smsShortCommand = smsShortCommand;
        this.finder = finder;
    }

    protected abstract void onSmsCommandFound(String sender, Context context);

    protected abstract void onSocialCommandFound(String sender, Context context);

    protected abstract void onAppCommandFound(String sender, Context context, Location location, Bundle extras);

    public String getOppositeCommand() { return null; }

    public String getLabel() { return StringUtils.capitalize(getSmsCommand().substring(0, getSmsCommand().length()-2)); }

    public boolean validateTokens() {
        return false;
    }

    public boolean hasParameters() {
        return finder.equals(Finder.STARTS);
    }

    public String getDefaultArgs() { return ""; }

    public boolean canResend() { return false; }

    public boolean hasOppositeCommand() { return getOppositeCommand() != null; }

    public boolean findSmsCommand(Context context, Intent intent) {
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
        } else if (StringUtils.isNotEmpty(smsCommand) && (StringUtils.isNotEmpty(PreferenceManager.getDefaultSharedPreferences(context).getString(MainActivity.NOTIFICATION_SOCIAL, "")) || StringUtils.isNotEmpty(PreferenceManager.getDefaultSharedPreferences(context).getString(MainActivity.NOTIFICATION_EMAIL, "")))) {
            sender = getSenderAddress(context, intent, smsCommand + "t");
            if (sender == null && StringUtils.isNotEmpty(smsShortCommand)) {
                sender = getSenderAddress(context, intent, smsShortCommand + "t");
            }
            if (sender != null) {
                onSocialCommandFound(sender, context);
                return true;
            }
        }
        return false;
    }

    public boolean findSocialCommand(Context context, String message) {
        if (StringUtils.isNotEmpty(smsCommand) && (StringUtils.isNotEmpty(PreferenceManager.getDefaultSharedPreferences(context).getString(MainActivity.NOTIFICATION_SOCIAL, "")) || StringUtils.isNotEmpty(PreferenceManager.getDefaultSharedPreferences(context).getString(MainActivity.NOTIFICATION_EMAIL, "")))) {
            if (findKeyword(context, smsCommand + "t", message)) {
                onSocialCommandFound(null, context);
                return true;
            } else if (findKeyword(context, smsShortCommand + "t", message)) {
                onSocialCommandFound(null, context);
                return true;
            }
        }
        return false;
    }

    public boolean findAppCommand(Context context, String message, String sender, Location location, Bundle extras) {
        if (StringUtils.isNotEmpty(smsCommand)) {
            if (findKeyword(context, smsCommand + "app", message)) {
                onAppCommandFound(sender, context, location, extras);
                return true;
            } else if (findKeyword(context, smsShortCommand + "app", message)) {
                onAppCommandFound(sender, context, location, extras);
                return true;
            }
        }
        return false;
    }

    private boolean findKeyword(Context context, String keyword, String message) {
        if (finder.equals(Finder.EQUALS)) {
            return findSmsCommand(context, message, keyword);
        } else if (finder.equals(Finder.STARTS)) {
            return findSmsCommand(context, message, keyword) && validateTokens();
        } else {
            return false;
        }
    }

    private ArrayList<SmsMessage> getMessagesWithKeyword(Context context, String keyword, Bundle bundle) {
        ArrayList<SmsMessage> list = new ArrayList<>();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus != null) {
                for (int i = 0; i < pdus.length; i++) {
                    SmsMessage sms;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        String format = bundle.getString("format");
                        sms = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                    } else {
                        sms = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    }
                    if (findSmsCommand(context, sms.getMessageBody(), keyword) && (finder.equals(Finder.EQUALS) || (finder.equals(Finder.STARTS) && validateTokens()))) {
                        list.add(sms);
                    }
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

    private boolean findSmsCommand(Context context, String message, String command) {
        //<command><pin> <args> or <command> <pin> <args>
        final PreferencesUtils prefs = new PreferencesUtils(context);
        boolean foundCommand = false;
        commandTokens = message.split(" ");
        final boolean isPinRequired = prefs.getBoolean("settings_sms_without_pin", true);
        if (commandTokens.length >= 1) {
            final String pin = prefs.getEncryptedString(PinActivity.DEVICE_PIN);
            foundCommand = StringUtils.equalsIgnoreCase(commandTokens[0], command + pin);
            if (!foundCommand) {
                if (commandTokens.length >= 2) {
                    foundCommand = StringUtils.equalsIgnoreCase(commandTokens[0], command) && (!isPinRequired || StringUtils.equals(commandTokens[1], pin));
                } else if (!isPinRequired) {
                    foundCommand = StringUtils.equalsIgnoreCase(commandTokens[0], command);
                } else if (StringUtils.equalsIgnoreCase(commandTokens[0], command)) {
                    Log.w(TAG, "Command " + commandTokens[0] + " without Security PIN has been received!");
                }
            }
        }
        //Log.d(TAG, "Matched " + message + " with " + command + ": " + foundCommand);
        return foundCommand;
    }

    void sendSocialNotification(final Context context, final String command) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
        final String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
        Intent newIntent = new Intent(context, SmsSenderService.class);
        newIntent.putExtra("telegramId", telegramId);
        newIntent.putExtra("email", email);
        if (StringUtils.isNotEmpty(command)) {
            newIntent.putExtra("command", command);
        }
        context.startService(newIntent);
    }

    void sendAppNotification(final Context context, final String command, final String sender) {
        Intent newIntent = new Intent(context, SmsSenderService.class);
        if (StringUtils.isNotEmpty(command)) {
            newIntent.putExtra("command", command);
        }
        newIntent.putExtra("app", sender);
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

    public String getSmsCommand() {
        return smsCommand;
    }

    public void setCommandTokens(String[] commandTokens) {
        this.commandTokens = commandTokens;
    }
}
