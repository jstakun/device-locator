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

    public final String getSmsCommand() {
        return smsCommand;
    }

    public final void setCommandTokens(String[] commandTokens) {
        this.commandTokens = commandTokens;
    }


    protected final boolean findSmsCommand(Context context, final String smsMessage, final String sender, final String pin, final boolean isPinRequired, final boolean hasSocialNotifiers) {
        boolean commandFound = false;
        //sms with sms notification
        if (StringUtils.startsWithIgnoreCase(smsMessage, smsCommand)) {
            commandFound = findCommandInMessage(context, smsCommand, smsMessage, pin, isPinRequired);
            auditCommand(context, smsCommand);
        }
        if (!commandFound && StringUtils.startsWithIgnoreCase(smsMessage, smsShortCommand)) {
            commandFound = findCommandInMessage(context, smsShortCommand, smsMessage, pin, isPinRequired);
            auditCommand(context, smsShortCommand);
        }
        if (commandFound) {
            onSmsCommandFound(sender, context);
            return true;
        } else if ((StringUtils.startsWithIgnoreCase(smsMessage, smsCommand + "t") || StringUtils.startsWithIgnoreCase(smsMessage, smsShortCommand + "t")) && hasSocialNotifiers) {
            //sms with social notification
            commandFound = findCommandInMessage(context, smsCommand + "t", smsMessage, pin, isPinRequired);
            if (!commandFound && StringUtils.startsWithIgnoreCase(smsMessage, smsShortCommand)) {
                commandFound = findCommandInMessage(context, smsShortCommand + "t", smsMessage, pin, isPinRequired);
            }
            if (commandFound) {
                onSocialCommandFound(sender, context);
                return true;
            }
        }
        return false;
    }

    protected final boolean findSocialCommand(Context context, String message, String pin, boolean isPinRequired, boolean hasSocialNotifiers) {
        if ((StringUtils.startsWithIgnoreCase(message, smsCommand + "t") || StringUtils.startsWithIgnoreCase(message, smsShortCommand + "t")) && hasSocialNotifiers) {
            auditCommand(context, message);
            if (findKeyword(context, smsCommand + "t", message, pin, isPinRequired)) {
                onSocialCommandFound(null, context);
                return true;
            } else if (findKeyword(context, smsShortCommand + "t", message, pin, isPinRequired)) {
                onSocialCommandFound(null, context);
                return true;
            }
        }
        return false;
    }

    protected final boolean findAppCommand(Context context, String message, String sender, Location location, Bundle extras, String pin, boolean isPinRequired) {
        if (StringUtils.startsWithIgnoreCase(message, smsCommand + "app") || StringUtils.startsWithIgnoreCase(message, smsShortCommand + "app")) {
            auditCommand(context, message);
            if (findKeyword(context, smsCommand + "app", message, pin, isPinRequired)) {
                onAppCommandFound(sender, context, location, extras);
                return true;
            } else if (findKeyword(context, smsShortCommand + "app", message, pin, isPinRequired)) {
                onAppCommandFound(sender, context, location, extras);
                return true;
            }
        }
        return false;
    }

    private boolean findKeyword(Context context, String keyword, String message, String pin, boolean isPinRequired) {
        if (finder.equals(Finder.EQUALS)) {
            return findCommandInMessage(context, keyword, message, pin, isPinRequired);
        } else if (finder.equals(Finder.STARTS)) {
            return findCommandInMessage(context, keyword, message, pin, isPinRequired) && validateTokens();
        } else {
            return false;
        }
    }

    private boolean findCommandInMessage(Context context, String command, String message, String pin, boolean isPinRequired) {
        //<command><pin> <args> or <command> <pin> <args>
        boolean foundCommand = false;
        if (finder == Finder.EQUALS || finder == Finder.STARTS) {
            commandTokens = message.split(" ");
            if (commandTokens.length >= 1) {
                foundCommand = StringUtils.equalsIgnoreCase(commandTokens[0], command + pin);
                if (!foundCommand) {
                    if (commandTokens.length >= 2) {
                        foundCommand = StringUtils.equalsIgnoreCase(commandTokens[0], command) && (!isPinRequired || StringUtils.equals(commandTokens[1], pin));
                    } else if (!isPinRequired) {
                        foundCommand = StringUtils.equalsIgnoreCase(commandTokens[0], command);
                    } else if (StringUtils.equalsIgnoreCase(commandTokens[0], command)) {
                        sendSocialNotification(context, Command.INVALID_PIN);
                        Log.e(TAG, "Command " + commandTokens[0] + " without valid Security PIN received!");
                    }
                }
            }
        }
        return foundCommand;
    }

    void sendSocialNotification(final Context context, final String command) {
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

    private void auditCommand(Context context, String command) {
        File auditFile = Files.getFilesDir(context, AUDIT_FILE, false);
        Files.appendLineToFileFromContextDir(auditFile, System.currentTimeMillis() + " " + command, 100, 10);
    }
}
