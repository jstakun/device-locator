package net.gmsworld.devicelocator.utilities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import net.gmsworld.devicelocator.MainActivity;
import net.gmsworld.devicelocator.services.SmsSenderService;

import org.apache.commons.lang3.StringUtils;

import java.io.File;

/**
 * Created by jstakun on 10/25/17.
 */

public abstract class AbstractCommand {

    private static final String TAG = "DeviceCommand";

    private static final String AUDIT_FILE = "audit_log.bin";

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

    public String getDefaultArgs() { return ""; }

    public int getConfirmation() { return -1; }

    public boolean canResend() { return false; }

    public final boolean hasParameters() {
        return finder.equals(Finder.STARTS);
    }

    public final boolean hasOppositeCommand() { return getOppositeCommand() != null; }

    protected final String getSmsCommand() {
        return smsCommand;
    }

    public final void setCommandTokens(String[] commandTokens) {
        this.commandTokens = commandTokens;
    }


    protected final boolean findSmsCommand(Context context, final String smsMessage, final String sender, final String pin, final boolean isPinRequired, final boolean hasSocialNotifiers) {
        int status = 0;
        //sms with sms notification
        if (StringUtils.startsWithIgnoreCase(smsMessage, smsCommand)) {
            status = findCommandInMessage(context, smsCommand, smsMessage, pin, sender, isPinRequired);
            auditCommand(context, smsCommand);
        }
        if (status == 0 && StringUtils.startsWithIgnoreCase(smsMessage, smsShortCommand)) {
            status = findCommandInMessage(context, smsShortCommand, smsMessage, pin, sender, isPinRequired);
            auditCommand(context, smsShortCommand);
        }
        if (status == 1) {
            onSmsCommandFound(sender, context);
            return true;
        } else if ((StringUtils.startsWithIgnoreCase(smsMessage, smsCommand + "t") || StringUtils.startsWithIgnoreCase(smsMessage, smsShortCommand + "t")) && hasSocialNotifiers) {
            //sms with social notification
            status = findCommandInMessage(context, smsCommand + "t", smsMessage, pin, sender, isPinRequired);
            if (status == 0 && StringUtils.startsWithIgnoreCase(smsMessage, smsShortCommand)) {
                status = findCommandInMessage(context, smsShortCommand + "t", smsMessage, pin, sender, isPinRequired);
            }
            if (status == 1) {
                onSocialCommandFound(sender, context);
                return true;
            }
        }
        Log.d(TAG, "No valid command found in sms message " + smsMessage);
        return false;
    }

    protected final boolean findSocialCommand(Context context, String message, String pin, String sender, boolean isPinRequired, boolean hasSocialNotifiers) {
        if ((StringUtils.startsWithIgnoreCase(message, smsCommand + "t") || StringUtils.startsWithIgnoreCase(message, smsShortCommand + "t")) && hasSocialNotifiers) {
            auditCommand(context, message);
            if (findKeyword(context, smsCommand + "t", message, pin, sender, isPinRequired)) {
                onSocialCommandFound(null, context);
                return true;
            } else if (findKeyword(context, smsShortCommand + "t", message, pin, sender, isPinRequired)) {
                onSocialCommandFound(null, context);
                return true;
            }
        }
        return false;
    }

    protected final boolean findAppCommand(Context context, String message, String sender, Location location, Bundle extras, String pin, boolean isPinRequired) {
        if (StringUtils.startsWithIgnoreCase(message, smsCommand + "app") || StringUtils.startsWithIgnoreCase(message, smsShortCommand + "app")) {
            auditCommand(context, message);
            if (findKeyword(context, smsCommand + "app", message, pin, sender, isPinRequired)) {
                onAppCommandFound(sender, context, location, extras);
                return true;
            } else if (findKeyword(context, smsShortCommand + "app", message, pin, sender, isPinRequired)) {
                onAppCommandFound(sender, context, location, extras);
                return true;
            }
        }
        return false;
    }

    private boolean findKeyword(Context context, String keyword, String message, String pin, String sender, boolean isPinRequired) {
        if (finder.equals(Finder.EQUALS)) {
            return (findCommandInMessage(context, keyword, message, pin, sender, isPinRequired) == 1);
        } else if (finder.equals(Finder.STARTS)) {
            return (findCommandInMessage(context, keyword, message, pin, sender, isPinRequired) == 1 && validateTokens());
        } else {
            return false;
        }
    }

    private int findCommandInMessage(Context context, String command, String message, String pin, String sender, boolean isPinRequired) {
        //<command><pin> <args> or <command> <pin> <args>
        Log.d(TAG, "Checking " + message + " with " + command + " and " + pin);
        int foundCommand = 0;
        if (finder == Finder.EQUALS || finder == Finder.STARTS) {
            commandTokens = message.split(" ");
            if (commandTokens.length >= 1) {
                if (StringUtils.equalsIgnoreCase(commandTokens[0], command + pin)) {
                    foundCommand = 1;
                }
                if (foundCommand == 0 && commandTokens.length >= 2) {
                    if (StringUtils.equalsIgnoreCase(commandTokens[0], command) && (!isPinRequired || StringUtils.equals(commandTokens[1], pin))) {
                        foundCommand = 1;
                    }
                }
                if (foundCommand == 0 && StringUtils.equalsIgnoreCase(commandTokens[0], command)) {
                    sendSocialNotification(context, Command.INVALID_PIN, sender, command);
                    Log.e(TAG, "Command " + commandTokens[0] + " with invalid Security PIN received!");
                    foundCommand = -1;
                } else if (foundCommand == 0 && StringUtils.startsWithIgnoreCase(commandTokens[0], command) && StringUtils.isNumeric(StringUtils.substring(commandTokens[0], commandTokens[0].length() - pin.length()))) {
                    sendSocialNotification(context, Command.INVALID_PIN, sender, command);
                    Log.e(TAG, "Command " + commandTokens[0] + " with invalid Security PIN received!");
                    foundCommand = -1;
                }
            }
        }
        return foundCommand;
    }

    void sendSocialNotification(final Context context, final String command, final String sender, final String invalidCommand) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final String email = settings.getString(MainActivity.NOTIFICATION_EMAIL, "");
        final String telegramId = settings.getString(MainActivity.NOTIFICATION_SOCIAL, "");
        final String phoneNumber = settings.getString(MainActivity.NOTIFICATION_PHONE_NUMBER, "");
        Intent newIntent = new Intent(context, SmsSenderService.class);
        newIntent.putExtra("telegramId", telegramId);
        newIntent.putExtra("email", email);
        newIntent.putExtra("phoneNumber", phoneNumber);
        if (StringUtils.isNotEmpty(command)) {
            newIntent.putExtra("command", command);
        }
        if (StringUtils.isNotEmpty(sender)) {
            newIntent.putExtra("sender", sender);
        }
        newIntent.putExtra("source", "mobile");
        if (StringUtils.isNotEmpty(invalidCommand)) {
            newIntent.putExtra("invalidCommand", invalidCommand);
        }
        context.startService(newIntent);
    }

    void sendAppNotification(final Context context, final String command, final String sender) {
        if (sender != null) {
            Intent newIntent = new Intent(context, SmsSenderService.class);
            if (StringUtils.isNotEmpty(command)) {
                newIntent.putExtra("command", command);
            }
            newIntent.putExtra("app", sender);
            context.startService(newIntent);
        }
    }

    void sendSmsNotification(final Context context, final String sender, final String command) {
        Intent newIntent = new Intent(context, SmsSenderService.class);
        newIntent.putExtra("phoneNumber", sender);
        if (StringUtils.isNotEmpty(command)) {
            newIntent.putExtra("command", command);
        }
        context.startService(newIntent);
    }

    private void auditCommand(Context context, String command) {
        File auditFile = Files.getFilesDir(context, AUDIT_FILE, false);
        Files.appendLineToFileFromContextDir(auditFile, System.currentTimeMillis() + " " + command, 100, 10);
    }
}
